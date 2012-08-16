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
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRA5RestIssueIndexStructureBuilder implements IJIRAIssueIndexStructureBuilder {

  /**
   * JIRA field constants
   */
  public static final String JF_KEY = "key";
  public static final String JF_FIELDS = "fields";
  public static final String JF_UPDATED = "updated";

  /**
   * Name of ElasticSearch index used to store issues
   */
  protected final String indexName;

  /**
   * Name of ElasticSearch type used to store issues into index
   */
  protected final String typeName;

  /**
   * @param indexName Name of ElasticSearch index used to store issues
   * @param typeName Name of ElasticSearch type used to store issues into index
   */
  public JIRA5RestIssueIndexStructureBuilder(String indexName, String typeName) {
    super();
    this.indexName = indexName;
    this.typeName = typeName;
  }

  @Override
  public String getRequiredJIRAIssueFields() {
    // TODO add additional indexed issue fields from River configuration (include unit test)
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
  protected XContentBuilder toJson(String jiraProjectKey, Map<String, Object> issue) throws Exception {
    XContentBuilder out = jsonBuilder().startObject();
    out.field("project_key", jiraProjectKey);
    out.field("issue_key", issue.get(JF_KEY));
    // TODO insert other fields into index
    return out.endObject();
  }

}
