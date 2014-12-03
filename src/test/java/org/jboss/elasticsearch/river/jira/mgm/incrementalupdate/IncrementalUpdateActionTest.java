/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

import org.elasticsearch.client.ClusterAdminClient;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link IncrementalUpdateAction}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IncrementalUpdateActionTest {

	@Test
	public void constructor() {
		Assert.assertEquals(IncrementalUpdateAction.NAME, IncrementalUpdateAction.INSTANCE.name());
	}

	@Test
	public void newRequestBuilder() {
		ClusterAdminClient client = Mockito.mock(ClusterAdminClient.class);

		IncrementalUpdateRequestBuilder rb = IncrementalUpdateAction.INSTANCE.newRequestBuilder(client);
		Assert.assertNotNull(rb);
	}

	@Test
	public void newResponse() {
		IncrementalUpdateResponse rb = IncrementalUpdateAction.INSTANCE.newResponse();
		Assert.assertNotNull(rb);
	}
}
