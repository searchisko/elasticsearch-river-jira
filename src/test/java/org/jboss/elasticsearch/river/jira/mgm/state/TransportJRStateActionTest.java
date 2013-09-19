/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import java.util.Date;

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
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link TransportJRStateAction}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class TransportJRStateActionTest {

	public static final ClusterName clusterName = new ClusterName("myCluster");

	@Test
	public void transportAction() {
		TransportJRStateAction tested = prepareTestedInstance(clusterName);
		Assert.assertEquals(JRStateAction.NAME, tested.transportAction());
	}

	@Test
	public void newRequest() {
		TransportJRStateAction tested = prepareTestedInstance(clusterName);
		Assert.assertNotNull(tested.newRequest());
	}

	@Test
	public void newNodeRequest() {
		TransportJRStateAction tested = prepareTestedInstance(clusterName);

		{
			Assert.assertNotNull(tested.newNodeRequest());
		}

		{
			JRStateRequest request = new JRStateRequest();
			NodeJRStateRequest nodeReq = tested.newNodeRequest("myNodeId", request);
			Assert.assertEquals(request, nodeReq.getRequest());
		}
	}

	@Test
	public void newNodeResponse() {
		TransportJRStateAction tested = prepareTestedInstance(clusterName);
		Mockito.when(clusterService.localNode()).thenReturn(dn);

		NodeJRStateResponse resp = tested.newNodeResponse();
		Assert.assertNotNull(resp);
		Assert.assertEquals(dn, resp.getNode());
	}

	@Test
	public void newNodeResponseArray() {
		TransportJRStateAction tested = prepareTestedInstance(clusterName);
		NodeJRStateResponse[] array = tested.newNodeResponseArray(2);
		Assert.assertNotNull(array);
		Assert.assertEquals(2, array.length);
	}

	@Test
	public void newResponse() {
		TransportJRStateAction tested = prepareTestedInstance(clusterName);

		NodeJRStateResponse[] array = new NodeJRStateResponse[0];
		JRStateResponse resp = tested.newResponse(clusterName, array);
		Assert.assertNotNull(resp);
		Assert.assertEquals(resp.getClusterName(), clusterName);
		Assert.assertEquals(resp.getNodes(), array);

	}

	@Test
	public void performOperationOnJiraRiver() throws Exception {

		TransportJRStateAction tested = prepareTestedInstance(clusterName);

		IJiraRiverMgm river = Mockito.mock(IJiraRiverMgm.class);

		{
			JRStateRequest req = new JRStateRequest("myriver");
			NodeJRStateResponse resp = tested.performOperationOnJiraRiver(river, req, dn);
			Assert.assertNotNull(resp);
			Assert.assertTrue(resp.isRiverFound());
			Assert.assertEquals(dn, resp.getNode());
			Assert.assertEquals(null, resp.stateInformation);
			Mockito.verify(river).getRiverOperationInfo(Mockito.eq(dn), (Date) Mockito.notNull());
			Mockito.verifyNoMoreInteractions(river);
		}

		Mockito.reset(river);
		{
			Mockito.when(river.getRiverOperationInfo(Mockito.eq(dn), Mockito.any(Date.class))).thenReturn("state info");
			JRStateRequest req = new JRStateRequest("myriver");
			NodeJRStateResponse resp = tested.performOperationOnJiraRiver(river, req, dn);
			Assert.assertNotNull(resp);
			Assert.assertTrue(resp.isRiverFound());
			Assert.assertEquals(dn, resp.getNode());
			Assert.assertEquals("state info", resp.stateInformation);
			Mockito.verify(river).getRiverOperationInfo(Mockito.eq(dn), (Date) Mockito.notNull());
			Mockito.verifyNoMoreInteractions(river);
		}

	}

	private static DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE, Version.CURRENT);
	private static ClusterService clusterService = Mockito.mock(ClusterService.class);

	public static TransportJRStateAction prepareTestedInstance(ClusterName clusterName) {
		Settings settings = Mockito.mock(Settings.class);
		ThreadPool threadPool = new ThreadPool();
		TransportService transportService = new TransportService(Mockito.mock(Transport.class), threadPool);
		TransportJRStateAction tested = new TransportJRStateAction(settings, clusterName, threadPool, clusterService,
				transportService);
		return tested;
	}
}
