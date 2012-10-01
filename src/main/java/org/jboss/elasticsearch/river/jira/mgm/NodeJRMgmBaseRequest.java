package org.jboss.elasticsearch.river.jira.mgm;

import java.io.IOException;

import org.elasticsearch.action.support.nodes.NodeOperationRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Base for Node Requests targeted to the Jira River.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class NodeJRMgmBaseRequest<T extends JRMgmBaseRequest> extends NodeOperationRequest {

  protected T request;

  protected NodeJRMgmBaseRequest() {
    super();
  }

  /**
   * Construct node request with data.
   * 
   * @param nodeId this request is for
   * @param request to be send to the node
   */
  protected NodeJRMgmBaseRequest(String nodeId, T request) {
    super(nodeId);
    this.request = request;
  }

  public T getRequest() {
    return request;
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    request = newRequest();
    request.readFrom(in);
  }

  protected abstract T newRequest();

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    request.writeTo(out);
  }

}
