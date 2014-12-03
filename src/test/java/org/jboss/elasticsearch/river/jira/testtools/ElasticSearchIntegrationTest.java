/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.testtools;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.jboss.elasticsearch.river.jira.DateTimeUtils;
import org.jboss.elasticsearch.river.jira.IESIntegration;
import org.jboss.elasticsearch.river.jira.JIRA5RestIssueIndexStructureBuilder;
import org.jboss.elasticsearch.river.jira.JIRAProjectIndexerCoordinator;
import org.jboss.elasticsearch.river.jira.JiraRiver;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;

/**
 * Class for ElasticSearch integration tests against some running ES cluster. This is not Unit test but helper for tests
 * during development!
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class ElasticSearchIntegrationTest {

	public static void main(String[] args) throws MalformedURLException {

		TransportClient client = new TransportClient();

		try {
			client.addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
			Map<String, Object> settings = new HashMap<String, Object>();
			Map<String, Object> jiraSettings = new HashMap<String, Object>();
			settings.put("jira", jiraSettings);
			jiraSettings.put("urlBase", "http://issues-stg.jboss.org");
			Settings gs = mock(Settings.class);
			RiverSettings rs = new RiverSettings(gs, settings);

			JiraRiver jr = new JiraRiver(new RiverName("rt", "my_jira_river"), rs, client);
			JIRA5RestIssueIndexStructureBuilder structureBuilder = new JIRA5RestIssueIndexStructureBuilder(
					mockEsIntegrationComponent(), "my_jira_index", "jira_issue", "http://issues-stg.jboss.org", null);

			String project = "ORG";
			// Date date = new Date();
			Date date = DateTimeUtils.parseISODateTime("2012-08-30T16:25:51");

			SearchRequestBuilder srb = jr.prepareESScrollSearchRequestBuilder(structureBuilder
					.getIssuesSearchIndexName(project));

			structureBuilder.buildSearchForIndexedDocumentsNotUpdatedAfter(srb, project, date);

			System.out.println(srb);

			SearchResponse response = jr.executeESSearchRequest(srb);
			response = jr.executeESScrollSearchNextRequest(response);

			System.out.println(response);

		} finally {
			client.close();
		}
	}

	protected static IESIntegration mockEsIntegrationComponent() {
		IESIntegration esIntegrationMock = mock(IESIntegration.class);
		Mockito.when(esIntegrationMock.createLogger(Mockito.any(Class.class))).thenReturn(
				ESLoggerFactory.getLogger(JIRAProjectIndexerCoordinator.class.getName()));
		RiverName riverName = new RiverName("jira", "river_name");
		Mockito.when(esIntegrationMock.riverName()).thenReturn(riverName);
		return esIntegrationMock;
	}

}
