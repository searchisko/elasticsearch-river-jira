package org.jboss.elasticsearch.river.jira;

import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

  /**
   * How often is project list refreshed from JIRA instance [ms].
   */
  protected static final long JIRA_PROJECTS_REFRESH_TIME = 30 * 60 * 1000;

  /**
   * ElasticSearch client to be used for indexing
   */
  protected Client client;

  /**
   * Configured JIRA client to access data from JIRA
   */
  protected IJIRAClient jiraClient;

  /**
   * Config - maximal number of parallel JIRA indexing threads
   */
  protected int maxIndexingThreads;

  /**
   * Config - index update period [ms]
   */
  protected int indexUpdatePeriod;

  /**
   * Config - name of ElasticSearch index used to store issues from this river
   */
  protected final String indexName;

  /**
   * Thread running {@link JIRAProjectIndexerCoordinator} is stored here.
   */
  protected Thread coordinatorThread;

  /**
   * USed {@link JIRAProjectIndexerCoordinator} instance is stored here.
   */
  protected IJIRAProjectIndexerCoordinator coordinatorInstance;

  /**
   * Flag set to true if this river is stopped from ElasticSearch server.
   */
  protected volatile boolean closed = false;

  /**
   * List of indexing excluded JIRA project keys loaded from river configuration
   * 
   * @see #getAllIndexedProjectsKeys()
   */
  protected List<String> projectKeysExcluded = null;

  /**
   * List of all JIRA project keys to be indexed. Loaded from river configuration, or from remote JIRA (excludes
   * removed)
   * 
   * @see #getAllIndexedProjectsKeys()
   */
  protected List<String> allIndexedProjectsKeys = null;

  /**
   * Next time when {@link #allIndexedProjectsKeys} need to be refreshed from remote JIRA instance.
   * 
   * @see #getAllIndexedProjectsKeys()
   */
  protected long allIndexedProjectsKeysNextRefresh = 0;

  @SuppressWarnings({ "unchecked" })
  @Inject
  public JiraRiver(RiverName riverName, RiverSettings settings, Client client) throws MalformedURLException {
    super(riverName, settings);
    this.client = client;

    String url = null;

    if (settings.settings().containsKey("jira")) {
      Map<String, Object> jiraSettings = (Map<String, Object>) settings.settings().get("jira");
      url = XContentMapValues.nodeStringValue(jiraSettings.get("urlBase"), null);
      if (url == null || url.trim().length() == 0) {
        throw new SettingsException("jira/urlBase element of configuration structure not found or empty");
      }
      Integer timeout = null;
      if (jiraSettings.get("timeout") != null) {
        timeout = XContentMapValues.nodeIntegerValue(jiraSettings.get("timeout"));
      }
      jiraClient = new JIRA5RestClient(url, XContentMapValues.nodeStringValue(jiraSettings.get("username"), null),
          XContentMapValues.nodeStringValue(jiraSettings.get("pwd"), null), timeout);
      jiraClient.setListJIRAIssuesMax(XContentMapValues.nodeIntegerValue(jiraSettings.get("maxIssuesPerRequest"), 50));
      maxIndexingThreads = XContentMapValues.nodeIntegerValue(jiraSettings.get("maxIndexingThreads"), 1);
      indexUpdatePeriod = XContentMapValues.nodeIntegerValue(jiraSettings.get("indexUpdatePeriod"), 5) * 60 * 1000;
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
      throw new SettingsException("jira element of configuration structure not found");
    }

    logger.info("creating JIRA River for JIRA base URL  [{}]", url);

    if (settings.settings().containsKey("index")) {
      Map<String, Object> indexSettings = (Map<String, Object>) settings.settings().get("index");
      indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name());
    } else {
      indexName = riverName.name();
    }
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
      } else {
        logger.warn("failed to create index [{}], disabling river...", e, indexName);
        return;
      }
    }

    coordinatorInstance = new JIRAProjectIndexerCoordinator(jiraClient, this, indexUpdatePeriod, maxIndexingThreads);
    coordinatorThread = acquireIndexingThread("jira_river_coordinator", coordinatorInstance);
    coordinatorThread.start();
  }

  @Override
  public void close() {
    logger.info("closing JIRA River");
    closed = true;
    if (coordinatorThread != null) {
      coordinatorThread.interrupt();
    }
    // free instances created in #start()
    coordinatorThread = null;
    coordinatorInstance = null;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public List<String> getAllIndexedProjectsKeys() throws Exception {
    if (allIndexedProjectsKeys == null || allIndexedProjectsKeysNextRefresh < System.currentTimeMillis()) {
      allIndexedProjectsKeys = jiraClient.getAllJIRAProjects();
      if (projectKeysExcluded != null) {
        allIndexedProjectsKeys.removeAll(projectKeysExcluded);
      }
      allIndexedProjectsKeysNextRefresh = System.currentTimeMillis() + JIRA_PROJECTS_REFRESH_TIME;
    }

    return allIndexedProjectsKeys;
  }

  @Override
  public void reportIndexingFinished(String jiraProjectKey, boolean finishedOK, int issuesUpdated, long timeElapsed,
      String errorMessage) {
    if (coordinatorInstance != null) {
      coordinatorInstance.reportIndexingFinished(jiraProjectKey, finishedOK);
    }
    // TODO log JIRA project indexing result for statistics and info reasons
  }

  @Override
  public void storeDatetimeValue(String projectKey, String propertyName, Date datetime, BulkRequestBuilder esBulk)
      throws IOException {
    String documentName = prepareValueStoreDocumentName(projectKey, propertyName);
    if (esBulk != null) {
      esBulk.add(indexRequest("_river")
          .type(riverName.name())
          .id(documentName)
          .source(
              jsonBuilder().startObject().field("projectKey", projectKey).field("propertyName", propertyName)
                  .field("value", datetime).endObject()));
    } else {
      client.prepareIndex("_river", riverName.name(), documentName)
          .setSource(jsonBuilder().startObject().field("timestamp", datetime).endObject()).execute().actionGet();
    }
  }

  @Override
  public Date readDatetimeValue(String projectKey, String propertyName) throws IOException {
    Date lastDate = null;
    String documentName = prepareValueStoreDocumentName(projectKey, propertyName);
    client.admin().indices().prepareRefresh("_river").execute().actionGet();
    GetResponse lastSeqGetResponse = client.prepareGet("_river", riverName().name(), documentName).execute()
        .actionGet();
    if (lastSeqGetResponse.exists()) {
      Object timestamp = lastSeqGetResponse.sourceAsMap().get("value");
      if (timestamp != null) {
        lastDate = ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(timestamp.toString()).toDate();
      }
    } else {
      if (logger.isDebugEnabled())
        logger.debug("{} document doesn't exist in JIRA river persistent store", documentName);
    }
    return lastDate;
  }

  /**
   * Prepare name of document where jira project related persistent value is stored
   * 
   * @param projectKey key of jira project stored value is for
   * @param propertyName name of value
   * @return document name
   * 
   * @see #storeDatetimeValue(String, String, Date, BulkRequestBuilder)
   * @see #readDatetimeValue(String, String)
   */
  protected static String prepareValueStoreDocumentName(String projectKey, String propertyName) {
    return "_" + propertyName + "_" + projectKey;
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

  @Override
  public Thread acquireIndexingThread(String threadName, Runnable runnable) {
    return EsExecutors.daemonThreadFactory(settings.globalSettings(), threadName).newThread(runnable);
  }

  /**
   * Parse comma separated string into list of tokens. Tokens are trimmed, empty tokens are not in result.
   * 
   * @param toParse String to parse
   * @return List of tokens if at least one token exists, null otherwise.
   */
  public static List<String> parseCsvString(String toParse) {
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
}
