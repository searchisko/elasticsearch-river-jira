/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import junit.framework.Assert;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.ClusterAdminClient;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link FullUpdateRequestBuilder}
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateRequestBuilderTest {

	@Test
	public void test() {

		ClusterAdminClient client = Mockito.mock(ClusterAdminClient.class);

		{
			FullUpdateRequestBuilder tested = new FullUpdateRequestBuilder(client);
			Assert.assertNull(tested.request().getRiverName());
			Assert.assertNull(tested.request().getProjectKey());

			try {
				tested.doExecute(null);
				Assert.fail("IllegalArgumentException must be thrown");
			} catch (IllegalArgumentException e) {
				// OK
			}

			Assert.assertEquals(tested, tested.setRiverName("my river"));
			Assert.assertEquals("my river", tested.request().getRiverName());
			Assert.assertNull(tested.request().getProjectKey());
			tested.doExecute(null);
		}

		{
			FullUpdateRequestBuilder tested = new FullUpdateRequestBuilder(client);
			Assert.assertEquals(tested, tested.setProjectKey("ORG"));
			Assert.assertNull(tested.request().getRiverName());
			Assert.assertEquals("ORG", tested.request().getProjectKey());
			try {
				tested.doExecute(null);
				Assert.fail("IllegalArgumentException must be thrown");
			} catch (IllegalArgumentException e) {
				// OK
			}

			Assert.assertEquals(tested, tested.setRiverName("my river"));
			Assert.assertEquals("my river", tested.request().getRiverName());
			Assert.assertEquals("ORG", tested.request().getProjectKey());

			ActionListener<FullUpdateResponse> al = new ActionListener<FullUpdateResponse>() {

				@Override
				public void onResponse(FullUpdateResponse response) {
				}

				@Override
				public void onFailure(Throwable e) {
				}
			};
			tested.doExecute(al);
			Mockito.verify(client).execute(FullUpdateAction.INSTANCE, tested.request(), al);

		}
	}

}
