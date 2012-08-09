/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;

/**
 * Class used to call necessary JIRA functions over REST API.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRA5RestClient {

  private HttpClient httpclient;

  protected String jiraRestAPIUrlBase;

  protected boolean isAuthConfigured = true;

  // TODO configure this
  protected int listJIRAIssuesMax = -1;

  /**
   * Constructor to create and configure remote JIRA REST API client.
   * 
   * @param jiraRestAPIUrlBase JIRA API URL used to call JIRA (see {@link #prepareAPIURLFromBaseURL(String)})
   */
  public JIRA5RestClient(String jiraUrlBase) {

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
    params.setSoTimeout(5000);
    params.setConnectionTimeout(5000);

    MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
    connectionManager.setParams(params);

    httpclient = new HttpClient(connectionManager);
    httpclient.getParams().setParameter("http.protocol.content-charset", "UTF-8");

    // TODO prepare basic http authentication if configured
    if (false) {
      httpclient.getState().setCredentials(new AuthScope(url.getHost(), -1, null),
          new UsernamePasswordCredentials("username", "password"));
      isAuthConfigured = true;
    }
    // TODO Proxy authentication
  }

  /**
   * Prepare JIRA API URL from JIRA base URL.
   * 
   * @param baseURL base JIRA URL, ie. http://issues.jboss.org
   * @return
   */
  protected static String prepareAPIURLFromBaseURL(String baseURL) {
    if (baseURL == null || "".equals(Strings.trimWhitespace(baseURL)))
      return null;
    if (!baseURL.endsWith("/")) {
      baseURL = baseURL + "/";
    }
    return baseURL + "rest/api/2/";
  }

  /**
   * Get list of issues from remote JIRA instance and parse them into <code>Map of Maps</code> structure. Issues are
   * ascending ordered by date of last change performed on issue. List is limited to only some number of issues (given
   * by both JIRA and this client configuration).
   * 
   * @param projectKey key of JIRA project to get issues for
   * @return List of issues informations parsed from JIRA reply into <code>Map of Maps</code> structure.
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> getJIRAChangedIssues(String projectKey) throws Exception {
    byte[] responseData = performJIRAChangedIssuesREST(projectKey);
    XContentParser parser = XContentHelper.createParser(responseData, 0, responseData.length);
    return (List<Map<String, Object>>) parser.mapAndClose().get("issues");
  }

  /**
   * Performs JIRA REST call for {@link #getJIRAChangedIssues(String)}.
   * 
   * @param projectKey
   * @return
   * @throws Exception
   * @see {@link #getJIRAChangedIssues(String)}
   */
  protected byte[] performJIRAChangedIssuesREST(String projectKey) throws Exception {
    List<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new NameValuePair("jql", "project='" + projectKey + "' ORDER BY updated ASC"));
    if (listJIRAIssuesMax > 0)
      params.add(new NameValuePair("maxResults", "" + listJIRAIssuesMax));
    // TODO add additional indexed issue fields from River configuration
    params.add(new NameValuePair("fields", "key,created,updated,reporter,assignee,summary,description"));

    return performJIRAGetRESTCall("search", params);
  }

  /**
   * Perform defined REST call to remote JIRA REST API.
   * 
   * @param restOperation name of rest operation to call on API
   * @param params GEP parameters used for call
   * @return response from server if successful
   * @throws Exception in case of unsuccessful call
   */
  protected byte[] performJIRAGetRESTCall(String restOperation, List<NameValuePair> params) throws Exception {
    HttpMethod method = new GetMethod(jiraRestAPIUrlBase + restOperation);
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

}
