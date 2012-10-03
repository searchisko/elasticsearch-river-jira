/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 */
package org.jboss.elasticsearch.river.jira.mgm.lifecycle;

/**
 * Enum with available JIRA river lifecycle commands.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public enum JRLifecycleCommand {

  STOP(1), RESTART(2);

  private int id;

  /**
   * @param id of command to pass inside ES cluster requests
   */
  private JRLifecycleCommand(int id) {
    this.id = id;
  }

  public static JRLifecycleCommand detectById(int id) {
    for (JRLifecycleCommand v : values()) {
      if (v.id == id)
        return v;
    }
    return null;
  }

  public int getId() {
    return id;
  }

}
