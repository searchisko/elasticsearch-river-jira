/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.search.SearchHit;

/**
 * Class used to run one index update process for one JIRA project. Can be used only for one run, then must be discarded
 * and new instance created!
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRAProjectIndexer implements Runnable {

  private static final ESLogger logger = Loggers.getLogger(JIRAProjectIndexer.class);

  /**
   * Property value where "last indexed issue update date" is stored
   * 
   * @see IESIntegration#storeDatetimeValue(String, String, Date, BulkRequestBuilder)
   * @see IESIntegration#readDatetimeValue(String, String)
   */
  protected static final String STORE_PROPERTYNAME_LAST_INDEXED_ISSUE_UPDATE_DATE = "lastIndexedIssueUpdateDate";

  protected final IJIRAClient jiraClient;

  protected final IESIntegration esIntegrationComponent;

  /**
   * Configured JIRA issue index structure builder to be used.
   */
  protected final IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilder;

  /**
   * Key of JIRA project updated.
   */
  protected final String projectKey;

  /**
   * <code>true</code> if full update indexing is necessary, <code>false</code> on incremental update.
   */
  protected boolean fullUpdate = false;

  /**
   * Time when indexing started.
   */
  protected long startTime = 0;

  /**
   * Number of issues updated during this indexing.
   */
  protected int updatedCount = 0;

  /**
   * Number of issues deleted during this indexing.
   */
  protected int deleteCount = 0;

  /**
   * Number of issue comments deleted during this indexing.
   */
  protected int deleteCommentsCount = 0;

  /**
   * @param projectKey JIRA project key for project to be indexed by this indexer.
   * @param jiraClient configured JIRA client to be used to obtain informations from JIRA.
   * @param esIntegrationComponent to be used to call River component and ElasticSearch functions
   */
  public JIRAProjectIndexer(String projectKey, boolean fullUpdate, IJIRAClient jiraClient,
      IESIntegration esIntegrationComponent, IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilder) {
    if (projectKey == null || projectKey.trim().length() == 0)
      throw new IllegalArgumentException("projectKey must be defined");
    this.jiraClient = jiraClient;
    this.projectKey = projectKey;
    this.fullUpdate = fullUpdate;
    this.esIntegrationComponent = esIntegrationComponent;
    this.jiraIssueIndexStructureBuilder = jiraIssueIndexStructureBuilder;
  }

  @Override
  public void run() {
    startTime = System.currentTimeMillis();
    try {
      processUpdate();
      processDelete(new Date(startTime));
      long timeElapsed = (System.currentTimeMillis() - startTime);
      esIntegrationComponent.reportIndexingFinished(projectKey, true, fullUpdate, updatedCount, deleteCount, new Date(
          startTime), timeElapsed, null);
      logger.info("Finished {} update for JIRA project {}. {} updated and {} deleted issues. Time elapsed {}s.",
          fullUpdate ? "full" : "incremental", projectKey, updatedCount, deleteCount, (timeElapsed / 1000));
    } catch (Throwable e) {
      long timeElapsed = (System.currentTimeMillis() - startTime);
      esIntegrationComponent.reportIndexingFinished(projectKey, false, fullUpdate, updatedCount, deleteCount, new Date(
          startTime), timeElapsed, e.getMessage());
      logger.error("Failed {} update for JIRA project {} due: {}", e, fullUpdate ? "full" : "incremental", projectKey,
          e.getMessage());
    }
  }

  /**
   * Process update of search index for configured JIRA project. A {@link #updatedCount} field is updated inside of this
   * method. A {@link #fullUpdate} field can be updated inside of this method.
   * 
   * @throws Exception
   * 
   */
  protected void processUpdate() throws Exception {
    updatedCount = 0;
    Date updatedAfter = null;
    if (!fullUpdate) {
      updatedAfter = Utils.roundDateToMinutePrecise(readLastIssueUpdatedDate(projectKey));
    }
    Date updatedAfterStarting = updatedAfter;
    if (updatedAfter == null)
      fullUpdate = true;
    Date lastIssueUpdatedDate = null;

    int startAt = 0;

    logger.info("Go to perform {} update for JIRA project {}", fullUpdate ? "full" : "incremental", projectKey);

    boolean cont = true;
    while (cont) {
      if (isClosed())
        return;

      if (logger.isDebugEnabled())
        logger.debug("Go to ask for updated JIRA issues for project {} with startAt {} updated {}", projectKey,
            startAt, (updatedAfter != null ? ("after " + updatedAfter) : "in whole history"));

      ChangedIssuesResults res = jiraClient.getJIRAChangedIssues(projectKey, startAt, updatedAfter, null);

      if (res.getIssuesCount() == 0) {
        cont = false;
      } else {
        Date firstIssueUpdatedDate = null;
        BulkRequestBuilder esBulk = esIntegrationComponent.prepareESBulkRequestBuilder();
        for (Map<String, Object> issue : res.getIssues()) {
          String issueKey = jiraIssueIndexStructureBuilder.extractIssueKey(issue);
          if (issueKey == null) {
            throw new IllegalArgumentException("Issue 'key' field not found in JIRA response for project " + projectKey
                + " within issue data: " + issue);
          }
          lastIssueUpdatedDate = Utils.roundDateToMinutePrecise(jiraIssueIndexStructureBuilder
              .extractIssueUpdated(issue));
          logger.debug("Go to update index for issue {} with updated {}", issueKey, lastIssueUpdatedDate);
          if (lastIssueUpdatedDate == null) {
            throw new IllegalArgumentException("'updated' field not found in JIRA response data for issue " + issueKey);
          }
          if (firstIssueUpdatedDate == null) {
            firstIssueUpdatedDate = lastIssueUpdatedDate;
          }

          jiraIssueIndexStructureBuilder.indexIssue(esBulk, projectKey, issue);
          updatedCount++;
          if (isClosed())
            break;
        }

        storeLastIssueUpdatedDate(esBulk, projectKey, lastIssueUpdatedDate);
        esIntegrationComponent.executeESBulkRequest(esBulk);

        // next logic depends on issues sorted by update time ascending when returned from
        // jiraClient.getJIRAChangedIssues()!!!!
        if (!lastIssueUpdatedDate.equals(firstIssueUpdatedDate)) {
          // processed issues updated in different times, so we can continue by issue filtering based on latest time
          // of update which is more safe for concurrent changes in JIRA
          updatedAfter = lastIssueUpdatedDate;
          cont = res.getTotal() > (res.getStartAt() + res.getIssuesCount());
          startAt = 0;
        } else {
          // more issues updated in same time, we must go over them using pagination only, which may sometimes lead
          // to some issue update lost due concurrent changes in JIRA
          startAt = res.getStartAt() + res.getIssuesCount();
          cont = res.getTotal() > startAt;
        }
      }
    }

    if (updatedCount > 0 && lastIssueUpdatedDate != null && updatedAfterStarting != null
        && updatedAfterStarting.equals(lastIssueUpdatedDate)) {
      // no any new issue during this update cycle, go to increment lastIssueUpdatedDate in store by one minute not to
      // index last issue again and again in next cycle - this is here due JQL minute precise on timestamp search
      storeLastIssueUpdatedDate(null, projectKey,
          Utils.roundDateToMinutePrecise(new Date(lastIssueUpdatedDate.getTime() + 64 * 1000)));
    }
  }

  /**
   * Process delete of issues from search index for configured JIRA project. A {@link #deleteCount} field is updated
   * inside of this method.
   * 
   * @param boundDate date when full update was started. We delete all search index documents not updated after this
   *          date (which means these issues are not in jira anymore).
   */
  protected void processDelete(Date boundDate) throws Exception {

    if (boundDate == null)
      throw new IllegalArgumentException("boundDate must be set");

    deleteCount = 0;
    deleteCommentsCount = 0;

    if (!fullUpdate)
      return;

    logger.debug("Go to process JIRA deletes for project {} for issues not updated in index after {}", projectKey,
        boundDate);

    String indexName = jiraIssueIndexStructureBuilder.getIssuesSearchIndexName(projectKey);
    esIntegrationComponent.refreshSearchIndex(indexName);

    logger.debug("go to delete indexed issues for project {} not updated after {}", projectKey, boundDate);
    SearchRequestBuilder srb = esIntegrationComponent.prepareESScrollSearchRequestBuilder(indexName);
    jiraIssueIndexStructureBuilder.buildSearchForIndexedDocumentsNotUpdatedAfter(srb, projectKey, boundDate);

    SearchResponse scrollResp = esIntegrationComponent.executeESSearchRequest(srb);

    if (scrollResp.hits().totalHits() > 0) {
      scrollResp = esIntegrationComponent.executeESScrollSearchNextRequest(scrollResp);
      BulkRequestBuilder esBulk = esIntegrationComponent.prepareESBulkRequestBuilder();
      while (scrollResp.hits().hits().length > 0) {
        for (SearchHit hit : scrollResp.getHits()) {
          logger.debug("Go to delete indexed issue for document id {}", hit.getId());
          if (jiraIssueIndexStructureBuilder.deleteIssueDocument(esBulk, hit)) {
            deleteCount++;
          } else {
            deleteCommentsCount++;
          }
        }
        scrollResp = esIntegrationComponent.executeESScrollSearchNextRequest(scrollResp);
      }
      esIntegrationComponent.executeESBulkRequest(esBulk);
    }
  }

  /**
   * Check if we must interrupt update process because ElasticSearch runtime needs it.
   * 
   * @return true if we must interrupt update process
   */
  protected boolean isClosed() {
    return esIntegrationComponent != null && esIntegrationComponent.isClosed();
  }

  /**
   * Get date of last issue updated for given JIRA project from persistent store inside ES cluster, so we can continue
   * in update process from this point.
   * 
   * @param jiraProjectKey JIRA project key to get date for.
   * @return date of last issue updated or null if not available (in this case indexing starts from the beginning of
   *         project history)
   * @throws IOException
   * @see #storeLastIssueUpdatedDate(BulkRequestBuilder, String, Date)
   */
  protected Date readLastIssueUpdatedDate(String jiraProjectKey) throws Exception {
    return esIntegrationComponent.readDatetimeValue(jiraProjectKey, STORE_PROPERTYNAME_LAST_INDEXED_ISSUE_UPDATE_DATE);
  }

  /**
   * Store date of last issue updated for given JIRA project into persistent store inside ES cluster, so we can continue
   * in update process from this point next time.
   * 
   * @param esBulk ElasticSearch bulk request to be used for update
   * @param jiraProjectKey JIRA project key to store date for.
   * @param lastIssueUpdatedDate date to store
   * @throws Exception
   * @see #readLastIssueUpdatedDate(String)
   */
  protected void storeLastIssueUpdatedDate(BulkRequestBuilder esBulk, String jiraProjectKey, Date lastIssueUpdatedDate)
      throws Exception {
    esIntegrationComponent.storeDatetimeValue(jiraProjectKey, STORE_PROPERTYNAME_LAST_INDEXED_ISSUE_UPDATE_DATE,
        lastIssueUpdatedDate, esBulk);
  }

}
