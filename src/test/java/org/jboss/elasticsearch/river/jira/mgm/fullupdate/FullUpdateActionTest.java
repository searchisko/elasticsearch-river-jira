/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import org.elasticsearch.client.internal.InternalClusterAdminClient;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link FullUpdateAction}.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateActionTest {

	@Test
	public void constructor() {
		Assert.assertEquals(FullUpdateAction.NAME, FullUpdateAction.INSTANCE.name());
	}

	@Test
	public void newRequestBuilder() {
		InternalClusterAdminClient client = Mockito.mock(InternalClusterAdminClient.class);

		FullUpdateRequestBuilder rb = FullUpdateAction.INSTANCE.newRequestBuilder(client);
		Assert.assertNotNull(rb);
	}

	@Test
	public void newResponse() {
		FullUpdateResponse rb = FullUpdateAction.INSTANCE.newResponse();
		Assert.assertNotNull(rb);
	}
}
