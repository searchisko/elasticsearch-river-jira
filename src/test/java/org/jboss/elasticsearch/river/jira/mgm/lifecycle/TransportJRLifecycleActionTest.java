/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import junit.framework.Assert;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.TransportService;
import org.jboss.elasticsearch.river.jira.IJiraRiverMgm;
import org.jboss.elasticsearch.river.jira.mgm.TransportJRMgmBaseAction;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link TransportJRLifecycleAction} and {@link TransportJRMgmBaseAction}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class TransportJRLifecycleActionTest {

	public static final ClusterName clusterName = new ClusterName("myCluster");

	@Test
	public void transportAction() {
		TransportJRLifecycleAction tested = prepareTestedInstance(clusterName);
		Assert.assertEquals(JRLifecycleAction.NAME, tested.transportAction());
	}

	@Test
	public void newRequest() {
		TransportJRLifecycleAction tested = prepareTestedInstance(clusterName);
		Assert.assertNotNull(tested.newRequest());
	}

	@Test
	public void newNodeRequest() {
		TransportJRLifecycleAction tested = prepareTestedInstance(clusterName);

		{
			Assert.assertNotNull(tested.newNodeRequest());
		}

		{
			JRLifecycleRequest request = new JRLifecycleRequest();
			NodeJRLifecycleRequest nodeReq = tested.newNodeRequest("myNodeId", request);
			Assert.assertEquals(request, nodeReq.getRequest());
		}
	}

	@Test
	public void newNodeResponse() {
		TransportJRLifecycleAction tested = prepareTestedInstance(clusterName);
		Mockito.when(clusterService.localNode()).thenReturn(dn);

		NodeJRLifecycleResponse resp = tested.newNodeResponse();
		Assert.assertNotNull(resp);
		Assert.assertEquals(dn, resp.getNode());
	}

	@Test
	public void newNodeResponseArray() {
		TransportJRLifecycleAction tested = prepareTestedInstance(clusterName);
		NodeJRLifecycleResponse[] array = tested.newNodeResponseArray(2);
		Assert.assertNotNull(array);
		Assert.assertEquals(2, array.length);
	}

	@Test
	public void newResponse() {
		TransportJRLifecycleAction tested = prepareTestedInstance(clusterName);

		NodeJRLifecycleResponse[] array = new NodeJRLifecycleResponse[0];
		JRLifecycleResponse resp = tested.newResponse(clusterName, array);
		Assert.assertNotNull(resp);
		Assert.assertEquals(resp.getClusterName(), clusterName);
		Assert.assertEquals(resp.getNodes(), array);

	}

	@Test
	public void performOperationOnJiraRiver() throws Exception {

		TransportJRLifecycleAction tested = prepareTestedInstance(clusterName);

		IJiraRiverMgm river = Mockito.mock(IJiraRiverMgm.class);

		{
			JRLifecycleRequest req = new JRLifecycleRequest("myriver", JRLifecycleCommand.RESTART);
			NodeJRLifecycleResponse resp = tested.performOperationOnJiraRiver(river, req, dn);
			Assert.assertNotNull(resp);
			Assert.assertTrue(resp.isRiverFound());
			Assert.assertEquals(dn, resp.getNode());
			Mockito.verify(river).restart();
			Mockito.verifyNoMoreInteractions(river);
		}

		Mockito.reset(river);
		{
			JRLifecycleRequest req = new JRLifecycleRequest("myriver", JRLifecycleCommand.STOP);
			NodeJRLifecycleResponse resp = tested.performOperationOnJiraRiver(river, req, dn);
			Assert.assertNotNull(resp);
			Assert.assertTrue(resp.isRiverFound());
			Assert.assertEquals(dn, resp.getNode());
			Mockito.verify(river).stop(true);
			Mockito.verifyNoMoreInteractions(river);
		}

	}

	private static DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE, Version.CURRENT);
	private static ClusterService clusterService = Mockito.mock(ClusterService.class);

	public static TransportJRLifecycleAction prepareTestedInstance(ClusterName clusterName) {
		Settings settings = Mockito.mock(Settings.class);
		ThreadPool threadPool = new ThreadPool();
		TransportService transportService = new TransportService(Mockito.mock(Transport.class), threadPool);
		TransportJRLifecycleAction tested = new TransportJRLifecycleAction(settings, clusterName, threadPool,
				clusterService, transportService);
		return tested;
	}
}
