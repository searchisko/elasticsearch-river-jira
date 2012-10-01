/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.fullupdate;

import java.io.IOException;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.jboss.elasticsearch.river.jira.mgm.JRMgmBaseRequest;

/**
 * Request for Full reindex.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class FullUpdateRequest extends JRMgmBaseRequest {

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
    super(riverName);
    this.projectKey = projectKey;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public void setProjectKey(String projectKey) {
    this.projectKey = projectKey;
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
    return "FullUpdateRequest [projectKey=" + projectKey + ", riverName=" + riverName + "]";
  }

}
