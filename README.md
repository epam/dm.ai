# dmtools
Delivery Management Tools

[![codecov](https://codecov.io/gh/IstiN/dmtools/branch/main/graph/badge.svg)](https://codecov.io/gh/IstiN/dmtools)


# Simple Run
Download release 
* set environment variables
  
https://github.com/IstiN/dmtools/releases

Run command:
java -cp dmtools.jar com.github.istin.dmtools.job.UrlEncodedJobTrigger "$JOB_PARAMS"

API of base64 encoded JOB_PARAMS: https://github.com/IstiN/dmtools/blob/main/api_description.md

# Build jar
gradle shadowJar

# get jira token
* go to settings
* go to security
  * find api tokens
  * go to Create and manage API tokens
    * Create an API token
    * give name for example 'dm_tools'
    * Create
    * copy value
* use your base64(email:token)
* add to config 

```
JIRA_BASE_PATH = https://yourjirabaseurl.com
JIRA_LOGIN_PASS_TOKEN = base64(email:token)
JIRA_AUTH_TYPE = Bearer
SLEEP_TIME_REQUEST=500
JIRA_WAIT_BEFORE_PERFORM=true
JIRA_LOGGING_ENABLED=true
JIRA_CLEAR_CACHE=true
```

# get bitbucket token
* go to repository
* go to repository settings
* go to access tokens
* Create Repository Access Token
* Give a name and all read permissions, or write as well if scripts will be used to create / comment pull request
* Create
  * copy token
* add to config

```
BITBUCKET_TOKEN = Bearer [token]
BITBUCKET_BASE_PATH = https://api.bitbucket.org
BITBUCKET_API_VERSION = V2
BITBUCKET_WORKSPACE = Your-Workspace
BITBUCKET_REPOSITORY = Your-Repository
BITBUCKET_BRANCH = Your-Main-Branch
```
