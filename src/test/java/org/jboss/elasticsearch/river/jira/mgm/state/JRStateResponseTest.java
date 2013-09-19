/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.junit.Test;

/**
 * Unit test for {@link JRStateResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateResponseTest {

	@Test
	public void constructor_filling() {
		ClusterName cn = new ClusterName("mycluster");

		NodeJRStateResponse[] nodes = new NodeJRStateResponse[0];
		JRStateResponse tested = new JRStateResponse(cn, nodes);

		Assert.assertEquals(cn, tested.getClusterName());
		Assert.assertEquals(nodes, tested.getNodes());
	}

	@Test
	public void serialization() throws IOException {
		ClusterName cn = new ClusterName("mycluster");

		DiscoveryNode dn = new DiscoveryNode("aa", DummyTransportAddress.INSTANCE, Version.CURRENT);
		DiscoveryNode dn2 = new DiscoveryNode("aa2", DummyTransportAddress.INSTANCE, Version.CURRENT);
		DiscoveryNode dn3 = new DiscoveryNode("aa3", DummyTransportAddress.INSTANCE, Version.CURRENT);

		{
			NodeJRStateResponse[] nodes = new NodeJRStateResponse[] {};
			JRStateResponse testedSrc = new JRStateResponse(cn, nodes);
			performSerializationAndBasicAsserts(testedSrc);

		}

		{
			NodeJRStateResponse[] nodes = new NodeJRStateResponse[] { new NodeJRStateResponse(dn, false, null),
					new NodeJRStateResponse(dn2, false, null), new NodeJRStateResponse(dn3, true, "responseText") };
			JRStateResponse testedSrc = new JRStateResponse(cn, nodes);
			JRStateResponse testedTarget = performSerializationAndBasicAsserts(testedSrc);

			Assert.assertEquals(testedSrc.getNodes()[0].getNode().getId(), testedTarget.getNodes()[0].getNode().getId());
			Assert.assertEquals(testedSrc.getNodes()[1].getNode().getId(), testedTarget.getNodes()[1].getNode().getId());
			Assert.assertEquals(testedSrc.getNodes()[2].getNode().getId(), testedTarget.getNodes()[2].getNode().getId());
		}

	}

	private JRStateResponse performSerializationAndBasicAsserts(JRStateResponse testedSrc) throws IOException {
		BytesStreamOutput out = new BytesStreamOutput();
		testedSrc.writeTo(out);
		JRStateResponse testedTarget = new JRStateResponse();
		testedTarget.readFrom(new BytesStreamInput(out.bytes()));

		Assert.assertEquals(testedSrc.getClusterName(), testedTarget.getClusterName());
		Assert.assertEquals(testedSrc.getNodes().length, testedTarget.getNodes().length);

		return testedTarget;
	}

}
