package org.jboss.elasticsearch.river.jira.mgm.riverslist;

import org.elasticsearch.action.support.nodes.NodeOperationRequest;

/**
 * Node Request to list names of all Jira Rivers.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeListRiversRequest extends NodeOperationRequest {

	protected NodeListRiversRequest() {
		super();
	}

	protected NodeListRiversRequest(String nodeId, ListRiversRequest request) {
		super(request, nodeId);
	}
}
