/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

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
 * Unit test for {@link IncrementalUpdateResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IncrementalUpdateResponseTest {

	@Test
	public void getSuccessNodeResponse() {

		IncrementalUpdateResponse tested = new IncrementalUpdateResponse(new ClusterName("cl"), null);
		Assert.assertNull(tested.getSuccessNodeResponse());

		NodeIncrementalUpdateResponse[] nodes = new NodeIncrementalUpdateResponse[] {};
		tested = new IncrementalUpdateResponse(new ClusterName("cl"), nodes);
		Assert.assertNull(tested.getSuccessNodeResponse());

		nodes = new NodeIncrementalUpdateResponse[] { new NodeIncrementalUpdateResponse(new DiscoveryNode("nd1",
				DummyTransportAddress.INSTANCE, Version.CURRENT), false, false, null) };
		tested = new IncrementalUpdateResponse(new ClusterName("cl"), nodes);
		Assert.assertNull(tested.getSuccessNodeResponse());

		nodes = new NodeIncrementalUpdateResponse[] {
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE, Version.CURRENT),
						false, false, null),
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE, Version.CURRENT),
						false, false, null),
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE, Version.CURRENT),
						false, false, null) };
		tested = new IncrementalUpdateResponse(new ClusterName("cl"), nodes);
		Assert.assertNull(tested.getSuccessNodeResponse());

		nodes = new NodeIncrementalUpdateResponse[] {
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE, Version.CURRENT),
						false, false, null),
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE, Version.CURRENT),
						true, false, null),
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE, Version.CURRENT),
						false, false, null) };
		tested = new IncrementalUpdateResponse(new ClusterName("cl"), nodes);
		NodeIncrementalUpdateResponse r = tested.getSuccessNodeResponse();
		Assert.assertNotNull(r);
		Assert.assertEquals("nd2", r.getNode().getId());

	}

	@Test
	public void serialization() throws IOException {
		NodeIncrementalUpdateResponse[] nodes = new NodeIncrementalUpdateResponse[] {
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE, Version.CURRENT),
						false, false, null),
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE, Version.CURRENT),
						false, false, null),
				new NodeIncrementalUpdateResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE, Version.CURRENT),
						true, true, "ORG") };
		IncrementalUpdateResponse testedSrc = new IncrementalUpdateResponse(new ClusterName("cl"), nodes);

		BytesStreamOutput out = new BytesStreamOutput();
		testedSrc.writeTo(out);

		IncrementalUpdateResponse testedTarget = new IncrementalUpdateResponse();
		testedTarget.readFrom(new BytesStreamInput(out.bytes()));

		Assert.assertEquals(3, testedTarget.getNodes().length);
		NodeIncrementalUpdateResponse r = testedTarget.getSuccessNodeResponse();
		Assert.assertNotNull(r);
		Assert.assertEquals("nd3", r.getNode().getId());
		Assert.assertTrue(r.isRiverFound());
		Assert.assertTrue(r.projectFound);
		Assert.assertEquals("ORG", r.reindexedProjectNames);
	}

}
