/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import org.elasticsearch.action.support.ActionFilters;
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
 * Full reindex transport action.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class TransportFullUpdateAction extends
		TransportJRMgmBaseAction<FullUpdateRequest, FullUpdateResponse, NodeFullUpdateRequest, NodeFullUpdateResponse> {

	@Inject
	public TransportFullUpdateAction(Settings settings, ClusterName clusterName, ThreadPool threadPool,
			ClusterService clusterService, TransportService transportService, ActionFilters actionFilters) {
		super(settings, FullUpdateAction.NAME, clusterName, threadPool, clusterService, transportService, actionFilters);
	}

	@Override
	protected NodeFullUpdateResponse performOperationOnJiraRiver(IJiraRiverMgm river, FullUpdateRequest req,
			DiscoveryNode node) throws Exception {
		logger.debug("Go to schedule full reindex for river '{}' and project {}", req.getRiverName(), req.getProjectKey());
		String ret = river.forceFullReindex(req.getProjectKey());
		return new NodeFullUpdateResponse(node, true, ret != null, ret);
	}

	@Override
	protected FullUpdateRequest newRequest() {
		return new FullUpdateRequest();
	}

	@Override
	protected NodeFullUpdateRequest newNodeRequest() {
		return new NodeFullUpdateRequest();
	}

	@Override
	protected NodeFullUpdateRequest newNodeRequest(String nodeId, FullUpdateRequest request) {
		return new NodeFullUpdateRequest(nodeId, request);
	}

	@Override
	protected NodeFullUpdateResponse newNodeResponse() {
		return new NodeFullUpdateResponse(clusterService.localNode());
	}

	@Override
	protected NodeFullUpdateResponse[] newNodeResponseArray(int len) {
		return new NodeFullUpdateResponse[len];
	}

	@Override
	protected FullUpdateResponse newResponse(ClusterName clusterName, NodeFullUpdateResponse[] array) {
		return new FullUpdateResponse(clusterName, array);
	}

}
