# config.py

import os
from dotenv import load_dotenv
from datetime import datetime
import logging
import pytz
import pandas as pd

##################################################
# BUSINESS CONFIGURATION
##################################################

SCRIPT_VERSION = "V1.0"
ACCOUNT_NAME = "MyAccount"  # Fallback value - actual account name loaded from resource plan file

# WIQL (Work Item Query Language) — ADO equivalent of Jira JQL
# Examples:
#   "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.WorkItemType] = 'User Story' ORDER BY [System.ChangedDate] DESC"
#   "SELECT [System.Id] FROM WorkItems WHERE [System.IterationPath] UNDER 'MyProject\\Sprint 1'"
WIQL_QUERY = "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.WorkItemType] IN ('User Story', 'Product Backlog Item', 'Task', 'Bug', 'Feature', 'Epic', 'Issue') ORDER BY [System.ChangedDate] DESC"

TIMEZONE = "UTC"
TARGET_TIMEZONE = pytz.timezone(TIMEZONE)

# Set path to the raw_data file if different from current folder where python script is run from
# like: /mnt/c/Users/Projects/Project_A/ (if empty defaults to current directory)
PATH_TO_RAW_DATA_FILE = ""

# Below is a VALID_ISSUE_LOGIC parameter that is an expression combining rules below
# using standard logical operators (`and`, `or`, `not`) and parentheses.
#
# Available rules:
# rule_1  - IsAIDeveloper = True
# rule_2  - Issue_type = User Story
# rule_3  - Issue type = Task
# rule_4  - Issue type = Subtask  (ADO: child tasks)
# rule_5  - Issue_type = Sub-task
# rule_6  - Issue_type = Feature
# rule_7  - Issue_type = Bug
# rule_8  - No of child items = 0
# rule_9  - Parent Issue Type = Feature
# rule_10 - Parent Issue Type = User Story
# rule_11 - issue_vendor = "EPAM"
#
# If VALID_ISSUE_LOGIC left blank -> 'valid_issue_flag' will be TRUE for all records
VALID_ISSUE_LOGIC = ""

# Set USE_RESOURCE_PLAN to True if you have emails and team names of AI developers
# (in 'resource plan and info.xlsx'). If False, all rows in 'is_ai_enabled_flag' will
# be TRUE and 'assignee_team_name_at_status' will be 'unknown_team'.
USE_RESOURCE_PLAN = False

# Propagate AI-enabled work flags and team names from child items to their parent issues
PROPOGATE_TEAM_FROM_SUBTASKS_TO_PARENT = False

##################################################
# SPRINT VALIDATION CONFIGURATION
##################################################

MIN_SPRINT_DURATION_DAYS = 3    # Minimum acceptable sprint duration
MAX_SPRINT_DURATION_DAYS = 30   # Maximum acceptable sprint duration

##################################################
# PHASE 2 — REPOS / PR / PIPELINE FETCH CONFIG
##################################################

# Commits and PRs are fetched from this date onward.
# Defaults to 1 year back; override with an ISO date string e.g. "2024-01-01".
DATA_FETCH_FROM_DATE = os.getenv("DATA_FETCH_FROM_DATE", "")

# Max commits fetched per repository (0 = unlimited)
MAX_COMMITS_PER_REPO = 5000

# Max pipeline runs fetched per pipeline definition (0 = unlimited)
MAX_PIPELINE_RUNS = 1000

##################################################
# ADO FIELD MAPPINGS
##################################################
# Standard ADO fields used throughout the pipeline

ADO_FIELDS = {
    'title':           'System.Title',
    'work_item_type':  'System.WorkItemType',
    'state':           'System.State',
    'project':         'System.TeamProject',
    'iteration_path':  'System.IterationPath',
    'area_path':       'System.AreaPath',
    'assigned_to':     'System.AssignedTo',
    'created_by':      'System.CreatedBy',
    'created_date':    'System.CreatedDate',
    'changed_date':    'System.ChangedDate',
    'changed_by':      'System.ChangedBy',
    'parent':          'System.Parent',
    'tags':            'System.Tags',
    'description':     'System.Description',
    'story_points':    'Microsoft.VSTS.Scheduling.StoryPoints',
    'original_estimate': 'Microsoft.VSTS.Scheduling.OriginalEstimate',
    'completed_work':  'Microsoft.VSTS.Scheduling.CompletedWork',
    'resolved_date':   'Microsoft.VSTS.Common.ResolvedDate',
    'closed_date':     'Microsoft.VSTS.Common.ClosedDate',
}

# States treated as "resolved/done" for calculating resolution date
RESOLVED_STATES = {'resolved', 'closed', 'done', 'completed'}

APPROVED_STATUS_CATEGORIES = [
    "Waiting before requirements", "Requirements", "Waiting before Development", "Development",
    "Waiting before Testing", "Testing", "Waiting before UAT", "UAT",
    "Waiting before Release", "Release", "Done"
]

APPROVED_STANDARD_ISSUE_TYPES = [
    "Epic", "Work Item", "Bug", "Subtask"
]

##################################################
# CREDENTIALS & ENVIRONMENT VARIABLES (.env only)
##################################################

load_dotenv()

ADO_ORG_URL = os.getenv("ADO_ORG_URL")
ADO_PROJECT = os.getenv("ADO_PROJECT")
ADO_PAT = os.getenv("ADO_PAT")
ADO_TEAM = os.getenv("ADO_TEAM", f"{os.getenv('ADO_PROJECT', '')} Team")

##################################################
# FILE CONFIGURATION
##################################################

EXPORT_EXCEL_FILE_NAME = f"raw_data_{datetime.now().strftime('%Y%m%d_%H%M%S')}.xlsx"
RAW_ADO_JSON_FILE_NAME = "ado_raw_issues.json"
USER_MAP_JSON_FILE_NAME = "ado_user_map.json"
RAW_REPOS_JSON_FILE_NAME = "ado_raw_repos.json"
RAW_PRS_JSON_FILE_NAME = "ado_raw_pullrequests.json"
RAW_PIPELINES_JSON_FILE_NAME = "ado_raw_pipelines.json"
STATUS_CATEGORIES_FILE = "Status Categories prepared.xlsx"
RESOURCE_PLAN_FILE = "resource plan and info.xlsx"
STATUS_CATEGORY_SHEET = "status_category"
STATUS_CATEGORY_LEVEL_SHEET = "status_category_level"
SPRINT_DOD_SHEET = "sprint_dod"
SPRINTS_USAGE_SHEET = "sprints_usage"
ISSUE_TYPES_CATEGORIES_SHEET = "issue_types_categories"

##################################################
# LOGGING SETUP
##################################################

LOGGING_LEVEL = logging.DEBUG
logging.basicConfig(level=LOGGING_LEVEL, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)
