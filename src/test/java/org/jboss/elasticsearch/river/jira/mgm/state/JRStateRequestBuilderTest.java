/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.state;

import junit.framework.Assert;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.internal.InternalClusterAdminClient;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link JRStateRequestBuilder}
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRStateRequestBuilderTest {

	@Test
	public void test() {

		InternalClusterAdminClient client = Mockito.mock(InternalClusterAdminClient.class);

		{
			JRStateRequestBuilder tested = new JRStateRequestBuilder(client);
			Assert.assertNull(tested.request().getRiverName());

			try {
				tested.doExecute(null);
				Assert.fail("IllegalArgumentException must be thrown");
			} catch (IllegalArgumentException e) {
				// OK
			}

		}

		{
			JRStateRequestBuilder tested = new JRStateRequestBuilder(client);
			Assert.assertEquals(tested, tested.setRiverName("my river"));
			Assert.assertEquals("my river", tested.request().getRiverName());
			ActionListener<JRStateResponse> al = new ActionListener<JRStateResponse>() {

				@Override
				public void onResponse(JRStateResponse response) {
				}

				@Override
				public void onFailure(Throwable e) {
				}
			};
			tested.doExecute(al);
			Mockito.verify(client).execute(JRStateAction.INSTANCE, tested.request(), al);

		}
	}

}
