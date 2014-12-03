/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

import java.io.IOException;

import junit.framework.Assert;

import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.junit.Test;

/**
 * Unit test for {@link NodeIncrementalUpdateRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class NodeIncrementalUpdateRequestTest {

	@Test
	public void constructor() {

		// note nodeId cann't be asserted because private and no getter for it :-(

		{
			NodeIncrementalUpdateRequest tested = new NodeIncrementalUpdateRequest();
			Assert.assertNull(tested.getRequest());
		}

		{
			IncrementalUpdateRequest request = new IncrementalUpdateRequest();
			NodeIncrementalUpdateRequest tested = new NodeIncrementalUpdateRequest("myNode", request);
			Assert.assertEquals(request, tested.getRequest());
		}

	}

	@SuppressWarnings("unused")
	@Test
	public void serialization() throws IOException {

		{
			IncrementalUpdateRequest request = new IncrementalUpdateRequest("my river", null);
			NodeIncrementalUpdateRequest testedSrc = new NodeIncrementalUpdateRequest("myNode", request);
			NodeIncrementalUpdateRequest testedTarget = performSerializationAndBasicAsserts(testedSrc);
		}
		{
			IncrementalUpdateRequest request = new IncrementalUpdateRequest("my river 2", "AAA");
			NodeIncrementalUpdateRequest testedSrc = new NodeIncrementalUpdateRequest("myNode2", request);
			NodeIncrementalUpdateRequest testedTarget = performSerializationAndBasicAsserts(testedSrc);
		}

	}

	private NodeIncrementalUpdateRequest performSerializationAndBasicAsserts(NodeIncrementalUpdateRequest testedSrc)
			throws IOException {
		BytesStreamOutput out = new BytesStreamOutput();
		testedSrc.writeTo(out);
		NodeIncrementalUpdateRequest testedTarget = new NodeIncrementalUpdateRequest();
		testedTarget.readFrom(new BytesStreamInput(out.bytes()));
		Assert.assertEquals(testedSrc.getRequest().getRiverName(), testedTarget.getRequest().getRiverName());
		Assert.assertEquals(testedSrc.getRequest().getProjectKey(), testedTarget.getRequest().getProjectKey());

		return testedTarget;
	}

}
