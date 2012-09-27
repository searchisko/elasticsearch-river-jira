/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.elasticsearch.common.xcontent.XContentBuilder;

/**
 * Value object holding info about one indexing run.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class ProjectIndexingInfo {

  private static final String DOCFIELD_ISSUES_DELETED = "issues_deleted";
  private static final String DOCVAL_RESULT_OK = "OK";
  private static final String DOCVAL_TYPE_FULL = "FULL";
  public static final String DOCFIELD_ERROR_MESSAGE = "error_message";
  public static final String DOCFIELD_TIME_ELAPSED = "time_elapsed";
  public static final String DOCFIELD_RESULT = "result";
  public static final String DOCFIELD_ISSUES_UPDATED = "issues_updated";
  public static final String DOCFIELD_UPDATE_TYPE = "update_type";
  public static final String DOCFIELD_START_DATE = "start_date";
  public static final String DOCFIELD_PROJECT_KEY = "project_key";
  /**
   * Key of JIRA project this indexing is for.
   */
  public String projectKey;
  /**
   * <code>true</code> if reported indexing was full update, <code>false</code> on incremental update.
   */
  public boolean fullUpdate;
  /**
   * Number of issues updated during this indexing run.
   */
  public int issuesUpdated;
  /**
   * Number of issues deleted during this indexing run.
   */
  public int issuesDeleted;
  /**
   * Number of comment documents deleted during this indexing run.
   */
  public int commentsDeleted;

  /**
   * Date of indexing start.
   */
  public Date startDate;
  /**
   * <code>true</code> if indexing finished OK, <code>false</code> if finished due error.
   */
  public boolean finishedOK;
  /**
   * time of this indexing run [ms]. Available after finished.
   */
  public long timeElapsed;
  /**
   * error message if indexing finished with error
   */
  public String errorMessage;

  /**
   * Partially filling constructor.
   * 
   * @param projectKey
   * @param fullUpdate
   */
  public ProjectIndexingInfo(String projectKey, boolean fullUpdate) {
    super();
    this.projectKey = projectKey;
    this.fullUpdate = fullUpdate;
  }

  /**
   * Full filling constructor.
   * 
   * @param projectKey
   * @param fullUpdate
   * @param issuesUpdated
   * @param issuesDeleted
   * @param commentsDeleted
   * @param startDate
   * @param finishedOK
   * @param timeElapsed
   * @param errorMessage
   */
  public ProjectIndexingInfo(String projectKey, boolean fullUpdate, int issuesUpdated, int issuesDeleted,
      int commentsDeleted, Date startDate, boolean finishedOK, long timeElapsed, String errorMessage) {
    super();
    this.projectKey = projectKey;
    this.fullUpdate = fullUpdate;
    this.issuesUpdated = issuesUpdated;
    this.issuesDeleted = issuesDeleted;
    this.commentsDeleted = commentsDeleted;
    this.startDate = startDate;
    this.finishedOK = finishedOK;
    this.timeElapsed = timeElapsed;
    this.errorMessage = errorMessage;
  }

  /**
   * Add object with project indexing info to given document builder.
   * 
   * @param builder to add information Object into
   * @param printProjectKey set to true to print project key into document
   * @param printFinalStatus set to true to print final status info into document
   * @return builder same as on input.
   * @throws IOException
   */
  public XContentBuilder buildDocument(XContentBuilder builder, boolean printProjectKey, boolean printFinalStatus)
      throws IOException {
    builder.startObject();
    if (printProjectKey)
      builder.field(DOCFIELD_PROJECT_KEY, projectKey);

    builder.field(DOCFIELD_UPDATE_TYPE, fullUpdate ? DOCVAL_TYPE_FULL : "INCREMENTAL");
    builder.field(DOCFIELD_START_DATE, startDate);
    builder.field(DOCFIELD_ISSUES_UPDATED, issuesUpdated);
    builder.field(DOCFIELD_ISSUES_DELETED, issuesDeleted);
    if (printFinalStatus) {
      builder.field(DOCFIELD_RESULT, finishedOK ? DOCVAL_RESULT_OK : "ERROR");
      builder.field(DOCFIELD_TIME_ELAPSED, timeElapsed + "ms");
      if (!finishedOK && !Utils.isEmpty(errorMessage)) {
        builder.field(DOCFIELD_ERROR_MESSAGE, errorMessage);
      }
    }
    builder.endObject();
    return builder;
  }

  /**
   * Read object back from document created over {@link #buildDocument(XContentBuilder, boolean, boolean)}.
   * 
   * @param document to read
   * @return object instance or null
   */
  public static ProjectIndexingInfo readFromDocument(Map<String, Object> document) {
    if (document == null)
      return null;
    ProjectIndexingInfo ret = new ProjectIndexingInfo((String) document.get(DOCFIELD_PROJECT_KEY),
        DOCVAL_TYPE_FULL.equals(document.get(DOCFIELD_UPDATE_TYPE)));
    ret.startDate = DateTimeUtils.parseISODateTime((String) document.get(DOCFIELD_START_DATE));
    ret.issuesUpdated = Utils.nodeIntegerValue(document.get(DOCFIELD_ISSUES_UPDATED));
    ret.issuesDeleted = Utils.nodeIntegerValue(document.get(DOCFIELD_ISSUES_DELETED));
    ret.finishedOK = DOCVAL_RESULT_OK.equals(document.get(DOCFIELD_RESULT));
    ret.timeElapsed = Long.parseLong(((String) document.get(DOCFIELD_TIME_ELAPSED)).replace("ms", ""));
    ret.errorMessage = (String) document.get(DOCFIELD_ERROR_MESSAGE);
    return ret;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((projectKey == null) ? 0 : projectKey.hashCode());
    return result;
  }

}
