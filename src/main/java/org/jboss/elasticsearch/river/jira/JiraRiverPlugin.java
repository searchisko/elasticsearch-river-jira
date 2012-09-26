package org.jboss.elasticsearch.river.jira;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.rest.RestModule;
import org.elasticsearch.river.RiversModule;
import org.jboss.elasticsearch.river.jira.mgm.fullreindex.FullReindexAction;
import org.jboss.elasticsearch.river.jira.mgm.fullreindex.RestFullReindexAction;
import org.jboss.elasticsearch.river.jira.mgm.fullreindex.TransportFullReindexAction;

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

  public void onModule(RestModule module) {
    module.addRestAction(RestFullReindexAction.class);
  }

  public void onModule(ActionModule module) {
    module.registerAction(FullReindexAction.INSTANCE, TransportFullReindexAction.class);
  }
}
