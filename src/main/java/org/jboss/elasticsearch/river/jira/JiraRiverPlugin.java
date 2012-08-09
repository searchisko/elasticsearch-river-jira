package org.jboss.elasticsearch.river.jira;

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.river.RiversModule;

/**
 * JIRA River ElasticSearch Plugin class.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiverPlugin extends AbstractPlugin {

  @Inject
  public JiraRiverPlugin() {
  }

  @Override
  public String name() {
    return "river-jira";
  }

  @Override
  public String description() {
    return "River JIRA Plugin";
  }

  public void onModule(RiversModule module) {
    module.registerRiver("jira", JiraRiverModule.class);
  }
}
