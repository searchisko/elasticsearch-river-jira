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
  public List<String> getAllIndexedProjectsKeys() throws Exception;

  /**
   * Callback method - report that indexing of some JIRA project was finished. Used to coordinate parallel indexing of
   * all projects and gather indexing statistics/audit data.
   * 
   * @param jiraProjectKey JIRA project key for finished indexing
   * @param finishedOK set to <code>true</code> if indexing finished OK, <code>false</code> if finished due error
   * @param issuesUpdated number of issues updated during this indexing
   * @param timeElapsed time of this indexing run [ms]
   * @param errorMessage error message if indexing finished with error
   */
  public void reportIndexingFinished(String jiraProjectKey, boolean finishedOK, int issuesUpdated, long timeElapsed,
      String errorMessage);

  /**
   * Check if EclipseSearch instance is closed, so we must interrupt long running indexing processes.
   * 
   * @return true if ES instance is closed, so we must interrupt long running indexing processes
   */
  public boolean isClosed();

  /**
   * Persistently store datetime value as document into ElasticSearch river configuration area.
   * 
   * @param documentName name of document where timestamp is stored
   * @param datetime to be stored
   * @param esBulk to be used for value store, if <code>null</code> then value is stored immediately
   * @throws IOException
   * 
   * @see {@link #readDatetimeValue(String)}
   */
  public void storeDatetimeValue(String documentName, Date datetime, BulkRequestBuilder esBulk) throws Exception;

  /**
   * Read datetime value from document in ElasticSearch river configuration persistent area.
   * 
   * @param documentName name of document where timestamp is stored
   * @return datetime or null if do not exists
   * @throws IOException
   * @see {@link #storeDatetimeValue(BulkRequestBuilder, String, Date)}
   */
  public Date readDatetimeValue(String documentName) throws Exception;

  /**
   * Get ElasticSearch bulk request to be used for index update by more issues.
   * 
   * @return bulk request instance
   * @see #executeESBulkRequestBuilder(BulkRequestBuilder)
   */
  public BulkRequestBuilder getESBulkRequestBuilder();

  /**
   * Perform ElasticSearch bulk request against ElasticSearch cluster.
   * 
   * @param esBulk to perform
   * @throws Exception in case of update failure
   * @see #getESBulkRequestBuilder()
   */
  public void executeESBulkRequestBuilder(BulkRequestBuilder esBulk) throws Exception;

  /**
   * Acquire thread from ElasticSearch infrastructure to run indexing.
   * 
   * @param threadName name of thread
   * @param runnable to run in this thread
   * @return {@link Thread} instance - not started yet!
   */
  public Thread acquireIndexingThread(String threadName, Runnable runnable);

}
