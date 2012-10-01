package org.jboss.elasticsearch.river.jira.mgm;

import java.io.IOException;

import org.elasticsearch.action.support.nodes.NodeOperationResponse;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Abstract base for node responses used for JIRA river management.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class NodeJRMgmBaseResponse extends NodeOperationResponse {

  protected boolean riverFound = false;

  protected NodeJRMgmBaseResponse() {
  }

  public NodeJRMgmBaseResponse(DiscoveryNode node) {
    super(node);
  }

  /**
   * Create response with values to be send back to requestor.
   * 
   * @param node this response is for.
   * @param riverFound set to true if you found river on this node
   */
  public NodeJRMgmBaseResponse(DiscoveryNode node, boolean riverFound) {
    super(node);
    this.riverFound = riverFound;
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    riverFound = in.readBoolean();
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeBoolean(riverFound);
  }

  /**
   * @return true if river was found on this node
   */
  public boolean isRiverFound() {
    return riverFound;
  }

}
