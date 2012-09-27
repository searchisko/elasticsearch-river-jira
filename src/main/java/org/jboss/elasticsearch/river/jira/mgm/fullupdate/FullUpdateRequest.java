/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import java.io.IOException;

import org.elasticsearch.action.support.nodes.NodesOperationRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

/**
 * Request for Full reindex.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateRequest extends NodesOperationRequest {

  /**
   * Name of JIRA river to request full reindex on.
   */
  private String riverName;

  /**
   * Key of JIRA project to request full reindex for. Null or Empty means full reindex for all projects.
   */
  private String projectKey;

  FullUpdateRequest() {

  }

  /**
   * Construct request.
   * 
   * @param riverName for request
   * @param projectKey for request, optional
   */
  public FullUpdateRequest(String riverName, String projectKey) {
    super();
    this.riverName = riverName;
    this.projectKey = projectKey;
  }

  public String getRiverName() {
    return riverName;
  }

  public String getProjectKey() {
    return projectKey;
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    super.readFrom(in);
    riverName = in.readOptionalString();
    projectKey = in.readOptionalString();
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    super.writeTo(out);
    out.writeOptionalString(riverName);
    out.writeOptionalString(projectKey);
  }

}
