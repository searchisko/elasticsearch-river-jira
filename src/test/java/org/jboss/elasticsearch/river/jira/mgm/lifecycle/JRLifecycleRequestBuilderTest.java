/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import junit.framework.Assert;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.ClusterAdminClient;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link JRLifecycleRequestBuilder}
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleRequestBuilderTest {

	@Test
	public void test() {

		ClusterAdminClient client = Mockito.mock(ClusterAdminClient.class);

		{
			JRLifecycleRequestBuilder tested = new JRLifecycleRequestBuilder(client);
			Assert.assertNull(tested.request().getRiverName());
			Assert.assertNull(tested.request().getCommand());

			try {
				tested.doExecute(null);
				Assert.fail("IllegalArgumentException must be thrown");
			} catch (IllegalArgumentException e) {
				// OK
			}

			Assert.assertEquals(tested, tested.setRiverName("my river"));
			Assert.assertEquals("my river", tested.request().getRiverName());
			Assert.assertNull(tested.request().getCommand());

			try {
				tested.doExecute(null);
				Assert.fail("IllegalArgumentException must be thrown");
			} catch (IllegalArgumentException e) {
				// OK
			}
		}

		{
			JRLifecycleRequestBuilder tested = new JRLifecycleRequestBuilder(client);
			Assert.assertEquals(tested, tested.setCommand(JRLifecycleCommand.RESTART));
			Assert.assertNull(tested.request().getRiverName());
			Assert.assertEquals(JRLifecycleCommand.RESTART, tested.request().getCommand());
			try {
				tested.doExecute(null);
				Assert.fail("IllegalArgumentException must be thrown");
			} catch (IllegalArgumentException e) {
				// OK
			}

			Assert.assertEquals(tested, tested.setRiverName("my river"));
			Assert.assertEquals("my river", tested.request().getRiverName());
			Assert.assertEquals(JRLifecycleCommand.RESTART, tested.request().getCommand());

			ActionListener<JRLifecycleResponse> al = new ActionListener<JRLifecycleResponse>() {

				@Override
				public void onResponse(JRLifecycleResponse response) {
				}

				@Override
				public void onFailure(Throwable e) {
				}
			};
			tested.doExecute(al);
			Mockito.verify(client).execute(JRLifecycleAction.INSTANCE, tested.request(), al);

		}
	}

}
