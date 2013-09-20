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

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;

/**
 * Class used to call JIRA 5 series functions to obtain JIRA content over REST API version 2. One instance of this class
 * is used to access one instance of JIRA.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRA5RestClient implements IJIRAClient {

	private static final ESLogger logger = Loggers.getLogger(JIRA5RestClient.class);

	private DefaultHttpClient httpclient;

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

		PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
		connectionManager.setDefaultMaxPerRoute(20);
		connectionManager.setMaxTotal(20);

		httpclient = new DefaultHttpClient(connectionManager);
		HttpParams params = httpclient.getParams();
		params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, "UTF-8");
		if (timeout != null) {
			params.setParameter(CoreConnectionPNames.SO_TIMEOUT, timeout);
			params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, timeout);
		}

		if (jiraUsername != null && !"".equals(jiraUsername.trim())) {
			String host = url.getHost();
			httpclient.getCredentialsProvider().setCredentials(new AuthScope(host, AuthScope.ANY_PORT),
					new UsernamePasswordCredentials(jiraUsername, jiraPassword));
			isAuthConfigured = true;
		}
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
		XContentParser parser = null;
		try {
			byte[] responseData = performJIRAGetRESTCall("project", null);
			logger.debug("JIRA REST response data: {}", new String(responseData));

			StringBuilder sb = new StringBuilder();
			sb.append("{ \"projects\" : ").append(new String(responseData, "UTF-8")).append("}");
			responseData = sb.toString().getBytes("UTF-8");
			parser = XContentFactory.xContent(XContentType.JSON).createParser(responseData);
			Map<String, Object> responseParsed = parser.mapAndClose();

			List<String> ret = new ArrayList<String>();
			for (Map<String, Object> mk : (List<Map<String, Object>>) responseParsed.get("projects")) {
				ret.add((String) mk.get("key"));
			}

			return ret;
		} finally {
			if (parser != null)
				parser.close();
		}
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

		XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(responseData);
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
		params.add(new BasicNameValuePair("jql", prepareJIRAChangedIssuesJQL(projectKey, updatedAfter, updatedBefore)));
		if (listJIRAIssuesMax > 0)
			params.add(new BasicNameValuePair("maxResults", "" + listJIRAIssuesMax));
		params.add(new BasicNameValuePair("startAt", startAt + ""));

		if (indexStructureBuilder != null) {
			String fields = indexStructureBuilder.getRequiredJIRACallIssueFields();
			if (fields != null) {
				params.add(new BasicNameValuePair("fields", fields));
			}
			String expands = indexStructureBuilder.getRequiredJIRACallIssueExpands();
			if (expands != null && expands.length() > 0) {
				params.add(new BasicNameValuePair("expand", expands));
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
	 * @param params GET parameters used for call
	 * @return response from server if successful
	 * @throws Exception in case of unsuccessful call
	 */
	protected byte[] performJIRAGetRESTCall(String restOperation, List<NameValuePair> params) throws Exception {

		String url = jiraRestAPIUrlBase + restOperation;
		logger.debug("Go to perform JIRA REST API call to the {} with parameters {}", url, params);

		URIBuilder builder = new URIBuilder(url);
		if (params != null) {
			for (NameValuePair param : params) {
				builder.addParameter(param.getName(), param.getValue());
			}
		}
		HttpGet method = new HttpGet(builder.build());
		method.addHeader("Accept", "application/json");
		try {

			// Preemptive authentication enabled - see
			// http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html#d5e1032
			HttpHost targetHost = new HttpHost(builder.getHost(), builder.getPort(), builder.getScheme());
			AuthCache authCache = new BasicAuthCache();
			BasicScheme basicAuth = new BasicScheme();
			authCache.put(targetHost, basicAuth);
			BasicHttpContext localcontext = new BasicHttpContext();
			localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);

			HttpResponse response = httpclient.execute(method, localcontext);
			int statusCode = response.getStatusLine().getStatusCode();
			byte[] responseContent = null;
			if (response.getEntity() != null) {
				responseContent = EntityUtils.toByteArray(response.getEntity());
			}
			if (statusCode != HttpStatus.SC_OK) {
				throw new Exception("Failed JIRA REST API call. HTTP error code: " + statusCode + " Response body: "
						+ responseContent);
			}
			return responseContent;
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
