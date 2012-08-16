/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.Map;

import org.elasticsearch.action.bulk.BulkRequestBuilder;

/**
 * Interface for component responsible to transform issue data obtained from JIRA instance call to the document stored
 * in ElasticSearch index.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public interface IJIRAIssueIndexStructureBuilder {

  /**
   * Get issue fields required from jira to build index document.
   * 
   * @return comma separated list of fields
   */
  public String getRequiredJIRAIssueFields();

  /**
   * Store issue obtained from JIRA into search index.
   * 
   * @param esBulk bulk operation builder used to index issue data
   * @param jiraProjectKey JIRA project key indexed issue is for
   * @param issue data obtained from JIRA to be indexed (JSON paresd into Map of Map structure)
   * @throws Exception
   */
  public void indexIssue(BulkRequestBuilder esBulk, String jiraProjectKey, Map<String, Object> issue) throws Exception;

}
