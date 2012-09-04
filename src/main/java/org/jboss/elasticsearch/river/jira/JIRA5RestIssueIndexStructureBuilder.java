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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
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
  public static final String JF_KEY = "key";
  public static final String JF_UPDATED = "fields.updated";

  /**
   * Name of River to be stored in document to mark indexing source
   */
  protected final String riverName;

  /**
   * Name of ElasticSearch index used to store issues
   */
  protected final String indexName;

  /**
   * Name of ElasticSearch type used to store issues into index
   */
  protected final String typeName;

  protected static final String CONFIG_FIELDS = "fields";
  protected static final String CONFIG_FIELDS_JIRAFIELD = "jira_field";
  protected static final String CONFIG_FIELDS_VALUEFILTER = "value_filter";
  protected static final String CONFIG_FILTERS = "value_filters";
  protected static final String CONFIG_FIELDRIVERNAME = "field_river_name";
  protected static final String CONFIG_FIELDPROJECTKEY = "field_project_key";

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
   * Set of fields requested from JIRA during call
   */
  protected Set<String> jiraCallFieldSet = new LinkedHashSet<String>();

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
  public JIRA5RestIssueIndexStructureBuilder(String riverName, String indexName, String typeName,
      Map<String, Object> settings) throws SettingsException {
    super();
    this.riverName = riverName;
    this.indexName = indexName;
    this.typeName = typeName;

    if (settings != null) {
      indexFieldForRiverName = XContentMapValues.nodeStringValue(settings.get(CONFIG_FIELDRIVERNAME), null);
      indexFieldForProjectKey = XContentMapValues.nodeStringValue(settings.get(CONFIG_FIELDPROJECTKEY), null);
      filtersConfig = (Map<String, Map<String, String>>) settings.get(CONFIG_FILTERS);
      fieldsConfig = (Map<String, Map<String, String>>) settings.get(CONFIG_FIELDS);
    }

    loadDefaultsIfNecessary();

    // TODO validate filtersConfig
    // TODO validate fieldsConfig - project key field is mandatory!

    prepareJiraCallFieldSet();
  }

  private void prepareJiraCallFieldSet() {
    // fields always necessary to get from jira
    jiraCallFieldSet.add(getJiraCallFieldName(JF_UPDATED));
    // other fields from configuration
    for (Map<String, String> fc : fieldsConfig.values()) {
      String jf = getJiraCallFieldName(fc.get(CONFIG_FIELDS_JIRAFIELD));
      if (jf != null) {
        jiraCallFieldSet.add(jf);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void loadDefaultsIfNecessary() {
    Map<String, Object> settingsDefault = loadDefaultSettingsMapFromFile();
    if (filtersConfig == null || filtersConfig.isEmpty()) {
      filtersConfig = (Map<String, Map<String, String>>) settingsDefault.get(CONFIG_FILTERS);
    }
    if (filtersConfig == null) {
      filtersConfig = new HashMap<String, Map<String, String>>();
    }

    if (fieldsConfig == null || fieldsConfig.isEmpty()) {
      fieldsConfig = (Map<String, Map<String, String>>) settingsDefault.get(CONFIG_FIELDS);
    }
    if (fieldsConfig == null) {
      fieldsConfig = new HashMap<String, Map<String, String>>();
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
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadDefaultSettingsMapFromFile() throws SettingsException {
    Map<String, Object> json = Utils.loadJSONFromJarPackagedFile("/templates/river_configuration_example.json");
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
  public void indexIssue(BulkRequestBuilder esBulk, String jiraProjectKey, Map<String, Object> issue) throws Exception {
    esBulk.add(indexRequest(indexName).type(typeName).id(XContentMapValues.nodeStringValue(issue.get(JF_KEY), null))
        .source(toJson(jiraProjectKey, issue)));
  }

  @Override
  public void buildSearchForIndexedIssuesNotUpdatedAfter(SearchRequestBuilder srb, String jiraProjectKey, Date date) {
    FilterBuilder filterTime = FilterBuilders.rangeFilter("_timestamp").lt(date);
    FilterBuilder filterProject = FilterBuilders.termFilter(indexFieldForProjectKey, jiraProjectKey);
    FilterBuilder filter = FilterBuilders.boolFilter().must(filterTime).must(filterProject);
    srb.setTypes(typeName).setQuery(QueryBuilders.matchAllQuery()).addField("_id").setFilter(filter);
  }

  @Override
  public void deleteIssue(BulkRequestBuilder esBulk, SearchHit issueDocumentToDelete) throws Exception {
    esBulk.add(deleteRequest(indexName).type(typeName).id(issueDocumentToDelete.getId()));
  }

  /**
   * Convert JIRA returned issue REST data inot JSON document for index.
   * 
   * @param jiraProjectKey key of jira project document is for
   * @param issue issue data from JIRA REST call
   * @return JSON builder
   * @throws Exception
   */
  protected XContentBuilder toJson(String jiraProjectKey, Map<String, Object> issue) throws Exception {
    XContentBuilder out = jsonBuilder().startObject();
    addValueToTheIndexField(out, indexFieldForRiverName, riverName);
    addValueToTheIndexField(out, indexFieldForProjectKey, jiraProjectKey);

    for (String indexFieldName : fieldsConfig.keySet()) {
      Map<String, String> fieldConfig = fieldsConfig.get(indexFieldName);
      Map<String, String> filter = null;
      if (fieldConfig.get(CONFIG_FIELDS_VALUEFILTER) != null) {
        filter = filtersConfig.get(fieldConfig.get(CONFIG_FIELDS_VALUEFILTER));
      }
      addValueToTheIndex(out, indexFieldName, fieldConfig.get(CONFIG_FIELDS_JIRAFIELD), issue, filter);
    }
    return out.endObject();
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
