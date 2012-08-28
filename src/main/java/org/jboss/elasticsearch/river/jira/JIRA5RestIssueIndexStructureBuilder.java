/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;

/**
 * JIRA 5 REST API implementation of component responsible to transform issue data obtained from JIRA instance call to
 * the document stored in ElasticSearch index.
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
   * Value filter for User JIRA JSON Object to leave only some fields for index.
   */
  protected static final Set<String> VALUE_FIELD_FILTER_USER = new HashSet<String>();
  static {
    VALUE_FIELD_FILTER_USER.add("name");
    VALUE_FIELD_FILTER_USER.add("emailAddress");
    VALUE_FIELD_FILTER_USER.add("displayName");
  }

  /**
   * Value filter for User JIRA JSON Object to leave only some fields for index.
   */
  protected static final Set<String> VALUE_FIELD_FILTER_NAME = new HashSet<String>();
  static {
    VALUE_FIELD_FILTER_NAME.add("name");
  }

  /**
   * JIRA field constants
   */
  public static final String JF_KEY = "key";
  public static final String JF_SELF = "self";
  public static final String JF_FIELDS = "fields";
  public static final String JF_UPDATED = "updated";

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

  /**
   * Set of fields requested from JIRA
   */
  protected Set<String> jiraCallFieldSet = new LinkedHashSet<String>();

  /**
   * Constructor.
   * 
   * @param riverName name of ElasticSearch River instance this indexer is running inside to be stored in search index
   *          to identify indexed documents source.
   * @param indexName name of ElasticSearch index used to store issues
   * @param typeName name of ElasticSearch type used to store issues into index
   */
  public JIRA5RestIssueIndexStructureBuilder(String riverName, String indexName, String typeName) {
    super();
    this.riverName = riverName;
    this.indexName = indexName;
    this.typeName = typeName;

    // TODO remove some of default fields - River configuration
    // TODO index additional indexed issue fields - River configuration
    // TODO index issue comments - River configuration

    // fields always necessary to get from jira
    jiraCallFieldSet.add("key");
    jiraCallFieldSet.add("updated");
    // and others optional fields
    jiraCallFieldSet.addAll(DEFAULT_JIRA_FIELD_SET);

  }

  /**
   * Default Set of fields requested from JIRA and indexed.
   */
  protected static final Set<String> DEFAULT_JIRA_FIELD_SET = new LinkedHashSet<String>();
  static {
    DEFAULT_JIRA_FIELD_SET.add("status");
    DEFAULT_JIRA_FIELD_SET.add("issuetype");
    DEFAULT_JIRA_FIELD_SET.add("created");
    DEFAULT_JIRA_FIELD_SET.add("reporter");
    DEFAULT_JIRA_FIELD_SET.add("assignee");
    DEFAULT_JIRA_FIELD_SET.add("summary");
    DEFAULT_JIRA_FIELD_SET.add("description");
    DEFAULT_JIRA_FIELD_SET.add("components");
    DEFAULT_JIRA_FIELD_SET.add("fixVersions");
    DEFAULT_JIRA_FIELD_SET.add("resolutiondate");
    DEFAULT_JIRA_FIELD_SET.add("labels");
  }

  @Override
  public String getRequiredJIRAIssueFields() {
    return Utils.createCsvString(jiraCallFieldSet);
  }

  @Override
  public void indexIssue(BulkRequestBuilder esBulk, String jiraProjectKey, Map<String, Object> issue) throws Exception {
    esBulk.add(indexRequest(indexName).type(typeName).id(XContentMapValues.nodeStringValue(issue.get(JF_KEY), null))
        .source(toJson(jiraProjectKey, issue)));
  }

  /**
   * Convert JIRA returned issue REST data inot JSON document for index.
   * 
   * @param jiraProjectKey key of jira project document is for
   * @param issue issue data from JIRA REST call
   * @return JSON builder
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  protected XContentBuilder toJson(String jiraProjectKey, Map<String, Object> issue) throws Exception {
    XContentBuilder out = jsonBuilder().startObject();
    addValueToTheIndexField(out, "river", riverName);
    addValueToTheIndexField(out, "project_key", jiraProjectKey);
    addValueToTheIndex(out, "issue_key", JF_KEY, issue);
    addValueToTheIndex(out, "document_url", JF_SELF, issue);

    Map<String, Object> fields = (Map<String, Object>) issue.get(JF_FIELDS);
    if (fields != null) {
      addValueToTheIndex(out, "issue_type", "issuetype.name", fields);
      addValueToTheIndex(out, "summary", "summary", fields);
      addValueToTheIndex(out, "status", "status.name", fields);
      addValueToTheIndex(out, "created", "created", fields);
      addValueToTheIndex(out, "updated", "updated", fields);
      addValueToTheIndex(out, "resolutiondate", "resolutiondate", fields);
      addValueToTheIndex(out, "description", "description", fields);
      addValueToTheIndex(out, "labels", "labels", fields);
      addValueToTheIndex(out, "reporter", "reporter", fields, VALUE_FIELD_FILTER_USER);
      addValueToTheIndex(out, "assignee", "assignee", fields, VALUE_FIELD_FILTER_USER);
      addValueToTheIndex(out, "fix_versions", "fixVersions", fields, VALUE_FIELD_FILTER_NAME);
      addValueToTheIndex(out, "components", "components", fields, VALUE_FIELD_FILTER_NAME);
    }
    return out.endObject();
  }

  /**
   * See {@link #addValueToTheIndex(XContentBuilder, String, String, Map, String[])} for javadoc because this call given
   * method without filter.
   */
  protected void addValueToTheIndex(XContentBuilder out, String indexField, String valuePath, Map<String, Object> values)
      throws Exception {
    addValueToTheIndex(out, indexField, valuePath, values, null);
  }

  /**
   * Get defined value from values structure and add it into index document. Some additional tasks can be performed
   * inside this method, as field filtering, index field name remapping given by configuration etc.
   * 
   * @param out content builder to add indexed value field into
   * @param indexField name of field for index (but can be remapped inside of this method to another name by component
   *          configuration etc.)
   * @param valuePath path to get value from <code>values</code> structure. Dot notation for nested values can be used
   *          here (see {@link XContentMapValues#extractValue(String, Map)}).
   * @param values structure to get value from. Can be <code>null</code> - nothing added in this case, but not
   *          exception.
   * @param valueFieldFilter if value is JSON Object (javaMap here) or List of JSON Objects, then fields in this objects
   *          are filtered to leave only fields named here. No filtering if this is <code>null</code>.
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  protected void addValueToTheIndex(XContentBuilder out, String indexField, String valuePath,
      Map<String, Object> values, Set<String> valueFieldFilter) throws Exception {
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
        Utils.filterDataInMap((Map<String, Object>) v, valueFieldFilter);
      } else if (v instanceof List) {
        for (Object o : (List<?>) v) {
          if (o instanceof Map) {
            Utils.filterDataInMap((Map<String, Object>) o, valueFieldFilter);
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

}
