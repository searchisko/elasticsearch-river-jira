JIRA River Plugin for ElasticSearch
===================================

The JIRA River Plugin allows index [Atlassian JIRA](http://www.atlassian.com/software/jira) issues and issue comments into [ElasticSearch](http://www.elasticsearch.org). It's implemented as ElasticSearch [river](http://www.elasticsearch.org/guide/reference/river/) [plugin](http://www.elasticsearch.org/guide/reference/modules/plugins.html) and uses [JIRA REST API](https://developer.atlassian.com/display/JIRADEV/JIRA+REST+APIs) to obtain issus from JIRA instance.

**This plugin is in very early alpha phase of development, not functional yet!!**

In order to install the plugin into ElasticSearch, simply run: `bin/plugin -install elasticsearch/elasticsearch-river-jira/1.0.0`.

    ----------------------------------------------------------------------------
    | JIRA Plugin    | ElasticSearch    | JIRA version | JIRA REST API version |
    ----------------------------------------------------------------------------
    | master         | 0.19 -> master   | 5+           | 2                     |
    ----------------------------------------------------------------------------

The JIRA river indexes JIRA issues and comments, and makes it searchable by ElasticSearch. JIRA is pooled periodically to detect changed issues (search operation with JQL query over `updatedDate` field) to update search index.

Creating the JIRA river can be done using:

	curl -XPUT localhost:9200/_river/my_jira_river/_meta -d '
	{
	    "type" : "jira",
	    "jira" : {
	        "urlBase"             : "https://issues.jboss.org",
	        "username"            : "jira_username",
	        "pwd"                 : "jira_user_password",
	        "timeout"             : 5000,
	        "maxIndexingThreads"  : 1,
	        "maxIssuesPerRequest" : 50,
	        "projectKeysIndexed"  : "ORG,AS7",
	        "projectKeysExcluded" : "ORG,IOP",
	        "indexUpdatePeriod"   : 5
	    },
	    "index" : {
	        "index" : "my_jira_index",
	        "type"  : "jira_issue"
	    }
	}
	'

The above lists all the options controlling the creation of a JIRA river. 
* `jira/urlBase` is required in order to connect to the JIRA REST API. It's only base URL, path to REST API is added automatically.
* `jira/username` and `jira/pwd` are optional JIRA login credentials. Anonymous JIRA access is used if not provided.
* `jira/timeout` defines timeout for http/s REST request to the JIRA [ms]. Optional parameter.
* `jira/maxIndexingThreads` defines maximal number of parallel indexing threads running for this river. Optional, default 1. This setting influences load on both JIRA and ElasticSearch servers during indexing.
* `jira/maxIssuesPerRequest` defines maximal number of updated issues requested from JIRA by one REST request. Optional, 50 used if not provided. The maximum allowable value is dictated by the JIRA configuration property `jira.search.views.default.max`. If you specify a value that is higher than this number, your request results will be truncated to this number anyway.
* `jira/projectKeysIndexed` comma separated list of JIRA project keys to be indexed. Optional, list of projects is obtained from JIRA instance if ommited (so new projects are indexed automatically).
* `jira/projectKeysExcluded` comma separated list of JIRA project keys to be excluded from indexing if list is obtained from JIRA instance (so used only if no `jira/projectKeysIndexed` is defined). Optional.
* `jira/indexUpdatePeriod` period in minutes how ofter is search index updated from JIRA instance. Optional, default 5 minutes.
* `index/index` defines name of search index where JIRA issues are stored. Parameter is optional, name of river is used if ommited. No index is created by river code. You can rely on '[Automatic Index Creation](http://www.elasticsearch.org/guide/reference/api/index_.html)' if enabled, or [create it manually](http://www.elasticsearch.org/guide/reference/api/admin-indices-create-index.html) before river creation.
* `index/type` defines document type used when issue is stored into search index. Parameter is optional, `jira_issue` is used if ommited. No type [Mapping](http://www.elasticsearch.org/guide/reference/mapping/) is created by river code. You can rely on '[Automatic Mapping Creation](http://www.elasticsearch.org/guide/reference/api/index_.html)' if enabled, or [create it manually](http://www.elasticsearch.org/guide/reference/api/admin-indices-put-mapping.html) before river creation. See later for description of issue document structure written to the search index.

Indexed JIRA issue structure
----------------------------
JIRA River writes next JSON document structure to the search index. Next table describes every default field: 

    -----------------------------------------------------------------------------------------------------------------------------
    | **index field**  | **JIRA JSON field**   | **indexed field value notes**                                                  |
    -----------------------------------------------------------------------------------------------------------------------------
    | river            | N/A                   | name of JiraRiver document was indexed over                                    |
    -----------------------------------------------------------------------------------------------------------------------------
    | project_key      | fields.project.key    |                                                                                |
    -----------------------------------------------------------------------------------------------------------------------------
    | issue_key        | key                   | Issue key from jira - also used as ID of document in the index, eg. `ORG-12`   |
    -----------------------------------------------------------------------------------------------------------------------------
    | document_url     | self                  | URL to issue in JIRA                                                           |
    -----------------------------------------------------------------------------------------------------------------------------
    | issue_type       | fields.issuetype.name | Name of issue type, eg. `Bug`, `Feature Request` etc.                          |
    -----------------------------------------------------------------------------------------------------------------------------
    | summary          | fields.summary        | Title of issue                                                                 |
    -----------------------------------------------------------------------------------------------------------------------------
    | status           | fields.status.name    | Name of issue status, eg. `Open`, `Resolved`, `Closed` etc.                    |
    -----------------------------------------------------------------------------------------------------------------------------
    | created          | fields.created        | Full timestamp format eg. `2012-08-15T03:30:02.000-0400`                       |
    -----------------------------------------------------------------------------------------------------------------------------
    | updated          | fields.updated        | Full timestamp format eg. `2012-08-15T03:30:02.000-0400`                       |
    -----------------------------------------------------------------------------------------------------------------------------
    | resolutiondate   | fields.resolutiondate | Full timestamp format eg. `2012-08-15T03:30:02.000-0400`                       |
    -----------------------------------------------------------------------------------------------------------------------------
    | description      | fields.description    | Main description text for issue. May contain JIRA WIKI syntax                  |
    -----------------------------------------------------------------------------------------------------------------------------
    | labels           | fields.labels         | Array od String values with all labels                                         |
    -----------------------------------------------------------------------------------------------------------------------------
    | reporter         | fields.reporter       | Object containing fields `name` (JIRA username), `emailAddress`, `displayName` |
    -----------------------------------------------------------------------------------------------------------------------------
    | assignee         | fields.assignee       | Object containing fields `name` (JIRA username), `emailAddress`, `displayName` |
    -----------------------------------------------------------------------------------------------------------------------------
    | fix_versions     | fields.fixVersions    | Array containing Objects with `name` field                                     |
    -----------------------------------------------------------------------------------------------------------------------------
    | components       | field.components      | Array containing Objects with `name` field                                     |
    -----------------------------------------------------------------------------------------------------------------------------

Code creating indexed document structure is inside `org.jboss.elasticsearch.river.jira.JIRA5RestIssueIndexStructureBuilder` class.

TODO List
---------
* Configurable list of JIRA issue fields ommited from the indexing (if you do not want to index fields indexed by default due index size and performance reasons)
* Configurable list of additional JIRA issue fields to be indexed (to be able to index JIRA custom fields)
* JIRA issue comments indexing
* JIRA issue delete indexing (incrementa over all issue keys list comparation with configurable checking period, or full reindex in configured period)
* Implement some mechanism to allow mapping of some issue fields (Project, Reporter, Assignee, Status, Type, ...) to common set of fields (title, link, project, authors, dates af some activity) and values (normalized set of Issue types, Statuses, authors and projects mapping) shared with other document types and/or other issue trackers to integrate them into search frontent GUI.
* Implement some mechanism which allows to initiate full reindex of all issues (calleable over REST)
* Implement some mechanism which allows to initiate full reindex of all issues for defined JIRA project (calleable over REST)
* Implement REST endpoint where you can monitor status of JIRA river (which projects are indexed by river, which projects are indexed just now, last time of indexing run for projects etc.)
* Store info about every JIRA project update run (time started, time finished, time elapsed, number of issues updated, etc.) for statistical reasons (stored into defined search index to be searchable).
* Credentials for http proxy authentication used for JIRA REST calls

License
-------

    This software is licensed under the Apache 2 license, quoted below.

    Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors as indicated by the @authors tag. 
    All rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
