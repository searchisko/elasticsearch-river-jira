/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.testtools;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

/**
 * Base class for unit tests which need to run some test against ElasticSearch cluster. You can use next pattern in your
 * unit test method to obtain testing client connected to inmemory ES cluster without any data.
 * 
 * <pre>
 * try{
 *   Client client = prepareESClientForUnitTest();
 *   
 *   ... your unit test code here
 *   
 * } finally {
 *   finalizeESClientForUnitTest();
 * }
 * 
 * </pre>
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public abstract class ESRealClientTestBase {

	private Client client;

	private Node node;

	private File tempFolder;

	/**
	 * Prepare ES inmemory client for unit test. Do not forgot to call {@link #finalizeESClientForUnitTest()} at the end
	 * of test!
	 * 
	 * @return
	 * @throws Exception
	 */
	public final Client prepareESClientForUnitTest() throws Exception {
		try {
			// For unit tests it is recommended to use local node.
			// This is to ensure that your node will never join existing cluster on the network.

			// path.data location
			tempFolder = new File("tmp");
			String tempFolderName = tempFolder.getCanonicalPath();

			if (tempFolder.exists()) {
				FileUtils.deleteDirectory(tempFolder);
			}
			if (!tempFolder.mkdir()) {
				throw new IOException("Could not create a temporary folder [" + tempFolderName + "]");
			}

			// Make sure that the index and metadata are not stored on the disk
			// path.data folder is created but we make sure it is removed after test finishes
			Settings settings = org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder()
					.put("index.store.type", "memory").put("gateway.type", "none").put("http.enabled", "false")
					.put("path.data", tempFolderName).put("node.river", "_none_").build();

			node = NodeBuilder.nodeBuilder().settings(settings).local(true).node();

			client = node.client();

			client.admin().cluster().health((new ClusterHealthRequest()).waitForYellowStatus()).actionGet();

			return client;
		} catch (Exception e) {
			finalizeESClientForUnitTest();
			throw e;
		}
	}

	public final void finalizeESClientForUnitTest() {
		if (client != null)
			client.close();
		client = null;
		if (node != null)
			node.close();
		node = null;

		if (tempFolder != null && tempFolder.exists()) {
			try {
				FileUtils.deleteDirectory(tempFolder);
			} catch (IOException e) {
				// nothing to do
			}
		}
		tempFolder = null;
	}

	/**
	 * Create index in inmemory client.
	 * 
	 * @param indexName
	 */
	public void indexCreate(String indexName) {
		client.admin().indices().create(new CreateIndexRequest(indexName)).actionGet();
		client.admin().cluster().health((new ClusterHealthRequest(indexName)).waitForYellowStatus()).actionGet();
	}

}
