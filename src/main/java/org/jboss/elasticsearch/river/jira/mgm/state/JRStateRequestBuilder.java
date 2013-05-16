/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.nodes.NodesOperationRequestBuilder;
import org.elasticsearch.client.ClusterAdminClient;
import org.elasticsearch.client.internal.InternalClusterAdminClient;

/**
 * Request builder to get state of some jira river.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateRequestBuilder extends
		NodesOperationRequestBuilder<JRStateRequest, JRStateResponse, JRStateRequestBuilder> {

	public JRStateRequestBuilder(ClusterAdminClient client) {
		super((InternalClusterAdminClient) client, new JRStateRequest());
	}

	/**
	 * Set name of river to get state for.
	 * 
	 * @param riverName name of river to force full index update for
	 * @return builder for chaining
	 */
	public JRStateRequestBuilder setRiverName(String riverName) {
		this.request.setRiverName(riverName);
		return this;
	}

	@Override
	protected void doExecute(ActionListener<JRStateResponse> listener) {
		if (request.getRiverName() == null)
			throw new IllegalArgumentException("riverName must be provided for request");
		((InternalClusterAdminClient) client).execute(JRStateAction.INSTANCE, request, listener);
	}

}
