package org.jboss.elasticsearch.river.jira;

import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.ElasticSearchParseException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.jboss.elasticsearch.tools.content.StructuredContentPreprocessorFactory;

/**
 * JIRA River implementation class.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiver extends AbstractRiverComponent implements River, IESIntegration {

  /**
   * How often is project list refreshed from JIRA instance [ms].
   */
  protected static final long JIRA_PROJECTS_REFRESH_TIME = 30 * 60 * 1000;

  public static final String INDEX_ISSUE_TYPE_NAME_DEFAULT = "jira_issue";

  public static final String INDEX_ACTIVITY_TYPE_NAME_DEFAULT = "jira_river_indexupdate";

  /**
   * ElasticSearch client to be used for indexing
   */
  protected Client client;

  /**
   * Configured JIRA client to access data from JIRA
   */
  protected IJIRAClient jiraClient;

  /**
   * Configured JIRA issue index structure builder to be used.
   */
  protected IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilder;

  /**
   * Config - maximal number of parallel JIRA indexing threads
   */
  protected int maxIndexingThreads;

  /**
   * Config - index update period [ms]
   */
  protected long indexUpdatePeriod;

  /**
   * Config - index full update period [ms]
   */
  protected long indexFullUpdatePeriod = -1;

  /**
   * Config - name of ElasticSearch index used to store issues from this river
   */
  protected String indexName;

  /**
   * Config - name of ElasticSearch type used to store issues from this river in index
   */
  protected String typeName;

  /**
   * Config - Base URL of JIRA instance to index by this river
   */
  protected String jiraUrlBase = null;

  /**
   * Config - name of ElasticSearch index used to store river activity records - null means no activity stored
   */
  protected String activityLogIndexName;

  /**
   * Config - name of ElasticSearch type used to store river activity records in index
   */
  protected String activityLogTypeName;

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

  /**
   * Public constructor used by ElasticSearch.
   * 
   * @param riverName
   * @param settings
   * @param client
   * @throws MalformedURLException
   */
  @SuppressWarnings({ "unchecked" })
  @Inject
  public JiraRiver(RiverName riverName, RiverSettings settings, Client client) throws MalformedURLException {
    super(riverName, settings);
    this.client = client;

    String jiraUser = null;
    String jiraJqlTimezone = TimeZone.getDefault().getDisplayName();

    if (settings.settings().containsKey("jira")) {
      Map<String, Object> jiraSettings = (Map<String, Object>) settings.settings().get("jira");
      jiraUrlBase = XContentMapValues.nodeStringValue(jiraSettings.get("urlBase"), null);
      if (Utils.isEmpty(jiraUrlBase)) {
        throw new SettingsException("jira/urlBase element of configuration structure not found or empty");
      }
      Integer timeout = new Long(parseTimeValue(jiraSettings, "timeout", 5, TimeUnit.SECONDS)).intValue();
      jiraUser = XContentMapValues.nodeStringValue(jiraSettings.get("username"), "Anonymous access");
      jiraClient = new JIRA5RestClient(jiraUrlBase, XContentMapValues.nodeStringValue(jiraSettings.get("username"),
          null), XContentMapValues.nodeStringValue(jiraSettings.get("pwd"), null), timeout);
      jiraClient.setListJIRAIssuesMax(XContentMapValues.nodeIntegerValue(jiraSettings.get("maxIssuesPerRequest"), 50));
      if (jiraSettings.get("jqlTimeZone") != null) {
        TimeZone tz = TimeZone.getTimeZone(XContentMapValues.nodeStringValue(jiraSettings.get("jqlTimeZone"), null));
        jiraJqlTimezone = tz.getDisplayName();
        jiraClient.setJQLDateFormatTimezone(tz);
      }
      maxIndexingThreads = XContentMapValues.nodeIntegerValue(jiraSettings.get("maxIndexingThreads"), 1);
      indexUpdatePeriod = parseTimeValue(jiraSettings, "indexUpdatePeriod", 5, TimeUnit.MINUTES);
      indexFullUpdatePeriod = parseTimeValue(jiraSettings, "indexFullUpdatePeriod", 12, TimeUnit.HOURS);
      if (jiraSettings.containsKey("projectKeysIndexed")) {
        allIndexedProjectsKeys = Utils.parseCsvString(XContentMapValues.nodeStringValue(
            jiraSettings.get("projectKeysIndexed"), null));
        if (allIndexedProjectsKeys != null) {
          // stop loading from JIRA
          allIndexedProjectsKeysNextRefresh = Long.MAX_VALUE;
        }
      }
      if (jiraSettings.containsKey("projectKeysExcluded")) {
        projectKeysExcluded = Utils.parseCsvString(XContentMapValues.nodeStringValue(
            jiraSettings.get("projectKeysExcluded"), null));
      }
    } else {
      throw new SettingsException("'jira' element of river configuration structure not found");
    }

    Map<String, Object> indexSettings = null;
    if (settings.settings().containsKey("index")) {
      indexSettings = (Map<String, Object>) settings.settings().get("index");
      indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name());
      typeName = XContentMapValues.nodeStringValue(indexSettings.get("type"), INDEX_ISSUE_TYPE_NAME_DEFAULT);
    } else {
      indexName = riverName.name();
      typeName = INDEX_ISSUE_TYPE_NAME_DEFAULT;
    }

    Map<String, Object> activityLogSettings = null;
    if (settings.settings().containsKey("activity_log")) {
      activityLogSettings = (Map<String, Object>) settings.settings().get("activity_log");
      activityLogIndexName = Utils
          .trimToNull(XContentMapValues.nodeStringValue(activityLogSettings.get("index"), null));
      if (activityLogIndexName == null) {
        throw new SettingsException(
            "'activity_log/index' element of river configuration structure must be defined with some string");
      }
      activityLogTypeName = Utils.trimToNull(XContentMapValues.nodeStringValue(activityLogSettings.get("type"),
          INDEX_ACTIVITY_TYPE_NAME_DEFAULT));
    }

    jiraIssueIndexStructureBuilder = new JIRA5RestIssueIndexStructureBuilder(riverName.getName(), indexName, typeName,
        jiraUrlBase, indexSettings);
    preparePreprocessors(indexSettings, jiraIssueIndexStructureBuilder);

    jiraClient.setIndexStructureBuilder(jiraIssueIndexStructureBuilder);

    logger
        .info(
            "Created JIRA River for JIRA base URL [{}], jira user '{}', JQL timezone '{}'. Search index name '{}', document type for issues '{}'.",
            jiraUrlBase, jiraUser, jiraJqlTimezone, indexName, typeName);
    if (activityLogIndexName != null) {
      logger.info(
          "Activity log for JIRA River is enabled. Search index name '{}', document type for index updates '{}'.",
          activityLogIndexName, activityLogTypeName);
    }

  }

  @SuppressWarnings("unchecked")
  private void preparePreprocessors(Map<String, Object> indexSettings,
      IJIRAIssueIndexStructureBuilder indexStructureBuilder) {
    if (indexSettings != null) {
      List<Map<String, Object>> preproclist = (List<Map<String, Object>>) indexSettings.get("preprocessors");
      if (preproclist != null && preproclist.size() > 0) {
        for (Map<String, Object> ppc : preproclist) {
          try {
            indexStructureBuilder.addIssueDataPreprocessor(StructuredContentPreprocessorFactory.createPreprocessor(ppc,
                client));
          } catch (IllegalArgumentException e) {
            throw new SettingsException(e.getMessage(), e);
          }
        }
      }
    }
  }

  /**
   * Constructor for unit tests, nothing is initialized in river.
   * 
   * @param riverName
   * @param settings
   */
  protected JiraRiver(RiverName riverName, RiverSettings settings) {
    super(riverName, settings);
  }

  /**
   * Parse time value from river settings/config map. Value must be number, which is normaly in milliseconds, but you
   * can postfix it by one of next letters to set units
   * <ul>
   * <li><code>s</code> - seconds
   * <li><code>m</code> - minutes
   * <li><code>h</code> - hours
   * <li><code>d</code> - days
   * <li><code>w</code> - weeks
   * </ul>
   * 
   * @param jiraSettings map to get value from
   * @param key of config value in map
   * @param defaultDuration default duration used if no value in config
   * @param defaultTimeUnit time unit for default duration - if null no default is used, so return 0 as default in this
   *          case
   * @return time value in millis
   */
  protected static long parseTimeValue(Map<String, Object> jiraSettings, String key, long defaultDuration,
      TimeUnit defaultTimeUnit) {
    long ret = 0;
    if (jiraSettings == null || !jiraSettings.containsKey(key)) {
      if (defaultTimeUnit != null) {
        ret = new TimeValue(defaultDuration, defaultTimeUnit).millis();
      }
    } else {
      try {
        ret = TimeValue.parseTimeValue(XContentMapValues.nodeStringValue(jiraSettings.get(key), null),
            new TimeValue(defaultDuration, defaultTimeUnit)).millis();
      } catch (ElasticSearchParseException e) {
        throw new ElasticSearchParseException(e.getMessage() + " for setting: " + key);
      }
    }
    return ret;
  }

  @Override
  public void start() {
    logger.info("starting JIRA River");
    coordinatorInstance = new JIRAProjectIndexerCoordinator(jiraClient, this, jiraIssueIndexStructureBuilder,
        indexUpdatePeriod, maxIndexingThreads, indexFullUpdatePeriod);
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
  public void reportIndexingFinished(String jiraProjectKey, boolean finishedOK, boolean fullUpdate, int issuesUpdated,
      int issuesDeleted, Date startDate, long timeElapsed, String errorMessage) {
    if (coordinatorInstance != null) {
      coordinatorInstance.reportIndexingFinished(jiraProjectKey, finishedOK, fullUpdate);
    }
    if (activityLogIndexName != null) {
      try {
        client
            .prepareIndex(activityLogIndexName, activityLogTypeName)
            .setSource(
                prepareUpdateActivityLogDocument(jiraProjectKey, finishedOK, fullUpdate, issuesUpdated, issuesDeleted,
                    startDate, timeElapsed, errorMessage)).execute().actionGet();
      } catch (Exception e) {
        logger.error("Error during update result witing to the audit log {}", e.getMessage());
      }
    }
  }

  /**
   * Prepare document to log update activity result.
   * 
   * @param projectKey
   * @param finishedOK
   * @param fullUpdate
   * @param issuesUpdated
   * @param issuesDeleted
   * @param startDate
   * @param timeElapsed
   * @param errorMessage
   * @return document to store into search index
   * 
   * @see #reportIndexingFinished(String, boolean, boolean, int, int, Date, long, String)
   * @throws IOException
   */
  protected XContentBuilder prepareUpdateActivityLogDocument(String projectKey, boolean finishedOK, boolean fullUpdate,
      int issuesUpdated, int issuesDeleted, Date startDate, long timeElapsed, String errorMessage) throws IOException {
    XContentBuilder builder = jsonBuilder().startObject();
    builder.field("projectKey", projectKey);
    builder.field("updateType", fullUpdate ? "FULL" : "INCREMENTAL");
    builder.field("result", finishedOK ? "OK" : "ERROR");
    builder.field("startDate", startDate).field("timeElapsed", timeElapsed + "ms");
    builder.field("issuesUpdated", issuesUpdated).field("issuesDeleted", issuesDeleted);
    if (!finishedOK && !Utils.isEmpty(errorMessage)) {
      builder.field("errorMessage", errorMessage);
    }
    builder.endObject();

    return builder;
  }

  @Override
  public void storeDatetimeValue(String projectKey, String propertyName, Date datetime, BulkRequestBuilder esBulk)
      throws IOException {
    String documentName = prepareValueStoreDocumentName(projectKey, propertyName);
    if (logger.isDebugEnabled())
      logger.debug(
          "Going to write {} property with datetime value {} for project {} using {} update. Document name is {}.",
          propertyName, datetime, projectKey, (esBulk != null ? "bulk" : "direct"), documentName);
    if (esBulk != null) {
      esBulk.add(indexRequest("_river").type(riverName.name()).id(documentName)
          .source(storeDatetimeValueBuildDocument(projectKey, propertyName, datetime)));
    } else {
      client.prepareIndex("_river", riverName.name(), documentName)
          .setSource(storeDatetimeValueBuildDocument(projectKey, propertyName, datetime)).execute().actionGet();
    }
  }

  /**
   * Constant for field in JSON document used to store values.
   * 
   * @see #storeDatetimeValue(String, String, Date, BulkRequestBuilder)
   * @see #readDatetimeValue(String, String)
   * @see #storeDatetimeValueBuildDocument(String, String, Date)
   * 
   */
  protected static final String STORE_FIELD_VALUE = "value";

  /**
   * Prepare JSON document to be stored inside {@link #storeDatetimeValue(String, String, Date, BulkRequestBuilder)}.
   * 
   * @param projectKey key of project value is for
   * @param propertyName name of property
   * @param datetime value to store
   * @return JSON document
   * @throws IOException
   * @see #storeDatetimeValue(String, String, Date, BulkRequestBuilder)
   * @see #readDatetimeValue(String, String)
   */
  protected XContentBuilder storeDatetimeValueBuildDocument(String projectKey, String propertyName, Date datetime)
      throws IOException {
    return jsonBuilder().startObject().field("projectKey", projectKey).field("propertyName", propertyName)
        .field(STORE_FIELD_VALUE, DateTimeUtils.formatISODateTime(datetime)).endObject();
  }

  @Override
  public Date readDatetimeValue(String projectKey, String propertyName) throws IOException {
    Date lastDate = null;
    String documentName = prepareValueStoreDocumentName(projectKey, propertyName);

    if (logger.isDebugEnabled())
      logger.debug("Going to read datetime value from {} property for project {}. Document name is {}.", propertyName,
          projectKey, documentName);

    refreshSearchIndex("_river");
    GetResponse lastSeqGetResponse = client.prepareGet("_river", riverName.name(), documentName).execute().actionGet();
    if (lastSeqGetResponse.exists()) {
      Object timestamp = lastSeqGetResponse.sourceAsMap().get(STORE_FIELD_VALUE);
      if (timestamp != null) {
        lastDate = DateTimeUtils.parseISODateTime(timestamp.toString());
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
  public BulkRequestBuilder prepareESBulkRequestBuilder() {
    return client.prepareBulk();
  }

  @Override
  public void executeESBulkRequest(BulkRequestBuilder esBulk) throws Exception {
    BulkResponse response = esBulk.execute().actionGet();
    if (response.hasFailures()) {
      throw new ElasticSearchException("Failed to execute ES index bulk update: " + response.buildFailureMessage());
    }
  }

  @Override
  public Thread acquireIndexingThread(String threadName, Runnable runnable) {
    return EsExecutors.daemonThreadFactory(settings.globalSettings(), threadName).newThread(runnable);
  }

  @Override
  public void refreshSearchIndex(String indexName) {
    client.admin().indices().prepareRefresh(indexName).execute().actionGet();
  }

  private static final long ES_SCROLL_KEEPALIVE = 60000;

  @Override
  public SearchRequestBuilder prepareESScrollSearchRequestBuilder(String indexName) {
    return client.prepareSearch(indexName).setScroll(new TimeValue(ES_SCROLL_KEEPALIVE)).setSearchType(SearchType.SCAN)
        .setSize(100);
  }

  public SearchResponse executeESSearchRequest(SearchRequestBuilder searchRequestBuilder) {
    return searchRequestBuilder.execute().actionGet();
  }

  @Override
  public SearchResponse executeESScrollSearchNextRequest(SearchResponse scrollResp) {
    return client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(new TimeValue(ES_SCROLL_KEEPALIVE)).execute()
        .actionGet();
  }

}
