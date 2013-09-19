/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.riverslist;

import junit.framework.Assert;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.TransportService;
import org.jboss.elasticsearch.river.jira.IJiraRiverMgm;
import org.jboss.elasticsearch.river.jira.JiraRiver;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link TransportListRiversAction}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class TransportListRiversActionTest {

	public static final ClusterName clusterName = new ClusterName("myCluster");

	@Test
	public void transportAction() {
		TransportListRiversAction tested = prepareTestedInstance(clusterName);
		Assert.assertEquals(ListRiversAction.NAME, tested.transportAction());
	}

	@Test
	public void newRequest() {
		TransportListRiversAction tested = prepareTestedInstance(clusterName);
		Assert.assertNotNull(tested.newRequest());
	}

	@SuppressWarnings("unused")
	@Test
	public void newNodeRequest() {
		TransportListRiversAction tested = prepareTestedInstance(clusterName);

		{
			Assert.assertNotNull(tested.newNodeRequest());
		}

		{
			ListRiversRequest request = new ListRiversRequest();
			NodeListRiversRequest nodeReq = tested.newNodeRequest("myNodeId", request);
		}
	}

	@Test
	public void newNodeResponse() {
		TransportListRiversAction tested = prepareTestedInstance(clusterName);
		Mockito.when(clusterService.localNode()).thenReturn(dn);

		NodeListRiversResponse resp = tested.newNodeResponse();
		Assert.assertNotNull(resp);
		Assert.assertEquals(dn, resp.getNode());
	}

	@Test
	public void nodeOperation() throws Exception {

		TransportListRiversAction tested = prepareTestedInstance(clusterName);

        String myRiverName1 = "myRiver";
        String myRiverName2 = "myRiver2";

        try {

            {
                NodeListRiversRequest req = Mockito.mock(NodeListRiversRequest.class);
                NodeListRiversResponse resp = tested.nodeOperation(req);
                Assert.assertNotNull(resp);
                Assert.assertNotNull(resp.jiraRiverNames);
                Assert.assertEquals(0, resp.jiraRiverNames.size());
            }

            {
                IJiraRiverMgm jiraRiverMock = Mockito.mock(IJiraRiverMgm.class);
                RiverName riverName = new RiverName("jira", myRiverName1);
                Mockito.when(jiraRiverMock.riverName()).thenReturn(riverName);
                JiraRiver.addRunningInstance(jiraRiverMock);
                NodeListRiversRequest req = Mockito.mock(NodeListRiversRequest.class);
                NodeListRiversResponse resp = tested.nodeOperation(req);
                Assert.assertNotNull(resp);
                Assert.assertNotNull(resp.jiraRiverNames);
                Assert.assertEquals(1, resp.jiraRiverNames.size());
                Assert.assertTrue(resp.jiraRiverNames.contains(myRiverName1));
            }

            {
                IJiraRiverMgm jiraRiverMock = Mockito.mock(IJiraRiverMgm.class);
                RiverName riverName = new RiverName("jira", myRiverName2);
                Mockito.when(jiraRiverMock.riverName()).thenReturn(riverName);
                JiraRiver.addRunningInstance(jiraRiverMock);
                NodeListRiversRequest req = Mockito.mock(NodeListRiversRequest.class);
                NodeListRiversResponse resp = tested.nodeOperation(req);
                Assert.assertNotNull(resp);
                Assert.assertNotNull(resp.jiraRiverNames);
                Assert.assertEquals(2, resp.jiraRiverNames.size());
                Assert.assertTrue(resp.jiraRiverNames.contains(myRiverName1));
                Assert.assertTrue(resp.jiraRiverNames.contains(myRiverName2));
            }

        } finally {
            JiraRiver.removeRunningInstances(myRiverName1, myRiverName2);
        }

	}

	private static DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE, Version.CURRENT);
	private static ClusterService clusterService = Mockito.mock(ClusterService.class);

	public static TransportListRiversAction prepareTestedInstance(ClusterName clusterName) {
		Settings settings = Mockito.mock(Settings.class);
		ThreadPool threadPool = new ThreadPool();
		TransportService transportService = new TransportService(Mockito.mock(Transport.class), threadPool);
		TransportListRiversAction tested = new TransportListRiversAction(settings, clusterName, threadPool, clusterService,
				transportService);
		return tested;
	}
}
