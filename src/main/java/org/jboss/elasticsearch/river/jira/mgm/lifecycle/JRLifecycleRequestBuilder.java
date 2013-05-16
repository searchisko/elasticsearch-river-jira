/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.nodes.NodesOperationRequestBuilder;
import org.elasticsearch.client.ClusterAdminClient;
import org.elasticsearch.client.internal.InternalClusterAdminClient;

/**
 * Request builder to perform lifecycle method of some jira river.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleRequestBuilder extends
		NodesOperationRequestBuilder<JRLifecycleRequest, JRLifecycleResponse, JRLifecycleRequestBuilder> {

	public JRLifecycleRequestBuilder(ClusterAdminClient client) {
		super((InternalClusterAdminClient) client, new JRLifecycleRequest());
	}

	/**
	 * Set name of river to get state for.
	 * 
	 * @param riverName name of river to force full index update for
	 * @return builder for chaining
	 */
	public JRLifecycleRequestBuilder setRiverName(String riverName) {
		this.request.setRiverName(riverName);
		return this;
	}

	/**
	 * Set command to request.
	 * 
	 * @param command to be set
	 * @return builder for chaining
	 */
	public JRLifecycleRequestBuilder setCommand(JRLifecycleCommand command) {
		this.request.setCommand(command);
		return this;
	}

	@Override
	protected void doExecute(ActionListener<JRLifecycleResponse> listener) {
		if (request.getRiverName() == null)
			throw new IllegalArgumentException("riverName must be provided for request");
		if (request.getCommand() == null)
			throw new IllegalArgumentException("command must be provided for request");
		((InternalClusterAdminClient) client).execute(JRLifecycleAction.INSTANCE, request, listener);
	}

}
