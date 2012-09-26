package org.jboss.elasticsearch.river.jira;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.river.River;
import org.jboss.elasticsearch.river.jira.mgm.fullreindex.FullReindexAction;
import org.jboss.elasticsearch.river.jira.mgm.fullreindex.TransportFullReindexAction;

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
    registerAction(FullReindexAction.INSTANCE, TransportFullReindexAction.class);
  }
}
