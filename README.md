JIRA River Plugin for ElasticSearch
===================================

The JIRA River Plugin allows index [Atlassian JIRA](http://www.atlassian.com/software/jira) issues and issue comments into [ElasticSearch](http://www.elasticsearch.org). It's implemented as ElasticSearch [river](http://www.elasticsearch.org/guide/reference/river/) [plugin](http://www.elasticsearch.org/guide/reference/modules/plugins.html) and uses [JIRA REST API](https://developer.atlassian.com/display/JIRADEV/JIRA+REST+APIs) to obtain issus from JIRA instance.

**This plugin is in very early alpha phase of development!!**

In order to install the plugin into ElasticSearch, simply run: `bin/plugin -install elasticsearch/elasticsearch-river-jira/1.0.0`.

    ----------------------------------------------------------------------------
    | JIRA Plugin    | ElasticSearch    | JIRA version | JIRA REST API version |
    ----------------------------------------------------------------------------
    | master         | 0.19 -> master   | 5+           | 2                     |
    ----------------------------------------------------------------------------

The JIRA river indexes JIRA issues and comments, and makes it searchable by ElasticSearch.

Creating the twitter river can be done using:

	curl -XPUT localhost:9200/_river/my_jira_river/_meta -d '
	{
	    "type" : "jira",
	    "jira" : {
	        "urlBase" : "https://issues.jboss.org",
	    },
	    "index" : {
	        "index" : "my_jira_index",
	    }
	}
	'

The above lists all the options controlling the creation of a JIRA river. 
The urlBase parameter is required in order to connect to the JIRA REST API.
The index defines name of search index where JIRA issues are stored. Parameter is optional, name of river is used if ommited.

License
-------

    This software is licensed under the Apache 2 license, quoted below.

    Copyright 2012 RedHat <http://www.redhat.com> and other contributors 

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
