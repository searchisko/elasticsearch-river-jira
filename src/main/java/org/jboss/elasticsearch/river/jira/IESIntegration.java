/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.IndicesAdminClient;

/**
 * Interface for component which allows integration of indexer components into ElasticSearch River instance.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public interface IESIntegration {

  /**
   * Get JIRA project keys for all projects which needs to be indexed. Is loaded from river configuration or from JIRA
   * instance (in this case excludes are removed from it) - depends on river configuration.
   * 
   * @return list of project keys.
   * @throws Exception
   */
  List<String> getAllIndexedProjectsKeys() throws Exception;

  /**
   * Callback method - report that indexing of some JIRA project was finished. Used to coordinate parallel indexing of
   * all projects and gather indexing statistics/audit data.
   * 
   * @param jiraProjectKey JIRA project key for finished indexing
   * @param finishedOK set to <code>true</code> if indexing finished OK, <code>false</code> if finished due error
   * @param fullUpdate set to <code>true</code> if reported indexing was full update, <code>false</code> on incremental
   *          update
   * @param issuesUpdated number of issues updated during this indexing run
   * @param issuesDeleted number of issues deleted during this indexing run
   * @param startDate date of indexing start
   * @param timeElapsed time of this indexing run [ms]
   * @param errorMessage error message if indexing finished with error
   */
  void reportIndexingFinished(String jiraProjectKey, boolean finishedOK, boolean fullUpdate, int issuesUpdated,
      int issuesDeleted, Date startDate, long timeElapsed, String errorMessage);

  /**
   * Check if EclipseSearch instance is closed, so we must interrupt long running indexing processes.
   * 
   * @return true if ES instance is closed, so we must interrupt long running indexing processes
   */
  boolean isClosed();

  /**
   * Persistently store datetime value for jira project as document into ElasticSearch river configuration area.
   * 
   * @param projectKey jira project key this value is for
   * @param propertyName name of property for this value identification
   * @param datetime to be stored
   * @param esBulk to be used for value store process, if <code>null</code> then value is stored immediately
   * @throws IOException
   * 
   * @see {@link #readDatetimeValue(String, String)}
   */
  void storeDatetimeValue(String projectKey, String propertyName, Date datetime, BulkRequestBuilder esBulk)
      throws Exception;

  /**
   * Read datetime value for jira project from document in ElasticSearch river configuration persistent area.
   * 
   * @param projectKey jira project key this value is for
   * @param propertyName name of property for this value identification
   * @return datetime or null if do not exists
   * @throws IOException
   * @see {@link #storeDatetimeValue(String, String, Date, BulkRequestBuilder)}
   */
  Date readDatetimeValue(String projectKey, String propertyName) throws Exception;

  /**
   * Prepare ElasticSearch bulk request to be used for index update by more issues.
   * 
   * @return bulk request instance
   * @see #executeESBulkRequest(BulkRequestBuilder)
   */
  BulkRequestBuilder prepareESBulkRequestBuilder();

  /**
   * Execute ElasticSearch bulk request against ElasticSearch cluster.
   * 
   * @param esBulk to perform
   * @throws Exception in case of update failure
   * @see #prepareESBulkRequestBuilder()
   */
  void executeESBulkRequest(BulkRequestBuilder esBulk) throws Exception;

  /**
   * Acquire thread from ElasticSearch infrastructure to run indexing.
   * 
   * @param threadName name of thread
   * @param runnable to run in this thread
   * @return {@link Thread} instance - not started yet!
   */
  Thread acquireIndexingThread(String threadName, Runnable runnable);

  /**
   * Refresh search index to be up to date for search operations. See
   * {@link IndicesAdminClient#refresh(org.elasticsearch.action.admin.indices.refresh.RefreshRequest)}.
   * 
   * @param indexName to be refreshed
   */
  void refreshSearchIndex(String indexName);

  /**
   * Prepare builder for Scroll Search request. See http://www.elasticsearch.org/guide/reference/java-api/search.html.
   * 
   * @param indexName name of index to prepare scroll for
   * @return scroll search builder to be used
   * @see #executeESSearchRequest(SearchRequestBuilder)
   * @see #executeESScrollSearchNextRequest(SearchResponse)
   */
  SearchRequestBuilder prepareESScrollSearchRequestBuilder(String indexName);

  /**
   * Execute search on passed search request builder and return result. Can be used for normal search, or first search
   * in Scroll scenario (http://www.elasticsearch.org/guide/reference/java-api/search.html).
   * 
   * @param searchRequestBuilder to execute
   * @return actual response
   */
  SearchResponse executeESSearchRequest(SearchRequestBuilder searchRequestBuilder);

  /**
   * Execute subsequent scroll search request. See http://www.elasticsearch.org/guide/reference/java-api/search.html.
   * 
   * @param scrollResp response from previous scroll search request
   * @return actual response
   * @see #prepareESScrollSearchRequestBuilder(String)
   * @see #executeESSearchRequest(SearchRequestBuilder)
   */
  SearchResponse executeESScrollSearchNextRequest(SearchResponse scrollResp);

}
