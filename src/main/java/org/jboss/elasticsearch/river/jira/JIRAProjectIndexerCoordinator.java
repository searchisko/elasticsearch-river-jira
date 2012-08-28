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
   */
  protected static final String STORE_PROPERTYNAME_LAST_INDEX_UPDATE_START_DATE = "lastIndexUpdateStartDate";

  protected static final int COORDINATOR_THREAD_WAITS_QUICK = 2 * 1000;
  protected static final int COORDINATOR_THREAD_WAITS_SLOW = 60 * 1000;
  protected int coordinatorThreadWaits = COORDINATOR_THREAD_WAITS_QUICK;

  protected IESIntegration esIntegrationComponent;

  /**
   * JIRA client to access data from JIRA
   */
  protected IJIRAClient jiraClient;

  protected IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilder;

  protected int maxIndexingThreads;

  protected int indexUpdatePeriod;

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
   * @param indexUpdatePeriod index update period [ms]
   * @param maxIndexingThreads maximal number of parallel JIRA indexing threads started by this coordinator
   */
  public JIRAProjectIndexerCoordinator(IJIRAClient jiraClient, IESIntegration esIntegrationComponent,
      IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilder, int indexUpdatePeriod, int maxIndexingThreads) {
    super();
    this.jiraClient = jiraClient;
    this.esIntegrationComponent = esIntegrationComponent;
    this.indexUpdatePeriod = indexUpdatePeriod;
    this.maxIndexingThreads = maxIndexingThreads;
    this.jiraIssueIndexStructureBuilder = jiraIssueIndexStructureBuilder;
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
    while (projectIndexers.size() < maxIndexingThreads && !projectKeysToIndexQueue.isEmpty()) {
      if (esIntegrationComponent.isClosed())
        throw new InterruptedException();
      String projectKey = projectKeysToIndexQueue.poll();
      Thread it = esIntegrationComponent.acquireIndexingThread("jira_river_indexer_" + projectKey,
          new JIRAProjectIndexer(projectKey, jiraClient, esIntegrationComponent, jiraIssueIndexStructureBuilder));
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
   * Report that indexing of JIRA project was finished. Used to coordinate parallel indexing of all projects.
   * 
   * @param jiraProjectKey JIRA project key for finished indexing
   * @param finishedOK set to <code>true</code> if indexing finished OK, <code>false</code> if finished due error
   */
  @Override
  public void reportIndexingFinished(String jiraProjectKey, boolean finishedOK) {
    synchronized (projectIndexers) {
      projectIndexers.remove(jiraProjectKey);
    }
  }

}
