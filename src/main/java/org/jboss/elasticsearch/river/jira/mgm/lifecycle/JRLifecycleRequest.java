/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

import java.io.IOException;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseRequest;

/**
 * Request for JiraRiver lifecycle change.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JRLifecycleRequest extends JRMgmBaseRequest<JRLifecycleRequest> {

	protected JRLifecycleCommand command;

	JRLifecycleRequest() {

	}

	/**
	 * Construct request.
	 * 
	 * @param riverName for request
	 * @param command to be performed
	 */
	public JRLifecycleRequest(String riverName, JRLifecycleCommand command) {
		super(riverName);
		if (command == null)
			throw new IllegalArgumentException("command must be provided");
		this.command = command;
	}

	@Override
	public void readFrom(StreamInput in) throws IOException {
		super.readFrom(in);
		command = JRLifecycleCommand.detectById(in.readVInt());
	}

	@Override
	public void writeTo(StreamOutput out) throws IOException {
		super.writeTo(out);
		out.writeVInt(command.getId());
	}

	public JRLifecycleCommand getCommand() {
		return command;
	}

	public void setCommand(JRLifecycleCommand command) {
		this.command = command;
	}

	@Override
	public String toString() {
		return "JRLifecycleRequest [command=" + command + ", riverName=" + riverName + "]";
	}

}
