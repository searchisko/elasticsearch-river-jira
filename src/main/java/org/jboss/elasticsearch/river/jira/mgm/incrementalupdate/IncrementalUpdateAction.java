/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

import org.elasticsearch.action.admin.cluster.ClusterAction;
import org.elasticsearch.client.ClusterAdminClient;

/**
 * JIRA River Force incremental index update action implementation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IncrementalUpdateAction extends
		ClusterAction<IncrementalUpdateRequest, IncrementalUpdateResponse, IncrementalUpdateRequestBuilder> {

	public static final IncrementalUpdateAction INSTANCE = new IncrementalUpdateAction();
	public static final String NAME = "jira_river/force_incremental_update";

	protected IncrementalUpdateAction() {
		super(NAME);
	}

	@Override
	public IncrementalUpdateRequestBuilder newRequestBuilder(ClusterAdminClient client) {
		return new IncrementalUpdateRequestBuilder(client);
	}

	@Override
	public IncrementalUpdateResponse newResponse() {
		return new IncrementalUpdateResponse();
	}

}
