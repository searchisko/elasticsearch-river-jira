/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseActionListener;
import org.jboss.elasticsearch.river.jira.mgm.RestJRMgmBaseAction;

import static org.elasticsearch.rest.RestStatus.OK;

/**
 * REST action handler for force incremental index update operation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class RestIncrementalUpdateAction extends RestJRMgmBaseAction {

	@Inject
	protected RestIncrementalUpdateAction(Settings settings, Client client, RestController controller) {
		super(settings, client);
		String baseUrl = baseRestMgmUrl();
		controller.registerHandler(org.elasticsearch.rest.RestRequest.Method.POST, baseUrl + "incrementalupdate", this);
		controller.registerHandler(org.elasticsearch.rest.RestRequest.Method.POST, baseUrl
				+ "incrementalupdate/{projectKey}", this);
	}

	@Override
	public void handleRequest(final RestRequest restRequest, final RestChannel restChannel, Client client) {

		final String riverName = restRequest.param("riverName");
		final String projectKey = restRequest.param("projectKey");

		IncrementalUpdateRequest actionRequest = new IncrementalUpdateRequest(riverName, projectKey);

		client
				.admin()
				.cluster()
				.execute(
						IncrementalUpdateAction.INSTANCE,
						actionRequest,
						new JRMgmBaseActionListener<IncrementalUpdateRequest, IncrementalUpdateResponse, NodeIncrementalUpdateResponse>(
								actionRequest, restRequest, restChannel) {

							@Override
							protected void handleJiraRiverResponse(NodeIncrementalUpdateResponse nodeInfo) throws Exception {
								if (actionRequest.isProjectKeyRequest() && !nodeInfo.projectFound) {
									restChannel
											.sendResponse(new BytesRestResponse(RestStatus.NOT_FOUND, buildMessageDocument(restRequest,
													"Project '" + projectKey + "' is not indexed by JiraRiver with name: " + riverName)));
								} else {
									restChannel.sendResponse(new BytesRestResponse(OK, buildMessageDocument(restRequest,
											"Forced incremental reindex for JIRA projects: " + nodeInfo.reindexedProjectNames)));
								}
							}

						});
	}

}
