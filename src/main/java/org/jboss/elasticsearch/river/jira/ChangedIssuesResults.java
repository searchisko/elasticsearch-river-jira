/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.util.List;
import java.util.Map;

/**
 * Info about changed issues returned from JIRA server. List of issues with pagination informations.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 * @see JIRA5RestClient#getJIRAChangedIssues(String, java.util.Date, java.util.Date)
 */
public class ChangedIssuesResults {

  /**
   * Starting position of returned issues in complete list of issues matching search in JIRA. 0 based.
   */
  private int startAt;

  /**
   * maxResults constraint applied for search of these results
   */
  private int maxResults;

  /**
   * Total number of issues in JIRA matching performed search criteria on JIRA side.
   */
  private int total;

  /**
   * Issues returned from JIRA - count may be limited due maxResults constraint, first issue is from {@link #startAt}
   * position.
   */
  private List<Map<String, Object>> issues;

  /**
   * Constructor.
   * 
   * @param issues Issues returned from JIRA - count may be limited due maxResults constraint, first issue is from
   *          {@link #startAt} position.
   * @param startAt Starting position of returned issues in complete list of issues matching search in JIRA. 0 based.
   * @param maxResults constraint applied for search of these results
   * @param total number of issues in JIRA matching performed search criteria on JIRA side.
   */
  public ChangedIssuesResults(List<Map<String, Object>> issues, Integer startAt, Integer maxResults, Integer total) {
    super();
    if (startAt == null) {
      throw new IllegalArgumentException("startAt cant be null");
    }
    if (maxResults == null) {
      throw new IllegalArgumentException("maxResults cant be null");
    }
    if (total == null) {
      throw new IllegalArgumentException("total cant be null");
    }
    this.issues = issues;
    this.startAt = startAt;
    this.maxResults = maxResults;
    this.total = total;
  }

  /**
   * @return the startAt
   */
  public int getStartAt() {
    return startAt;
  }

  /**
   * @return the maxResults
   */
  public int getMaxResults() {
    return maxResults;
  }

  /**
   * @return the total
   */
  public int getTotal() {
    return total;
  }

  /**
   * @return the issues
   */
  public List<Map<String, Object>> getIssues() {
    return issues;
  }

  /**
   * Get number of issues in this result part
   * 
   * @return
   * @see #getIssues()
   */
  public int getIssuesCount() {
    if (issues == null)
      return 0;
    return issues.size();
  }

  @Override
  public String toString() {
    return "ChangedIssuesResults [startAt=" + startAt + ", maxResults=" + maxResults + ", total=" + total + ", issues="
        + issues + "]";
  }

}
