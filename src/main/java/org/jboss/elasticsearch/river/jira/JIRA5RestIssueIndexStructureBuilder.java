/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import static org.elasticsearch.client.Requests.deleteRequest;
import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

/**
 * JIRA 5 REST API implementation of component responsible to transform issue data obtained from JIRA instance call to
 * the document stored in ElasticSearch index. Intended to cooperate with {@link JIRA5RestClient}.
 * <p>
 * Testing URLs:<br>
 * <code>https://issues.jboss.org/rest/api/2/search?jql=project=ORG</code>
 * <code>https://issues.jboss.org/rest/api/2/search?jql=project=ORG&fields=</code>
 * <code>https://issues.jboss.org/rest/api/2/search?jql=project=ORG&fields=&expand=</code>
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRA5RestIssueIndexStructureBuilder implements IJIRAIssueIndexStructureBuilder {

  private static final ESLogger logger = Loggers.getLogger(JIRA5RestIssueIndexStructureBuilder.class);

  /**
   * JIRA REST response field constants
   */
  protected static final String JF_KEY = "key";
  protected static final String JF_ID = "id";
  protected static final String JF_UPDATED = "fields.updated";
  protected static final String JF_COMMENT = "fields.comment";
  protected static final String JF_COMMENTS = JF_COMMENT + ".comments";

  /**
   * Name of River to be stored in document to mark indexing source
   */
  protected String riverName;

  /**
   * Name of ElasticSearch index used to store issues
   */
  protected String indexName;

  /**
   * Name of ElasticSearch type used to store issues into index
   */
  protected String typeName;

  protected static final String CONFIG_FIELDS = "fields";
  protected static final String CONFIG_FIELDS_JIRAFIELD = "jira_field";
  protected static final String CONFIG_FIELDS_VALUEFILTER = "value_filter";
  protected static final String CONFIG_FILTERS = "value_filters";
  protected static final String CONFIG_FIELDRIVERNAME = "field_river_name";
  protected static final String CONFIG_FIELDPROJECTKEY = "field_project_key";
  protected static final String CONFIG_FIELDISSUEKEY = "field_issue_key";
  protected static final String CONFIG_FIELDJIRAURL = "field_jira_url";
  protected static final String CONFIG_COMMENTMODE = "comment_mode";
  protected static final String CONFIG_FIELDCOMMENTS = "field_comments";
  protected static final String CONFIG_COMMENTTYPE = "comment_type";
  protected static final String CONFIG_COMMENTFILEDS = "comment_fields";

  /**
   * Fields configuration structure. Key is name of field in search index. Value is map of configurations for given
   * index field containing <code>CONFIG_FIELDS_xx</code> constants as keys.
   */
  protected Map<String, Map<String, String>> fieldsConfig;

  /**
   * Value filters configuration structure. Key is name of filter. Value is map of filter configurations to be used in
   * {@link Utils#remapDataInMap(Map, Map)}.
   */
  protected Map<String, Map<String, String>> filtersConfig;

  /**
   * Name of field in search index where river name is stored.
   */
  protected String indexFieldForRiverName = null;

  /**
   * Name of field in search index where JIRA project key is stored.
   */
  protected String indexFieldForProjectKey = null;

  /**
   * Name of field in search index where JIRA issue key is stored.
   */
  protected String indexFieldForIssueKey = null;

  /**
   * Name of field in search index where JIRA GUI URL (to issue or comment) is stored.
   */
  protected String indexFieldForJiraURL = null;

  /**
   * Set of fields requested from JIRA during call
   */
  protected Set<String> jiraCallFieldSet = new LinkedHashSet<String>();

  /**
   * Base URL used to generate JIRA GUI issue show URL.
   * 
   * @see #prepareJIRAGUIUrl(String, String)
   */
  protected String jiraIssueShowUrlBase;

  /**
   * Issue comment indexing mode.
   */
  protected IssueCommentIndexingMode commentIndexingMode;

  /**
   * Name of field in search index issue document where array of comments is stored in case of
   * {@link IssueCommentIndexingMode#EMBEDDED}.
   */
  protected String indexFieldForComments = null;

  /**
   * Name of ElasticSearch type used to store issues comments into index in case of
   * {@link IssueCommentIndexingMode#STANDALONE} or {@link IssueCommentIndexingMode#CHILD}.
   */
  protected String commentTypeName;

  /**
   * Fields configuration structure for comment document. Key is name of field in search index. Value is map of
   * configurations for given index field containing <code>CONFIG_FIELDS_xx</code> constants as keys.
   */
  protected Map<String, Map<String, String>> commentFieldsConfig;

  /**
   * Constructor for unit tests. Nothing is filled inside.
   */
  protected JIRA5RestIssueIndexStructureBuilder() {
    super();
  }

  /**
   * Constructor.
   * 
   * @param riverName name of ElasticSearch River instance this indexer is running inside to be stored in search index
   *          to identify indexed documents source.
   * @param indexName name of ElasticSearch index used to store issues
   * @param typeName name of ElasticSearch type used to store issues into index
   * @param settings map to load structure settings from
   * @throws SettingsException
   */
  @SuppressWarnings("unchecked")
  public JIRA5RestIssueIndexStructureBuilder(String riverName, String indexName, String typeName, String jiraUrlBase,
      Map<String, Object> settings) throws SettingsException {
    super();
    this.riverName = riverName;
    this.indexName = indexName;
    this.typeName = typeName;

    constructJIRAIssueShowUrlBase(jiraUrlBase);

    if (settings != null) {
      indexFieldForRiverName = XContentMapValues.nodeStringValue(settings.get(CONFIG_FIELDRIVERNAME), null);
      indexFieldForProjectKey = XContentMapValues.nodeStringValue(settings.get(CONFIG_FIELDPROJECTKEY), null);
      indexFieldForIssueKey = XContentMapValues.nodeStringValue(settings.get(CONFIG_FIELDISSUEKEY), null);
      indexFieldForJiraURL = XContentMapValues.nodeStringValue(settings.get(CONFIG_FIELDJIRAURL), null);
      filtersConfig = (Map<String, Map<String, String>>) settings.get(CONFIG_FILTERS);
      fieldsConfig = (Map<String, Map<String, String>>) settings.get(CONFIG_FIELDS);

      commentIndexingMode = IssueCommentIndexingMode.parseConfiguration(XContentMapValues.nodeStringValue(
          settings.get(CONFIG_COMMENTMODE), null));
      indexFieldForComments = XContentMapValues.nodeStringValue(settings.get(CONFIG_FIELDCOMMENTS), null);
      commentTypeName = XContentMapValues.nodeStringValue(settings.get(CONFIG_COMMENTTYPE), null);
      commentFieldsConfig = (Map<String, Map<String, String>>) settings.get(CONFIG_COMMENTFILEDS);

    }

    loadDefaultsIfNecessary();

    validateConfiguration();

    prepareJiraCallFieldSet();
  }

  /**
   * Construct value for {@link #jiraIssueShowUrlBase}.
   * 
   * @param jiraUrlBase base URL of jira instance
   */
  protected void constructJIRAIssueShowUrlBase(String jiraUrlBase) {
    jiraIssueShowUrlBase = jiraUrlBase;
    if (!jiraIssueShowUrlBase.endsWith("/")) {
      jiraIssueShowUrlBase += "/";
    }
    jiraIssueShowUrlBase += "browse/";
  }

  @SuppressWarnings("unchecked")
  private void loadDefaultsIfNecessary() {
    Map<String, Object> settingsDefault = loadDefaultSettingsMapFromFile();
    if (filtersConfig == null || filtersConfig.isEmpty()) {
      filtersConfig = (Map<String, Map<String, String>>) settingsDefault.get(CONFIG_FILTERS);
    }
    if (fieldsConfig == null || fieldsConfig.isEmpty()) {
      fieldsConfig = (Map<String, Map<String, String>>) settingsDefault.get(CONFIG_FIELDS);
    }
    if (Utils.isEmpty(indexFieldForRiverName)) {
      indexFieldForRiverName = XContentMapValues.nodeStringValue(settingsDefault.get(CONFIG_FIELDRIVERNAME), null);
    } else {
      indexFieldForRiverName = indexFieldForRiverName.trim();
    }
    if (Utils.isEmpty(indexFieldForProjectKey)) {
      indexFieldForProjectKey = XContentMapValues.nodeStringValue(settingsDefault.get(CONFIG_FIELDPROJECTKEY), null);
    } else {
      indexFieldForProjectKey = indexFieldForProjectKey.trim();
    }
    if (Utils.isEmpty(indexFieldForIssueKey)) {
      indexFieldForIssueKey = XContentMapValues.nodeStringValue(settingsDefault.get(CONFIG_FIELDISSUEKEY), null);
    } else {
      indexFieldForIssueKey = indexFieldForIssueKey.trim();
    }
    if (Utils.isEmpty(indexFieldForJiraURL)) {
      indexFieldForJiraURL = XContentMapValues.nodeStringValue(settingsDefault.get(CONFIG_FIELDJIRAURL), null);
    } else {
      indexFieldForJiraURL = indexFieldForJiraURL.trim();
    }

    if (commentIndexingMode == null) {
      commentIndexingMode = IssueCommentIndexingMode.parseConfiguration(XContentMapValues.nodeStringValue(
          settingsDefault.get(CONFIG_COMMENTMODE), null));
    }
    if (Utils.isEmpty(indexFieldForComments)) {
      indexFieldForComments = XContentMapValues.nodeStringValue(settingsDefault.get(CONFIG_FIELDCOMMENTS), null);
    } else {
      indexFieldForComments = indexFieldForComments.trim();
    }
    if (Utils.isEmpty(commentTypeName)) {
      commentTypeName = XContentMapValues.nodeStringValue(settingsDefault.get(CONFIG_COMMENTTYPE), null);
    } else {
      commentTypeName = commentTypeName.trim();
    }
    if (commentFieldsConfig == null || commentFieldsConfig.isEmpty()) {
      commentFieldsConfig = (Map<String, Map<String, String>>) settingsDefault.get(CONFIG_COMMENTFILEDS);
    }
  }

  private void validateConfiguration() {

    if (filtersConfig == null) {
      throw new SettingsException("No default 'index/value_filters' configuration found");
    }
    if (fieldsConfig == null) {
      throw new SettingsException("No default 'index/fields' configuration found");
    }
    if (Utils.isEmpty(indexFieldForRiverName)) {
      throw new SettingsException("No default 'index/field_river_name' configuration found");
    }
    if (Utils.isEmpty(indexFieldForProjectKey)) {
      throw new SettingsException("No default 'index/field_project_key' configuration found");
    }
    if (Utils.isEmpty(indexFieldForIssueKey)) {
      throw new SettingsException("No default 'index/field_issue_key' configuration found");
    }
    if (Utils.isEmpty(indexFieldForJiraURL)) {
      throw new SettingsException("No default 'index/field_jira_url' configuration found");
    }
    if (commentIndexingMode == null) {
      throw new SettingsException("No default 'index/comment_mode' configuration found");
    }
    if (commentFieldsConfig == null) {
      throw new SettingsException("No default 'index/comment_fields' configuration found");
    }
    if (commentTypeName == null) {
      throw new SettingsException("No default 'index/comment_type' configuration found");
    }
    if (Utils.isEmpty(indexFieldForComments)) {
      throw new SettingsException("No default 'index/field_comments' configuration found");
    }

    for (String idxFieldName : fieldsConfig.keySet()) {
      if (Utils.isEmpty(idxFieldName)) {
        throw new SettingsException("Empty key found in 'index/fields' map.");
      }
      Map<String, String> fc = fieldsConfig.get(idxFieldName);
      if (Utils.isEmpty(fc.get(CONFIG_FIELDS_JIRAFIELD))) {
        throw new SettingsException("'jira_field' is not defined in 'index/fields/" + idxFieldName + "'");
      }
      String fil = fc.get(CONFIG_FIELDS_VALUEFILTER);
      if (fil != null && !filtersConfig.containsKey(fil)) {
        throw new SettingsException("Filter definition not found for filter name '" + fil
            + "' defined in 'index/fields/" + idxFieldName + "/value_filter'");
      }
    }
    for (String idxFieldName : commentFieldsConfig.keySet()) {
      if (Utils.isEmpty(idxFieldName)) {
        throw new SettingsException("Empty key found in 'index/comment_fields' map.");
      }
      Map<String, String> fc = commentFieldsConfig.get(idxFieldName);
      if (Utils.isEmpty(fc.get(CONFIG_FIELDS_JIRAFIELD))) {
        throw new SettingsException("'jira_field' is not defined in 'index/comment_fields/" + idxFieldName + "'");
      }
      String fil = fc.get(CONFIG_FIELDS_VALUEFILTER);
      if (fil != null && !filtersConfig.containsKey(fil)) {
        throw new SettingsException("Filter definition not found for filter name '" + fil
            + "' defined in 'index/comment_fields/" + idxFieldName + "/value_filter'");
      }
    }

  }

  protected void prepareJiraCallFieldSet() {
    jiraCallFieldSet.clear();
    // fields always necessary to get from jira
    jiraCallFieldSet.add(getJiraCallFieldName(JF_UPDATED));
    // other fields from configuration
    for (Map<String, String> fc : fieldsConfig.values()) {
      String jf = getJiraCallFieldName(fc.get(CONFIG_FIELDS_JIRAFIELD));
      if (jf != null) {
        jiraCallFieldSet.add(jf);
      }
    }
    if (commentIndexingMode != null && commentIndexingMode != IssueCommentIndexingMode.NONE) {
      jiraCallFieldSet.add(getJiraCallFieldName(JF_COMMENT));
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadDefaultSettingsMapFromFile() throws SettingsException {
    Map<String, Object> json = Utils.loadJSONFromJarPackagedFile("/templates/river_configuration_default.json");
    return (Map<String, Object>) json.get("index");
  }

  @Override
  public String getIssuesSearchIndexName(String jiraProjectKey) {
    return indexName;
  }

  @Override
  public String getRequiredJIRACallIssueFields() {
    return Utils.createCsvString(jiraCallFieldSet);
  }

  @Override
  public String extractIssueKey(Map<String, Object> issue) {
    return XContentMapValues.nodeStringValue(issue.get(JF_KEY), null);
  }

  @Override
  public Date extractIssueUpdated(Map<String, Object> issue) {
    return Utils.parseISODateTime(XContentMapValues.nodeStringValue(XContentMapValues.extractValue(JF_UPDATED, issue),
        null));
  }

  public String extractCommentId(Map<String, Object> comment) {
    return XContentMapValues.nodeStringValue(comment.get(JF_ID), null);
  }

  @Override
  public void indexIssue(BulkRequestBuilder esBulk, String jiraProjectKey, Map<String, Object> issue) throws Exception {

    String issueKey = extractIssueKey(issue);
    esBulk.add(indexRequest(indexName).type(typeName).id(issueKey)
        .source(prepareIssueIndexedDocument(jiraProjectKey, issue)));

    if (commentIndexingMode.isExtraDocumentIndexed()) {
      List<Map<String, Object>> comments = extractIssueComments(issue);
      if (comments != null && !comments.isEmpty()) {
        for (Map<String, Object> comment : comments) {
          String commentId = extractCommentId(comment);
          IndexRequest irq = indexRequest(indexName).type(commentTypeName).id(commentId)
              .source(prepareCommentIndexedDocument(jiraProjectKey, issueKey, comment));
          if (commentIndexingMode == IssueCommentIndexingMode.CHILD) {
            irq.parent(issueKey);
          }
          esBulk.add(irq);
        }
      }
    }

  }

  @Override
  public void buildSearchForIndexedDocumentsNotUpdatedAfter(SearchRequestBuilder srb, String jiraProjectKey, Date date) {
    FilterBuilder filterTime = FilterBuilders.rangeFilter("_timestamp").lt(date);
    FilterBuilder filterProject = FilterBuilders.termFilter(indexFieldForProjectKey, jiraProjectKey);
    FilterBuilder filterSource = FilterBuilders.termFilter(indexFieldForRiverName, riverName);
    FilterBuilder filter = FilterBuilders.boolFilter().must(filterTime).must(filterProject).must(filterSource);
    srb.setQuery(QueryBuilders.matchAllQuery()).addField("_id").setFilter(filter);
    if (commentIndexingMode.isExtraDocumentIndexed())
      srb.setTypes(typeName, commentTypeName);
    else
      srb.setTypes(typeName);
  }

  @Override
  public void deleteIssue(BulkRequestBuilder esBulk, SearchHit documentToDelete) throws Exception {
    esBulk.add(deleteRequest(indexName).type(documentToDelete.getType()).id(documentToDelete.getId()));
  }

  /**
   * Convert JIRA returned issue REST data into JSON document to be stored in search index.
   * 
   * @param jiraProjectKey key of jira project document is for.
   * @param issue issue data from JIRA REST call
   * @return JSON builder with issue document for index
   * @throws Exception
   */
  protected XContentBuilder prepareIssueIndexedDocument(String jiraProjectKey, Map<String, Object> issue)
      throws Exception {
    String issueKey = extractIssueKey(issue);

    XContentBuilder out = jsonBuilder().startObject();
    addValueToTheIndexField(out, indexFieldForRiverName, riverName);
    addValueToTheIndexField(out, indexFieldForProjectKey, jiraProjectKey);
    addValueToTheIndexField(out, indexFieldForIssueKey, issueKey);
    addValueToTheIndexField(out, indexFieldForJiraURL, prepareJIRAGUIUrl(issueKey, null));

    for (String indexFieldName : fieldsConfig.keySet()) {
      Map<String, String> fieldConfig = fieldsConfig.get(indexFieldName);
      addValueToTheIndex(out, indexFieldName, fieldConfig.get(CONFIG_FIELDS_JIRAFIELD), issue,
          fieldConfig.get(CONFIG_FIELDS_VALUEFILTER));
    }

    if (commentIndexingMode == IssueCommentIndexingMode.EMBEDDED) {
      List<Map<String, Object>> comments = extractIssueComments(issue);
      if (comments != null && !comments.isEmpty()) {
        out.startArray(indexFieldForComments);
        for (Map<String, Object> comment : comments) {
          out.startObject();
          addCommonFieldsToCommentIndexedDocument(out, issueKey, comment);
          out.endObject();
        }
        out.endArray();
      }
    }
    return out.endObject();
  }

  /**
   * Convert JIRA returned REST data into JSON document to be stored in search index for comments in child and
   * standalone mode.
   * 
   * @param projectKey key of jira project document is for.
   * @param issueKey this comment is for
   * @param comment data from JIRA REST call
   * @return JSON builder with comment document for index
   * @throws Exception
   */
  protected XContentBuilder prepareCommentIndexedDocument(String projectKey, String issueKey,
      Map<String, Object> comment) throws Exception {
    XContentBuilder out = jsonBuilder().startObject();
    addValueToTheIndexField(out, indexFieldForRiverName, riverName);
    addValueToTheIndexField(out, indexFieldForProjectKey, projectKey);
    addValueToTheIndexField(out, indexFieldForIssueKey, issueKey);
    addCommonFieldsToCommentIndexedDocument(out, issueKey, comment);
    return out.endObject();
  }

  private void addCommonFieldsToCommentIndexedDocument(XContentBuilder out, String issueKey, Map<String, Object> comment)
      throws Exception {
    addValueToTheIndexField(out, indexFieldForJiraURL, prepareJIRAGUIUrl(issueKey, extractCommentId(comment)));
    for (String indexFieldName : commentFieldsConfig.keySet()) {
      Map<String, String> commentFieldConfig = commentFieldsConfig.get(indexFieldName);
      addValueToTheIndex(out, indexFieldName, commentFieldConfig.get(CONFIG_FIELDS_JIRAFIELD), comment,
          commentFieldConfig.get(CONFIG_FIELDS_VALUEFILTER));
    }
  }

  /**
   * Get comments for issue from JIRA JSON data.
   * 
   * @param issue Map of Maps issue data structure loaded from JIRA.
   * @return list of comments if available in issu data
   */
  @SuppressWarnings("unchecked")
  protected List<Map<String, Object>> extractIssueComments(Map<String, Object> issue) {
    List<Map<String, Object>> comments = (List<Map<String, Object>>) XContentMapValues.extractValue(JF_COMMENTS, issue);
    return comments;
  }

  /**
   * Prepare URL of issue or comment in JIRA GUI.
   * 
   * @param issueKey mandatory key of JIRA issue.
   * @param commentId id of comment, may be null
   * @return URL to show issue or comment in JIRA GUI
   */
  protected String prepareJIRAGUIUrl(String issueKey, String commentId) {
    if (commentId == null) {
      return jiraIssueShowUrlBase + issueKey;
    } else {
      return jiraIssueShowUrlBase + issueKey + "?focusedCommentId=" + commentId
          + "&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-" + commentId;
    }
  }

  /**
   * Get defined value from values structure and add it into index document. Calls
   * {@link #addValueToTheIndex(XContentBuilder, String, String, Map, Map)} and receive filter from
   * {@link #filtersConfig} based on passed <code>valueFieldFilterName</code>)
   * 
   * @param out content builder to add indexed value field into
   * @param indexField name of field for index
   * @param valuePath path to get value from <code>values</code> structure. Dot notation for nested values can be used
   *          here (see {@link XContentMapValues#extractValue(String, Map)}).
   * @param values structure to get value from. Can be <code>null</code> - nothing added in this case, but not
   *          exception.
   * @param valueFieldFilterName name of filter definition to get it from {@link #filtersConfig}
   * @throws Exception
   */
  protected void addValueToTheIndex(XContentBuilder out, String indexField, String valuePath,
      Map<String, Object> values, String valueFieldFilterName) throws Exception {
    Map<String, String> filter = null;
    if (!Utils.isEmpty(valueFieldFilterName)) {
      filter = filtersConfig.get(valueFieldFilterName);
    }
    addValueToTheIndex(out, indexField, valuePath, values, filter);
  }

  /**
   * Get defined value from values structure and add it into index document.
   * 
   * @param out content builder to add indexed value field into
   * @param indexField name of field for index
   * @param valuePath path to get value from <code>values</code> structure. Dot notation for nested values can be used
   *          here (see {@link XContentMapValues#extractValue(String, Map)}).
   * @param values structure to get value from. Can be <code>null</code> - nothing added in this case, but not
   *          exception.
   * @param valueFieldFilter if value is JSON Object (java Map here) or List of JSON Objects, then fields in this
   *          objects are filtered to leave only fields named here and remap them - see
   *          {@link Utils#remapDataInMap(Map, Map)}. No filtering performed if this is <code>null</code>.
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  protected void addValueToTheIndex(XContentBuilder out, String indexField, String valuePath,
      Map<String, Object> values, Map<String, String> valueFieldFilter) throws Exception {
    if (values == null) {
      return;
    }
    Object v = null;
    if (valuePath.contains(".")) {
      v = XContentMapValues.extractValue(valuePath, values);
    } else {
      v = values.get(valuePath);
    }
    if (v != null && valueFieldFilter != null && !valueFieldFilter.isEmpty()) {
      if (v instanceof Map) {
        Utils.remapDataInMap((Map<String, Object>) v, valueFieldFilter);
      } else if (v instanceof List) {
        for (Object o : (List<?>) v) {
          if (o instanceof Map) {
            Utils.remapDataInMap((Map<String, Object>) o, valueFieldFilter);
          } else {
            logger.warn("Filter defined for field which is not filterable - jira array field '{}' with value: {}",
                valuePath, v);
          }
        }
      } else {
        logger.warn("Filter defined for field which is not filterable - jira field '{}' with value: {}", valuePath, v);
      }
    }
    addValueToTheIndexField(out, indexField, v);
  }

  /**
   * Add value into field in index document. Do not add it if value is <code>null</code>!
   * 
   * @param out builder to add field into.
   * @param indexField real name of field used in index.
   * @param value to be added to the index field. Can be <code>null</code>, nothing added in this case
   * @throws Exception
   * 
   * @see {@link XContentBuilder#field(String, Object)}.
   */
  protected void addValueToTheIndexField(XContentBuilder out, String indexField, Object value) throws Exception {
    if (value != null)
      out.field(indexField, value);
  }

  /**
   * Get name of JIRA field used in REST call from full jira field name.
   * 
   * @param fullJiraFieldName
   * @return call field name or null
   * @see #getRequiredJIRACallIssueFields()
   */
  protected static String getJiraCallFieldName(String fullJiraFieldName) {
    if (Utils.isEmpty(fullJiraFieldName)) {
      return null;
    }
    fullJiraFieldName = fullJiraFieldName.trim();
    if (fullJiraFieldName.startsWith("fields.")) {
      String jcrf = fullJiraFieldName.substring("fields.".length());
      if (Utils.isEmpty(jcrf)) {
        logger.warn("Bad format of jira field name '{}', nothing will be in search index", fullJiraFieldName);
        return null;
      }
      if (jcrf.contains(".")) {
        jcrf = jcrf.substring(0, jcrf.indexOf("."));
      }
      if (Utils.isEmpty(jcrf)) {
        logger.warn("Bad format of jira field name '{}', nothing will be in search index", fullJiraFieldName);
        return null;
      }
      return jcrf.trim();
    } else {
      return null;
    }
  }
}
