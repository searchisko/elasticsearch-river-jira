package org.jboss.elasticsearch.river.jira.testtools;

import junit.framework.Assert;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jboss.elasticsearch.river.jira.ProjectIndexingInfo;

public class ProjectInfoMatcher extends BaseMatcher<ProjectIndexingInfo> {

  String project;
  boolean fullUpdate;
  boolean finishedOK;
  int issuesUpdated;
  int issuesDeleted;
  String errorMessage;

  /**
   * @param project
   * @param fullUpdate
   * @param finishedOK
   * @param issuesUpdated
   * @param issuesDeleted
   * @param errorMessage
   */
  public ProjectInfoMatcher(String project, boolean fullUpdate, boolean finishedOK, int issuesUpdated,
      int issuesDeleted, String errorMessage) {
    super();
    this.project = project;
    this.fullUpdate = fullUpdate;
    this.finishedOK = finishedOK;
    this.issuesUpdated = issuesUpdated;
    this.issuesDeleted = issuesDeleted;
    this.errorMessage = errorMessage;
  }

  @Override
  public boolean matches(Object arg0) {
    ProjectIndexingInfo info = (ProjectIndexingInfo) arg0;
    Assert.assertEquals(project, info.projectKey);
    Assert.assertEquals(fullUpdate, info.fullUpdate);
    Assert.assertEquals(finishedOK, info.finishedOK);
    Assert.assertEquals(issuesUpdated, info.issuesUpdated);
    Assert.assertEquals(issuesDeleted, info.issuesDeleted);
    Assert.assertEquals(errorMessage, info.errorMessage);
    Assert.assertNotNull(info.startDate);
    return true;
  }

  @Override
  public void describeTo(Description arg0) {
  }

}