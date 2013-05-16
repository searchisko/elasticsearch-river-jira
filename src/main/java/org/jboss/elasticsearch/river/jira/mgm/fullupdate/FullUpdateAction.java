/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import org.elasticsearch.action.admin.cluster.ClusterAction;
import org.elasticsearch.client.ClusterAdminClient;

/**
 * JIRA River Force full index update action implementation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateAction extends ClusterAction<FullUpdateRequest, FullUpdateResponse, FullUpdateRequestBuilder> {

	public static final FullUpdateAction INSTANCE = new FullUpdateAction();
	public static final String NAME = "jira_river/force_full_update";

	protected FullUpdateAction() {
		super(NAME);
	}

	@Override
	public FullUpdateRequestBuilder newRequestBuilder(ClusterAdminClient client) {
		return new FullUpdateRequestBuilder(client);
	}

	@Override
	public FullUpdateResponse newResponse() {
		return new FullUpdateResponse();
	}

}
