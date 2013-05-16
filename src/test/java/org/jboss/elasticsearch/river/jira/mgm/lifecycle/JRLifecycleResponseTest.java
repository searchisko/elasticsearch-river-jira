/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseResponse;
import org.junit.Test;

/**
 * Unit test for {@link JRLifecycleResponse} and {@link JRMgmBaseResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleResponseTest {

	@Test
	public void constructor_filling() {
		ClusterName cn = new ClusterName("mycluster");

		NodeJRLifecycleResponse[] nodes = new NodeJRLifecycleResponse[0];
		JRLifecycleResponse tested = new JRLifecycleResponse(cn, nodes);

		Assert.assertEquals(cn, tested.getClusterName());
		Assert.assertEquals(nodes, tested.getNodes());
	}

	@Test
	public void serialization() throws IOException {
		ClusterName cn = new ClusterName("mycluster");

		DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE);
		DiscoveryNode dn2 = new DiscoveryNode("aa2", DummyTransportAddress.INSTANCE);
		DiscoveryNode dn3 = new DiscoveryNode("aa3", DummyTransportAddress.INSTANCE);

		{
			NodeJRLifecycleResponse[] nodes = new NodeJRLifecycleResponse[] {};
			JRLifecycleResponse testedSrc = new JRLifecycleResponse(cn, nodes);
			performSerializationAndBasicAsserts(testedSrc);

		}

		{
			NodeJRLifecycleResponse[] nodes = new NodeJRLifecycleResponse[] { new NodeJRLifecycleResponse(dn, false),
					new NodeJRLifecycleResponse(dn2, false), new NodeJRLifecycleResponse(dn3, true) };
			JRLifecycleResponse testedSrc = new JRLifecycleResponse(cn, nodes);
			JRLifecycleResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);

			Assert.assertEquals(testedSrc.getNodes()[0].getNode().getId(), testedTarget.getNodes()[0].getNode().getId());
			Assert.assertEquals(testedSrc.getNodes()[1].getNode().getId(), testedTarget.getNodes()[1].getNode().getId());
			Assert.assertEquals(testedSrc.getNodes()[2].getNode().getId(), testedTarget.getNodes()[2].getNode().getId());
		}

	}

	private JRLifecycleResponse performSerializationAndBasicAsserts(JRLifecycleResponse testedSrc) throws IOException {
		BytesStreamOutput out = new BytesStreamOutput();
		testedSrc.writeTo(out);
		JRLifecycleResponse testedTarget = new JRLifecycleResponse();
		testedTarget.readFrom(new BytesStreamInput(out.bytes()));

		Assert.assertEquals(testedSrc.getClusterName(), testedTarget.getClusterName());
		Assert.assertEquals(testedSrc.getNodes().length, testedTarget.getNodes().length);

		return testedTarget;
	}

	@Test
	public void getSuccessNodeResponse() {

		ClusterName cn = new ClusterName("mycluster");

		DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE);
		DiscoveryNode dn2 = new DiscoveryNode("aa2", DummyTransportAddress.INSTANCE);
		DiscoveryNode dn3 = new DiscoveryNode("aa3", DummyTransportAddress.INSTANCE);

		{
			JRLifecycleResponse tested = new JRLifecycleResponse();
			Assert.assertNull(tested.getSuccessNodeResponse());
		}

		{
			NodeJRLifecycleResponse[] nodes = new NodeJRLifecycleResponse[0];
			JRLifecycleResponse tested = new JRLifecycleResponse(cn, nodes);
			Assert.assertNull(tested.getSuccessNodeResponse());
		}

		{
			NodeJRLifecycleResponse[] nodes = new NodeJRLifecycleResponse[] { new NodeJRLifecycleResponse(dn, false) };
			JRLifecycleResponse tested = new JRLifecycleResponse(cn, nodes);
			Assert.assertNull(tested.getSuccessNodeResponse());
		}

		{
			NodeJRLifecycleResponse[] nodes = new NodeJRLifecycleResponse[] { new NodeJRLifecycleResponse(dn, true) };
			JRLifecycleResponse tested = new JRLifecycleResponse(cn, nodes);
			Assert.assertEquals(nodes[0], tested.getSuccessNodeResponse());
		}

		{
			NodeJRLifecycleResponse[] nodes = new NodeJRLifecycleResponse[] { new NodeJRLifecycleResponse(dn, true) };
			JRLifecycleResponse tested = new JRLifecycleResponse(cn, nodes);
			Assert.assertEquals(nodes[0], tested.getSuccessNodeResponse());
		}

		{
			NodeJRLifecycleResponse[] nodes = new NodeJRLifecycleResponse[] { new NodeJRLifecycleResponse(dn, false),
					new NodeJRLifecycleResponse(dn2, false), new NodeJRLifecycleResponse(dn3, true) };
			JRLifecycleResponse tested = new JRLifecycleResponse(cn, nodes);
			Assert.assertEquals(nodes[2], tested.getSuccessNodeResponse());
		}

	}

}
