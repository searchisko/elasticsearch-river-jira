package org.jboss.elasticsearch.river.jira;

import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Interface for JIRA Client implementation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public interface IJIRAClient {

  /**
   * Get projectKeys of all projects in configured JIRA instance.
   * 
   * @return list of project keys
   * @throws Exception
   */
  public abstract List<String> getAllJIRAProjects() throws Exception;

  /**
   * Get list of issues from remote JIRA instance and parse them into <code>Map of Maps</code> structure. Issues are
   * ascending ordered by date of last update performed on issue. List is limited to only some number of issues (given
   * by both JIRA and this client configuration).
   * 
   * @param projectKey mandatory key of JIRA project to get issues for
   * @param startAt the index of the first issue to return (0-based)
   * @param updatedAfter optional parameter to return issues updated only after given date.
   * @param updatedBefore optional parameter to return issues updated only before given date.
   * @return List of issues informations parsed from JIRA reply into <code>Map of Maps</code> structure.
   * @throws Exception
   */
  public abstract ChangedIssuesResults getJIRAChangedIssues(String projectKey, int startAt, Date updatedAfter,
      Date updatedBefore) throws Exception;

  /**
   * Configuration - Set Timezone used to format date into JQL.
   * 
   * @param zone to set
   */
  public void setJQLDateFormatTimezone(TimeZone zone);

  /**
   * Configuration - Set maximal number of issues returned from {@link #getJIRAChangedIssues(String, int, Date, Date)}.
   * Called in time of configuration.
   * 
   * @param listJIRAIssuesMax to set
   */
  public abstract void setListJIRAIssuesMax(int listJIRAIssuesMax);

  /**
   * Get maximal number of issues returned from {@link #getJIRAChangedIssues(String, int, Date, Date)} configured for
   * this instance.
   * 
   * @return configured value
   */
  public abstract int getListJIRAIssuesMax();

  /**
   * Add index structure builder so JIRA client can obtain only fields necessary for indexing.
   * 
   * @param indexStructureBuilder
   * @see IJIRAIssueIndexStructureBuilder#getRequiredJIRACallIssueFields()
   */
  public void setIndexStructureBuilder(IJIRAIssueIndexStructureBuilder indexStructureBuilder);

  /**
   * Get actual index structure builder.
   * 
   * @return actual index structure builder
   */
  public IJIRAIssueIndexStructureBuilder getIndexStructureBuilder();

}