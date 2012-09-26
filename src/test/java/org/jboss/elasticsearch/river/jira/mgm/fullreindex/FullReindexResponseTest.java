/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullreindex;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.junit.Test;

/**
 * Unit test for {@link FullReindexResponse}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullReindexResponseTest {

  @Test
  public void getSuccessNodeResponse() {

    FullReindexResponse tested = new FullReindexResponse(new ClusterName("cl"), null);
    Assert.assertNull(tested.getSuccessNodeResponse());

    NodeFullReindexResponse[] nodes = new NodeFullReindexResponse[] {};
    tested = new FullReindexResponse(new ClusterName("cl"), nodes);
    Assert.assertNull(tested.getSuccessNodeResponse());

    nodes = new NodeFullReindexResponse[] { new NodeFullReindexResponse(new DiscoveryNode("nd1",
        DummyTransportAddress.INSTANCE), false, false, null) };
    tested = new FullReindexResponse(new ClusterName("cl"), nodes);
    Assert.assertNull(tested.getSuccessNodeResponse());

    nodes = new NodeFullReindexResponse[] {
        new NodeFullReindexResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE), false, false, null),
        new NodeFullReindexResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE), false, false, null),
        new NodeFullReindexResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE), false, false, null) };
    tested = new FullReindexResponse(new ClusterName("cl"), nodes);
    Assert.assertNull(tested.getSuccessNodeResponse());

    nodes = new NodeFullReindexResponse[] {
        new NodeFullReindexResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE), false, false, null),
        new NodeFullReindexResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE), true, false, null),
        new NodeFullReindexResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE), false, false, null) };
    tested = new FullReindexResponse(new ClusterName("cl"), nodes);
    NodeFullReindexResponse r = tested.getSuccessNodeResponse();
    Assert.assertNotNull(r);
    Assert.assertEquals("nd2", r.getNode().getId());

  }

  @Test
  public void serialization() throws IOException {
    NodeFullReindexResponse[] nodes = new NodeFullReindexResponse[] {
        new NodeFullReindexResponse(new DiscoveryNode("nd1", DummyTransportAddress.INSTANCE), false, false, null),
        new NodeFullReindexResponse(new DiscoveryNode("nd2", DummyTransportAddress.INSTANCE), false, false, null),
        new NodeFullReindexResponse(new DiscoveryNode("nd3", DummyTransportAddress.INSTANCE), true, true, "ORG") };
    FullReindexResponse testedSrc = new FullReindexResponse(new ClusterName("cl"), nodes);

    BytesStreamOutput out = new BytesStreamOutput();
    testedSrc.writeTo(out);

    FullReindexResponse testedTarget = new FullReindexResponse();
    testedTarget.readFrom(new BytesStreamInput(out.bytes()));

    Assert.assertEquals(3, testedTarget.nodes().length);
    NodeFullReindexResponse r = testedTarget.getSuccessNodeResponse();
    Assert.assertNotNull(r);
    Assert.assertEquals("nd3", r.getNode().getId());
    Assert.assertTrue(r.riverFound);
    Assert.assertTrue(r.projectFound);
    Assert.assertEquals("ORG", r.reindexedProjectNames);
  }

}
