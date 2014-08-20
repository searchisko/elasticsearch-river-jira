/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.jboss.elasticsearch.river.jira.IJiraRiverMgm;
import org.jboss.elasticsearch.river.jira.mgm.TransportJRMgmBaseAction;

/**
 * JiraRiver lifecycle method transport action.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class TransportJRLifecycleAction extends
		TransportJRMgmBaseAction<JRLifecycleRequest, JRLifecycleResponse, NodeJRLifecycleRequest, NodeJRLifecycleResponse> {

	@Inject
	public TransportJRLifecycleAction(Settings settings, ClusterName clusterName, ThreadPool threadPool,
			ClusterService clusterService, TransportService transportService) {
		super(settings, JRLifecycleAction.NAME, clusterName, threadPool, clusterService, transportService);
	}

	@Override
	protected NodeJRLifecycleResponse performOperationOnJiraRiver(IJiraRiverMgm river, JRLifecycleRequest req,
			DiscoveryNode node) throws Exception {
		JRLifecycleCommand command = req.getCommand();
		logger.debug("Go to perform lifecycle command {} on river '{}'", command, req.getRiverName());
		switch (command) {
		case STOP:
			river.stop(true);
			break;
		case RESTART:
			river.restart();
			break;
		default:
			throw new UnsupportedOperationException("Command " + command + " is not supported");
		}

		return new NodeJRLifecycleResponse(node, true);
	}

	@Override
	protected JRLifecycleRequest newRequest() {
		return new JRLifecycleRequest();
	}

	@Override
	protected NodeJRLifecycleRequest newNodeRequest() {
		return new NodeJRLifecycleRequest();
	}

	@Override
	protected NodeJRLifecycleRequest newNodeRequest(String nodeId, JRLifecycleRequest request) {
		return new NodeJRLifecycleRequest(nodeId, request);
	}

	@Override
	protected NodeJRLifecycleResponse newNodeResponse() {
		return new NodeJRLifecycleResponse(clusterService.localNode());
	}

	@Override
	protected NodeJRLifecycleResponse[] newNodeResponseArray(int len) {
		return new NodeJRLifecycleResponse[len];
	}

	@Override
	protected JRLifecycleResponse newResponse(ClusterName clusterName, NodeJRLifecycleResponse[] array) {
		return new JRLifecycleResponse(clusterName, array);
	}

}
