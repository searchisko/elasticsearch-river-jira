/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.support.nodes.TransportNodesOperationAction;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.jboss.elasticsearch.river.jira.JiraRiver;

/**
 * Full reindex transport action.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class TransportFullUpdateAction extends
    TransportNodesOperationAction<FullUpdateRequest, FullUpdateResponse, NodeFullUpdateRequest, NodeFullUpdateResponse> {

  private static final ESLogger logger = Loggers.getLogger(TransportFullUpdateAction.class);

  @Inject
  public TransportFullUpdateAction(Settings settings, ClusterName clusterName, ThreadPool threadPool,
      ClusterService clusterService, TransportService transportService) {
    super(settings, clusterName, threadPool, clusterService, transportService);

  }

  @Override
  protected String transportAction() {
    return FullUpdateAction.NAME;
  }

  @Override
  protected String executor() {
    return ThreadPool.Names.MANAGEMENT;
  }

  @Override
  protected FullUpdateRequest newRequest() {
    return new FullUpdateRequest();
  }

  @Override
  protected FullUpdateResponse newResponse(FullUpdateRequest request,
      @SuppressWarnings("rawtypes") AtomicReferenceArray responses) {
    final List<NodeFullUpdateResponse> nodesInfos = new ArrayList<NodeFullUpdateResponse>();
    for (int i = 0; i < responses.length(); i++) {
      Object resp = responses.get(i);
      if (resp instanceof NodeFullUpdateResponse) {
        nodesInfos.add((NodeFullUpdateResponse) resp);
      }
    }
    return new FullUpdateResponse(clusterName, nodesInfos.toArray(new NodeFullUpdateResponse[nodesInfos.size()]));
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
    return new NodeFullUpdateResponse(clusterService.state().nodes().localNode());
  }

  @Override
  protected NodeFullUpdateResponse nodeOperation(NodeFullUpdateRequest nodeRequest) throws ElasticSearchException {
    FullUpdateRequest req = nodeRequest.request;
    logger.debug("Go to look for river '{}' on this node", req.getRiverName());
    JiraRiver river = JiraRiver.getRunningInstance(req.getRiverName());
    if (river == null) {
      return newNodeResponse();
    } else {
      logger.debug("River {} found on this node", req.getRiverName());
      logger
          .debug("Go to schedule full reindex for river '{}' and project {}", req.getRiverName(), req.getProjectKey());
      try {
        String ret = river.forceFullReindex(req.getProjectKey());
        return new NodeFullUpdateResponse(clusterService.state().nodes().localNode(), true, ret != null, ret);
      } catch (Exception e) {
        throw new ElasticSearchException(e.getMessage(), e);
      }
    }
  }

  @Override
  protected boolean accumulateExceptions() {
    return false;
  }

}
