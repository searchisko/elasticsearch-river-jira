JIRA River Plugin for ElasticSearch
===================================

The JIRA River Plugin allows index [Atlassian JIRA](http://www.atlassian.com/software/jira) issues and issue comments into [ElasticSearch](http://www.elasticsearch.org). It's implemented as ElasticSearch [river](http://www.elasticsearch.org/guide/reference/river/) [plugin](http://www.elasticsearch.org/guide/reference/modules/plugins.html) and uses [JIRA REST API](https://developer.atlassian.com/display/JIRADEV/JIRA+REST+APIs) to obtain issus from JIRA instance.

In order to install the plugin into ElasticSearch, simply run: `bin/plugin -install jbossorg/elasticsearch-river-jira/1.1.0`.

    -----------------------------------------------------------------------
    | JIRA River | ElasticSearch    | JIRA | JIRA REST API | Release date |
    -----------------------------------------------------------------------
    | master     | 0.19.9           | 5+   | 2             |              |
    -----------------------------------------------------------------------
    | 1.1.0      | 0.19.9           | 5+   | 2             | 21.9.2012    |
    -----------------------------------------------------------------------
    | 1.0.0      | 0.19             | 5+   | 2             | 11.9.2012    |
    -----------------------------------------------------------------------

For cahngelogs, planned milestones/enhancements and known bugs see [github issue tracker](https://github.com/jbossorg/elasticsearch-river-jira/issues) please.

The JIRA river indexes JIRA issues and comments, and makes them searchable by ElasticSearch. JIRA is pooled periodically to detect changed issues (search operation with JQL query over `updatedDate` field) to update search index in incremental update mode. 
Periodical full update may be configured too to completely refresh search index and remove issues deleted in JIRA from it (deletes are not catched by incremental updates).

Creating the JIRA river can be done using:

	curl -XPUT localhost:9200/_river/my_jira_river/_meta -d '
	{
	    "type" : "jira",
	    "jira" : {
	        "urlBase"               : "https://issues.jboss.org",
	        "username"              : "jira_username",
	        "pwd"                   : "jira_user_password",
	        "jqlTimeZone"           : "America/New York",
	        "timeout"               : "5s",
	        "maxIssuesPerRequest"   : 50,
	        "projectKeysIndexed"    : "ORG,AS7",
	        "indexUpdatePeriod"     : "5m",
	        "indexFullUpdatePeriod" : "1h",
	        "maxIndexingThreads"    : 2
	    },
	    "index" : {
	        "index" : "my_jira_index",
	        "type"  : "jira_issue"
	    },
	    "activity_log": {
	        "index" : "jira_river_activity",
	        "type"  : "jira_river_indexupdate"
	    }
	}
	'

The example above lists all the main options controlling the creation and behaviour of a JIRA river. Full list of options with description is here:
* `jira/urlBase` is required in order to connect to the JIRA REST API. It's only base URL, path to REST API is added automatically.
* `jira/username` and `jira/pwd` are optional JIRA login credentials to access jira issues. Anonymous JIRA access is used if not provided.
* `jira/jqlTimeZone` is optional [identifier of timezone](http://docs.oracle.com/javase/6/docs/api/java/util/TimeZone.html#getTimeZone%28java.lang.String%29) used to format time values into JQL when requesting updated issues. Timezone of ElasticSearch JVM is used if not provided. JQL uses timezone of jira user who perform JQL query (so this setting must reflex [jira timezone of user](https://confluence.atlassian.com/display/JIRA/Choosing+a+Time+Zone) provided by `jira/username` parameter), default timezone of JIRA in case of Anonymous access. Incorrect setting of this value may lead to some issue updates not reflected in search index during incremental update!!
* `jira/timeout` time value, defines timeout for http/s REST request to the JIRA. Optional, 5s is default if not provided.
* `jira/maxIssuesPerRequest` defines maximal number of updated issues requested from JIRA by one REST request. Optional, 50 used if not provided. The maximum allowable value is dictated by the JIRA configuration property `jira.search.views.default.max`. If you specify a value that is higher than this number, your request results will be truncated to this number anyway.
* `jira/projectKeysIndexed` comma separated list of JIRA project keys to be indexed. Optional, list of projects is obtained from JIRA instance if ommited (so new projects are indexed automatically).
* `jira/projectKeysExcluded` comma separated list of JIRA project keys to be excluded from indexing if list is obtained from JIRA instance (so used only if no `jira/projectKeysIndexed` is defined). Optional.
* `jira/indexUpdatePeriod`  time value, defines how ofter is search index updated from JIRA instance. Optional, default 5 minutes.
* `jira/indexFullUpdatePeriod` time value, defines how ofter is search index updated from JIRA instance in full update mode. Optional, default 12 hours. You can use `0` to disable automatic full updates. Full update updates all issues in search index from JIRA, and removes issues deleted in JIRA from search index also. This brings more load to both JIRA and ElasticSearch servers, and may run for long time in case of JIRA instance with many issues. Incremental updates are performed between full updates as defined by `indexUpdatePeriod` parameter.
* `jira/maxIndexingThreads` defines maximal number of parallel indexing threads running for this river. Optional, default 1. This setting influences load on both JIRA and ElasticSearch servers during indexing. Threads are started per JIRA project update. If there is more threads allowed, then one is always dedicated for incremental updates only (so full updates do not block incremental updates for another projects).
* `index/index` defines name of search [index](http://www.elasticsearch.org/guide/appendix/glossary.html#index) where JIRA issues are stored. Parameter is optional, name of river is used if ommited. See related notes later!
* `index/type` defines [type](http://www.elasticsearch.org/guide/appendix/glossary.html#type) used when issue is stored into search index. Parameter is optional, `jira_issue` is used if ommited. See related notes later!
* `index/field_river_name`, `index/field_project_key`, `index/field_issue_key`, `index/field_jira_url` `index/fields`, `index/value_filters`, `index/jira_field_issue_document_id` can be used to change structure of indexed issue document. See 'JIRA issue index document structure' chapter.
* `index/comment_mode` defines mode of issue comments indexing: `none` - no comments indexed, `embedded` - comments indexed as array in issue document, `child` - comment indexed as separate document with [parent-child relation](http://www.elasticsearch.org/guide/reference/mapping/parent-field.html) to issue document, `standalone` - comment indexed as separate document. Setting is optional, `embedded` value is default if not provided.
* `index/comment_type` defines [type](http://www.elasticsearch.org/guide/appendix/glossary.html#type) used when issue comment is stored into search index in `child` or `standalone` mode. Parameter is optional, `jira_issue_comment` is used if ommited. See related notes later!
* `index/field_comments`, `index/comment_fields` can be used to change structure comment informations in indexed documents. See 'JIRA issue index document structure' chapter.
* `index/preprocessors` optional parameter. Defines chain of preprocessors applied to issue data readed from JIRA before stored into index. See related notes later!
* `activity_log` part defines where information about jira river index update activity are stored. If ommited then no activity informations are stored.
* `activity_log/index` defines name of index where information about jira river activity are stored.
* `activity_log/type` defines [type](http://www.elasticsearch.org/guide/appendix/glossary.html#type) used to store information about jira river activity. Parameter is optional, `jira_river_indexupdate` is used if ommited.

Time value in configuration is number representing milliseconds, but you can use next postfixes appended to the number to define units: `s` for seconds, `m` for minutes, `h` for hours, `d` for days and `w` for weeks. So for example value `5h` means five fours, `2w` means two weeks.
 
To get rid of some unwanted WARN log messages add next line to the [logging configuration file](http://www.elasticsearch.org/guide/reference/setup/configuration.html) of your ElasticSearch instance which is `config/logging.yml`:

	org.apache.commons.httpclient: ERROR

And to get rid of extensive INFO messages from index update runs use:

	org.jboss.elasticsearch.river.jira.JIRAProjectIndexer: WARN


Notes for Index and Document type mapping creation
--------------------------------------------------
Configured Search [index](http://www.elasticsearch.org/guide/appendix/glossary.html#index) is NOT explicitly created by river code. You need to [create it manually](http://www.elasticsearch.org/guide/reference/api/admin-indices-create-index.html) BEFORE river creation.

	curl -XPUT 'http://localhost:9200/my_jira_index/'

Type [Mapping](http://www.elasticsearch.org/guide/reference/mapping/) for issue is not explicitly created by river code for configured document type. The river REQUIRES [Automatic Timestamp Field](http://www.elasticsearch.org/guide/reference/mapping/timestamp-field.html) and `keyword` analyzer for `project_key` and `source` fields to be able to correctly remove issues deleted in JIRA from index during full update! So you need to create issue type mapping manually BEFORE river creation, with next content at least:

	curl -XPUT localhost:9200/my_jira_index/jira_issue/_mapping -d '
	{
	    "jira_issue" : {
	        "_timestamp" : { "enabled" : true },
	        "properties" : {
	            "project_key" : {"type" : "string", "analyzer" : "keyword"},
	            "source"      : {"type" : "string", "analyzer" : "keyword"}
	        }
	    }
	}
	'

Same apply for 'comment' mapping if you use `child` or `standalone` mode!

	curl -XPUT localhost:9200/my_jira_index/jira_issue_comment/_mapping -d '
	{
	    "jira_issue_comment" : {
	        "_timestamp" : { "enabled" : true },
	        "properties" : {
	            "project_key" : {"type" : "string", "analyzer" : "keyword"},
	            "source"      : {"type" : "string", "analyzer" : "keyword"}
	        }
	    }
	}
	'

Alternativelly you can store [mappings in ElasticSearch node configuration](http://www.elasticsearch.org/guide/reference/mapping/conf-mappings.html).

See next chapter for description of JIRA issue indexed document structure to create better mappings meeting your needs. 

If you use update activity logging then you can create index and mapping for it too:

	curl -XPUT 'http://localhost:9200/jira_river_activity/'
	curl -XPUT localhost:9200/jira_river_activity/jira_river_indexupdate/_mapping -d '
	{
	    "jira_river_indexupdate" : {
	        "properties" : {
	            "projectKey" : {"type" : "string", "analyzer" : "keyword"},
	            "updateType" : {"type" : "string", "analyzer" : "keyword"},
	            "result"     : {"type" : "string", "analyzer" : "keyword"}
	         }
	    }
	}
	'

JIRA issue index document structure
-----------------------------------
You can configure which fields from JIRA will be available in search index and under which names. See [river_configuration_default.json](https://github.com/jbossorg/elasticsearch-river-jira/blob/master/src/main/resources/templates/river_configuration_default.json) file for example of river configuration, which is used to create default configuration.

JIRA River writes JSON document with following structure to the search index for issue by default. Issue key is used as document [id](http://www.elasticsearch.org/guide/appendix/glossary.html#id) in search index by default (you can change this over `index/jira_field_issue_document_id` setting which defines field in issue data which value is used as document id. Be carefull for uniqueness of this value!).

    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | **index field** | **JIRA JSON field**   | **indexed field value notes**                                                | **river configuration** |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | source          | N/A                   | name of JiraRiver document was indexed over                                  | index/field_river_name  |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | document_url    | N/A                   | URL to show issue in JIRA GUI                                                | index/field_jira_url    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | project_key     | fields.project.key    | Key of project in JIRA                                                       | index/field_project_key |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | project_name    | fields.project.name   | Name of project in JIRA                                                      | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | issue_key       | key                   | Issue key from jira - also used as ID of document in the index, eg. `ORG-12` | index/field_issue_key   |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | issue_type      | fields.issuetype.name | Name of issue type, eg. `Bug`, `Feature Request` etc.                        | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | summary         | fields.summary        | Title of issue                                                               | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | status          | fields.status.name    | Name of issue status, eg. `Open`, `Resolved`, `Closed` etc.                  | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | created         | fields.created        | Full timestamp format eg. `2012-08-15T03:30:02.000-0400`                     | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | updated         | fields.updated        | Full timestamp format eg. `2012-08-15T03:30:02.000-0400`                     | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | resolutiondate  | fields.resolutiondate | Full timestamp format eg. `2012-08-15T03:30:02.000-0400`                     | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | description     | fields.description    | Main description text for issue. May contain JIRA WIKI syntax                | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | labels          | fields.labels         | Array od String values with all labels                                       | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | reporter        | fields.reporter       | Object with fields `username`, `email_address`, `display_name`               | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | assignee        | fields.assignee       | Object with fields `username`, `email_address`, `display_name`               | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | fix_versions    | fields.fixVersions    | Array containing Objects with `name` field                                   | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | components      | field.components      | Array containing Objects with `name` field                                   | index/fields            |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | comments        | field.comment         | Array of comments (comment indexing in `embedded` mode is used by default)   | index/field_comments    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------

JIRA River uses following structure to store comment informations in search index by default. Comment id is used as document [id](http://www.elasticsearch.org/guide/appendix/glossary.html#id) in search index in `child` or `standalone` mode.

    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | **index field** | **JIRA comment JSON field** | **indexed field value notes**                                          | **river configuration** |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | source          | N/A                   | name of JiraRiver comment was indexed over, not used in `embedded` mode      | index/field_river_name  |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | project_key     | N/A                   | Key of project in JIRA comment is for, not used in `embedded` mode           | index/field_project_key |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | issue_key       | N/A                   | key of issue comment is for, not used in `embedded` mode                     | index/field_issue_key   |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | document_url    | N/A                   | URL to show comment in JIRA GUI                                              | index/field_jira_url    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | comment_id      | id                    | Name of project in JIRA                                                      | index/comment_fields    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | comment_body    | body                  | Comment text. May contain JIRA WIKI syntax                                   | index/comment_fields    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | comment_created | created               | Full timestamp format eg. `2012-08-15T03:30:02.000-0400`                     | index/comment_fields    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | comment_updated | updated               | Full timestamp format eg. `2012-08-15T03:30:02.000-0400`                     | index/comment_fields    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | comment_author  | author                | Object with fields `username`, `email_address`, `display_name`               | index/comment_fields    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------
    | comment_updater | updateAuthor          | Object with fields `username`, `email_address`, `display_name`               | index/comment_fields    |
    ----------------------------------------------------------------------------------------------------------------------------------------------------

You can also implement and configure some preprocessors, which allows you to change/extend issue informations loaded from JIRA and store these changes/extensions to the search index.
This allows you for example value normalizations, or creation of some index fields with values agregated from more issue fields.

Framework called [structured-content-tools](https://github.com/jbossorg/structured-content-tools) is used to implement these preprocessors. Example how to configure preprocessors is visible [here](https://github.com/jbossorg/elasticsearch-river-jira/blob/master/src/main/resources/examples/river_configuration_example.json).
Some generic configurable preprocessors implementation are available in the [structured-content-tools framework](https://github.com/jbossorg/structured-content-tools).

Management REST API
-------------------
JIRA river supports next REST operations for management purposes. Note `my_jira_river` in examples is name of jira river you can call operation for.

Force full index update for all jira projects:

	curl -XPOST localhost:9200/_river/my_jira_river/_mgm/fullupdate

Force full index update for jira project with key `projectKey`:

	curl -XPOST localhost:9200/_river/my_jira_river/_mgm/fullupdate/projectKey



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
