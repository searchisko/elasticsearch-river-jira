/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import static org.elasticsearch.client.Requests.indexRequest;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.util.Map;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;

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
  }

  @Override
  public String getRequiredJIRAIssueFields() {
    // TODO add additional indexed issue fields from River configuration (include unit test)
    // TODO issue comments
    return "key,status,issuetype,created,updated,reporter,assignee,summary,description";
  }

  @Override
  public void indexIssue(BulkRequestBuilder esBulk, String jiraProjectKey, Map<String, Object> issue) throws Exception {
    esBulk.add(indexRequest(indexName).type(typeName).id((String) issue.get(JF_KEY))
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
    // TODO IS - some normalized name for 'river' field due common search GUI?
    out.field("river", riverName);
    out.field("project_key", jiraProjectKey);
    // TODO IS - add normalized 'project' field due common search GUI?
    out.field("issue_key", issue.get(JF_KEY));
    out.field("document_url", issue.get(JF_SELF));

    Map<String, Object> fields = (Map<String, Object>) issue.get(JF_FIELDS);
    if (fields != null) {
      // TODO IS - some normalized name for 'title' field due common search GUI?
      out.field("title", fields.get("summary"));

      // TODO insert other fields into index
    }
    // TODO insert other fields into index
    return out.endObject();
  }

}
