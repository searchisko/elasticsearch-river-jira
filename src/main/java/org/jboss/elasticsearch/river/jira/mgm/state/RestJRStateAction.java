/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseActionListener;
import org.jboss.elasticsearch.river.jira.mgm.RestJRMgmBaseAction;

/**
 * REST action handler for Jira river get state operation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class RestJRStateAction extends RestJRMgmBaseAction {

	@Inject
	protected RestJRStateAction(Settings settings, Client client, RestController controller) {
		super(settings, client);
		String baseUrl = baseRestMgmUrl();
		controller.registerHandler(org.elasticsearch.rest.RestRequest.Method.GET, baseUrl + "state", this);
	}

	@Override
	public void handleRequest(final RestRequest restRequest, final RestChannel restChannel, Client client) {

		JRStateRequest actionRequest = new JRStateRequest(restRequest.param("riverName"));

		client
				.admin()
				.cluster()
				.execute(
						JRStateAction.INSTANCE,
						actionRequest,
						new JRMgmBaseActionListener<JRStateRequest, JRStateResponse, NodeJRStateResponse>(actionRequest,
								restRequest, restChannel) {

							@Override
							protected void handleJiraRiverResponse(NodeJRStateResponse nodeInfo) throws Exception {
								restChannel.sendResponse(new BytesRestResponse(RestStatus.OK, XContentType.JSON.restContentType(),
										nodeInfo.stateInformation.getBytes()));
							}

						});
	}
}
