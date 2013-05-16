/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.riverslist;

import org.elasticsearch.action.admin.cluster.ClusterAction;
import org.elasticsearch.client.ClusterAdminClient;

/**
 * All JIRA River names in ES cluster listing action implementation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class ListRiversAction extends ClusterAction<ListRiversRequest, ListRiversResponse, ListRiversRequestBuilder> {

	public static final ListRiversAction INSTANCE = new ListRiversAction();
	public static final String NAME = "jira_river/list_river_names";

	protected ListRiversAction() {
		super(NAME);
	}

	@Override
	public ListRiversRequestBuilder newRequestBuilder(ClusterAdminClient client) {
		return new ListRiversRequestBuilder(client);
	}

	@Override
	public ListRiversResponse newResponse() {
		return new ListRiversResponse();
	}

}
