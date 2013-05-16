/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.junit.Test;

/**
 * Unit test for {@link FullUpdateResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateResponseTest {

	@Test
	public void getSuccessNodeResponse() {

		FullUpdateResponse tested = new FullUpdateResponse(new ClusterName("cl"), null);
		Assert.assertNull(tested.getSuccessNodeResponse());

		NodeFullUpdateResponse[] nodes = new NodeFullUpdateResponse[] {};
		tested = new FullUpdateResponse(new ClusterName("cl"), nodes);
		Assert.assertNull(tested.getSuccessNodeResponse());

		nodes = new NodeFullUpdateResponse[] { new NodeFullUpdateResponse(new DiscoveryNode("nd1",
				DummyTransportAddress.INSTANCE), false, false, null) };
		tested = new FullUpdateResponse(new ClusterName("cl"), nodes);
		Assert.assertNull(tested.getSuccessNodeResponse());

		nodes = new NodeFullUpdateResponse[] {
				new NodeFullUpdateResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE), false, false, null),
				new NodeFullUpdateResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE), false, false, null),
				new NodeFullUpdateResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE), false, false, null) };
		tested = new FullUpdateResponse(new ClusterName("cl"), nodes);
		Assert.assertNull(tested.getSuccessNodeResponse());

		nodes = new NodeFullUpdateResponse[] {
				new NodeFullUpdateResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE), false, false, null),
				new NodeFullUpdateResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE), true, false, null),
				new NodeFullUpdateResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE), false, false, null) };
		tested = new FullUpdateResponse(new ClusterName("cl"), nodes);
		NodeFullUpdateResponse r = tested.getSuccessNodeResponse();
		Assert.assertNotNull(r);
		Assert.assertEquals("nd2", r.getNode().getId());

	}

	@Test
	public void serialization() throws IOException {
		NodeFullUpdateResponse[] nodes = new NodeFullUpdateResponse[] {
				new NodeFullUpdateResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE), false, false, null),
				new NodeFullUpdateResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE), false, false, null),
				new NodeFullUpdateResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE), true, true, "ORG") };
		FullUpdateResponse testedSrc = new FullUpdateResponse(new ClusterName("cl"), nodes);

		BytesStreamOutput out = new BytesStreamOutput();
		testedSrc.writeTo(out);

		FullUpdateResponse testedTarget = new FullUpdateResponse();
		testedTarget.readFrom(new BytesStreamInput(out.bytes()));

		Assert.assertEquals(3, testedTarget.getNodes().length);
		NodeFullUpdateResponse r = testedTarget.getSuccessNodeResponse();
		Assert.assertNotNull(r);
		Assert.assertEquals("nd3", r.getNode().getId());
		Assert.assertTrue(r.isRiverFound());
		Assert.assertTrue(r.projectFound);
		Assert.assertEquals("ORG", r.reindexedProjectNames);
	}

}
