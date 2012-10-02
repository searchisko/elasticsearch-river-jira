package org.jboss.elasticsearch.river.jira;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.rest.RestModule;
import org.elasticsearch.river.RiversModule;
import org.jboss.elasticsearch.river.jira.mgm.fullupdate.FullUpdateAction;
import org.jboss.elasticsearch.river.jira.mgm.fullupdate.RestFullUpdateAction;
import org.jboss.elasticsearch.river.jira.mgm.fullupdate.TransportFullUpdateAction;
import org.jboss.elasticsearch.river.jira.mgm.state.JRStateAction;
import org.jboss.elasticsearch.river.jira.mgm.state.RestJRStateAction;
import org.jboss.elasticsearch.river.jira.mgm.state.TransportJRStateAction;

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
    module.addRestAction(RestFullUpdateAction.class);
    module.addRestAction(RestJRStateAction.class);
  }

  public void onModule(ActionModule module) {
    module.registerAction(FullUpdateAction.INSTANCE, TransportFullUpdateAction.class);
    module.registerAction(JRStateAction.INSTANCE, TransportJRStateAction.class);
  }
}
