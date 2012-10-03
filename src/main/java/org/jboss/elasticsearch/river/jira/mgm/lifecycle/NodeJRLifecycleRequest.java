package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import org.jboss.elasticsearch.river.jira.mgm.NodeJRMgmBaseRequest;

/**
 * Node request for JiraRiver lifecycle command.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeJRLifecycleRequest extends NodeJRMgmBaseRequest<JRLifecycleRequest> {

  NodeJRLifecycleRequest() {
    super();
  }

  /**
   * Construct node request with data.
   * 
   * @param nodeId this request is for
   * @param request to be send to the node
   */
  NodeJRLifecycleRequest(String nodeId, JRLifecycleRequest request) {
    super(nodeId, request);
  }

  @Override
  protected JRLifecycleRequest newRequest() {
    return new JRLifecycleRequest();
  }

}
