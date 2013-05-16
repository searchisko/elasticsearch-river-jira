package org.jboss.elasticsearch.river.jira;

import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.jboss.elasticsearch.tools.content.StructuredContentPreprocessorFactory;

/**
 * JIRA River implementation class.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiver extends AbstractRiverComponent implements River, IESIntegration, IJiraRiverMgm {

	/**
	 * Map of running river instances. Used for management operations dispatching. See {@link #getRunningInstance(String)}
	 */
	protected static Map<String, IJiraRiverMgm> riverInstances = new HashMap<String, IJiraRiverMgm>();

	/**
	 * Name of datetime property where permanent indexing stop date is stored
	 * 
	 * @see #storeDatetimeValue(String, String, Date, BulkRequestBuilder)
	 * @see #readDatetimeValue(String, String)
	 */
	protected static final String PERMSTOREPROP_RIVER_STOPPED_PERMANENTLY = "river_stopped_permanently";

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
	protected volatile boolean closed = true;

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
	 * Last project indexing info store. Key in map is project key.
	 */
	protected Map<String, ProjectIndexingInfo> lastProjectIndexingInfo = new HashMap<String, ProjectIndexingInfo>();

	/**
	 * Date of last restart of this river.
	 */
	protected Date lastRestartDate;

	/**
	 * Timestamp of permanent stop of this river.
	 */
	protected Date permanentStopDate;

	/**
	 * Public constructor used by ElasticSearch.
	 * 
	 * @param riverName
	 * @param settings
	 * @param client
	 * @throws MalformedURLException
	 */
	@Inject
	public JiraRiver(RiverName riverName, RiverSettings settings, Client client) throws MalformedURLException {
		super(riverName, settings);
		this.client = client;
		configure(settings.settings());
	}

	/**
	 * Configure jira river.
	 * 
	 * @param settings used for configuration.
	 */
	@SuppressWarnings({ "unchecked" })
	protected void configure(Map<String, Object> settings) {

		if (!closed)
			throw new IllegalStateException("Jira River must be stopped to configure it!");

		String jiraUser = null;
		String jiraJqlTimezone = TimeZone.getDefault().getDisplayName();

		if (settings.containsKey("jira")) {
			Map<String, Object> jiraSettings = (Map<String, Object>) settings.get("jira");
			jiraUrlBase = XContentMapValues.nodeStringValue(jiraSettings.get("urlBase"), null);
			if (Utils.isEmpty(jiraUrlBase)) {
				throw new SettingsException("jira/urlBase element of configuration structure not found or empty");
			}
			Integer timeout = new Long(Utils.parseTimeValue(jiraSettings, "timeout", 5, TimeUnit.SECONDS)).intValue();
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
			indexUpdatePeriod = Utils.parseTimeValue(jiraSettings, "indexUpdatePeriod", 5, TimeUnit.MINUTES);
			indexFullUpdatePeriod = Utils.parseTimeValue(jiraSettings, "indexFullUpdatePeriod", 12, TimeUnit.HOURS);
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
		if (settings.containsKey("index")) {
			indexSettings = (Map<String, Object>) settings.get("index");
			indexName = XContentMapValues.nodeStringValue(indexSettings.get("index"), riverName.name());
			typeName = XContentMapValues.nodeStringValue(indexSettings.get("type"), INDEX_ISSUE_TYPE_NAME_DEFAULT);
		} else {
			indexName = riverName.name();
			typeName = INDEX_ISSUE_TYPE_NAME_DEFAULT;
		}

		Map<String, Object> activityLogSettings = null;
		if (settings.containsKey("activity_log")) {
			activityLogSettings = (Map<String, Object>) settings.get("activity_log");
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
						"Configured JIRA River '{}' for JIRA base URL [{}], jira user '{}', JQL timezone '{}'. Search index name '{}', document type for issues '{}'.",
						riverName.getName(), jiraUrlBase, jiraUser, jiraJqlTimezone, indexName, typeName);
		if (activityLogIndexName != null) {
			logger.info(
					"Activity log for JIRA River '{}' is enabled. Search index name '{}', document type for index updates '{}'.",
					riverName.getName(), activityLogIndexName, activityLogTypeName);
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
	 * Constructor for unit tests, nothing is initialized/configured in river.
	 * 
	 * @param riverName
	 * @param settings
	 */
	protected JiraRiver(RiverName riverName, RiverSettings settings) {
		super(riverName, settings);
	}

	@Override
	public synchronized void start() {
		if (!closed)
			throw new IllegalStateException("Can't start already running river");
		logger.info("starting JIRA River");
		synchronized (riverInstances) {
			addRunningInstance(this);
		}
		refreshSearchIndex(getRiverIndexName());
		try {
			if ((permanentStopDate = readDatetimeValue(null, PERMSTOREPROP_RIVER_STOPPED_PERMANENTLY)) != null) {
				logger
						.info("JIRA River indexing process not started because stopped permanently, you can restart it over management REST API");
				return;
			}
		} catch (IOException e) {
			// OK, we will start river
		}
		logger.info("starting JIRA River indexing process");
		closed = false;
		lastRestartDate = new Date();
		coordinatorInstance = new JIRAProjectIndexerCoordinator(jiraClient, this, jiraIssueIndexStructureBuilder,
				indexUpdatePeriod, maxIndexingThreads, indexFullUpdatePeriod);
		coordinatorThread = acquireIndexingThread("jira_river_coordinator", coordinatorInstance);
		coordinatorThread.start();
	}

	@Override
	public synchronized void close() {
		logger.info("closing JIRA River on this node");
		closed = true;
		if (coordinatorThread != null) {
			coordinatorThread.interrupt();
		}
		// free instances created in #start()
		coordinatorThread = null;
		coordinatorInstance = null;
		synchronized (riverInstances) {
			riverInstances.remove(riverName().getName());
		}
	}

	/**
	 * Stop jira river, but leave instance existing in {@link #riverInstances} so it can be found over management REST
	 * calls and/or reconfigured and started later again. Note that standard ES river {@link #close()} method
	 * implementation removes river instance from {@link #riverInstances}.
	 * 
	 * @param permanent set to true if info about river stopped can be persisted
	 */
	@Override
	public synchronized void stop(boolean permanent) {
		logger.info("stopping JIRA River indexing process");
		closed = true;
		if (coordinatorThread != null) {
			coordinatorThread.interrupt();
		}
		// free instances created in #start()
		coordinatorThread = null;
		coordinatorInstance = null;
		if (permanent) {
			try {
				permanentStopDate = new Date();
				storeDatetimeValue(null, PERMSTOREPROP_RIVER_STOPPED_PERMANENTLY, permanentStopDate, null);
				refreshSearchIndex(getRiverIndexName());
				logger.info("JIRA River indexing process stopped permanently, you can restart it over management REST API");
			} catch (IOException e) {
				logger.warn("Permanent stopped value storing failed {}", e.getMessage());
			}
		}
	}

	/**
	 * Reconfigure jira river. Must be stopped!
	 */
	public synchronized void reconfigure() {
		if (!closed)
			throw new IllegalStateException("Jira River must be stopped to reconfigure it!");

		logger.info("reconfiguring JIRA River");
		String riverIndexName = getRiverIndexName();
		refreshSearchIndex(riverIndexName);
		GetResponse resp = client.prepareGet(riverIndexName, riverName().name(), "_meta").execute().actionGet();
		if (resp.isExists()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Configuration document: {}", resp.getSourceAsString());
			}
			Map<String, Object> newset = resp.getSource();
			configure(newset);
		} else {
			throw new IllegalStateException("Configuration document not found to reconfigure jira river "
					+ riverName().name());
		}
	}

	/**
	 * Restart jira river. Configuration of river is updated.
	 */
	@Override
	public synchronized void restart() {
		logger.info("restarting JIRA River");
		boolean cleanPermanent = true;
		if (!closed) {
			cleanPermanent = false;
			stop(false);
			// wait a while to allow currently running indexers to finish??
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				return;
			}
		} else {
			logger.debug("stopped already");
		}
		reconfigure();
		if (cleanPermanent) {
			deleteDatetimeValue(null, PERMSTOREPROP_RIVER_STOPPED_PERMANENTLY);
		}
		start();
		logger.info("JIRA River restarted");
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Force full index update for some project(s) in this jira river. Used for REST management operations handling.
	 * 
	 * @param jiraProjectKey optional key of project to reindex, if null or empty then all projects are forced to full
	 *          reindex
	 * @return CSV list of projects forced to reindex. <code>null</code> if project passed over
	 *         <code>jiraProjectKey</code> parameter was not found in this indexer
	 * @throws Exception
	 */
	@Override
	public String forceFullReindex(String jiraProjectKey) throws Exception {
		if (coordinatorInstance == null)
			return null;
		List<String> pkeys = getAllIndexedProjectsKeys();
		if (Utils.isEmpty(jiraProjectKey)) {
			if (pkeys != null) {
				for (String k : pkeys) {
					coordinatorInstance.forceFullReindex(k);
				}
				return Utils.createCsvString(pkeys);
			} else {
				return "";
			}

		} else {
			if (pkeys != null && pkeys.contains(jiraProjectKey)) {
				coordinatorInstance.forceFullReindex(jiraProjectKey);
				return jiraProjectKey;
			} else {
				return null;
			}
		}
	}

	/**
	 * Get info about current operation of this river. Used for REST management operations handling.
	 * 
	 * @return String with JSON formatted info.
	 * @throws Exception
	 */
	@Override
	public String getRiverOperationInfo(DiscoveryNode esNode, Date currentDate) throws Exception {

		XContentBuilder builder = jsonBuilder().prettyPrint();
		builder.startObject();
		builder.field("river_name", riverName().getName());
		builder.field("info_date", currentDate);
		builder.startObject("indexing");
		builder.field("state", closed ? "stopped" : "running");
		if (!closed)
			builder.field("last_restart", lastRestartDate);
		else if (permanentStopDate != null)
			builder.field("stopped_permanently", permanentStopDate);
		builder.endObject();
		if (esNode != null) {
			builder.startObject("node");
			builder.field("id", esNode.getId());
			builder.field("name", esNode.getName());
			builder.endObject();
		}
		if (coordinatorInstance != null) {
			List<ProjectIndexingInfo> currProjectIndexingInfo = coordinatorInstance.getCurrentProjectIndexingInfo();
			if (currProjectIndexingInfo != null) {
				builder.startArray("current_indexing");
				for (ProjectIndexingInfo pi : currProjectIndexingInfo) {
					pi.buildDocument(builder, true, false);
				}
				builder.endArray();
			}
		}
		List<String> pkeys = getAllIndexedProjectsKeys();
		if (pkeys != null) {
			builder.startArray("indexed_jira_projects");
			for (String projectKey : pkeys) {
				builder.startObject();
				builder.field("project_key", projectKey);
				ProjectIndexingInfo lastIndexing = getLastProjectIndexingInfo(projectKey);
				if (lastIndexing != null) {
					builder.field("last_indexing");
					lastIndexing.buildDocument(builder, false, true);
				}
				builder.endObject();
			}
			builder.endArray();
		}
		builder.endObject();
		return builder.string();
	}

	/**
	 * @param projectKey to get info for
	 * @return project indexing info or null if not found.
	 */
	protected ProjectIndexingInfo getLastProjectIndexingInfo(String projectKey) {
		ProjectIndexingInfo lastIndexing = lastProjectIndexingInfo.get(projectKey);
		if (lastIndexing == null && activityLogIndexName != null) {
			try {
				refreshSearchIndex(activityLogIndexName);
				SearchResponse sr = client.prepareSearch(activityLogIndexName).setTypes(activityLogTypeName)
						.setFilter(FilterBuilders.termFilter(ProjectIndexingInfo.DOCFIELD_PROJECT_KEY, projectKey))
						.setQuery(QueryBuilders.matchAllQuery()).addSort(ProjectIndexingInfo.DOCFIELD_START_DATE, SortOrder.DESC)
						.addField("_source").setSize(1).execute().actionGet();
				if (sr.getHits().getTotalHits() > 0) {
					SearchHit hit = sr.getHits().getAt(0);
					lastIndexing = ProjectIndexingInfo.readFromDocument(hit.sourceAsMap());
				} else {
					logger.debug("No last indexing info found in activity log for project {}", projectKey);
				}
			} catch (Exception e) {
				logger.warn("Error during LastProjectIndexingInfo reading from activity log ES index: {} {}", e.getClass()
						.getName(), e.getMessage());
			}
		}
		return lastIndexing;
	}

	/**
	 * Get running instance of jira river for given name. Used for REST management operations handling.
	 * 
	 * @param riverName to get instance for
	 * @return river instance or null if not found
	 * @see #addRunningInstance(IJiraRiverMgm)
	 * @see #getRunningInstances()
	 */
	public static IJiraRiverMgm getRunningInstance(String riverName) {
		if (riverName == null)
			return null;
		return riverInstances.get(riverName);
	}

	/**
	 * Put running instance of jira river into registry. Used for REST management operations handling.
	 * 
	 * @param riverName to get instance for
	 * @see #getRunningInstances()
	 * @see #getRunningInstance(String)
	 */
	public static void addRunningInstance(IJiraRiverMgm jiraRiver) {
		riverInstances.put(jiraRiver.riverName().getName(), jiraRiver);
	}

	/**
	 * Get running instances of all jira rivers. Used for REST management operations handling.
	 * 
	 * @return Set with names of all jira river instances registered for management
	 * @see #addRunningInstance(IJiraRiverMgm)
	 * @see #getRunningInstance(String)
	 */
	public static Set<String> getRunningInstances() {
		return Collections.unmodifiableSet((riverInstances.keySet()));
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
	public void reportIndexingFinished(ProjectIndexingInfo indexingInfo) {
		lastProjectIndexingInfo.put(indexingInfo.projectKey, indexingInfo);
		if (coordinatorInstance != null) {
			try {
				coordinatorInstance.reportIndexingFinished(indexingInfo.projectKey, indexingInfo.finishedOK,
						indexingInfo.fullUpdate);
			} catch (Exception e) {
				logger.warn("Indexing finished reporting to coordinator failed due {}", e.getMessage());
			}
		}
		writeActivityLogRecord(indexingInfo);
	}

	/**
	 * Write indexing info into activity log if enabled.
	 * 
	 * @param indexingInfo to write
	 */
	protected void writeActivityLogRecord(ProjectIndexingInfo indexingInfo) {
		if (activityLogIndexName != null) {
			try {
				client.prepareIndex(activityLogIndexName, activityLogTypeName)
						.setSource(indexingInfo.buildDocument(jsonBuilder(), true, true)).execute().actionGet();
			} catch (Exception e) {
				logger.error("Error during index update result writing to the audit log {}", e.getMessage());
			}
		}
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
			esBulk.add(indexRequest(getRiverIndexName()).type(riverName.name()).id(documentName)
					.source(storeDatetimeValueBuildDocument(projectKey, propertyName, datetime)));
		} else {
			client.prepareIndex(getRiverIndexName(), riverName.name(), documentName)
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
		XContentBuilder builder = jsonBuilder().startObject();
		if (projectKey != null)
			builder.field("projectKey", projectKey);
		builder.field("propertyName", propertyName).field(STORE_FIELD_VALUE, DateTimeUtils.formatISODateTime(datetime));
		builder.endObject();
		return builder;
	}

	@Override
	public Date readDatetimeValue(String projectKey, String propertyName) throws IOException {
		Date lastDate = null;
		String documentName = prepareValueStoreDocumentName(projectKey, propertyName);

		if (logger.isDebugEnabled())
			logger.debug("Going to read datetime value from {} property for project {}. Document name is {}.", propertyName,
					projectKey, documentName);

		refreshSearchIndex(getRiverIndexName());
		GetResponse lastSeqGetResponse = client.prepareGet(getRiverIndexName(), riverName.name(), documentName).execute()
				.actionGet();
		if (lastSeqGetResponse.isExists()) {
			Object timestamp = lastSeqGetResponse.getSourceAsMap().get(STORE_FIELD_VALUE);
			if (timestamp != null) {
				lastDate = DateTimeUtils.parseISODateTime(timestamp.toString());
			}
		} else {
			if (logger.isDebugEnabled())
				logger.debug("{} document doesn't exist in JIRA river persistent store", documentName);
		}
		return lastDate;
	}

	@Override
	public boolean deleteDatetimeValue(String projectKey, String propertyName) {
		String documentName = prepareValueStoreDocumentName(projectKey, propertyName);

		if (logger.isDebugEnabled())
			logger.debug("Going to delete datetime value from {} property for project {}. Document name is {}.",
					propertyName, projectKey, documentName);

		refreshSearchIndex(getRiverIndexName());

		DeleteResponse lastSeqGetResponse = client.prepareDelete(getRiverIndexName(), riverName.name(), documentName)
				.execute().actionGet();
		if (lastSeqGetResponse.isNotFound()) {
			if (logger.isDebugEnabled()) {
				logger.debug("{} document doesn't exist in JIRA river persistent store", documentName);
			}
			return false;
		} else {
			return true;
		}

	}

	/**
	 * @return
	 */
	protected String getRiverIndexName() {
		return "_river";
		// return RiverIndexName.Conf.indexName(settings.globalSettings());
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
		if (projectKey != null)
			return "_" + propertyName + "_" + projectKey;
		else
			return "_" + propertyName;
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
