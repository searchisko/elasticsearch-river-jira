package org.jboss.elasticsearch.river.jira;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.river.River;
import org.jboss.elasticsearch.river.jira.mgm.fullupdate.FullUpdateAction;
import org.jboss.elasticsearch.river.jira.mgm.fullupdate.TransportFullUpdateAction;

/**
 * JIRA River ElasticSearch Module class.
 * 
 * @author Vlastimil Elias (velias at redhat dot com)
 */
public class JiraRiverModule extends ActionModule {

  public JiraRiverModule() {
    super(true);
  }

  @Override
  protected void configure() {
    bind(River.class).to(JiraRiver.class).asEagerSingleton();
    registerAction(FullUpdateAction.INSTANCE, TransportFullUpdateAction.class);
  }
}
