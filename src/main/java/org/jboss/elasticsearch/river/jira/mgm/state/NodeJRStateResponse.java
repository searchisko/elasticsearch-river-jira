package org.jboss.elasticsearch.river.jira.mgm.state;

import java.io.IOException;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.jboss.elasticsearch.river.jira.mgm.NodeJRMgmBaseResponse;

/**
 * JiraRiver state information node response.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeJRStateResponse extends NodeJRMgmBaseResponse {

	protected String stateInformation;

	protected NodeJRStateResponse() {
	}

	public NodeJRStateResponse(DiscoveryNode node) {
		super(node);
	}

	/**
	 * Create response with values to be send back to requestor.
	 * 
	 * @param node this response is for.
	 * @param riverFound set to true if you found river on this node
	 * @param stateInformation JSON with river state information if river is found on this node.
	 */
	public NodeJRStateResponse(DiscoveryNode node, boolean riverFound, String stateInformation) {
		super(node, riverFound);
		this.stateInformation = stateInformation;
	}

	@Override
	public void readFrom(StreamInput in) throws IOException {
		super.readFrom(in);
		stateInformation = in.readOptionalString();
	}

	@Override
	public void writeTo(StreamOutput out) throws IOException {
		super.writeTo(out);
		out.writeOptionalString(stateInformation);
	}

	public String getStateInformation() {
		return stateInformation;
	}

}
