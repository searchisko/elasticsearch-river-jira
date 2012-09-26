/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullreindex;

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
public class TransportFullReindexAction
    extends
    TransportNodesOperationAction<FullReindexRequest, FullReindexResponse, NodeFullReindexRequest, NodeFullReindexResponse> {

  private static final ESLogger logger = Loggers.getLogger(TransportFullReindexAction.class);

  @Inject
  public TransportFullReindexAction(Settings settings, ClusterName clusterName, ThreadPool threadPool,
      ClusterService clusterService, TransportService transportService) {
    super(settings, clusterName, threadPool, clusterService, transportService);

  }

  @Override
  protected String transportAction() {
    return FullReindexAction.NAME;
  }

  @Override
  protected String executor() {
    return ThreadPool.Names.MANAGEMENT;
  }

  @Override
  protected FullReindexRequest newRequest() {
    return new FullReindexRequest();
  }

  @Override
  protected FullReindexResponse newResponse(FullReindexRequest request,
      @SuppressWarnings("rawtypes") AtomicReferenceArray responses) {
    final List<NodeFullReindexResponse> nodesInfos = new ArrayList<NodeFullReindexResponse>();
    for (int i = 0; i < responses.length(); i++) {
      Object resp = responses.get(i);
      if (resp instanceof NodeFullReindexResponse) {
        nodesInfos.add((NodeFullReindexResponse) resp);
      }
    }
    return new FullReindexResponse(clusterName, nodesInfos.toArray(new NodeFullReindexResponse[nodesInfos.size()]));
  }

  @Override
  protected NodeFullReindexRequest newNodeRequest() {
    return new NodeFullReindexRequest();
  }

  @Override
  protected NodeFullReindexRequest newNodeRequest(String nodeId, FullReindexRequest request) {
    return new NodeFullReindexRequest(nodeId, request);
  }

  @Override
  protected NodeFullReindexResponse newNodeResponse() {
    return new NodeFullReindexResponse();
  }

  @Override
  protected NodeFullReindexResponse nodeOperation(NodeFullReindexRequest nodeRequest) throws ElasticSearchException {
    FullReindexRequest req = nodeRequest.request;
    logger.debug("Go to schedule full reindex for river '{}' and project {}", req.getRiverName(), req.getProjectKey());
    JiraRiver river = JiraRiver.getRunningInstance(req.getRiverName());
    if (river == null) {
      return new NodeFullReindexResponse(clusterService.state().nodes().localNode(), false, false, null);
    } else {
      logger.debug("River found {}", req.getRiverName());
      try {
        String ret = river.forceFullReindex(req.getProjectKey());
        return new NodeFullReindexResponse(clusterService.state().nodes().localNode(), true, ret != null, ret);
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
