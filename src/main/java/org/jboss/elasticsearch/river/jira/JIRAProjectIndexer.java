/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.Date;
import java.util.Map;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
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

  protected final IESIntegration esIntegrationComponent;

  protected final String projectKey;

  public JIRAProjectIndexer(String projectKey, IJIRAClient jiraClient, IESIntegration esIntegrationComponent) {
    this.jiraClient = jiraClient;
    this.projectKey = projectKey;
    this.esIntegrationComponent = esIntegrationComponent;
  }

  @Override
  public void run() {
    int updatedCount = 0;
    long startTime = System.currentTimeMillis();
    try {
      updatedCount = processUpdate();
    } catch (Exception e) {
      logger.error("Failed to process JIRA updates for project {} due: {}", e, projectKey, e.getMessage());
    } finally {
      logger.info("Finished processing of JIRA updates for project {}. Updated {} issues. Time elapsed {}s.",
          projectKey, updatedCount, ((System.currentTimeMillis() - startTime) / 1000));
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
    Date updatedAfter = esIntegrationComponent.getLastIssueUpdatedDate(projectKey);
    logger.info("Go to process JIRA updates for project {} for issues updated {}", projectKey,
        (updatedAfter != null ? ("after " + updatedAfter) : "in whole history"));

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
        BulkRequestBuilder esBulk = esIntegrationComponent.getESBulkRequestBuilder();
        for (Map<String, Object> issue : res.getIssues()) {
          lastIssueUpdated = (String) ((Map<String, Object>) issue.get("fields")).get("updated");
          if (firstIssueUpdated == null) {
            firstIssueUpdated = lastIssueUpdated;
          }
          logger.debug("Go to update index for issue {}", issue.get("key"));
          // TODO write JIRA issue to the index esBulk update
          updatedCount++;
          if (isClosed())
            break;
        }

        esIntegrationComponent.storeLastIssueUpdatedDate(esBulk, projectKey, ISODateTimeFormat.dateTimeParser()
            .parseDateTime(lastIssueUpdated).toDate());
        esIntegrationComponent.executeESBulkRequestBuilder(esBulk);

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
   * Check if we must interrupt update process because ElasticSearch runtime needs it.
   * 
   * @return true if we must interrupt update process
   */
  protected boolean isClosed() {
    return esIntegrationComponent != null && esIntegrationComponent.isClosed();
  }

}
