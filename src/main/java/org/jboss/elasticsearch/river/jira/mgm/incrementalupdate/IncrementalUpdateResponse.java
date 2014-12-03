/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

import org.elasticsearch.cluster.ClusterName;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseResponse;

/**
 * Response for incremental reindex request. All node responses are agregated here.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IncrementalUpdateResponse extends JRMgmBaseResponse<NodeIncrementalUpdateResponse> {

	public IncrementalUpdateResponse() {

	}

	public IncrementalUpdateResponse(ClusterName clusterName, NodeIncrementalUpdateResponse[] nodes) {
		super(clusterName, nodes);
	}

	@Override
	protected NodeIncrementalUpdateResponse[] newNodeResponsesArray(int len) {
		return new NodeIncrementalUpdateResponse[len];
	}

	@Override
	protected NodeIncrementalUpdateResponse newNodeResponse() {
		return new NodeIncrementalUpdateResponse();
	}

}
