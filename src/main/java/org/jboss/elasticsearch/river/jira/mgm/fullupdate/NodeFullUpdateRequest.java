package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import org.jboss.elasticsearch.river.jira.mgm.NodeJRMgmBaseRequest;

/**
 * Full reindex node request.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeFullUpdateRequest extends NodeJRMgmBaseRequest<FullUpdateRequest> {

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
    super(nodeId, request);
  }

  @Override
  protected FullUpdateRequest newRequest() {
    return new FullUpdateRequest();
  }

}
