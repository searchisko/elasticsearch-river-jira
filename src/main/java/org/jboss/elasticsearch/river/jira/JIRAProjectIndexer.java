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
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.support.XContentMapValues;

/**
 * Class used to run one index update process for one JIRA project.
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

  protected final String projectKey;

  protected int updatedCount = 0;

  /**
   * @param projectKey JIRA project key for project to be indexed by this indexer.
   * @param jiraClient configured JIRA client to be used to obtain informations from JIRA.
   * @param esIntegrationComponent to be used to call River component and ElasticSearch functions
   */
  public JIRAProjectIndexer(String projectKey, IJIRAClient jiraClient, IESIntegration esIntegrationComponent,
      IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilder) {
    if (projectKey == null || projectKey.trim().length() == 0)
      throw new IllegalArgumentException("projectKey must be defined");
    this.jiraClient = jiraClient;
    this.projectKey = projectKey;
    this.esIntegrationComponent = esIntegrationComponent;
    this.jiraIssueIndexStructureBuilder = jiraIssueIndexStructureBuilder;
  }

  @Override
  public void run() {
    long startTime = System.currentTimeMillis();
    try {
      processUpdate();
      long timeElapsed = (System.currentTimeMillis() - startTime);
      esIntegrationComponent.reportIndexingFinished(projectKey, true, updatedCount, timeElapsed, null);
      logger.info("Finished processing of JIRA updates for project {}. Updated {} issues. Time elapsed {}s.",
          projectKey, updatedCount, (timeElapsed / 1000));
    } catch (Throwable e) {
      long timeElapsed = (System.currentTimeMillis() - startTime);
      esIntegrationComponent.reportIndexingFinished(projectKey, false, updatedCount, timeElapsed, e.getMessage());
      logger.error("Failed to process JIRA updates for project {} due: {}", e, projectKey, e.getMessage());
    }
  }

  /**
   * Process update of search index for configured JIRA project. A {@link #updatedCount} field is updated inside of this
   * method.
   * 
   * @return number of issues updated in index - same as {@link #updatedCount}
   * @throws Exception
   * 
   */
  @SuppressWarnings("unchecked")
  protected int processUpdate() throws Exception {
    updatedCount = 0;
    Date updatedAfter = Utils.roundDateToMinutePrecise(readLastIssueUpdatedDate(projectKey));
    logger.info("Go to process JIRA updates for project {} for issues updated {}", projectKey,
        (updatedAfter != null ? ("after " + updatedAfter) : "in whole history"));
    Date updatedAfterStarting = updatedAfter;
    int startAt = 0;

    boolean cont = true;
    while (cont) {
      if (isClosed())
        return updatedCount;

      if (logger.isDebugEnabled())
        logger.debug("Go to ask JIRA issues for project {} with startAt {} updated {}", projectKey, startAt,
            (updatedAfter != null ? ("after " + updatedAfter) : "in whole history"));

      ChangedIssuesResults res = jiraClient.getJIRAChangedIssues(projectKey, startAt, updatedAfter, null);

      if (res.getIssuesCount() == 0) {
        cont = false;
      } else {
        String firstIssueUpdated = null;
        String lastIssueUpdated = null;
        BulkRequestBuilder esBulk = esIntegrationComponent.getESBulkRequestBuilder();
        for (Map<String, Object> issue : res.getIssues()) {
          String issueKey = XContentMapValues.nodeStringValue(issue.get(JIRA5RestIssueIndexStructureBuilder.JF_KEY),
              null);
          if (issueKey == null) {
            throw new IllegalArgumentException("'key' field not found in JIRA data");
          }
          lastIssueUpdated = XContentMapValues.nodeStringValue(((Map<String, Object>) issue
              .get(JIRA5RestIssueIndexStructureBuilder.JF_FIELDS)).get(JIRA5RestIssueIndexStructureBuilder.JF_UPDATED),
              null);
          logger.debug("Go to update index for issue {} with updated {}", issueKey, lastIssueUpdated);
          if (lastIssueUpdated == null) {
            throw new IllegalArgumentException("'updated' field not found in JIRA data for key " + issueKey);
          }
          if (firstIssueUpdated == null) {
            firstIssueUpdated = lastIssueUpdated;
          }

          jiraIssueIndexStructureBuilder.indexIssue(esBulk, projectKey, issue);
          updatedCount++;
          if (isClosed())
            break;
        }

        Date lastIssueUpdatedDate = Utils.parseISODateWithMinutePrecise(lastIssueUpdated);

        storeLastIssueUpdatedDate(esBulk, projectKey, lastIssueUpdatedDate);
        esIntegrationComponent.executeESBulkRequestBuilder(esBulk);

        // next logic depends on issues sorted by update time ascending when returned from
        // jiraClient.getJIRAChangedIssues()!!!!
        if (!lastIssueUpdatedDate.equals(Utils.parseISODateWithMinutePrecise(firstIssueUpdated))) {
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

    if (updatedCount > 0 && updatedAfterStarting != null && updatedAfter != null
        && updatedAfterStarting.equals(updatedAfter)) {
      // no any new issue during this update cycle, go to increment lastIssueUpdatedDate in store by one minute not to
      // index last issue again and again in next cycle - this is here due JQL minute precise on timestamp search
      storeLastIssueUpdatedDate(null, projectKey,
          Utils.roundDateToMinutePrecise(new Date(updatedAfter.getTime() + 64 * 1000)));
    }

    return updatedCount;
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
