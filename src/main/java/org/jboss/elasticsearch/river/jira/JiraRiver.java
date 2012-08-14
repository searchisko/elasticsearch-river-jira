package org.jboss.elasticsearch.river.jira;

import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;

/**
 * JIRA River implementation class
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiver extends AbstractRiverComponent implements River, IESIntegration {

  protected static final int coordinatorThreadWaits = 1000;

  /**
   * How often is project list refreshed from JIRA instance [ms].
   */
  protected static final long JIRA_PROJECTS_REFRESH_TIME = 30 * 60 * 1000;

  protected Client client;

  protected IJIRAClient jiraClient;

  protected final String indexName;

  protected Thread thread;

  protected volatile boolean closed = false;

  @SuppressWarnings({ "unchecked" })
  @Inject
  public JiraRiver(RiverName riverName, RiverSettings settings, Client client) throws MalformedURLException {
    super(riverName, settings);
    this.client = client;

    String url = null;

    if (settings.settings().containsKey("jira")) {
      Map<String, Object> jiraSettings = (Map<String, Object>) settings.settings().get("jira");
      url = XContentMapValues.nodeStringValue(jiraSettings.get("urlBase"), null);
      Integer timeout = null;
      if (jiraSettings.get("timeout") != null) {
        try {
          timeout = new Integer(XContentMapValues.nodeIntegerValue(jiraSettings.get("timeout")));
        } catch (NumberFormatException e) {
          logger.warn("timeout parameter is not valid number");
        }
      }
      jiraClient = new JIRA5RestClient(url, XContentMapValues.nodeStringValue(jiraSettings.get("username"), null),
          XContentMapValues.nodeStringValue(jiraSettings.get("pwd"), null), timeout);
      jiraClient.setListJIRAIssuesMax(XContentMapValues.nodeIntegerValue(jiraSettings.get("maxIssuesPerRequest"), 50));
      if (jiraSettings.containsKey("projectKeysIndexed")) {
        allIndexedProjectsKeys = parseCsvString(XContentMapValues.nodeStringValue(
            jiraSettings.get("projectKeysIndexed"), null));
        if (allIndexedProjectsKeys != null) {
          // stop loading from JIRA
          allIndexedProjectsKeysNextRefresh = Long.MAX_VALUE;
        }
      }
      if (jiraSettings.containsKey("projectKeysExcluded")) {
        projectKeysExcluded = parseCsvString(XContentMapValues.nodeStringValue(jiraSettings.get("projectKeysExcluded"),
            null));
      }

    } else {
      throw new SettingsException("jira configuration structure not found");
    }

    logger.info("creating JIRA River for JIRA base URL  [{}]", url);

    if (settings.settings().containsKey("index")) {
      Map<String, Object> indexSettings = (Map<String, Object>) settings.settings().get("index");
      indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name());
    } else {
      indexName = riverName.name();
    }
  }

  /**
   * Parse comma separated string into list of tokens. Tokens are trimmed, empty tokens are not in result.
   * 
   * @param toParse String to parse
   * @return List of tokens if at least one token exists, null otherwise.
   */
  protected static List<String> parseCsvString(String toParse) {
    if (toParse == null || toParse.length() == 0) {
      return null;
    }
    String[] t = toParse.split(",");
    if (t.length == 0) {
      return null;
    }
    List<String> ret = new ArrayList<String>();
    for (String s : t) {
      if (s != null) {
        s = s.trim();
        if (s.length() > 0) {
          ret.add(s);
        }
      }
    }
    if (ret.isEmpty())
      return null;
    else
      return ret;
  }

  @Override
  public void start() {
    logger.info("starting JIRA River");
    try {
      client.admin().indices().prepareCreate(indexName).execute().actionGet();
    } catch (Exception e) {
      if (ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException) {
        // that's fine
      } else if (ExceptionsHelper.unwrapCause(e) instanceof ClusterBlockException) {
        // ok, not recovered yet..., lets start indexing and hope we recover by the first bulk
        // TODO: a smarter logic can be to register for cluster event listener here, and only start sampling when the
        // block is removed...
      } else {
        logger.warn("failed to create index [{}], disabling river...", e, indexName);
        return;
      }
    }

    thread = EsExecutors.daemonThreadFactory(settings.globalSettings(), "jira_slurper_coordinator").newThread(
        new JIRAProjectIndexerCoordinator());
    thread.start();
  }

  @Override
  public void close() {
    logger.info("closing JIRA River");
    closed = true;
    if (thread != null) {
      thread.interrupt();
    }
    // TODO close other threads processing JIRA projects if any
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  protected List<String> allIndexedProjectsKeys = null;
  protected List<String> projectKeysExcluded = null;
  protected long allIndexedProjectsKeysNextRefresh = 0;
  protected Queue<String> projectKeysToIndexQueue = new LinkedBlockingQueue<String>();
  protected volatile ArrayList<JIRAProjectIndexer> projectIndexers;

  /**
   * Get JIRA project keys for all projects which needs to be indexed. Is loaded from river configuration or from JIRA
   * instance - depends on river configuration.
   * 
   * @return list of project keys.
   * @throws Exception
   */
  protected List<String> getAllIndexedProjectsKeys() throws Exception {
    if (allIndexedProjectsKeys == null || allIndexedProjectsKeysNextRefresh < System.currentTimeMillis()) {
      allIndexedProjectsKeys = jiraClient.getAllJIRAProjects();
      if (projectKeysExcluded != null) {
        allIndexedProjectsKeys.removeAll(projectKeysExcluded);
      }
      allIndexedProjectsKeysNextRefresh = System.currentTimeMillis() + JIRA_PROJECTS_REFRESH_TIME;
    }

    return allIndexedProjectsKeys;
  }

  public class JIRAProjectIndexerCoordinator implements Runnable {

    @Override
    public void run() {
      logger.info("JIRA river projects indexing coordinator task started");
      while (true) {
        if (closed) {
          logger.info("JIRA river projects indexing coordinator task stopped");
          return;
        }
        try {
          // TODO check which JIRA projects need index updates and coordinate parallel threads to do this.
        } catch (Exception e) {
          if (closed)
            return;
          logger.error("Failed to process JIRA update coordination task " + e.getMessage(), e);
        }
        try {
          if (logger.isDebugEnabled())
            logger.debug("JIRA river coordinator task is going to sleep for {} ms", coordinatorThreadWaits);
          Thread.sleep(coordinatorThreadWaits);
        } catch (InterruptedException e1) {
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Date getLastIssueUpdatedDate(String jiraProjectKey) {
    Date lastDate = null;
    String lastupdateField = getLastIssueUpdatedDateFieldName(jiraProjectKey);
    try {
      client.admin().indices().prepareRefresh("_river").execute().actionGet();
      GetResponse lastSeqGetResponse = client.prepareGet("_river", riverName().name(), lastupdateField).execute()
          .actionGet();
      if (lastSeqGetResponse.exists()) {
        Map<String, Object> jiraProjectLastUpdateState = (Map<String, Object>) lastSeqGetResponse.sourceAsMap().get(
            "lastupdatedissuedate");

        if (jiraProjectLastUpdateState != null) {
          Object lastupdate = jiraProjectLastUpdateState.get(lastupdateField);
          if (lastupdate != null) {
            String strLastDate = lastupdate.toString();
            lastDate = ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(strLastDate).toDate();
          }
        }
      } else {
        if (logger.isDebugEnabled())
          logger.debug("{} doesn't exist in JIRA river persistent store", lastupdateField);
      }
    } catch (Exception e) {
      throw new ElasticSearchException("Failed to get " + lastupdateField, e);
    }
    return lastDate;
  }

  /**
   * Get field name used to store "last issue updated" value in ES river index
   * 
   * @param jiraProjectKey key of JIRA project to store value for
   * @return name of field used to store value in ES river index
   * 
   * @see #getLastIssueUpdatedDate(String)
   * @see #storeLastIssueUpdatedDate(BulkRequestBuilder, String, Date)
   */
  protected String getLastIssueUpdatedDateFieldName(String jiraProjectKey) {
    return "_lastupdatedissue_" + jiraProjectKey;
  }

  @Override
  public void storeLastIssueUpdatedDate(BulkRequestBuilder esBulk, String jiraProjectKey, Date lastIssueUpdatedDate)
      throws Exception {
    String lastupdateField = getLastIssueUpdatedDateFieldName(jiraProjectKey);
    esBulk.add(indexRequest("_river")
        .type(riverName.name())
        .id(lastupdateField)
        .source(
            jsonBuilder().startObject().startObject("lastupdatedissuedate")
                .field(lastupdateField, lastIssueUpdatedDate).endObject().endObject()));
  }

  @Override
  public BulkRequestBuilder getESBulkRequestBuilder() {
    return client.prepareBulk();
  }

  @Override
  public void executeESBulkRequestBuilder(BulkRequestBuilder esBulk) throws Exception {
    BulkResponse response = esBulk.execute().actionGet();
    if (response.hasFailures()) {
      throw new ElasticSearchException("Failed to execute ES index bulk update: " + response.buildFailureMessage());
    }
  }
}
