package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

import org.jboss.elasticsearch.river.jira.mgm.NodeJRMgmBaseRequest;

/**
 * Incremental reindex node request.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeIncrementalUpdateRequest extends NodeJRMgmBaseRequest<IncrementalUpdateRequest> {

	NodeIncrementalUpdateRequest() {
		super();
	}

	/**
	 * Construct node request with data.
	 * 
	 * @param nodeId this request is for
	 * @param request to be send to the node
	 */
	NodeIncrementalUpdateRequest(String nodeId, IncrementalUpdateRequest request) {
		super(nodeId, request);
	}

	@Override
	protected IncrementalUpdateRequest newRequest() {
		return new IncrementalUpdateRequest();
	}

}
