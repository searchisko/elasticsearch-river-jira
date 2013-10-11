/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm;

import java.io.IOException;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;
import org.elasticsearch.rest.action.support.RestXContentBuilder;

/**
 * Base action listener used for Jira River management action calls. Handles response in case if jira river is not
 * found, or if there is some error.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class JRMgmBaseActionListener<Request extends JRMgmBaseRequest<?>, Response extends JRMgmBaseResponse<NodeResponse>, NodeResponse extends NodeJRMgmBaseResponse>
		implements ActionListener<Response> {

	protected final ESLogger logger;

	protected final RestRequest restRequest;
	protected final RestChannel restChannel;
	protected final Request actionRequest;

	/**
	 * Create new action listener.
	 * 
	 * @param actionRequest this listener process result for
	 * @param restRequest we handle
	 * @param restChannel to write rest response into
	 */
	public JRMgmBaseActionListener(Request actionRequest, RestRequest restRequest, RestChannel restChannel) {
		super();
		this.restRequest = restRequest;
		this.restChannel = restChannel;
		this.actionRequest = actionRequest;
		logger = Loggers.getLogger(getClass());
	}

	@Override
	public void onResponse(Response response) {
		try {
			NodeResponse nodeInfo = response.getSuccessNodeResponse();
			if (nodeInfo == null) {
				restChannel.sendResponse(new XContentRestResponse(restRequest, RestStatus.NOT_FOUND, buildMessageDocument(
						restRequest, "No JiraRiver found for name: " + actionRequest.getRiverName())));
			} else {
				handleJiraRiverResponse(nodeInfo);
			}
		} catch (Exception e) {
			onFailure(e);
		}
	}

	/**
	 * Implement this in subclasses to handle response from jira river and return REST response based on it.
	 * 
	 * @param nodeInfo operation response from node with jira river, never null
	 * @throws Exception if something is wrong
	 */
	protected abstract void handleJiraRiverResponse(NodeResponse nodeInfo) throws Exception;

	@Override
	public void onFailure(Throwable e) {
		try {
			restChannel.sendResponse(new XContentThrowableRestResponse(restRequest, e));
		} catch (IOException e1) {
			logger.error("Failed to send failure response", e1);
		}
	}

	/**
	 * Build response document with only one field called <code>message</code>. You can use this in
	 * {@link #handleJiraRiverResponse(NodeJRMgmBaseResponse)} implementation if you only need to return simple message.
	 * 
	 * @param restRequest to build response document for
	 * @param message to be placed in document
	 * @return document with message
	 * @throws IOException
	 */
	public static XContentBuilder buildMessageDocument(RestRequest restRequest, String message) throws IOException {
		XContentBuilder builder = RestXContentBuilder.restContentBuilder(restRequest);
		builder.startObject();
		builder.field("message", message);
		builder.endObject();
		return builder;
	}

}
