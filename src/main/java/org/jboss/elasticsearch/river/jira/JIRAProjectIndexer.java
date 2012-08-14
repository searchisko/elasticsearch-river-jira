/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.Date;
import java.util.Map;

import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

/**
 * Class used to run one index update process for one JIRA project.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRAProjectIndexer implements Runnable {

  private static final ESLogger logger = Loggers.getLogger(JIRAProjectIndexer.class);

  protected final IJIRAClient jiraClient;

  protected final JiraRiver jiraRiver;

  protected final String projectKey;

  public JIRAProjectIndexer(String projectKey, IJIRAClient jiraClient, JiraRiver jiraRiver) {
    this.jiraClient = jiraClient;
    this.projectKey = projectKey;
    this.jiraRiver = jiraRiver;
  }

  @Override
  public void run() {
    int updatedCount = 0;
    long startTime = System.currentTimeMillis();
    try {
      updatedCount = processUpdate();
    } catch (Exception e) {
      logger.error("Failed to process JIRA updates for project " + projectKey, e);
    } finally {
      logger.info("Finished processing of JIRA updates for project " + projectKey + ". Updated " + updatedCount
          + " issues. Time elapsed " + ((System.currentTimeMillis() - startTime) / 1000) + "s.");
    }
  }

  /**
   * 
   * @return number of issues updated in index
   * @throws Exception
   * 
   */
  @SuppressWarnings("unchecked")
  protected int processUpdate() throws Exception {
    int updatedCount = 0;
    // TODO load updatedAfter from persistent store to continue last indexing
    Date updatedAfter = null;
    logger.info("Go to process JIRA updates for project " + projectKey + " for issues updated "
        + (updatedAfter != null ? ("after " + updatedAfter) : "in whole history"));

    int startAt = 0;

    boolean cont = true;
    while (cont) {
      if (isClosed())
        return updatedCount;
      ChangedIssuesResults res = jiraClient.getJIRAChangedIssues(projectKey, startAt, updatedAfter, null);

      if (res.getIssuesCount() == 0) {
        cont = false;
      } else {
        String firstIssueUpdated = null;
        String lastIssueUpdated = null;
        for (Map<String, Object> issue : res.getIssues()) {
          lastIssueUpdated = (String) ((Map<String, Object>) issue.get("fields")).get("updated");
          if (firstIssueUpdated == null) {
            firstIssueUpdated = lastIssueUpdated;
          }
          updatedCount++;
          logger.debug("Go to update index for issue {}", issue.get("key"));
          if (isClosed())
            break;
        }

        // TODO persist lastIssueUpdated if not null so we can continue next time to update starting this datetime

        // next logic depends on issues sorted by update time ascending when returned from
        // jiraClient.getJIRAChangedIssues()!!!!
        if (!lastIssueUpdated.equals(firstIssueUpdated)) {
          // processed issues updated in different times, so we can continue by issue filtering based on latest time
          // of update which is more safe for concurrent changes in JIRA
          updatedAfter = ISODateTimeFormat.dateTimeParser().parseDateTime(lastIssueUpdated).toDate();
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
    return updatedCount;
  }

  /**
   * Check if we must interrupt update process because JIRA River is closed/stopped.
   * 
   * @return true if we must interrupt update process
   */
  protected boolean isClosed() {
    return jiraRiver != null && jiraRiver.isClosed();
  }

}
