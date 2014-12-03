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
 * Unit test for {@link IncrementalUpdateRequest}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IncrementalUpdateRequestTest {

	@Test
	public void constructor_empty() {
		{
			IncrementalUpdateRequest tested = new IncrementalUpdateRequest();

			tested.setRiverName("myriver");
			tested.setProjectKey("AAA");
			Assert.assertEquals("myriver", tested.getRiverName());
			Assert.assertEquals("AAA", tested.getProjectKey());
		}
	}

	@Test
	public void constructor_filling() {

		try {
			new IncrementalUpdateRequest(null, "AAA");
			Assert.fail("IllegalArgumentException must be thrown");
		} catch (IllegalArgumentException e) {
			// OK
		}
		{
			IncrementalUpdateRequest tested = new IncrementalUpdateRequest("myriver", null);
			Assert.assertEquals("myriver", tested.getRiverName());
			Assert.assertNull(tested.getProjectKey());
			Assert.assertFalse(tested.isProjectKeyRequest());
		}

		{
			IncrementalUpdateRequest tested = new IncrementalUpdateRequest("myriver", "AAA");
			Assert.assertEquals("myriver", tested.getRiverName());
			Assert.assertEquals("AAA", tested.getProjectKey());
			Assert.assertTrue(tested.isProjectKeyRequest());
		}
	}

	@Test
	public void serialization() throws IOException {

		{
			IncrementalUpdateRequest testedSrc = new IncrementalUpdateRequest();
			IncrementalUpdateRequest testedTarget = performserialization(testedSrc);
			Assert.assertNull(testedTarget.getRiverName());
			Assert.assertNull(testedTarget.getProjectKey());
		}

		{
			IncrementalUpdateRequest testedSrc = new IncrementalUpdateRequest("myriver", null);
			IncrementalUpdateRequest testedTarget = performserialization(testedSrc);
			Assert.assertEquals("myriver", testedTarget.getRiverName());
			Assert.assertNull(testedTarget.getProjectKey());
		}

		{
			IncrementalUpdateRequest testedSrc = new IncrementalUpdateRequest("myriver", "ORG");
			IncrementalUpdateRequest testedTarget = performserialization(testedSrc);
			Assert.assertEquals("myriver", testedTarget.getRiverName());
			Assert.assertEquals("ORG", testedTarget.getProjectKey());
		}

	}

	/**
	 * @param testedSrc
	 * @return
	 * @throws IOException
	 */
	private IncrementalUpdateRequest performserialization(IncrementalUpdateRequest testedSrc) throws IOException {
		BytesStreamOutput out = new BytesStreamOutput();
		testedSrc.writeTo(out);
		IncrementalUpdateRequest testedTarget = new IncrementalUpdateRequest();
		testedTarget.readFrom(new BytesStreamInput(out.bytes()));
		return testedTarget;
	}

}
