/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;

/**
 * Class used to call JIRA 5 series functions to obtain JIRA content over REST API version 2. One instance of this class
 * is used to access one instance of JIRA.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRA5RestClient implements IJIRAClient {

  private static final ESLogger logger = Loggers.getLogger(JIRA5RestClient.class);

  private HttpClient httpclient;

  protected String jiraRestAPIUrlBase;

  protected boolean isAuthConfigured = false;

  protected int listJIRAIssuesMax = -1;

  protected IJIRAIssueIndexStructureBuilder indexStructureBuilder;

  /**
   * Constructor to create and configure remote JIRA REST API client.
   * 
   * @param jiraRestAPIUrlBase JIRA API URL used to call JIRA (see {@link #prepareAPIURLFromBaseURL(String)})
   * @param jiraUsername optional username to authenticate into JIRA
   * @param jiraPassword optional password to authenticate into JIRA
   * @param timeout JIRA http/s connection timeout in milliseconds
   */
  public JIRA5RestClient(String jiraUrlBase, String jiraUsername, String jiraPassword, Integer timeout) {

    jiraRestAPIUrlBase = prepareAPIURLFromBaseURL(jiraUrlBase);
    if (jiraRestAPIUrlBase == null) {
      throw new SettingsException("Parameter jira/urlBase must be set!");
    }

    URL url = null;
    try {
      url = new URL(jiraRestAPIUrlBase);
    } catch (MalformedURLException e) {
      throw new SettingsException("Parameter jira/urlBase is malformed " + e.getMessage());
    }

    HttpConnectionManagerParams params = new HttpConnectionManagerParams();
    params.setDefaultMaxConnectionsPerHost(20);
    params.setMaxTotalConnections(20);
    if (timeout != null) {
      params.setSoTimeout(timeout);
      params.setConnectionTimeout(timeout);
    }

    MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
    connectionManager.setParams(params);

    httpclient = new HttpClient(connectionManager);
    httpclient.getParams().setParameter("http.protocol.content-charset", "UTF-8");

    if (jiraUsername != null && !"".equals(jiraUsername.trim())) {
      httpclient.getParams().setAuthenticationPreemptive(true);
      String host = url.getHost();
      httpclient.getState().setCredentials(new AuthScope(host, -1, null),
          new UsernamePasswordCredentials(jiraUsername, jiraPassword));
      isAuthConfigured = true;
    }

    // TODO HTTP Proxy authentication
  }

  /**
   * Prepare JIRA API URL from JIRA base URL.
   * 
   * @param baseURL base JIRA URL, ie. http://issues.jboss.org
   * @return
   */
  protected static String prepareAPIURLFromBaseURL(String baseURL) {
    if (Utils.isEmpty(baseURL))
      return null;
    if (!baseURL.endsWith("/")) {
      baseURL = baseURL + "/";
    }
    return baseURL + "rest/api/2/";
  }

  /**
   * Get projectKeys of all projects in configured JIRA instance.
   * 
   * @return list of project keys
   * @throws Exception
   */
  @Override
  @SuppressWarnings("unchecked")
  public List<String> getAllJIRAProjects() throws Exception {

    byte[] responseData = performJIRAGetRESTCall("project", null);
    logger.debug("JIRA REST response data: {}", new String(responseData));

    StringBuilder sb = new StringBuilder();
    sb.append("{ \"projects\" : ").append(new String(responseData, "UTF-8")).append("}");
    responseData = sb.toString().getBytes("UTF-8");
    XContentParser parser = XContentHelper.createParser(responseData, 0, responseData.length);
    Map<String, Object> responseParsed = parser.mapAndClose();

    List<String> ret = new ArrayList<String>();
    for (Map<String, Object> mk : (List<Map<String, Object>>) responseParsed.get("projects")) {
      ret.add((String) mk.get("key"));
    }

    return ret;
  }

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
  @Override
  @SuppressWarnings("unchecked")
  public ChangedIssuesResults getJIRAChangedIssues(String projectKey, int startAt, Date updatedAfter, Date updatedBefore)
      throws Exception {
    byte[] responseData = performJIRAChangedIssuesREST(projectKey, startAt, updatedAfter, updatedBefore);
    logger.debug("JIRA REST response data: {}", new String(responseData));

    XContentParser parser = XContentHelper.createParser(responseData, 0, responseData.length);
    Map<String, Object> responseParsed = parser.mapAndClose();
    Integer startAtRet = Utils.nodeIntegerValue(responseParsed.get("startAt"));
    Integer maxResults = Utils.nodeIntegerValue(responseParsed.get("maxResults"));
    Integer total = Utils.nodeIntegerValue(responseParsed.get("total"));
    List<Map<String, Object>> issues = (List<Map<String, Object>>) responseParsed.get("issues");
    if (startAtRet == null || maxResults == null || total == null) {
      throw new IllegalArgumentException("Bad response structure from JIRA: startAt=" + startAtRet + " maxResults="
          + maxResults + " total=" + total);
    }
    return new ChangedIssuesResults(issues, startAtRet, maxResults, total);
  }

  /**
   * Performs JIRA REST call for {@link #getJIRAChangedIssues(String)}.
   * 
   * @param projectKey mandatory key of JIRA project to get issues for
   * @param startAt the index of the first issue to return (0-based)
   * @param updatedAfter optional parameter to return issues updated only after given date.
   * @param updatedBefore optional parameter to return issues updated only before given date.
   * @return data returned from JIRA REST call (JSON formatted)
   * @throws Exception
   * @see {@link #getJIRAChangedIssues(String)}
   */
  protected byte[] performJIRAChangedIssuesREST(String projectKey, int startAt, Date updatedAfter, Date updatedBefore)
      throws Exception {
    List<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new NameValuePair("jql", prepareJIRAChangedIssuesJQL(projectKey, updatedAfter, updatedBefore)));
    if (listJIRAIssuesMax > 0)
      params.add(new NameValuePair("maxResults", "" + listJIRAIssuesMax));
    params.add(new NameValuePair("startAt", startAt + ""));

    if (indexStructureBuilder != null) {
      String fields = indexStructureBuilder.getRequiredJIRAIssueFields();
      if (fields != null) {
        params.add(new NameValuePair("fields", indexStructureBuilder.getRequiredJIRAIssueFields()));
      }
    }

    return performJIRAGetRESTCall("search", params);
  }

  /**
   * Prepare JQL (JIRA Query Language) query text used to implement {@link #getJIRAChangedIssues(String, Date)}
   * operation.
   * 
   * @param projectKey mandatory key of JIRA project to get issues for
   * @param updatedAfter optional parameter to return issues updated only after given date.
   * @param updatedBefore optional parameter to return issues updated only before given date.
   * @return JQL string for given conditions
   * @throws IllegalArgumentException if some input parameter is illegal
   * @see #getJIRAChangedIssues(String, Date)
   * @see #performJIRAChangedIssuesREST(String, Date)
   */
  protected String prepareJIRAChangedIssuesJQL(String projectKey, Date updatedAfter, Date updatedBefore) {
    if (Utils.isEmpty(projectKey)) {
      throw new IllegalArgumentException("projectKey must be defined");
    }
    StringBuilder sb = new StringBuilder();
    sb.append("project='").append(projectKey).append("'");
    if (updatedAfter != null) {
      sb.append(" and updatedDate >= \"").append(formatJQLDate(updatedAfter)).append("\"");
    }
    if (updatedBefore != null) {
      sb.append(" and updatedDate <= \"").append(formatJQLDate(updatedBefore)).append("\"");
    }
    sb.append(" ORDER BY updated ASC");
    logger.debug("JIRA JQL string: {}", sb.toString());
    return sb.toString();
  }

  private static final String JQL_DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm";

  protected SimpleDateFormat jqlDateFormat = new SimpleDateFormat(JQL_DATE_FORMAT_PATTERN);

  @Override
  public void setJQLDateFormatTimezone(TimeZone zone) {
    jqlDateFormat.setTimeZone(zone);
  }

  /**
   * Format {@link Date} to {@link String} to be used in JQL (JIRA Query Language).
   * 
   * @param date to format
   * @return formatted date
   */
  protected synchronized String formatJQLDate(Date date) {
    String ret = null;
    if (date != null)
      ret = jqlDateFormat.format(date);
    return ret;
  }

  /**
   * Perform defined REST call to remote JIRA REST API.
   * 
   * @param restOperation name of REST operation to call on JIRA API (eg. 'search' or 'project' )
   * @param params GEP parameters used for call
   * @return response from server if successful
   * @throws Exception in case of unsuccessful call
   */
  protected byte[] performJIRAGetRESTCall(String restOperation, List<NameValuePair> params) throws Exception {

    String url = jiraRestAPIUrlBase + restOperation;
    logger.debug("Go to perform JIRA REST API call to the {} with parameters {}", url, params);

    HttpMethod method = new GetMethod(url);
    method.setDoAuthentication(isAuthConfigured);
    method.setFollowRedirects(true);
    method.addRequestHeader("Accept", "application/json");
    if (params != null) {
      method.setQueryString(params.toArray(new NameValuePair[] {}));
    }
    try {
      int statusCode = httpclient.executeMethod(method);

      if (statusCode != HttpStatus.SC_OK) {
        throw new Exception("Failed JIRA REST API call. HTTP error code: " + statusCode + " Response body: "
            + method.getResponseBodyAsString());
      }
      return method.getResponseBody();
    } finally {
      method.releaseConnection();
    }
  }

  @Override
  public void setIndexStructureBuilder(IJIRAIssueIndexStructureBuilder indexStructureBuilder) {
    this.indexStructureBuilder = indexStructureBuilder;
  }

  @Override
  public IJIRAIssueIndexStructureBuilder getIndexStructureBuilder() {
    return indexStructureBuilder;
  }

  @Override
  public void setListJIRAIssuesMax(int listJIRAIssuesMax) {
    this.listJIRAIssuesMax = listJIRAIssuesMax;
  }

  @Override
  public int getListJIRAIssuesMax() {
    return listJIRAIssuesMax;
  }

}
