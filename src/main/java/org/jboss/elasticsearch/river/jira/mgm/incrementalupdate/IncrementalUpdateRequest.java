/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.incrementalupdate;

import java.io.IOException;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.jboss.elasticsearch.river.jira.Utils;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseRequest;

/**
 * Request for Incremental reindex.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class IncrementalUpdateRequest extends JRMgmBaseRequest<IncrementalUpdateRequest> {

	/**
	 * Key of JIRA project to request incremental reindex for. Null or Empty means full reindex for all projects.
	 */
	private String projectKey;

	IncrementalUpdateRequest() {

	}

	/**
	 * Construct request.
	 * 
	 * @param riverName for request
	 * @param projectKey for request, optional
	 */
	public IncrementalUpdateRequest(String riverName, String projectKey) {
		super(riverName);
		this.projectKey = projectKey;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public boolean isProjectKeyRequest() {
		return !Utils.isEmpty(projectKey);
	}

	@Override
	public void readFrom(StreamInput in) throws IOException {
		super.readFrom(in);
		projectKey = in.readOptionalString();
	}

	@Override
	public void writeTo(StreamOutput out) throws IOException {
		super.writeTo(out);
		out.writeOptionalString(projectKey);
	}

	@Override
	public String toString() {
		return "IncrementalUpdateRequest [projectKey=" + projectKey + ", riverName=" + riverName + "]";
	}

}
