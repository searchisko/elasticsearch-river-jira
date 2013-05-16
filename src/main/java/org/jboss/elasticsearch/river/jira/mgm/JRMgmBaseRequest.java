/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm;

import java.io.IOException;

import org.elasticsearch.action.support.nodes.NodesOperationRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Base for action Requests targeted to the Jira River.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRMgmBaseRequest<T extends NodesOperationRequest<T>> extends NodesOperationRequest<T> {

	/**
	 * Name of JIRA river to request full reindex on.
	 */
	protected String riverName;

	protected JRMgmBaseRequest() {
		super();
	}

	/**
	 * Construct request.
	 * 
	 * @param riverName for request
	 */
	public JRMgmBaseRequest(String riverName) {
		super();
		if (riverName == null)
			throw new IllegalArgumentException("riverName must be provided");
		this.riverName = riverName;
	}

	public String getRiverName() {
		return riverName;
	}

	public void setRiverName(String riverName) {
		this.riverName = riverName;
	}

	@Override
	public void readFrom(StreamInput in) throws IOException {
		super.readFrom(in);
		riverName = in.readOptionalString();
	}

	@Override
	public void writeTo(StreamOutput out) throws IOException {
		super.writeTo(out);
		out.writeOptionalString(riverName);
	}

}
