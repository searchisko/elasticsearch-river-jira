/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

/**
 * JIRA PRoject indexing coordinator components. Coordinate parallel indexing of more JIRA projects, and also handles
 * how often one project issue updates should be checked.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRAProjectIndexerCoordinator implements IJIRAProjectIndexerCoordinator {

  private static final ESLogger logger = Loggers.getLogger(JIRAProjectIndexerCoordinator.class);

  /**
   * Property value where "last index update start date" is stored for JIRA project
   * 
   * @see IESIntegration#storeDatetimeValue(String, String, Date, BulkRequestBuilder)
   * @see IESIntegration#readDatetimeValue(String, String)
   * @see #projectIndexUpdateNecessary(String)
   */
  protected static final String STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE = "lastIndexUpdateStartDate";

  /**
   * Property value where "last index full update date" is stored for JIRA project
   * 
   * @see IESIntegration#storeDatetimeValue(String, String, Date, BulkRequestBuilder)
   * @see IESIntegration#readDatetimeValue(String, String)
   * @see #projectIndexFullUpdateNecessary(String)
   */
  protected static final String STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE = "lastIndexFullUpdateStartDate";

  protected static final int COORDINATOR_THREAD_WAITS_QUICK = 2 * 1000;
  protected static final int COORDINATOR_THREAD_WAITS_SLOW = 30 * 1000;
  protected int coordinatorThreadWaits = COORDINATOR_THREAD_WAITS_QUICK;

  protected IESIntegration esIntegrationComponent;

  /**
   * JIRA client to access data from JIRA
   */
  protected IJIRAClient jiraClient;

  protected IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilder;

  protected int maxIndexingThreads;

  /**
   * Period of index update from jira [ms].
   */
  protected int indexUpdatePeriod;

  /**
   * Period of index automatic full update from jira [ms]. value <= 0 means never.
   */
  protected int indexFullUpdatePeriod = -1;

  /**
   * Queue of project keys which needs to be reindexed in near future.
   * 
   * @see JIRAProjectIndexerCoordinator
   */
  protected Queue<String> projectKeysToIndexQueue = new LinkedBlockingQueue<String>();

  /**
   * Map where currently running JIRA project indexer threads are stored.
   * 
   * @see JIRAProjectIndexerCoordinator
   */
  protected final Map<String, Thread> projectIndexers = new HashMap<String, Thread>();

  /**
   * Constructor with parameters.
   * 
   * @param jiraClient configured jira client to be passed into {@link JIRAProjectIndexer} instances started from
   *          coordinator
   * @param esIntegrationComponent to be used to call River component and ElasticSearch functions
   * @param jiraIssueIndexStructureBuilder component used to build structures for search index
   * @param indexUpdatePeriod index update period [ms]
   * @param maxIndexingThreads maximal number of parallel JIRA indexing threads started by this coordinator
   * @param indexFullUpdatePeriod period of index automatic full update from jira [ms]. value <= 0 means never.
   */
  public JIRAProjectIndexerCoordinator(IJIRAClient jiraClient, IESIntegration esIntegrationComponent,
      IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilder, int indexUpdatePeriod, int maxIndexingThreads,
      int indexFullUpdatePeriod) {
    super();
    this.jiraClient = jiraClient;
    this.esIntegrationComponent = esIntegrationComponent;
    this.indexUpdatePeriod = indexUpdatePeriod;
    this.maxIndexingThreads = maxIndexingThreads;
    this.jiraIssueIndexStructureBuilder = jiraIssueIndexStructureBuilder;
    this.indexFullUpdatePeriod = indexFullUpdatePeriod;
  }

  @Override
  public void run() {
    logger.info("JIRA river projects indexing coordinator task started");
    try {
      while (true) {
        if (esIntegrationComponent.isClosed()) {
          return;
        }
        try {
          processLoopTask();
        } catch (InterruptedException e1) {
          return;
        } catch (Exception e) {
          if (esIntegrationComponent.isClosed())
            return;
          logger.error("Failed to process JIRA update coordination task {}", e, e.getMessage());
        }
        try {
          if (esIntegrationComponent.isClosed())
            return;
          logger.debug("JIRA river coordinator task is going to sleep for {} ms", coordinatorThreadWaits);
          Thread.sleep(coordinatorThreadWaits);
        } catch (InterruptedException e1) {
          return;
        }
      }
    } finally {
      synchronized (projectIndexers) {
        for (Thread pi : projectIndexers.values()) {
          pi.interrupt();
        }
        projectIndexers.clear();
      }
      logger.info("JIRA river projects indexing coordinator task stopped");
    }
  }

  /**
   * Process coordination tasks in one loop of coordinator.
   * 
   * @throws Exception
   * @throws InterruptedException id interrupted
   */
  protected void processLoopTask() throws Exception, InterruptedException {
    if (projectKeysToIndexQueue.isEmpty()) {
      fillProjectKeysToIndexQueue();
    }
    if (projectKeysToIndexQueue.isEmpty()) {
      // no projects to process now, we can slow down looping
      coordinatorThreadWaits = COORDINATOR_THREAD_WAITS_SLOW;
    } else {
      // some projects to process now, we need to loop quickly to process it
      coordinatorThreadWaits = COORDINATOR_THREAD_WAITS_QUICK;
      startIndexers();
    }
  }

  /**
   * Fill {@link #projectKeysToIndexQueue} by projects which needs to be indexed now.
   * 
   * @throws Exception in case of problem
   * @throws InterruptedException if indexing interruption is requested by ES server
   */
  protected void fillProjectKeysToIndexQueue() throws Exception, InterruptedException {
    List<String> ap = esIntegrationComponent.getAllIndexedProjectsKeys();
    if (ap != null && !ap.isEmpty()) {
      for (String projectKey : ap) {
        if (esIntegrationComponent.isClosed())
          throw new InterruptedException();
        // do not schedule project for indexing if indexing runs already for it
        synchronized (projectIndexers) {
          if (projectIndexers.containsKey(projectKey)) {
            continue;
          }
        }
        if (projectIndexUpdateNecessary(projectKey)) {
          projectKeysToIndexQueue.add(projectKey);
        }
      }
    }
  }

  /**
   * Start indexers for projects in {@link #projectKeysToIndexQueue} but not more than {@link #maxIndexingThreads}.
   * 
   * @throws InterruptedException if indexing process is interrupted
   * @throws Exception
   */
  protected void startIndexers() throws InterruptedException, Exception {
    String firstSkippedFullIndex = null;
    while (projectIndexers.size() < maxIndexingThreads && !projectKeysToIndexQueue.isEmpty()) {
      if (esIntegrationComponent.isClosed())
        throw new InterruptedException();
      String projectKey = projectKeysToIndexQueue.poll();

      boolean fullUpdateNecessary = projectIndexFullUpdateNecessary(projectKey);

      // reserve last free thread for incremental updates!!!
      if (fullUpdateNecessary && maxIndexingThreads > 1 && projectIndexers.size() == (maxIndexingThreads - 1)) {
        projectKeysToIndexQueue.add(projectKey);
        // try to find some project for incremental update, if not any found then end
        if (firstSkippedFullIndex == null) {
          firstSkippedFullIndex = projectKey;
        } else {
          if (firstSkippedFullIndex == projectKey)
            return;
        }
        continue;
      }

      Thread it = esIntegrationComponent.acquireIndexingThread("jira_river_indexer_" + projectKey,
          new JIRAProjectIndexer(projectKey, fullUpdateNecessary, jiraClient, esIntegrationComponent,
              jiraIssueIndexStructureBuilder));
      esIntegrationComponent.storeDatetimeValue(projectKey, STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE,
          new Date(), null);
      synchronized (projectIndexers) {
        projectIndexers.put(projectKey, it);
      }
      it.start();
    }
  }

  /**
   * Check if search index update for given JIRA project have to be performed now.
   * 
   * @param projectKey JIRA project key
   * @return true to perform index update now
   * @throws IOException
   */
  protected boolean projectIndexUpdateNecessary(String projectKey) throws Exception {
    Date lastIndexing = esIntegrationComponent.readDatetimeValue(projectKey,
        STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE);
    if (logger.isDebugEnabled())
      logger.debug("Project {} last indexing start date is {}. We perform next indexing after {}ms.", projectKey,
          lastIndexing, indexUpdatePeriod);
    return lastIndexing == null || lastIndexing.getTime() < ((System.currentTimeMillis() - indexUpdatePeriod));
  }

  /**
   * Check if search index full update for given JIRA project have to be performed now.
   * 
   * @param projectKey JIRA project key
   * @return true to perform index full update now
   * @throws IOException
   */
  protected boolean projectIndexFullUpdateNecessary(String projectKey) throws Exception {
    if (indexFullUpdatePeriod < 1) {
      return false;
    }
    Date lastIndexing = esIntegrationComponent.readDatetimeValue(projectKey,
        STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE);
    if (logger.isDebugEnabled())
      logger.debug("Project {} last full update date is {}. We perform next full indexing after {}ms.", projectKey,
          lastIndexing, indexFullUpdatePeriod);
    return lastIndexing == null || lastIndexing.getTime() < ((System.currentTimeMillis() - indexFullUpdatePeriod));
  }

  @Override
  public void reportIndexingFinished(String jiraProjectKey, boolean finishedOK, boolean fullUpdate) {
    synchronized (projectIndexers) {
      projectIndexers.remove(jiraProjectKey);
    }
    if (finishedOK && fullUpdate) {
      try {
        esIntegrationComponent.storeDatetimeValue(jiraProjectKey, STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE,
            new Date(), null);
      } catch (Exception e) {
        logger.error("Can't store {} value due: {}", STORE_PROPERTYNAME_LAST_INDEX_FULL_UPDATE_DATE, e.getMessage());
      }
    }
  }

  /**
   * Configuration - Set period of index automatic full update from jira [ms]. value <= 0 means never.
   * 
   * @param indexFullUpdatePeriod to set
   */
  public void setIndexFullUpdatePeriod(int indexFullUpdatePeriod) {
    this.indexFullUpdatePeriod = indexFullUpdatePeriod;
  }

}
