/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.Date;

import org.elasticsearch.action.bulk.BulkRequestBuilder;

/**
 * Interface for component which allows integration of indexer into ElasticSearch instance.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public interface IESIntegration {

  /**
   * @return true if ES instance is closed, so we must interrupt long running indexing processes
   */
  public boolean isClosed();

  /**
   * Get date of last issue updated for given JIRA project from persistent store inside ES cluster, so we can continue
   * in update process from this point.
   * 
   * @param jiraProjectKey JIRA project key to get date for.
   * @return date of last issue updated or null if not available (in this case indexing starts from the beginning of
   *         project history)
   * @see #storeLastIssueUpdatedDate(BulkRequestBuilder, String, Date)
   */
  public Date getLastIssueUpdatedDate(String jiraProjectKey);

  /**
   * Get ElasticSearch bulk request to be used for index update by more issues.
   * 
   * @return bulk request instance
   * @see #executeESBulkRequestBuilder(BulkRequestBuilder)
   */
  public BulkRequestBuilder getESBulkRequestBuilder();

  /**
   * Store date of last issue updated for given JIRA project into persistent store inside ES cluster, so we can continue
   * in update process from this point next time.
   * 
   * @param esBulk ElasticSearch bulk request to be used for update
   * @param jiraProjectKey JIRA project key to store date for.
   * @param lastIssueUpdatedDate date to store
   * @throws Exception
   * @see #getLastIssueUpdatedDate(String)
   */
  public void storeLastIssueUpdatedDate(BulkRequestBuilder esBulk, String jiraProjectKey, Date lastIssueUpdatedDate)
      throws Exception;

  /**
   * Perform ElasticSearch bulk request against ElasticSearch cluster.
   * 
   * @param esBulk to perform
   * @throws Exception in case of update failure
   * @see #getESBulkRequestBuilder()
   */
  public void executeESBulkRequestBuilder(BulkRequestBuilder esBulk) throws Exception;

}
