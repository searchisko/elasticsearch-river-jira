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
	List<String> getAllJIRAProjects() throws Exception;

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
	ChangedIssuesResults getJIRAChangedIssues(String projectKey, int startAt, Date updatedAfter, Date updatedBefore)
			throws Exception;

    /**
     * Configuration - Set JQL Template used while querying issues from jira.
     * This should include '%s' (w/o quotes) as placeholders for PROJECT KEY, AFTER CRITERION and BEFORE CRITERION
     *
     * For example such template: "project='%s' %s %s ORDER BY updated ASC"
     * When populated with following strings:
     * 1) MYPROJECT
     * 2) AND updatedDate >= "2013-12-24 23:59"
     * 3) AND updatedDate <= "2014-12-24 23:59"
     * Would become: "project='MYPROJECT' AND updatedDate >= "2013-12-24 23:59" AND updatedDate <= "2014-12-24 23:59" ORDER BY updated ASC"
     * Also note that in case of rendering JQL according to template, program may not need to insert AFTER and BEFORE criterions.
     * In such cases empty string is inserted instead. Therefore template may not depend on criterions being nonempty strings.
     *
     * @param jqlTemplate suitable String for usage as format in String.format(format, args)
     */
    void setJqlTemplate(String jqlTemplate);

    /**
	 * Configuration - Set Timezone used to format date into JQL.
	 * 
	 * @param zone to set
	 */
	void setJQLDateFormatTimezone(TimeZone zone);

	/**
	 * Configuration - Set maximal number of issues returned from {@link #getJIRAChangedIssues(String, int, Date, Date)}.
	 * Called in time of configuration.
	 * 
	 * @param listJIRAIssuesMax to set
	 */
	void setListJIRAIssuesMax(int listJIRAIssuesMax);

	/**
	 * Get maximal number of issues returned from {@link #getJIRAChangedIssues(String, int, Date, Date)} configured for
	 * this instance.
	 * 
	 * @return configured value
	 */
	int getListJIRAIssuesMax();

	/**
	 * Add index structure builder so JIRA client can obtain only fields necessary for indexing.
	 * 
	 * @param indexStructureBuilder
	 * @see IJIRAIssueIndexStructureBuilder#getRequiredJIRACallIssueFields()
	 */
	void setIndexStructureBuilder(IJIRAIssueIndexStructureBuilder indexStructureBuilder);

	/**
	 * Get actual index structure builder.
	 * 
	 * @return actual index structure builder
	 */
	IJIRAIssueIndexStructureBuilder getIndexStructureBuilder();

	/**
	 * Get base URL of jira API used by this client
	 * 
	 * @return
	 */
	String getJiraAPIUrlBase();

}