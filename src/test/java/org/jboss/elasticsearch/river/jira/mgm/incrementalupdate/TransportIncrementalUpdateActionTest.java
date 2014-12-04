/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

import junit.framework.Assert;

import org.elasticsearch.Version;
import org.elasticsearch.action.support.ActionFilters;
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
 * Unit test for {@link TransportIncrementalUpdateAction}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class TransportIncrementalUpdateActionTest {

	public static final ClusterName clusterName = new ClusterName("myCluster");

	@Test
	public void newRequest() {
		TransportIncrementalUpdateAction tested = prepareTestedInstance(clusterName);
		Assert.assertNotNull(tested.newRequest());
	}

	@Test
	public void newNodeRequest() {
		TransportIncrementalUpdateAction tested = prepareTestedInstance(clusterName);

		{
			Assert.assertNotNull(tested.newNodeRequest());
		}

		{
			IncrementalUpdateRequest request = new IncrementalUpdateRequest();
			NodeIncrementalUpdateRequest nodeReq = tested.newNodeRequest("myNodeId", request);
			Assert.assertEquals(request, nodeReq.getRequest());
		}
	}

	@Test
	public void newNodeResponse() {
		TransportIncrementalUpdateAction tested = prepareTestedInstance(clusterName);
		Mockito.when(clusterService.localNode()).thenReturn(dn);

		NodeIncrementalUpdateResponse resp = tested.newNodeResponse();
		Assert.assertNotNull(resp);
		Assert.assertEquals(dn, resp.getNode());
	}

	@Test
	public void newNodeResponseArray() {
		TransportIncrementalUpdateAction tested = prepareTestedInstance(clusterName);
		NodeIncrementalUpdateResponse[] array = tested.newNodeResponseArray(2);
		Assert.assertNotNull(array);
		Assert.assertEquals(2, array.length);
	}

	@Test
	public void newResponse() {
		TransportIncrementalUpdateAction tested = prepareTestedInstance(clusterName);

		NodeIncrementalUpdateResponse[] array = new NodeIncrementalUpdateResponse[0];
		IncrementalUpdateResponse resp = tested.newResponse(clusterName, array);
		Assert.assertNotNull(resp);
		Assert.assertEquals(resp.getClusterName(), clusterName);
		Assert.assertEquals(resp.getNodes(), array);

	}

	@Test
	public void performOperationOnJiraRiver() throws Exception {

		TransportIncrementalUpdateAction tested = prepareTestedInstance(clusterName);

		IJiraRiverMgm river = Mockito.mock(IJiraRiverMgm.class);

		{
			Mockito.when(river.forceIncrementalReindex(null)).thenReturn("AAA,JJJ");

			IncrementalUpdateRequest req = new IncrementalUpdateRequest("myriver", null);
			NodeIncrementalUpdateResponse resp = tested.performOperationOnJiraRiver(river, req, dn);
			Assert.assertNotNull(resp);
			Assert.assertTrue(resp.isRiverFound());
			Assert.assertEquals(dn, resp.getNode());
			Assert.assertEquals(true, resp.projectFound);
			Assert.assertEquals("AAA,JJJ", resp.reindexedProjectNames);
			Mockito.verify(river).forceIncrementalReindex(null);
			Mockito.verifyNoMoreInteractions(river);
		}

		// case - project found
		Mockito.reset(river);
		{
			Mockito.when(river.forceIncrementalReindex("AAA")).thenReturn("AAA");
			IncrementalUpdateRequest req = new IncrementalUpdateRequest("myriver", "AAA");
			NodeIncrementalUpdateResponse resp = tested.performOperationOnJiraRiver(river, req, dn);
			Assert.assertNotNull(resp);
			Assert.assertTrue(resp.isRiverFound());
			Assert.assertEquals(dn, resp.getNode());
			Assert.assertEquals(true, resp.projectFound);
			Assert.assertEquals("AAA", resp.reindexedProjectNames);
			Mockito.verify(river).forceIncrementalReindex("AAA");
			Mockito.verifyNoMoreInteractions(river);
		}

		// case - project not found
		Mockito.reset(river);
		{
			Mockito.when(river.forceIncrementalReindex("AAA")).thenReturn(null);
			IncrementalUpdateRequest req = new IncrementalUpdateRequest("myriver", "AAA");
			NodeIncrementalUpdateResponse resp = tested.performOperationOnJiraRiver(river, req, dn);
			Assert.assertNotNull(resp);
			Assert.assertTrue(resp.isRiverFound());
			Assert.assertEquals(dn, resp.getNode());
			Assert.assertEquals(false, resp.projectFound);
			Assert.assertEquals(null, resp.reindexedProjectNames);
			Mockito.verify(river).forceIncrementalReindex("AAA");
			Mockito.verifyNoMoreInteractions(river);
		}

	}

	private static DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE, Version.CURRENT);
	private static ClusterService clusterService = Mockito.mock(ClusterService.class);

	public static TransportIncrementalUpdateAction prepareTestedInstance(ClusterName clusterName) {
		Settings settings = Mockito.mock(Settings.class);
		ThreadPool threadPool = new ThreadPool("testtp");
		TransportService transportService = new TransportService(Mockito.mock(Transport.class), threadPool);
		ActionFilters actionFilters = Mockito.mock(ActionFilters.class);
		TransportIncrementalUpdateAction tested = new TransportIncrementalUpdateAction(settings, clusterName, threadPool,
				clusterService, transportService, actionFilters);
		return tested;
	}
}
