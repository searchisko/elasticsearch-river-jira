/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import org.elasticsearch.action.admin.cluster.ClusterAction;
import org.elasticsearch.client.ClusterAdminClient;

/**
 * JIRA River lifecycle action implementation.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleAction extends
		ClusterAction<JRLifecycleRequest, JRLifecycleResponse, JRLifecycleRequestBuilder> {

	public static final JRLifecycleAction INSTANCE = new JRLifecycleAction();
	public static final String NAME = "jira_river/lifecycle";

	protected JRLifecycleAction() {
		super(NAME);
	}

	@Override
	public JRLifecycleRequestBuilder newRequestBuilder(ClusterAdminClient client) {
		return new JRLifecycleRequestBuilder(client);
	}

	@Override
	public JRLifecycleResponse newResponse() {
		return new JRLifecycleResponse();
	}

}
