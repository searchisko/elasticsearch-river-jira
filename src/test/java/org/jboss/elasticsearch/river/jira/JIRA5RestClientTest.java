/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import junit.framework.Assert;

import org.apache.http.NameValuePair;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.river.RiverName;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link JIRA5RestClient}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JIRA5RestClientTest {

	/**
	 * URL used for JIRA5RestClient constructor in unit tests.
	 */
	protected static final String TEST_JIRA_URL = "https://issues.jboss.org";

	/**
	 * Date formatter used to prepare {@link Date} instances for tests
	 */
	protected SimpleDateFormat JQL_TEST_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	protected TimeZone JQL_TEST_TIMEZONE = TimeZone.getTimeZone("GMT");
	{
		JQL_TEST_DATE_FORMAT.setTimeZone(JQL_TEST_TIMEZONE);
	}

	/**
	 * Main method used to run integration tests with real JIRA call.
	 * 
	 * @param args not used
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String username = null;
		String pwd = null;
		IJIRAClient tested = new JIRA5RestClient(mockEsIntegrationComponent(), "https://issues.jboss.org", username, pwd,
				50000, null);

		// List<String> projects = tested.getAllJIRAProjects();
		// System.out.println(projects);

		ChangedIssuesResults ret = tested.getJIRAChangedIssues("ORG", 0,
				DateTimeUtils.parseISODateTime("2014-09-01T01:00:00Z"), null);
		System.out.println("total: " + ret.getTotal());
		// System.out.println(ret);
	}

	@Test
	public void constructor() {
		try {
			new JIRA5RestClient(mockEsIntegrationComponent(), null, null, null, 5000, null);
			Assert.fail("SettingsException not thrown");
		} catch (SettingsException e) {
			// OK
		}
		try {
			new JIRA5RestClient(mockEsIntegrationComponent(), "  ", null, null, 5000, null);
			Assert.fail("SettingsException not thrown");
		} catch (SettingsException e) {
			// OK
		}
		try {
			new JIRA5RestClient(mockEsIntegrationComponent(), "nonsenseUrl", null, null, 5000, null);
			Assert.fail("SettingsException not thrown");
		} catch (SettingsException e) {
			// OK
		}

		IESIntegration esMock = mockEsIntegrationComponent();
		JIRA5RestClient tested = new JIRA5RestClient(esMock, "http://issues.jboss.org", null, null, 5000, null);
		Mockito.verify(esMock).createLogger(JIRA5RestClient.class);
		Assert.assertEquals(JIRA5RestClient.prepareAPIURLFromBaseURL("http://issues.jboss.org", null),
                tested.jiraRestAPIUrlBase);
		tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, null, null, 5000, "latest");
		Assert.assertEquals(JIRA5RestClient.prepareAPIURLFromBaseURL(TEST_JIRA_URL, "latest"), tested.jiraRestAPIUrlBase);
		Assert.assertFalse(tested.isAuthConfigured);

		tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, "", "pwd", 5000, null);
		Assert.assertFalse(tested.isAuthConfigured);

		tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, "uname", "pwd", 5000, null);
		Assert.assertTrue(tested.isAuthConfigured);
	}

	@Test
	public void getAllJIRAProjects() throws Exception {

		IJIRAClient tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, null, null, 5000, null) {
			@Override
			protected byte[] performJIRAGetRESTCall(String restOperation, List<NameValuePair> params) throws Exception {
				Assert.assertEquals("project", restOperation);
				Assert.assertNull(params);
				return ("[{\"key\": \"ORG\", \"name\": \"ORG project\"},{\"key\": \"PPP\"}]").getBytes("UTF-8");
			};

		};

		List<String> ret = tested.getAllJIRAProjects();
		Assert.assertNotNull(ret);
		Assert.assertEquals(2, ret.size());
		Assert.assertTrue(ret.contains("ORG"));
		Assert.assertTrue(ret.contains("PPP"));
	}

	@Test
	public void getJIRAChangedIssues() throws Exception {
		final Date ua = new Date();
		final Date ub = new Date();

		IJIRAClient tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, null, null, 5000, null) {
			@Override
			protected byte[] performJIRAChangedIssuesREST(String projectKey, int startAt, Date updatedAfter,
					Date updatedBefore) throws Exception {
				Assert.assertEquals("ORG", projectKey);
				Assert.assertEquals(ua, updatedAfter);
				Assert.assertEquals(ub, updatedBefore);
				Assert.assertEquals(10, startAt);
				return "{\"startAt\": 5, \"maxResults\" : 10, \"total\" : 50, \"issues\" : [{\"key\" : \"ORG-45\"}]}"
						.getBytes("UTF-8");
			};
		};

		ChangedIssuesResults ret = tested.getJIRAChangedIssues("ORG", 10, ua, ub);
		Assert.assertEquals(5, ret.getStartAt());
		Assert.assertEquals(10, ret.getMaxResults());
		Assert.assertEquals(50, ret.getTotal());
		Assert.assertNotNull(ret.getIssues());
		Assert.assertEquals(1, ret.getIssuesCount());
	}

	@Test
	public void performJIRAChangedIssuesREST() throws Exception {
		final Date ua = new Date();
		final Date ub = new Date();

		JIRA5RestClient tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, null, null, 5000, null) {
			@Override
			protected byte[] performJIRAGetRESTCall(String restOperation, List<NameValuePair> params) throws Exception {
				Assert.assertEquals("search", restOperation);
				Assert.assertNotNull(params);
				String mr = "-1";
				String fields = "";
				String expand = "";
				String startAt = "";
				for (NameValuePair param : params) {
					if (param.getName().equals("maxResults")) {
						mr = param.getValue();
					} else if (param.getName().equals("jql")) {
						Assert.assertEquals("JQL string", param.getValue());
					} else if (param.getName().equals("fields")) {
						fields = param.getValue();
					} else if (param.getName().equals("expand")) {
						expand = param.getValue();
					} else if (param.getName().equals("startAt")) {
						startAt = param.getValue();
					}
				}

				if ("-1".equals(mr)) {
					Assert.assertEquals(3, params.size());
				} else if ("10".equals(mr)) {
					Assert.assertEquals(4, params.size());
				} else if ("20".equals(mr)) {
					Assert.assertEquals(3, params.size());
				}

				return ("{\"maxResults\": " + mr + ", \"startAt\": " + startAt + ", \"fields\" : \"" + fields + "\""
						+ ", \"expand\" : \"" + expand + "\" }").getBytes("UTF-8");
			};

			@Override
			protected String prepareJIRAChangedIssuesJQL(String projectKey, Date updatedAfter, Date updatedBefore) {
				Assert.assertEquals("ORG", projectKey);
				Assert.assertEquals(ua, updatedAfter);
				Assert.assertEquals(ub, updatedBefore);
				return "JQL string";
			}
		};
		IJIRAIssueIndexStructureBuilder jiraIssueIndexStructureBuilderMock = mock(IJIRAIssueIndexStructureBuilder.class);
		tested.setIndexStructureBuilder(jiraIssueIndexStructureBuilderMock);
		when(jiraIssueIndexStructureBuilderMock.getRequiredJIRACallIssueFields()).thenReturn(
				"key,status,issuetype,created,updated,reporter,assignee,summary,description");

		// case - no maxResults parameter defined
		byte[] ret = tested.performJIRAChangedIssuesREST("ORG", 10, ua, ub);
		Assert
				.assertEquals(
						"{\"maxResults\": -1, \"startAt\": 10, \"fields\" : \"key,status,issuetype,created,updated,reporter,assignee,summary,description\", \"expand\" : \"\" }",
						new String(ret, "UTF-8"));

		// case - maxResults parameter defined
		tested.listJIRAIssuesMax = 10;
		ret = tested.performJIRAChangedIssuesREST("ORG", 20, ua, ub);
		Assert
				.assertEquals(
						"{\"maxResults\": 10, \"startAt\": 20, \"fields\" : \"key,status,issuetype,created,updated,reporter,assignee,summary,description\", \"expand\" : \"\" }",
						new String(ret, "UTF-8"));

		// case - no fields defined
		reset(jiraIssueIndexStructureBuilderMock);
		tested.listJIRAIssuesMax = 20;
		ret = tested.performJIRAChangedIssuesREST("ORG", 30, ua, ub);
		Assert.assertEquals("{\"maxResults\": 20, \"startAt\": 30, \"fields\" : \"\", \"expand\" : \"\" }", new String(ret,
				"UTF-8"));

		// case - expand defines
		reset(jiraIssueIndexStructureBuilderMock);
		tested.listJIRAIssuesMax = 10;
		when(jiraIssueIndexStructureBuilderMock.getRequiredJIRACallIssueExpands()).thenReturn("changelog");
		ret = tested.performJIRAChangedIssuesREST("ORG", 30, ua, ub);
		Assert.assertEquals("{\"maxResults\": 10, \"startAt\": 30, \"fields\" : \"\", \"expand\" : \"changelog\" }",
				new String(ret, "UTF-8"));

	}

	@Test
	public void prepareAPIURLFromBaseURL() {
		Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL(null, null));
		Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL("", null));
		Assert.assertNull(JIRA5RestClient.prepareAPIURLFromBaseURL("  ", null));
		Assert.assertEquals("http://issues.jboss.org/rest/api/2/",
				JIRA5RestClient.prepareAPIURLFromBaseURL("http://issues.jboss.org", null));
		Assert.assertEquals("https://issues.jboss.org/rest/api/2/",
				JIRA5RestClient.prepareAPIURLFromBaseURL("https://issues.jboss.org/", ""));
		Assert.assertEquals("https://issues.jboss.org/rest/api/2/",
				JIRA5RestClient.prepareAPIURLFromBaseURL("https://issues.jboss.org/", "  "));
		Assert.assertEquals("https://issues.jboss.org/rest/api/latest/",
				JIRA5RestClient.prepareAPIURLFromBaseURL("https://issues.jboss.org/", "latest"));
	}

	@Test
	public void formatJQLDate() throws Exception {
		JIRA5RestClient tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, null, null, 5000, null);
		Assert.assertNull(tested.formatJQLDate(null));

		Date date1 = JQL_TEST_DATE_FORMAT.parse("2012-08-10 10:52");
		Date date2 = JQL_TEST_DATE_FORMAT.parse("2012-08-10 22:52");

		tested.setJQLDateFormatTimezone(TimeZone.getTimeZone("GMT"));
		Assert.assertEquals("2012-08-10 10:52", tested.formatJQLDate(date1));
		Assert.assertEquals("2012-08-10 22:52", tested.formatJQLDate(date2));
		tested.setJQLDateFormatTimezone(TimeZone.getTimeZone("GMT+1:00"));
		Assert.assertEquals("2012-08-10 11:52", tested.formatJQLDate(date1));
		Assert.assertEquals("2012-08-10 23:52", tested.formatJQLDate(date2));

	}

	@Test
	public void prepareJIRAChangedIssuesJQL() throws Exception {
		JIRA5RestClient tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, null, null, 5000, null);
		tested.setJQLDateFormatTimezone(JQL_TEST_TIMEZONE);
        tested.setJqlTemplate(JiraRiver.DEFAULT_JQL_TEMPLATE);
		try {
			tested.prepareJIRAChangedIssuesJQL(null, null, null);
			Assert.fail("IllegalArgumentException not thrown if project key is missing");
		} catch (IllegalArgumentException e) {
			// OK
		}
		try {
			tested.prepareJIRAChangedIssuesJQL("  ", null, null);
			Assert.fail("IllegalArgumentException not thrown if project key is missing");
		} catch (IllegalArgumentException e) {
			// OK
		}
		Assert.assertEquals("project='ORG' ORDER BY updated ASC", tested.prepareJIRAChangedIssuesJQL("ORG", null, null));
		Assert.assertEquals("project='ORG' and updatedDate >= \"2012-08-10 22:52\" ORDER BY updated ASC",
                tested.prepareJIRAChangedIssuesJQL("ORG", JQL_TEST_DATE_FORMAT.parse("2012-08-10 22:52"), null));
		Assert.assertEquals("project='ORG' and updatedDate <= \"2012-08-10 22:55\" ORDER BY updated ASC",
				tested.prepareJIRAChangedIssuesJQL("ORG", null, JQL_TEST_DATE_FORMAT.parse("2012-08-10 22:55")));
		Assert
				.assertEquals(
                        "project='ORG' and updatedDate >= \"2012-08-10 22:52\" and updatedDate <= \"2012-08-10 22:55\" ORDER BY updated ASC",
                        tested.prepareJIRAChangedIssuesJQL("ORG", JQL_TEST_DATE_FORMAT.parse("2012-08-10 22:52"),
                                JQL_TEST_DATE_FORMAT.parse("2012-08-10 22:55")));

	}

	@Test
	public void prepareJIRAChangedIssuesJQLWithTemplate() throws Exception {
		JIRA5RestClient tested = new JIRA5RestClient(mockEsIntegrationComponent(), TEST_JIRA_URL, null, null, 5000, null);
		tested.setJQLDateFormatTimezone(JQL_TEST_TIMEZONE);
        tested.setJqlTemplate("assignee=currentUser() AND project='%s' AND issueType='Bug'%s AND priority='P1 - Critical'%s ORDER BY updated ASC");
		try {
			tested.prepareJIRAChangedIssuesJQL(null, null, null);
			Assert.fail("IllegalArgumentException not thrown if project key is missing");
		} catch (IllegalArgumentException e) {
			// OK
		}
		try {
			tested.prepareJIRAChangedIssuesJQL("  ", null, null);
			Assert.fail("IllegalArgumentException not thrown if project key is missing");
		} catch (IllegalArgumentException e) {
			// OK
		}
        Date updatedAfter = JQL_TEST_DATE_FORMAT.parse("2012-08-10 22:52");
        Date updatedBefore = JQL_TEST_DATE_FORMAT.parse("2012-08-10 22:55");
        Assert.assertEquals(
                "assignee=currentUser() AND project='ORG' AND issueType='Bug' AND priority='P1 - Critical' ORDER BY updated ASC",
                tested.prepareJIRAChangedIssuesJQL("ORG", null, null));
        Assert.assertEquals(
                "assignee=currentUser() AND project='ORG' AND issueType='Bug' and updatedDate >= \"2012-08-10 22:52\" AND priority='P1 - Critical' ORDER BY updated ASC",
				tested.prepareJIRAChangedIssuesJQL("ORG", updatedAfter, null));
        Assert.assertEquals(
                "assignee=currentUser() AND project='ORG' AND issueType='Bug' AND priority='P1 - Critical' and updatedDate <= \"2012-08-10 22:55\" ORDER BY updated ASC",
				tested.prepareJIRAChangedIssuesJQL("ORG", null, updatedBefore));
		Assert.assertEquals(
                "assignee=currentUser() AND project='ORG' AND issueType='Bug' and updatedDate >= \"2012-08-10 22:52\" AND priority='P1 - Critical' and updatedDate <= \"2012-08-10 22:55\" ORDER BY updated ASC",
                tested.prepareJIRAChangedIssuesJQL("ORG", updatedAfter, updatedBefore));

    }

    protected static IESIntegration mockEsIntegrationComponent() {
		IESIntegration esIntegrationMock = mock(IESIntegration.class);
		Mockito.when(esIntegrationMock.createLogger(Mockito.any(Class.class))).thenReturn(
				ESLoggerFactory.getLogger(JIRAProjectIndexerCoordinator.class.getName()));
		RiverName riverName = new RiverName("jira", "my_river");
		Mockito.when(esIntegrationMock.riverName()).thenReturn(riverName);
		return esIntegrationMock;
	}
}
