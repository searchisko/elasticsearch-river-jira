package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import java.io.IOException;

import org.elasticsearch.action.support.nodes.NodeOperationRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Full reindex node request.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeFullUpdateRequest extends NodeOperationRequest {

  FullUpdateRequest request;

  NodeFullUpdateRequest() {
    super();
  }

  /**
   * Construct node request with data.
   * 
   * @param nodeId this request is for
   * @param request to be send to the node
   */
  NodeFullUpdateRequest(String nodeId, FullUpdateRequest request) {
    super(nodeId);
    this.request = request;
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    request = new FullUpdateRequest();
    request.readFrom(in);
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    request.writeTo(out);
  }

}
