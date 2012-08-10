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

  private final JIRA5RestClient jiraClient;

  private final String projectKey;

  public JIRAProjectIndexer(String projectKey, JIRA5RestClient jiraClient) {
    this.jiraClient = jiraClient;
    this.projectKey = projectKey;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void run() {
    logger.debug("Go to process JIRA updates for project " + projectKey);
    try {
      Date updatedAfter = null;
      int startAt = 0;

      boolean cont = true;
      while (cont) {
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
            // TODO update issue in search index
            System.out.println(issue.get("key"));
          }
          // next logic depends on issue update time ascending sorting on JIRA side called from
          // jiraClient.getJIRAChangedIssues()!!!!
          if (!lastIssueUpdated.equals(firstIssueUpdated)) {
            // processed issues updated in different times, so we can continue by issue filtering based on latest time
            // of update which is more safe for concurrent changes in JIRA
            // JIRA datetime format: 2009-03-23T08:38:52.000-0400
            updatedAfter = ISODateTimeFormat.dateTimeParser().parseDateTime(lastIssueUpdated).toDate();
            cont = res.getTotal() > (res.getStartAt() + res.getIssuesCount());
            startAt = 0;
          } else {
            // more issues updated in same time, we must go over them using pagination only, which may sometimes lead
            // to some issue update lost due concurrent changes in JIRA
            startAt = res.getStartAt() + res.getIssuesCount();
            cont = res.getTotal() > startAt;
          }
          // TODO completely finish this method if river is stopped
        }
      }
      // TODO persist lastIssueUpdated if not null so we can continue next time from this datetime
    } catch (Exception e) {
      logger.error("Failed to process JIRA updates for project " + projectKey, e);
    }
  }
}
