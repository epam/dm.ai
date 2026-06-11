# general_ado_stg02_v1.py
#
# Stage 2: Load raw JSON → process history/status categories/sprints → export Excel.
# Run general_ado_stg01_v1.py first.

import pandas as pd
from datetime import datetime
import os
import sys
from config import (
    logger, SCRIPT_VERSION, RESOURCE_PLAN_FILE, APPROVED_STATUS_CATEGORIES, APPROVED_STANDARD_ISSUE_TYPES,
    STATUS_CATEGORIES_FILE, STATUS_CATEGORY_SHEET, STATUS_CATEGORY_LEVEL_SHEET,
    SPRINT_DOD_SHEET, SPRINTS_USAGE_SHEET, ISSUE_TYPES_CATEGORIES_SHEET,
    USE_RESOURCE_PLAN, EXPORT_EXCEL_FILE_NAME, RAW_ADO_JSON_FILE_NAME,
    USER_MAP_JSON_FILE_NAME, PATH_TO_RAW_DATA_FILE, PROPOGATE_TEAM_FROM_SUBTASKS_TO_PARENT,
    TARGET_TIMEZONE, ACCOUNT_NAME, ADO_ORG_URL, WIQL_QUERY, VALID_ISSUE_LOGIC,
    RAW_REPOS_JSON_FILE_NAME, RAW_PRS_JSON_FILE_NAME, RAW_PIPELINES_JSON_FILE_NAME,
)
from export_and_load_files import (
    load_json_file, load_raw_data_from_excel, export_to_excel,
    load_all_status_category_sheets, load_all_resource_plan_sheets,
)
from build_sprint_schedule import extract_unique_sprints, create_sprints_schedule
from fetch_workitem_detail import fetch_workitem_details
from add_ai_developers import load_ai_developers, add_is_ai_developer_column
from add_valid_issue import add_valid_issue_columns
from build_workflow_dfs import (
    build_status_category, build_lead_time, aggregate_status_category_df,
    organize_status_category_column_order, build_fact_issue_level_measures,
)
from add_status_category import (
    validate_status_categories_file,
    extract_unique_sprint_dod_combinations, extract_sprint_usage_by_project,
    validate_sprint_dod_file, validate_sprints_usage_file, validate_issue_types_categories_file,
    load_sprints_usage_config, add_sprint_dod_mapping_column,
    add_sprint_completion_flag_column, add_sprint_completion_date_column,
    extract_unique_statuses, extract_unique_combinations, create_complete_status_template,
    add_status_category_column, validate_issue_types_mapping_coverage, add_standard_issue_type_column,
)
from add_misc_columns import (
    add_status_category_flag, add_status_category_duration, add_last_ai_enabled_flag,
    add_count_entries_and_exist_to_status_category_per_issue, add_category_entry_exit_dates,
    add_date_parts, add_is_forward_category_transition_flag, add_is_productive_efforts_flag,
    calculate_lead_time_median, add_sprint_id_last_exit_from_status_category,
    add_derived_sprint_columns, propagate_ai_work_from_subtasks_to_parents,
    add_completion_date_column, calculate_completion_based_lead_time,
)
from version import version_info, load_info_sheet, extract_unique_project_keys
from process_repos import flatten_repos, flatten_commits
from process_pullrequests import flatten_pull_requests, flatten_pr_reviewers
from process_pipelines import flatten_pipeline_runs
from process_pr_analytics import (
    build_pr_staging, build_fact_cr_lead_time,
    build_cr_lead_time_report, build_cr_lead_time_report_by_weeks,
    build_user_data,
)

# --- CONFIGURATION ---
logic_expression = VALID_ISSUE_LOGIC
STATUS_COLUMN = "issue_status_history_name"

EXPORT_FILE_NAME_DWH = f"tasks_data_{datetime.now().strftime('%Y%m%d_%H%M%S')}.xlsx"


def main():
    logger.info("_______  - 🔄 Started Stage 2")

    # Step 7: Load raw work items from JSON (Stage 1 exported {'work_items': [...], 'iterations': [...]})
    raw = load_json_file(RAW_ADO_JSON_FILE_NAME)
    if not raw:
        logger.error(f"Step 07: - ❌ No data loaded from {RAW_ADO_JSON_FILE_NAME}")
        return
    work_items = raw.get('work_items', raw) if isinstance(raw, dict) else raw
    iterations = raw.get('iterations', []) if isinstance(raw, dict) else []
    logger.info(f"Step 07: - ✅ Loaded {len(work_items)} work items and {len(iterations)} iterations from {RAW_ADO_JSON_FILE_NAME}.")

    # Step 8: Load user_map from JSON
    user_map = load_json_file(USER_MAP_JSON_FILE_NAME)
    if not user_map:
        logger.error(f"Step 08: - ❌ No user_map loaded from {USER_MAP_JSON_FILE_NAME}")
        return
    logger.info(f"Step 08: - ✅ User_map loaded from {USER_MAP_JSON_FILE_NAME}.")

    # Step 9: Extract sprints and sprint schedule
    account_name, epam_project_name, data_analysis_start_date, data_analysis_end_date = (
        load_info_sheet(RESOURCE_PLAN_FILE) or (ACCOUNT_NAME, '', None, None)
    )
    sprints_df = extract_unique_sprints(work_items, iterations, account_name, epam_project_name)
    sprints_schedule_df = create_sprints_schedule(work_items, sprints_df, account_name, epam_project_name)
    logger.info("Step 09: - ✅ Sprints and sprint schedule extracted.")

    # Step 10: Fetch work item details (detailed_data, history_data)
    try:
        detailed_data, history_data = fetch_workitem_details(work_items, user_map)
        detailed_df = pd.DataFrame(detailed_data)
        history_df = pd.DataFrame(history_data)
        logger.info(f"Step 10: -    detailed_df: {len(detailed_df)} rows × {len(detailed_df.columns)} cols")
        logger.info(f"Step 10: -    history_df:  {len(history_df)} rows × {len(history_df.columns)} cols")
    except Exception as e:
        logger.error(f"Step 10: - ❌ fetch_workitem_details failed: {e}")
        return

    # Step 10.5: Extract unique project names from detailed_df
    try:
        ado_project_names = extract_unique_project_keys(detailed_df)
        if ado_project_names:
            logger.info(f"Step 10.5: - ✅ Unique ADO project(s): {ado_project_names}")
        else:
            logger.warning("Step 10.5: - ⚠️  No project names extracted.")
    except Exception as e:
        logger.error(f"Step 10.5: - ❌ Failed to extract project names: {e}")
        ado_project_names = None

    # Step 11: Version info
    try:
        version_info_df = version_info()
        logger.info("Step 11: -    Created version info")
    except Exception as e:
        logger.error(f"Step 11: - ❌ version_info failed: {e}")

    # Step 12: Add account_name / epam_project_name columns to history_df
    history_df.insert(0, 'epam_project_name', epam_project_name)
    history_df.insert(0, 'account_name', account_name)

    # Step 13: Load AI developer dictionary
    ai_developers_dict = load_ai_developers(RESOURCE_PLAN_FILE, USE_RESOURCE_PLAN)

    # Step 14: Add Is AI Developer column
    history_df = add_is_ai_developer_column(history_df, ai_developers_dict, USE_RESOURCE_PLAN, df_name="ado_history")
    if history_df is None:
        return

    # Step 15: Add valid_issue_flag
    if not logic_expression:
        logger.info("Step 15: - ⚠️  No VALID_ISSUE_LOGIC defined -> 'valid_issue_flag' set TRUE for all records.")
        history_df["valid_issue_flag"] = "TRUE"
    else:
        history_df = add_valid_issue_columns(history_df, logic_expression)

    # Step 16: Build status_category_df
    status_category_df = build_status_category(history_df)

    # Step 17: Check / create Status_Categories template
    file = STATUS_CATEGORIES_FILE
    unique_statuses = extract_unique_statuses(status_category_df, status_column=STATUS_COLUMN)
    unique_combinations_df = extract_unique_combinations(
        status_category_df, status_col=STATUS_COLUMN,
        issue_type_col="issue_type", project_col="ado_project_name",
    )
    unique_sprint_dod_combinations_df = extract_unique_sprint_dod_combinations(
        status_category_df, issue_type_col="issue_type", project_col="ado_project_name",
    )
    sprints_usage_df = extract_sprint_usage_by_project(work_items)

    if not os.path.exists(file):
        logger.warning(f"Step 17: - ❌ Status categories file '{file}' not found")
        logger.info("Step 17: - Creating template...")
        file = create_complete_status_template(
            unique_combinations_df, unique_sprint_dod_combinations_df,
            STATUS_CATEGORIES_FILE, STATUS_CATEGORY_SHEET, SPRINT_DOD_SHEET,
            ISSUE_TYPES_CATEGORIES_SHEET, sprints_usage_df, SPRINTS_USAGE_SHEET,
        )
        if not file:
            logger.error("Step 17: - ❌ Template creation failed. Exiting.")
            return
        logger.info(f"Step 17: - ⚠️  Open '{file}', fill in 'Status Category' in '{STATUS_CATEGORY_SHEET}', then rerun.")
        logger.info(f"Step 17: - ⚠️  Also fill '{SPRINT_DOD_SHEET}' and '{ISSUE_TYPES_CATEGORIES_SHEET}' as needed.")
        logger.info(f"Step 17: - ⚠️  Approved Status Category values: {APPROVED_STATUS_CATEGORIES}")
        return

    # Step 18: Validate Status Categories file
    if not validate_status_categories_file(file, STATUS_CATEGORY_SHEET, APPROVED_STATUS_CATEGORIES, "status name", unique_statuses):
        logger.info("Step 18: - ❌ Status category validation failed. Exiting.")
        return

    if not validate_sprints_usage_file(file, SPRINTS_USAGE_SHEET, status_category_df):
        logger.info("Step 18b: - ❌ Sprint usage validation failed. Exiting.")
        return

    sprint_usage_config = load_sprints_usage_config(file, SPRINTS_USAGE_SHEET) or {}

    projects_using_sprints = [p for p, v in sprint_usage_config.items() if v]
    if projects_using_sprints:
        logger.info(f"Step 18d: - 🔍 {len(projects_using_sprints)} projects use sprints — validating Sprint DOD...")
        if not validate_sprint_dod_file(file, SPRINT_DOD_SHEET, unique_statuses, status_category_df):
            logger.info("Step 18d: - ❌ Sprint DOD validation failed. Exiting.")
            return
        logger.info("Step 18d: - ✅ Sprint DOD validation passed.")
    else:
        logger.info("Step 18d: - ⏭️  No projects use sprints — skipping Sprint DOD validation.")

    if not validate_issue_types_categories_file(file, ISSUE_TYPES_CATEGORIES_SHEET, APPROVED_STANDARD_ISSUE_TYPES):
        logger.info("Step 18e: - ❌ Issue types categories validation failed. Exiting.")
        return

    if not validate_issue_types_mapping_coverage(history_df, file, ISSUE_TYPES_CATEGORIES_SHEET):
        logger.info("Step 18f: - ❌ Issue types mapping coverage validation failed. Exiting.")
        return

    history_df = add_standard_issue_type_column(history_df, file, ISSUE_TYPES_CATEGORIES_SHEET)
    if history_df is None:
        logger.error("Step 18g: - ❌ Failed to add standard_issue_type column. Exiting.")
        return

    logger.info("Step 18h: - 🔄 Rebuilding status_category_df with standard_issue_type column...")
    status_category_df = build_status_category(history_df)
    logger.info(f"Step 18h: - ✅ Rebuilt status_category_df ({len(status_category_df)} rows).")

    try:
        status_category_sheets = load_all_status_category_sheets(STATUS_CATEGORIES_FILE)
        logger.info(f"Step 18i: - ✅ Loaded {len(status_category_sheets)} sheets from Status Categories file.")
    except Exception as e:
        logger.error(f"Step 18i: - ❌ {e}")
        return

    resource_plan_sheets = {}
    if USE_RESOURCE_PLAN:
        try:
            resource_plan_sheets = load_all_resource_plan_sheets(RESOURCE_PLAN_FILE)
            logger.info(f"Step 18j: - ✅ Loaded {len(resource_plan_sheets)} sheets from Resource Plan file.")
        except Exception as e:
            logger.error(f"Step 18j: - ❌ {e}")
            return
    else:
        logger.info("Step 18j: - ⏭️  USE_RESOURCE_PLAN=False — skipping resource plan load.")

    # Step 18k: Add 'ADO Project Name' column to rp_Info sheet
    try:
        if 'Info' in resource_plan_sheets and ado_project_names:
            resource_plan_sheets['Info']['ADO Project Name'] = ado_project_names
            logger.info(f"Step 18k: - ✅ Added 'ADO Project Name' to rp_Info: {ado_project_names}")
    except Exception as e:
        logger.error(f"Step 18k: - ❌ {e}")

    try:
        mapping_df = pd.read_excel(STATUS_CATEGORIES_FILE, sheet_name=STATUS_CATEGORY_SHEET)
        logger.info(f"Step 19: - ✅ Loaded status category mapping ({len(mapping_df)} entries).")
        sprint_dod_mapping_df = None
        if projects_using_sprints:
            sprint_dod_mapping_df = pd.read_excel(STATUS_CATEGORIES_FILE, sheet_name=SPRINT_DOD_SHEET)
            logger.info(f"Step 19: - ✅ Loaded sprint_dod mapping ({len(sprint_dod_mapping_df)} entries).")
        else:
            logger.info("Step 19: - ⏭️  Skipped sprint_dod mapping (no projects use sprints).")
    except Exception as e:
        logger.error(f"Step 19: - ❌ Error reading mappings: {e}")
        return

    # Steps 20-22: Sprint DOD columns
    history_df = add_sprint_dod_mapping_column(history_df, sprint_dod_mapping_df, sprint_usage_config)
    if history_df is None:
        return
    history_df = add_sprint_completion_flag_column(history_df, sprint_usage_config)
    if history_df is None:
        return
    history_df = add_sprint_completion_date_column(history_df, sprint_usage_config)
    if history_df is None:
        return

    # Step 23: Add Status Category columns
    status_category_df, sequence_df = add_status_category_column(status_category_df, mapping_df)
    if status_category_df is None:
        return

    # Steps 24-31: Misc derived columns
    status_category_df = add_status_category_flag(
        df=status_category_df,
        category_current_col="issue_current_status_category",
        category_history_col="status_category_history_name",
        target_col="is_current_status_category_flag",
    )
    status_category_df = add_status_category_duration(
        df=status_category_df,
        groupby_cols=["key", "status_category_history_name"],
        sum_col="duration_in_status",
        target_col="duration_in_status_category",
    )
    status_category_df = add_last_ai_enabled_flag(
        df=status_category_df,
        key_col="key",
        entry_count_col="entry_count",
        ai_flag_col="is_ai_enabled_flag",
        target_col="is_ai_enabled_work_flag",
    )
    status_category_df = add_category_entry_exit_dates(
        df=status_category_df,
        issue_col='key',
        status_category_col='status_category_history_name',
        current_flag_col='is_current_status_category_flag',
        entry_date_col='event_date',
        exit_date_col='event_exit_date',
    )
    for col in ['issue_create_date', 'issue_update_date', 'issue_resolution_date',
                'last_exit_date_from_category', 'first_entry_date_to_category']:
        status_category_df = add_date_parts(status_category_df, col)
    status_category_df = add_count_entries_and_exist_to_status_category_per_issue(
        df=status_category_df,
        issue_col='key',
        status_category_col='status_category_history_name',
        current_flag_col='is_current_status_category_flag',
        date_col='event_date',
    )
    status_category_df = add_is_forward_category_transition_flag(
        df=status_category_df,
        history_seq_col='status_category_history_seq_no',
        current_seq_col='issue_current_status_category_seq_no',
        category_col='status_category_history_name',
        done_category='Done',
    )
    status_category_df = add_is_productive_efforts_flag(
        df=status_category_df,
        category_col='status_category_history_name',
        target_col='is_productive_efforts_flag',
    )

    detailed_df = detailed_df.sort_values(by=["key", "issue_create_date"], ascending=[True, True])
    history_df = history_df.sort_values(by=["key", "issue_create_date"], ascending=[True, True])

    # Step 32: Aggregate status categories
    status_category_level_agg_df = aggregate_status_category_df(status_category_df)

    if PROPOGATE_TEAM_FROM_SUBTASKS_TO_PARENT:
        status_category_level_agg_df = propagate_ai_work_from_subtasks_to_parents(status_category_level_agg_df)
    else:
        logger.info("Step 32.5: - ⚠️  PROPOGATE_TEAM_FROM_SUBTASKS_TO_PARENT disabled — skipping.")

    status_category_level_agg_df = add_sprint_id_last_exit_from_status_category(status_category_level_agg_df, sprints_df)
    status_category_level_agg_df = add_derived_sprint_columns(status_category_level_agg_df, sprints_df)
    status_category_level_agg_df = add_completion_date_column(
        status_category_level_agg_df, history_df, status_category_sheets
    )
    status_category_level_agg_df = calculate_completion_based_lead_time(status_category_level_agg_df)
    status_category_level_agg_df = organize_status_category_column_order(status_category_level_agg_df)
    status_category_level_agg_df = status_category_level_agg_df.sort_values(
        by=['key', 'first_entry_date_to_category'], ascending=[True, True]
    ).reset_index(drop=True)

    # Steps 36-38: Lead time + fact table
    lead_time_df = build_lead_time(status_category_level_agg_df)
    lead_time_df = calculate_lead_time_median(lead_time_df)
    fact_issue_level_measures_df = build_fact_issue_level_measures(
        status_category_level_agg_df,
        sprints_schedule_df,
        ado_history_df=history_df,
        sprints_df=sprints_df,
    )

    # Steps 40-42: Phase 2 — Repos / PRs / Pipelines
    repos_df = commits_df = pr_df = pr_reviewers_df = pipeline_runs_df = None

    raw_repos = load_json_file(RAW_REPOS_JSON_FILE_NAME)
    if raw_repos:
        repos_df   = flatten_repos(raw_repos.get('repos', []) if isinstance(raw_repos, dict) else [])
        commits_df = flatten_commits(raw_repos.get('commits', []) if isinstance(raw_repos, dict) else [])
        logger.info(f"Step 40: - ✅ Repos/commits loaded.")
    else:
        logger.warning(f"Step 40: - ⚠️  {RAW_REPOS_JSON_FILE_NAME} not found — skipping repos/commits sheets.")

    raw_prs = load_json_file(RAW_PRS_JSON_FILE_NAME)
    pr_staging_df = fact_cr_lead_time_df = cr_lead_time_report_df = None
    cr_lead_time_report_by_weeks_df = user_data_df = None
    if raw_prs:
        pr_df          = flatten_pull_requests(raw_prs)
        pr_reviewers_df = flatten_pr_reviewers(raw_prs)
        logger.info(f"Step 41: - ✅ Pull requests loaded.")
        # PR analytics (staging, CR lead time, user data)
        pr_staging_df = build_pr_staging(raw_prs)
        fact_cr_lead_time_df = build_fact_cr_lead_time(pr_staging_df)
        cr_lead_time_report_df = build_cr_lead_time_report(fact_cr_lead_time_df)
        cr_lead_time_report_by_weeks_df = build_cr_lead_time_report_by_weeks(fact_cr_lead_time_df)
        user_data_df = build_user_data(raw_prs)
        logger.info(f"Step 41a: - ✅ PR analytics built.")
    else:
        logger.warning(f"Step 41: - ⚠️  {RAW_PRS_JSON_FILE_NAME} not found — skipping PR sheets.")

    raw_pipelines = load_json_file(RAW_PIPELINES_JSON_FILE_NAME)
    if raw_pipelines:
        pipeline_runs_df = flatten_pipeline_runs(raw_pipelines)
        logger.info(f"Step 42: - ✅ Pipeline runs loaded.")
    else:
        logger.warning(f"Step 42: - ⚠️  {RAW_PIPELINES_JSON_FILE_NAME} not found — skipping pipeline sheet.")

    # Step 39: Export to Excel
    export_to_excel(
        detailed_df=detailed_df,
        history_df=history_df,
        status_category_df=status_category_df,
        status_category_level_agg_df=status_category_level_agg_df,
        fact_issue_level_measures_df=fact_issue_level_measures_df,
        lead_time_df=lead_time_df,
        sequence_df=sequence_df,
        sprints_schedule_df=sprints_schedule_df,
        version_info_df=version_info_df,
        status_category_sheets=status_category_sheets,
        resource_plan_sheets=resource_plan_sheets,
        repos_df=repos_df,
        commits_df=commits_df,
        pr_df=pr_df,
        pr_reviewers_df=pr_reviewers_df,
        pipeline_runs_df=pipeline_runs_df,
        pr_staging_df=pr_staging_df,
        fact_cr_lead_time_df=fact_cr_lead_time_df,
        cr_lead_time_report_df=cr_lead_time_report_df,
        cr_lead_time_report_by_weeks_df=cr_lead_time_report_by_weeks_df,
        user_data_df=user_data_df,
        output_excel_file_name=EXPORT_FILE_NAME_DWH,
        show_skip_logs=True,
    )
    logger.info(f"_______  - 📁 Exported: {EXPORT_FILE_NAME_DWH}")
    logger.info("_______  - 🎉 Stage 2 completed successfully.")
    print("=" * 90)


if __name__ == "__main__":
    main()
