# build_workflow_dfs.py

import pandas as pd
import logging

# Get logger instance
logger = logging.getLogger(__name__)


def build_status_category(history_df):

    # Ensure valid_issue_flag is boolean
    history_df['valid_issue_flag'] = history_df['valid_issue_flag'].astype(str).str.upper() == "TRUE"

    status_category_df = history_df[history_df['valid_issue_flag'] == True].copy()

    date_cols = ['issue_create_date', 'issue_update_date', 'issue_resolution_date']

    # non-date values converted to NaT (null datetime) instead of causing errors
    for col in date_cols:
        status_category_df[col] = pd.to_datetime(status_category_df[col], errors='coerce')

    # Note: Date part columns (week_iso, year_week_iso, etc.) will be added later by add_date_parts() function
    # This ensures consistent naming convention throughout the pipeline

    columns_to_keep = [
        'account_name',
        'epam_project_name',
        'ado_project_name',
        'key',
        'issue_summary',
        'issue_current_status_name',
        'issue_type',
        'standard_issue_type',  # Added for standard issue type mapping
        'issue_no_of_subtasks',
        'issue_story_points',
        'issue_estimate_hours',
        'issue_actual_spent_hours',
        'parent_issue_id',
        'parent_issue_type',
        'issue_release',
        'issue_sprint',
        'issue_labels',
        'issue_create_date',
        'issue_update_date',
        'issue_resolution_date',
        'issue_closed_date',
        'issue_lead_time_for_ticket',
        'issue_status_history_name',
        'duration_in_status',
        'event_date',
        'event_exit_date',
        'entry_count',
        'is_ai_enabled_flag',
        'assignee_team_name_at_status',
        'is_issue_closed',
    ]

    status_category_df = status_category_df[columns_to_keep]
    
    # Rename assignee_team_name_at_status column
    status_category_df = status_category_df.rename(
        columns={'assignee_team_name_at_status': 'assignee_ai_team_name'}
    )

    return status_category_df


def aggregate_status_category_df(df):
    groupby_cols = ['key', 'status_category_history_name']
    if df.empty:
        logger.warning("aggregate_status_category_df: received empty dataframe; returning empty result.")
        return df.copy()
    
    # First, apply our custom team name logic before aggregation
    def get_team_name_for_group(group):
        # Check if there are any AI-enabled rows in this status category group
        ai_enabled_rows = group[group['is_ai_enabled_flag'].astype(bool) == True]
        
        if len(ai_enabled_rows) > 0:
            # If there are AI-enabled rows in this status category, get the team name from the last one
            last_ai_row = ai_enabled_rows.loc[ai_enabled_rows['entry_count'].idxmax()]
            return last_ai_row['assignee_ai_team_name']
        else:
            # If no AI-enabled rows in this status category, fall back to the last team name
            return group['assignee_ai_team_name'].iloc[-1]
    
    # Apply the custom logic to get the correct team name for each group
    team_names = df.groupby(groupby_cols).apply(get_team_name_for_group).reset_index()
    team_names.columns = ['key', 'status_category_history_name', 'assignee_ai_team_name_computed']
    
    # Merge the result back to the original DataFrame and update the original column
    df = df.merge(team_names, on=groupby_cols, how='left')
    
    # Update the original assignee_ai_team_name column with our computed values
    df['assignee_ai_team_name'] = df['assignee_ai_team_name_computed']
    
    agg_dict = {
        'account_name': 'last',
        'epam_project_name': 'last',
        'ado_project_name': 'last',
        'issue_summary': 'last',
        'issue_type': 'last',
        'standard_issue_type': 'last',  # Added for standard issue type mapping
        'issue_no_of_subtasks': 'last',
        'issue_story_points': 'last',
        'issue_labels': 'last',
        'issue_estimate_hours': 'last',
        'issue_actual_spent_hours': 'last',
        'parent_issue_id': 'last',
        'parent_issue_type': 'last',
        'issue_release': 'last',
        'issue_sprint': 'last',
        'issue_create_date': 'last',
        'issue_create_date_week_iso': 'last', 
        'issue_create_date_year_week_iso': 'last',
        'issue_create_date_year_month_iso': 'last',
        'issue_create_date_year_iso': 'last',
        'issue_update_date': 'last',
        'issue_update_date_week_iso': 'last',
        'issue_update_date_year_week_iso': 'last',
        'issue_update_date_year_month_iso': 'last',
        'issue_update_date_year_iso': 'last',
        'issue_resolution_date': 'last',
        'issue_resolution_date_week_iso': 'last',
        'issue_resolution_date_year_week_iso': 'last',
        'issue_resolution_date_year_month_iso': 'last',
        'issue_resolution_date_year_iso': 'last',
        'issue_closed_date': 'last',
        'issue_lead_time_for_ticket': 'last',
        'issue_current_status_category': 'last',
        'issue_current_status_category_seq_no': 'last',
        'status_category_history_seq_no': 'last',
        'is_current_status_category_flag': 'last',
        'duration_in_status_category': 'last',
        'is_ai_enabled_work_flag': 'last',
        'assignee_ai_team_name': 'last',
        'last_exit_date_from_category': 'last',
        'last_exit_date_from_category_week_iso': 'last',
        'last_exit_date_from_category_year_week_iso': 'last',
        'last_exit_date_from_category_year_month_iso': 'last',
        'last_exit_date_from_category_year_iso': 'last',
        'first_entry_date_to_category': 'first',
        'first_entry_date_to_category_week_iso': 'last',
        'first_entry_date_to_category_year_week_iso': 'last',
        'first_entry_date_to_category_year_month_iso': 'last',
        'first_entry_date_to_category_year_iso': 'last',
        'count_entries_to_status_category_per_issue': 'last',
        'count_exits_from_status_category_per_issue': 'last',
        'is_forward_category_transition_flag': 'last',
        'is_productive_efforts_flag': 'last',
        'is_issue_closed': 'last',
    }

    aggregated_df = df.groupby(groupby_cols).agg(agg_dict).reset_index()
    return aggregated_df


def organize_status_category_column_order(df):
    ordered_columns = [
        'account_name',
        'epam_project_name',
        'ado_project_name',
        'key',
        'issue_summary',
        'issue_type',
        'issue_no_of_subtasks',
        'issue_story_points',
        'issue_labels',
        'issue_estimate_hours',
        'issue_actual_spent_hours',
        'parent_issue_id',
        'parent_issue_type',
        'issue_release',
        'issue_sprint',
        'issue_create_date',
        'issue_create_date_week_iso',
        'issue_create_date_year_week_iso',
        'issue_create_date_year_month_iso',
        'issue_create_date_year_iso',
        'issue_update_date',
        'issue_update_date_week_iso',
        'issue_update_date_year_week_iso',
        'issue_update_date_year_month_iso',
        'issue_update_date_year_iso',
        'issue_resolution_date',
        'issue_resolution_date_week_iso',
        'issue_resolution_date_year_week_iso',
        'issue_resolution_date_year_month_iso',
        'issue_resolution_date_year_iso',
        'issue_lead_time_for_ticket',
        'issue_current_status_category',
        'issue_current_status_category_seq_no',
        'status_category_history_name',
        'status_category_history_seq_no',
        'is_current_status_category_flag',
        'duration_in_status_category',
        'is_ai_enabled_work_flag',
        'assignee_ai_team_name',
        'last_exit_date_from_category',
        'last_exit_date_from_category_week_iso',
        'last_exit_date_from_category_year_week_iso',
        'last_exit_date_from_category_year_month_iso',
        'last_exit_date_from_category_year_iso',
        'first_entry_date_to_category',
        'first_entry_date_to_category_week_iso',
        'first_entry_date_to_category_year_week_iso',
        'first_entry_date_to_category_year_month_iso',
        'first_entry_date_to_category_year_iso',
        'count_entries_to_status_category_per_issue',
        'count_exits_from_status_category_per_issue',
        'is_forward_category_transition_flag',
        'is_productive_efforts_flag',
        'sprint_id_last_exit_from_status_category',
        'sprint_name_last_exit_from_status_category',
        'is_sprint_data_available_for_category_flag',
        'is_sprint_data_available_for_issue_flag',
        'is_story_points_available_at_issue_flag',
        'issue_sprint_id_last',
        'issue_closed_date',
        'is_issue_closed',
        'standard_issue_type',  # Added for standard issue type mapping - MOVED TO END to preserve existing column order
        'issue_lead_time_completion_date',  # Added at END to preserve existing column order
    ]

    return df[ordered_columns]


def build_lead_time(df):

    columns_to_keep = [
        'account_name',
        'epam_project_name',
        'assignee_ai_team_name',
        'issue_type',
        'issue_resolution_date_year_month_iso',
        'issue_lead_time_for_ticket',
        'count_exits_from_status_category_per_issue',
    ]

    # Filter for resolved issues and AI-enabled flag
    filtered_df = df[
        (df['issue_resolution_date'].notnull()) &
        (df['is_ai_enabled_work_flag'] == 'TRUE')
    ]

    selected_df = filtered_df[columns_to_keep]

    # Rename columns
    renamed_df = selected_df.rename(
        columns={
            'account_name': 'Account',
            'epam_project_name': 'Project Code',
            'assignee_ai_team_name': 'Team',
            'issue_resolution_date_year_month_iso': 'Month',
            'issue_lead_time_for_ticket': 'Lead time (hours)',
            'count_exits_from_status_category_per_issue': '# of issues completed',
        }
    )

    return renamed_df


def build_fact_issue_level_measures(df, sprints_schedule_df=None, ado_history_df=None, sprints_df=None):
    """
    Build fact_issue_level_measures dataset from status_category_level_agg dataset.
    
    This function creates a subset of the status_category_level_agg dataset containing
    only the core issue-level measures needed for Team Velocity reporting.
    
    The dataset is deduplicated to have only ONE ROW PER ISSUE by selecting the current 
    status category (where is_current_status_category_flag = TRUE) and adding calculated
    fields for AI work, team assignment, sprint information, and sprint completion tracking.
    
    Args:
        df (DataFrame): The status_category_level_agg DataFrame
        sprints_schedule_df (DataFrame, optional): Sprints schedule DataFrame for sprint name lookup
        ado_history_df (DataFrame, optional): Jira history DataFrame for sprint completion dates
        sprints_df (DataFrame, optional): DEPRECATED - Sprints DataFrame for sprint date validation.
                                          Use sprints_schedule_df instead.
        
    Returns:
        DataFrame: Filtered dataset with unique rows per issue and additional calculated columns
    """
    
    # Deprecation warning for sprints_df parameter
    if sprints_df is not None:
        print("⚠️  WARNING: sprints_df parameter is deprecated and will be removed in future version.")
        print("   Please use sprints_schedule_df instead for all sprint operations.")
    
    # Define base columns to keep (from account_name till issue_current_status_category_seq_no)
    base_columns = [
        'account_name',
        'epam_project_name',
        'ado_project_name',
        'key',
        'issue_summary',
        'issue_type',
        'standard_issue_type',  # Added for standard issue type mapping
        'issue_no_of_subtasks',
        'issue_story_points',
        'issue_labels',
        'issue_estimate_hours',
        'issue_actual_spent_hours',
        'parent_issue_id',
        'parent_issue_type',
        'issue_release',
        'issue_sprint',
        'issue_create_date',
        'issue_create_date_week_iso',
        'issue_create_date_year_week_iso',
        'issue_create_date_year_month_iso',
        'issue_create_date_year_iso',
        'issue_update_date',
        'issue_update_date_week_iso',
        'issue_update_date_year_week_iso',
        'issue_update_date_year_month_iso',
        'issue_update_date_year_iso',
        'issue_resolution_date',
        'issue_resolution_date_week_iso',
        'issue_resolution_date_year_week_iso',
        'issue_resolution_date_year_month_iso',
        'issue_resolution_date_year_iso',
        'issue_lead_time_for_ticket',
        'issue_current_status_category',
        'issue_current_status_category_seq_no',
        'issue_closed_date',
        'is_issue_closed'
    ]
    
    # Additional columns from original dataset (Phase 2)
    additional_columns = [
        'is_sprint_data_available_for_issue_flag',
        'is_story_points_available_at_issue_flag',
        'issue_sprint_id_last'
    ]
    
    # Calculated columns added during processing (Phase 3) 
    calculated_columns = [
        'is_ai_enabled_work_at_issue_flag',
        'issue_assignee_team_name', 
        'issue_sprint_name_last'
    ]
    
    # Sprint enhancement columns (Phase 4) - Added conditionally based on available data
    sprint_enhancement_columns = [
        'issue_sprint_id_created_calculated',
        'issue_sprint_name_created_calculated',
        'issue_sprint_id_closed_calculated',
        'issue_sprint_name_closed_calculated',
        'issue_sprint_completed_event_date_according_dod',
        'is_issue_completed_in_issue_sprint_id_last',
        'issue_lead_time_completion_date'  # Added at ABSOLUTE END to preserve existing column order
    ]
    
    # Create a copy for processing
    fact_df = df.copy()
    
    # Step 1: Enhanced deduplication to ensure exactly one row per issue
    if 'is_current_status_category_flag' in fact_df.columns:
        # Convert to boolean if it's string
        if fact_df['is_current_status_category_flag'].dtype == 'object':
            fact_df['is_current_status_category_flag'] = fact_df['is_current_status_category_flag'].astype(str).str.upper() == 'TRUE'
        
        # Enhanced deduplication logic to handle edge cases
        current_status_rows = []
        
        for issue_key in fact_df['key'].unique():
            issue_data = fact_df[fact_df['key'] == issue_key].copy()
            
            # Check current status flags for this issue
            current_flags = issue_data[issue_data['is_current_status_category_flag'] == True]
            
            if len(current_flags) == 1:
                # Perfect case: exactly one current flag
                selected_row = current_flags.iloc[0]
            elif len(current_flags) > 1:
                # ISSUE #8 FIX: Multiple current flags detected
                logger.warning(f"Step 34: - ⚠️  Issue {issue_key}: Multiple current status flags detected ({len(current_flags)} flags), selecting highest seq_no")
                selected_row = current_flags.loc[current_flags['status_category_history_seq_no'].idxmax()]
            else:
                # No current flags: fallback to highest seq_no overall
                logger.warning(f"Step 34: - ⚠️  Issue {issue_key}: No current status flag found, using highest seq_no fallback")
                selected_row = issue_data.loc[issue_data['status_category_history_seq_no'].idxmax()]
            
            current_status_rows.append(selected_row)
        
        current_status_df = pd.DataFrame(current_status_rows).reset_index(drop=True)
        logger.info(f"Step 34: - ✅ Enhanced deduplication: filtered from {len(fact_df)} rows to {len(current_status_df)} unique issues")
    else:
        logger.warning("Step 34: - ⚠️  Column 'is_current_status_category_flag' not found, using fallback deduplication")
        # Fallback: take the last status category per issue (highest seq_no)
        current_status_df = fact_df.loc[fact_df.groupby('key')['status_category_history_seq_no'].idxmax()].copy()
        logger.info(f"Step 34: - ℹ️  Fallback deduplication: filtered from {len(fact_df)} rows to {len(current_status_df)} unique issues")
    
    # Step 2: Calculate additional columns (Phase 2)
    logger.info("Step 34: - 🧮 Calculating additional columns for fact_issue_level_measures...")
    
    # Step 2a: Calculate is_ai_enabled_work_at_issue_flag
    current_status_df = _calculate_ai_enabled_flag_per_issue(current_status_df, fact_df)
    
    # Step 2b: Calculate issue_assignee_team_name
    current_status_df = _calculate_assignee_team_per_issue(current_status_df, fact_df)
    
    # Step 2c: Calculate issue_sprint_name_last
    if sprints_schedule_df is not None:
        current_status_df = _calculate_sprint_name_last(current_status_df, sprints_schedule_df)
    else:
        logger.warning("Step 34: - ⚠️  sprints_schedule_df not provided, issue_sprint_name_last will be set to None")
        current_status_df['issue_sprint_name_last'] = None
    
    # Step 3: CRITICAL validation - ensure exactly one row per issue
    duplicate_issues = current_status_df['key'].duplicated().sum()
    if duplicate_issues > 0:
        logger.error(f"Step 34: - 🚨 CRITICAL: Found {duplicate_issues} duplicate issues after enhanced deduplication!")
        logger.error("Step 34: - 🚨 This indicates a serious data quality issue. Applying emergency deduplication.")
        
        # Show which issues are duplicated for debugging
        duplicated_keys = current_status_df[current_status_df['key'].duplicated(keep=False)]['key'].unique()
        logger.error(f"Step 34: - 🚨 Duplicated issues: {duplicated_keys.tolist()}")
        
        # Emergency deduplication: keep first occurrence
        current_status_df = current_status_df.drop_duplicates(subset=['key'], keep='first')
        logger.warning(f"Step 34: - ⚡ Emergency deduplication applied: {len(current_status_df)} unique issues remaining")
    else:
        logger.info(f"Step 34: - ✅ Uniqueness validation passed: {len(current_status_df)} unique issues, 0 duplicates")
    
    # Step 4: Prepare final column list with explicit ordering
    core_columns = base_columns + additional_columns + calculated_columns
    
    # Step 5: Ensure all required core columns exist
    available_core_columns = [col for col in core_columns if col in current_status_df.columns]
    
    # Check for missing core columns (excluding optional completion date column)
    missing_columns = set(core_columns) - set(available_core_columns)
    if missing_columns:
        logger.warning(f"Step 34: - ⚠️  Missing core columns in fact_issue_level_measures: {missing_columns}")
    
    #include completion date column if it exists in input data
    if 'issue_lead_time_completion_date' in current_status_df.columns:
        available_core_columns.append('issue_lead_time_completion_date')
        # logger.info("Step 34: - ✅ Found issue_lead_time_completion_date in input data, including in fact table")
    else:
        logger.warning("Step 34: - ⚠️  issue_lead_time_completion_date not found in input data")
    
    # Step 6: Select only required core columns (sprint enhancement columns added later)
    fact_df = current_status_df[available_core_columns].copy()
    
    # Step 7: Final verification
    final_unique_issues = fact_df['key'].nunique()
    total_rows = len(fact_df)
    
    if final_unique_issues != total_rows:
        logger.error(f"Step 34: - ❌ Data integrity issue: {total_rows} rows but only {final_unique_issues} unique issues!")
    else:
        logger.info(f"Step 34: - ✅ fact_issue_level_measures dataset created successfully:")
        logger.info(f"         - ℹ️  {total_rows} unique issues")
        logger.info(f"         - ℹ️  {len(available_core_columns)} core columns (including {len(calculated_columns)} new calculated columns)")
        logger.info(f"         - ℹ️  One row per issue key (deduplication successful)")
        logger.info(f"         - ℹ️  Sprint enhancement columns will be added next if data is available")
    
    # Step 34d: Add calculated sprint columns based on issue creation date (NEW FEATURE)
    if sprints_schedule_df is not None:
        logger.info("Step 34d: - 🚀 Adding sprint created calculated columns...")
        fact_df = _add_sprint_created_columns(fact_df, sprints_schedule_df)
        if fact_df is None:
            logger.error("Step 34d: - ❌ Failed to add sprint created calculated columns")
            return None
    else:
        logger.warning("Step 34d: - ⚠️  sprints_schedule_df not provided, skipping sprint created calculated columns")
    
    # Step 35: Add sprint completion tracking columns (NEW FEATURE) - MOVED BEFORE SPRINT CLOSED COLUMNS
    if ado_history_df is not None and sprints_schedule_df is not None:
        logger.info("Step 35: - 🚀 Adding sprint completion tracking columns...")
        
        # Step 35a: Add sprint completion date column
        fact_df = add_sprint_completion_date_to_fact_measures(fact_df, ado_history_df)
        if fact_df is None:
            logger.error("Step 35a: - ❌ Failed to add sprint completion date column")
            return None
        
        # Step 35b: Add sprint completion flag column  
        fact_df = add_sprint_completion_flag_to_fact_measures(fact_df, sprints_schedule_df)
        if fact_df is None:
            logger.error("Step 35b: - ❌ Failed to add sprint completion flag column")
            return None
        
        # Step 35c: Validate the new sprint completion data
        if not validate_fact_measures_sprint_data(fact_df, ado_history_df, sprints_schedule_df):
            logger.warning("Step 35c: - ⚠️ Sprint completion data validation found issues, but continuing...")
        
        # Update final statistics
        final_columns = len(fact_df.columns)
        logger.info(f"Step 35: - ✅ Sprint completion tracking completed:")
        logger.info(f"         - ℹ️  {final_columns} total columns (added 2 new sprint completion columns)")
        logger.info(f"         - ℹ️  {len(fact_df)} unique issues maintained")
    else:
        if ado_history_df is None:
            logger.warning("Step 35: - ⚠️ ado_history_df not provided, skipping sprint completion columns")
        if sprints_schedule_df is None:
            logger.warning("Step 35: - ⚠️ sprints_schedule_df not provided, skipping sprint completion columns")
    
    # Step 34e: Add calculated sprint columns based on sprint completion date according to DOD (MOVED AFTER SPRINT COMPLETION COLUMNS)
    if sprints_schedule_df is not None:
        logger.info("Step 34e: - 🚀 Adding sprint closed calculated columns...")
        fact_df = _add_sprint_closed_columns(fact_df, sprints_schedule_df)
        if fact_df is None:
            logger.error("Step 34e: - ❌ Failed to add sprint closed calculated columns")
            return None
    else:
        logger.warning("Step 34e: - ⚠️  sprints_schedule_df not provided, skipping sprint closed calculated columns")
    
    # Step 8: Final column ordering to ensure consistency across all executions
    fact_df = _organize_fact_measures_column_order(fact_df, base_columns, additional_columns, calculated_columns, sprint_enhancement_columns)
    
    return fact_df


def _organize_fact_measures_column_order(fact_df, base_columns, additional_columns, calculated_columns, sprint_enhancement_columns):
    """
    Organize fact_issue_level_measures columns in consistent order.
    
    Ensures columns appear in this exact order:
    1. Base columns (38 core issue fields)
    2. Additional columns (3 original calculated fields) 
    3. Calculated columns (3 inline calculated fields)
    4. Sprint enhancement columns (6 sprint-related fields, if present)
    
    Args:
        fact_df: The fact dataframe to reorder
        base_columns: List of base column names
        additional_columns: List of additional column names  
        calculated_columns: List of calculated column names
        sprint_enhancement_columns: List of sprint enhancement column names
        
    Returns:
        DataFrame with columns in consistent order
    """
    # Build expected column order
    expected_order = base_columns + additional_columns + calculated_columns + sprint_enhancement_columns
    
    # Select only columns that actually exist in the dataframe
    available_columns = [col for col in expected_order if col in fact_df.columns]
    
    # Check for any unexpected columns (not in our expected lists)
    unexpected_columns = [col for col in fact_df.columns if col not in expected_order]
    
    if unexpected_columns:
        logger.warning(f"Step 34: - ⚠️  Found unexpected columns in fact_measures: {unexpected_columns}")
        # Add unexpected columns at the end to avoid data loss
        available_columns.extend(unexpected_columns)
    
    # Reorder dataframe
    reordered_df = fact_df[available_columns].copy()
    
    logger.info(f"Step 34: - ✅ Final column organization completed:")
    logger.info(f"         - ℹ️  {len(available_columns)} total columns in consistent order")
    logger.info(f"         - ℹ️  Base: {len([c for c in base_columns if c in fact_df.columns])}, Additional: {len([c for c in additional_columns if c in fact_df.columns])}, Calculated: {len([c for c in calculated_columns if c in fact_df.columns])}, Sprint: {len([c for c in sprint_enhancement_columns if c in fact_df.columns])}")
    
    return reordered_df


def _calculate_ai_enabled_flag_per_issue(current_status_df, full_df):
    """
    Calculate is_ai_enabled_work_at_issue_flag for each unique issue.
    Logic: If ANY row for an issue has is_ai_enabled_work_flag = TRUE, then issue flag = TRUE
    """
    logger.info("Step 34: - 🧮 Calculating is_ai_enabled_work_at_issue_flag...")
    
    if 'is_ai_enabled_work_flag' not in full_df.columns:
        logger.warning("Step 34: - ⚠️  Column 'is_ai_enabled_work_flag' not found, setting is_ai_enabled_work_at_issue_flag to FALSE")
        current_status_df['is_ai_enabled_work_at_issue_flag'] = 'FALSE'
        return current_status_df
    
    # Convert to boolean if string
    ai_flag_df = full_df.copy()
    if ai_flag_df['is_ai_enabled_work_flag'].dtype == 'object':
        ai_flag_df['is_ai_enabled_work_flag'] = ai_flag_df['is_ai_enabled_work_flag'].astype(str).str.upper() == 'TRUE'
    
    # Group by issue key and check if ANY row has AI flag = TRUE
    issue_ai_summary = ai_flag_df.groupby('key')['is_ai_enabled_work_flag'].any().reset_index()
    issue_ai_summary['is_ai_enabled_work_at_issue_flag'] = issue_ai_summary['is_ai_enabled_work_flag'].map({True: 'TRUE', False: 'FALSE'})
    
    # Merge back to current status df
    current_status_df = current_status_df.merge(
        issue_ai_summary[['key', 'is_ai_enabled_work_at_issue_flag']], 
        on='key', 
        how='left'
    )
    
    # Handle any unmatched cases
    unmatched = current_status_df['is_ai_enabled_work_at_issue_flag'].isna().sum()
    if unmatched > 0:
        logger.warning(f"Step 34: - ⚠️  Found {unmatched} issues without AI flag data. Setting to FALSE as best fallback.")
        current_status_df['is_ai_enabled_work_at_issue_flag'].fillna('FALSE', inplace=True)
    
    ai_enabled_count = (current_status_df['is_ai_enabled_work_at_issue_flag'] == 'TRUE').sum()
    logger.info(f"Step 34: - 📊 {ai_enabled_count}/{len(current_status_df)} issues marked as AI-enabled")
    
    return current_status_df


def _calculate_assignee_team_per_issue(current_status_df, full_df):
    """
    Calculate issue_assignee_team_name for each unique issue.
    Complex logic to handle multiple teams per issue with priority rules.
    """
    logger.info("Step 34: - 🧮 Calculating issue_assignee_team_name...")
    
    if 'assignee_ai_team_name' not in full_df.columns:
        logger.warning("Step 34: - ⚠️  Column 'assignee_ai_team_name' not found, setting issue_assignee_team_name to 'unknown_team'")
        current_status_df['issue_assignee_team_name'] = 'unknown_team'
        return current_status_df
    
    team_assignments = []
    issues_with_warnings = []
    
    for issue_key in current_status_df['key'].unique():
        issue_rows = full_df[full_df['key'] == issue_key].copy()
        teams = issue_rows['assignee_ai_team_name'].dropna().unique()
        teams = [team for team in teams if team != '' and str(team).lower() != 'nan']
        
        if len(teams) == 0:
            # No team data
            selected_team = 'unknown_team'
        elif len(teams) == 1:
            # Only one team - simple case
            selected_team = teams[0]
        else:
            # Multiple teams - apply priority logic
            non_unknown_teams = [team for team in teams if team != 'unknown_team']
            
            if len(non_unknown_teams) == 0:
                # Only unknown teams
                selected_team = 'unknown_team'
            elif len(non_unknown_teams) == 1:
                # One non-unknown team
                selected_team = non_unknown_teams[0]
            else:
                # Multiple non-unknown teams - use current status or most recent
                current_status_rows = issue_rows[issue_rows['is_current_status_category_flag'] == 'TRUE']
                
                if len(current_status_rows) > 0:
                    current_teams = current_status_rows['assignee_ai_team_name'].dropna().unique()
                    current_non_unknown = [team for team in current_teams if team != 'unknown_team' and team != '']
                    
                    if len(current_non_unknown) > 0:
                        selected_team = current_non_unknown[0]  # Take first if multiple
                    else:
                        # No current non-unknown team, use most recent
                        selected_team = _get_most_recent_team(issue_rows, non_unknown_teams)
                else:
                    # No current status rows, use most recent
                    selected_team = _get_most_recent_team(issue_rows, non_unknown_teams)
                    
                # Log complex cases for monitoring
                if len(non_unknown_teams) > 1:
                    issues_with_warnings.append({
                        'issue_key': issue_key,
                        'teams_found': non_unknown_teams,
                        'selected_team': selected_team
                    })
        
        team_assignments.append({'key': issue_key, 'issue_assignee_team_name': selected_team})
    
    # Convert to DataFrame and merge
    team_df = pd.DataFrame(team_assignments)
    current_status_df = current_status_df.merge(team_df, on='key', how='left')
    
    # Handle any unmatched cases
    unmatched = current_status_df['issue_assignee_team_name'].isna().sum()
    if unmatched > 0:
        logger.warning(f"Step 34: - ⚠️  Found {unmatched} issues without team data. Setting to 'unknown_team' as fallback.")
        current_status_df['issue_assignee_team_name'].fillna('unknown_team', inplace=True)
    
    # Log warnings for complex team assignment cases
    if issues_with_warnings:
        logger.info(f"Step 34: - ℹ️  Found {len(issues_with_warnings)} jira tickets that multiple teams worked on:")
        for warning in issues_with_warnings[:5]:  # Show first 5 examples
            logger.info(f"         - ℹ️  {warning['issue_key']}: teams {warning['teams_found']} → selected '{warning['selected_team']}'")
        if len(issues_with_warnings) > 5:
            logger.info(f"         - ℹ️  ... and {len(issues_with_warnings) - 5} more similar cases")
    
    # Summary stats
    team_counts = current_status_df['issue_assignee_team_name'].value_counts()
    logger.info(f"Step 34: - 📋 Team distribution: {dict(team_counts.head())}")
    
    return current_status_df


def _get_most_recent_team(issue_rows, non_unknown_teams):
    """Helper function to get team from most recent exit date"""
    if 'last_exit_date_from_category' in issue_rows.columns:
        # Sort by exit date and get most recent non-unknown team
        sorted_rows = issue_rows.sort_values('last_exit_date_from_category', ascending=False, na_position='last')
        for _, row in sorted_rows.iterrows():
            if row['assignee_ai_team_name'] in non_unknown_teams:
                return row['assignee_ai_team_name']
    
    # Fallback: return first non-unknown team
    return non_unknown_teams[0] if non_unknown_teams else 'unknown_team'


def _calculate_sprint_name_last(current_status_df, sprints_schedule_df):
    """
    Calculate issue_sprint_name_last by joining issue_sprint_id_last with sprints_schedule.
    """
    logger.info("Step 34: - 🧮 Calculating issue_sprint_name_last...")
    
    if 'issue_sprint_id_last' not in current_status_df.columns:
        logger.warning("Step 34: - ⚠️  Column 'issue_sprint_id_last' not found, setting issue_sprint_name_last to None")
        current_status_df['issue_sprint_name_last'] = None
        return current_status_df
    
    if sprints_schedule_df is None or sprints_schedule_df.empty:
        logger.warning("Step 34: - ⚠️  sprints_schedule_df is empty, setting issue_sprint_name_last to None")
        current_status_df['issue_sprint_name_last'] = None
        return current_status_df
    
    # Check if sprints_schedule has required columns
    required_cols = ['sprint_id', 'sprint_name']
    available_cols = [col for col in required_cols if col in sprints_schedule_df.columns]
    
    if len(available_cols) != len(required_cols):
        logger.warning(f"Step 34: - ⚠️  sprints_schedule_df missing columns {set(required_cols) - set(available_cols)}")
        current_status_df['issue_sprint_name_last'] = None
        return current_status_df
    
    # Get unique sprint mappings
    sprint_mapping = sprints_schedule_df[['sprint_id', 'sprint_name']].drop_duplicates()
    
    # Merge to get sprint names
    current_status_df = current_status_df.merge(
        sprint_mapping, 
        left_on='issue_sprint_id_last', 
        right_on='sprint_id', 
        how='left'
    )
    
    # Rename and clean up
    if 'sprint_name' in current_status_df.columns:
        current_status_df['issue_sprint_name_last'] = current_status_df['sprint_name']
        current_status_df.drop('sprint_name', axis=1, inplace=True)
    
    if 'sprint_id' in current_status_df.columns:
        current_status_df.drop('sprint_id', axis=1, inplace=True)
    
    # Summary stats
    matched_sprints = current_status_df['issue_sprint_name_last'].notna().sum()
    logger.info(f"Step 34: - 📊 {matched_sprints}/{len(current_status_df)} issues matched to sprint names")
    
    return current_status_df


def add_sprint_completion_date_to_fact_measures(fact_measures_df, ado_history_df):
    """
    Add issue_sprint_completed_event_date_according_dod column to fact_issue_level_measures.
    
    Maps the first occurrence of sprint completion date from jira_history per issue key.
    
    Args:
        fact_measures_df (DataFrame): fact_issue_level_measures DataFrame
        ado_history_df (DataFrame): jira_history DataFrame with completion dates
        
    Returns:
        DataFrame: Updated fact_measures_df with new column
    """
    logger.info("Step 35a: - 🔄 Adding issue_sprint_completed_event_date_according_dod column...")
    
    try:
        # Validate input DataFrames
        if fact_measures_df is None or fact_measures_df.empty:
            logger.error("Step 35a: - ❌ fact_measures_df is None or empty")
            return None
            
        if ado_history_df is None or ado_history_df.empty:
            logger.error("Step 35a: - ❌ ado_history_df is None or empty")
            return None
        
        # Check required columns exist
        required_columns = ['key', 'issue_sprint_completed_event_date_according_dod']
        missing_cols = [col for col in required_columns if col not in ado_history_df.columns]
        if missing_cols:
            logger.error(f"Step 35a: - ❌ Missing required columns in jira_history: {missing_cols}")
            return None
        
        if 'key' not in fact_measures_df.columns:
            logger.error("Step 35a: - ❌ Missing 'key' column in fact_measures_df")
            return None
        
        # Create a copy to avoid modifying original
        result_df = fact_measures_df.copy()
        
        # Get first occurrence of completion date per issue key from jira_history
        completion_dates = ado_history_df.groupby('key')['issue_sprint_completed_event_date_according_dod'].first().reset_index()
        completion_dates.rename(columns={'issue_sprint_completed_event_date_according_dod': 'issue_sprint_completed_event_date_according_dod'}, inplace=True)
        
        # Track statistics before merge
        total_fact_issues = len(result_df)
        jira_history_issues_with_dates = completion_dates['issue_sprint_completed_event_date_according_dod'].notna().sum()
        jira_history_total_issues = len(completion_dates)
        
        # Merge with fact_measures_df
        result_df = result_df.merge(completion_dates, on='key', how='left')
        
        # Replace NaN with 'Not applicable'
        result_df['issue_sprint_completed_event_date_according_dod'] = result_df['issue_sprint_completed_event_date_according_dod'].fillna('Not applicable')
        
        # Final statistics
        mapped_dates = (result_df['issue_sprint_completed_event_date_according_dod'] != 'Not applicable').sum()
        not_applicable_count = (result_df['issue_sprint_completed_event_date_according_dod'] == 'Not applicable').sum()
        
        logger.info(f"Step 35a: - ✅ Sprint completion date column added successfully")
        logger.info(f"Step 35a: - 📊 {mapped_dates}/{total_fact_issues} issues have completion dates")
        logger.info(f"Step 35a: - 📊 {not_applicable_count}/{total_fact_issues} issues marked as 'Not applicable'")
        logger.info(f"Step 35a: - 📊 Source data: {jira_history_issues_with_dates}/{jira_history_total_issues} jira_history issues had dates")
        
        return result_df
        
    except Exception as e:
        logger.error(f"Step 35a: - ❌ Error adding sprint completion date column: {str(e)}")
        return None


def add_sprint_completion_flag_to_fact_measures(fact_measures_df, sprints_schedule_df):
    """
    Add is_issue_completed_in_issue_sprint_id_last column to fact_issue_level_measures.
    
    Creates TRUE/FALSE flag based on whether completion date falls within sprint boundaries.
    
    Args:
        fact_measures_df (DataFrame): fact_measures_df with completion dates
        sprints_schedule_df (DataFrame): sprints_schedule DataFrame with sprint date ranges
        
    Returns:
        DataFrame: Updated fact_measures_df with new flag column
    """
    logger.info("Step 35b: - 🔄 Adding is_issue_completed_in_issue_sprint_id_last column...")
    
    try:
        # Validate input DataFrames
        if fact_measures_df is None or fact_measures_df.empty:
            logger.error("Step 35b: - ❌ fact_measures_df is None or empty")
            return None
            
        if sprints_schedule_df is None or sprints_schedule_df.empty:
            logger.error("Step 35b: - ❌ sprints_schedule_df is None or empty")
            return None
        
        # Check required columns
        required_fact_cols = ['key', 'issue_sprint_id_last', 'issue_sprint_completed_event_date_according_dod']
        missing_fact_cols = [col for col in required_fact_cols if col not in fact_measures_df.columns]
        if missing_fact_cols:
            logger.error(f"Step 35b: - ❌ Missing required columns in fact_measures_df: {missing_fact_cols}")
            return None
        
        required_sprint_cols = ['sprint_id', 'sprint_start_date', 'sprint_end_date']
        missing_sprint_cols = [col for col in required_sprint_cols if col not in sprints_schedule_df.columns]
        if missing_sprint_cols:
            logger.error(f"Step 35b: - ❌ Missing required columns in sprints_schedule_df: {missing_sprint_cols}")
            return None
        
        # Create a copy to avoid modifying original
        result_df = fact_measures_df.copy()
        
        # Convert sprint_id to string for consistent merging
        sprints_clean = sprints_schedule_df.copy()
        sprints_clean['sprint_id'] = sprints_clean['sprint_id'].astype(str)
        result_df['issue_sprint_id_last'] = result_df['issue_sprint_id_last'].astype(str)
        
        # Merge with sprint data to get sprint date ranges
        result_df = result_df.merge(
            sprints_clean[['sprint_id', 'sprint_start_date', 'sprint_end_date']], 
            left_on='issue_sprint_id_last', 
            right_on='sprint_id', 
            how='left'
        )
        
        # Initialize the flag column
        result_df['is_issue_completed_in_issue_sprint_id_last'] = 'Not applicable'
        
        # Create mask for valid data (all required fields are not null/not 'Not applicable')
        valid_completion_dates = (result_df['issue_sprint_completed_event_date_according_dod'] != 'Not applicable') & \
                                (result_df['issue_sprint_completed_event_date_according_dod'].notna())
        valid_sprint_dates = result_df['sprint_start_date'].notna() & result_df['sprint_end_date'].notna()
        valid_sprint_ids = result_df['issue_sprint_id_last'].notna() & (result_df['issue_sprint_id_last'] != '')
        
        valid_mask = valid_completion_dates & valid_sprint_dates & valid_sprint_ids
        
        # Process only valid rows
        if valid_mask.sum() > 0:
            # Convert completion dates to datetime for comparison
            valid_rows = result_df[valid_mask].copy()
            valid_rows['completion_date_dt'] = pd.to_datetime(valid_rows['issue_sprint_completed_event_date_according_dod'], errors='coerce')
            valid_rows['sprint_start_dt'] = pd.to_datetime(valid_rows['sprint_start_date'], errors='coerce')
            valid_rows['sprint_end_dt'] = pd.to_datetime(valid_rows['sprint_end_date'], errors='coerce')
            
            # Apply inclusive date range check: sprint_start_date <= completion_date <= sprint_end_date
            within_sprint = (valid_rows['completion_date_dt'] >= valid_rows['sprint_start_dt']) & \
                          (valid_rows['completion_date_dt'] <= valid_rows['sprint_end_dt'])
            
            # Update the flag for valid rows
            result_df.loc[valid_mask, 'is_issue_completed_in_issue_sprint_id_last'] = within_sprint.map({True: 'TRUE', False: 'FALSE'})
        
        # Clean up temporary columns
        columns_to_drop = ['sprint_id', 'sprint_start_date', 'sprint_end_date']
        for col in columns_to_drop:
            if col in result_df.columns:
                result_df.drop(col, axis=1, inplace=True)
        
        # Calculate statistics
        total_issues = len(result_df)
        true_count = (result_df['is_issue_completed_in_issue_sprint_id_last'] == 'TRUE').sum()
        false_count = (result_df['is_issue_completed_in_issue_sprint_id_last'] == 'FALSE').sum()
        not_applicable_count = (result_df['is_issue_completed_in_issue_sprint_id_last'] == 'Not applicable').sum()
        
        logger.info(f"Step 35b: - ✅ Sprint completion flag column added successfully")
        logger.info(f"Step 35b: - 📊 {true_count}/{total_issues} issues completed within their last sprint (TRUE)")
        logger.info(f"Step 35b: - 📊 {false_count}/{total_issues} issues completed outside their last sprint (FALSE)")
        logger.info(f"Step 35b: - 📊 {not_applicable_count}/{total_issues} issues marked as 'Not applicable'")
        
        return result_df
        
    except Exception as e:
        logger.error(f"Step 35b: - ❌ Error adding sprint completion flag column: {str(e)}")
        return None


def validate_fact_measures_sprint_data(fact_measures_df, ado_history_df, sprints_schedule_df):
    """
    Perform risk-based validation of sprint completion data in fact_issue_level_measures.
    
    Args:
        fact_measures_df (DataFrame): Updated fact_measures_df
        ado_history_df (DataFrame): Source jira_history DataFrame  
        sprints_schedule_df (DataFrame): Sprints schedule DataFrame
        
    Returns:
        bool: True if validation passes, False if critical issues found
    """
    logger.info("Step 35c: - 🔍 Performing risk-based validation of sprint completion data...")
    
    try:
        validation_passed = True
        
        # HIGH RISK - Data Integrity Checks
        
        # 1. Duplicate Prevention
        duplicate_keys = fact_measures_df['key'].duplicated().sum()
        if duplicate_keys > 0:
            logger.error(f"Step 35c: - ❌ HIGH RISK: Found {duplicate_keys} duplicate keys in fact_measures_df")
            validation_passed = False
        else:
            logger.info(f"Step 35c: - ✅ Data integrity: No duplicate keys found")
        
        # 2. Key Mapping Validation
        fact_keys = set(fact_measures_df['key'])
        history_keys = set(ado_history_df['key'])
        missing_keys = fact_keys - history_keys
        if missing_keys:
            logger.error(f"Step 35c: - ❌ HIGH RISK: {len(missing_keys)} fact_measures keys not found in jira_history")
            validation_passed = False
        else:
            logger.info(f"Step 35c: - ✅ Key mapping: All fact_measures keys found in jira_history")
        
        # 3. Sprint ID Validation
        fact_sprint_ids = set(fact_measures_df['issue_sprint_id_last'].dropna().astype(str))
        sprint_ids = set(sprints_schedule_df['sprint_id'].astype(str))
        
        # Exclude -999 placeholder (for issues without sprint assignment) from validation
        fact_sprint_ids_to_validate = fact_sprint_ids - {'-999'}
        invalid_sprint_ids = fact_sprint_ids_to_validate - sprint_ids
        
        if invalid_sprint_ids:
            logger.warning(f"Step 35c: - ⚠️ MEDIUM RISK: {len(invalid_sprint_ids)} sprint IDs not found in sprints table")
            logger.warning(f"Step 35c: - ❌ Invalid sprint IDs: {sorted(invalid_sprint_ids)}")
        else:
            logger.info(f"Step 35c: - ✅ Sprint ID validation: All sprint IDs are valid")
            
        # Log placeholder usage statistics
        placeholder_count = (fact_measures_df['issue_sprint_id_last'].astype(str) == '-999').sum()
        if placeholder_count > 0:
            logger.info(f"Step 35c: - ℹ️  Found {placeholder_count} issues with no sprint assignment (sprint_id = -999)")
        
        # MEDIUM RISK - Business Logic Checks
        
        # 4. Date Range Validation
        future_dates = 0
        if 'issue_sprint_completed_event_date_according_dod' in fact_measures_df.columns:
            valid_dates_mask = fact_measures_df['issue_sprint_completed_event_date_according_dod'] != 'Not applicable'
            if valid_dates_mask.sum() > 0:
                valid_dates = pd.to_datetime(fact_measures_df.loc[valid_dates_mask, 'issue_sprint_completed_event_date_according_dod'], errors='coerce')
                future_dates = (valid_dates > pd.Timestamp.now()).sum()
                if future_dates > 0:
                    logger.warning(f"Step 35c: - ⚠️ MEDIUM RISK: {future_dates} completion dates are in the future")
        
        # 5. Sprint Date Consistency
        invalid_sprint_ranges = 0
        if not sprints_schedule_df.empty:
            sprint_dates = sprints_schedule_df[['sprint_start_date', 'sprint_end_date']].copy()
            sprint_dates['start_dt'] = pd.to_datetime(sprint_dates['sprint_start_date'], errors='coerce')
            sprint_dates['end_dt'] = pd.to_datetime(sprint_dates['sprint_end_date'], errors='coerce')
            invalid_ranges = (sprint_dates['start_dt'] >= sprint_dates['end_dt']).sum()
            if invalid_ranges > 0:
                logger.warning(f"Step 35c: - ⚠️ MEDIUM RISK: {invalid_ranges} sprints have start_date >= end_date")
        
        # LOW RISK - Data Quality Metrics
        
        # 6. Null Distribution Analysis
        total_issues = len(fact_measures_df)
        not_applicable_completion_dates = (fact_measures_df['issue_sprint_completed_event_date_according_dod'] == 'Not applicable').sum()
        not_applicable_flags = (fact_measures_df['is_issue_completed_in_issue_sprint_id_last'] == 'Not applicable').sum()
        
        completion_na_pct = (not_applicable_completion_dates / total_issues) * 100
        flag_na_pct = (not_applicable_flags / total_issues) * 100
        
        logger.info(f"Step 35c: - 📊 Data quality: {completion_na_pct:.1f}% completion dates are 'Not applicable'")
        logger.info(f"Step 35c: - 📊 Data quality: {flag_na_pct:.1f}% completion flags are 'Not applicable'")
        
        # 7. TRUE/FALSE Distribution
        true_flags = (fact_measures_df['is_issue_completed_in_issue_sprint_id_last'] == 'TRUE').sum()
        false_flags = (fact_measures_df['is_issue_completed_in_issue_sprint_id_last'] == 'FALSE').sum()
        
        if true_flags + false_flags > 0:
            true_pct = (true_flags / (true_flags + false_flags)) * 100
            logger.info(f"Step 35c: - 📊 Business metric: {true_pct:.1f}% of valid issues completed within their last sprint")
        
        # Final validation summary
        if validation_passed:
            logger.info(f"Step 35c: - ✅ Validation PASSED: All critical checks successful")
        else:
            logger.error(f"Step 35c: - ❌ Validation FAILED: Critical issues detected")
        
        return validation_passed
        
    except Exception as e:
        logger.error(f"Step 35c: - ❌ Error during validation: {str(e)}")
        return False


def _add_sprint_created_columns(fact_df, sprints_schedule_df):
    """
    Add calculated sprint columns based on issue creation date.
    
    For each issue, finds the sprint where issue_create_date falls between
    sprint_start_date and sprint_end_date (inclusive). If multiple sprints match,
    selects the one with the most recent sprint_end_date.
    
    Args:
        fact_df (DataFrame): The fact_issue_level_measures DataFrame
        sprints_schedule_df (DataFrame): The sprints_schedule DataFrame
        
    Returns:
        DataFrame: fact_df with two new columns:
                  - issue_sprint_id_created_calculated
                  - issue_sprint_name_created_calculated
    """
    logger.info("Step 34d: - 🧮 Calculating sprint created columns based on issue creation date...")
    
    if sprints_schedule_df is None or sprints_schedule_df.empty:
        logger.warning("Step 34d: - ⚠️  sprints_schedule_df is empty, setting sprint created columns to None")
        fact_df['issue_sprint_id_created_calculated'] = None
        fact_df['issue_sprint_name_created_calculated'] = None
        return fact_df
    
    # Initialize new columns with None
    fact_df['issue_sprint_id_created_calculated'] = None
    fact_df['issue_sprint_name_created_calculated'] = None
    
    # Pre-filter sprints to only valid ones
    valid_sprints = sprints_schedule_df[sprints_schedule_df['valid_sprint'] == True].copy()
    
    if valid_sprints.empty:
        logger.warning("Step 34d: - ⚠️  No valid sprints found in sprints_schedule_df")
        return fact_df
    
    # Ensure date columns are datetime
    fact_df['issue_create_date'] = pd.to_datetime(fact_df['issue_create_date'], errors='coerce')
    valid_sprints['sprint_start_date'] = pd.to_datetime(valid_sprints['sprint_start_date'], errors='coerce')
    valid_sprints['sprint_end_date'] = pd.to_datetime(valid_sprints['sprint_end_date'], errors='coerce')
    
    # Remove sprints with invalid dates
    valid_sprints = valid_sprints.dropna(subset=['sprint_start_date', 'sprint_end_date'])
    
    # Check if required columns exist
    required_cols = ['ado_project_name']
    missing_cols = [col for col in required_cols if col not in valid_sprints.columns]
    if missing_cols:
        logger.error(f"Step 34d: - ❌ Missing required columns in sprints_schedule_df: {missing_cols}")
        logger.error(f"Step 34d: - 📋 Available columns: {list(valid_sprints.columns)}")
        fact_df['issue_sprint_id_created_calculated'] = None
        fact_df['issue_sprint_name_created_calculated'] = None
        return fact_df
    
    # Create case-insensitive project name columns for matching
    fact_df['ado_project_name_upper'] = fact_df['ado_project_name'].str.upper()
    valid_sprints['ado_project_name_upper'] = valid_sprints['ado_project_name'].str.upper()
    
    logger.info(f"Step 34d: - 📊 Processing {len(fact_df)} issues against {len(valid_sprints)} valid sprints")
    
    # Statistics tracking
    primary_matches = 0
    fallback_matches = 0
    no_matches = 0
    multiple_matches = 0
    invalid_create_dates = 0
    
    # Enhanced matching logic: Primary + Fallback
    for idx, row in fact_df.iterrows():
        issue_key = row['key']
        issue_create_date = row['issue_create_date']
        ado_project_name_upper = row['ado_project_name_upper']
        
        # Skip if create date is null
        if pd.isna(issue_create_date):
            invalid_create_dates += 1
            continue
        
        # Get all sprints for this project (for both primary and fallback matching)
        project_sprints_all = valid_sprints[valid_sprints['ado_project_name_upper'] == ado_project_name_upper]
        
        if project_sprints_all.empty:
            no_matches += 1
            continue
            
        # PRIMARY MATCHING: Direct date range matching
        project_sprints_direct = project_sprints_all[
            (project_sprints_all['sprint_start_date'] <= issue_create_date) &
            (project_sprints_all['sprint_end_date'] >= issue_create_date)
        ]
        
        selected_sprint = None
        match_type = None
        
        if not project_sprints_direct.empty:
            # Primary match found
            if len(project_sprints_direct) == 1:
                selected_sprint = project_sprints_direct.iloc[0]
                primary_matches += 1
                match_type = "direct"
            else:
                # Multiple direct matches - select the one with most recent sprint_end_date
                selected_sprint = project_sprints_direct.loc[project_sprints_direct['sprint_end_date'].idxmax()]
                multiple_matches += 1
                primary_matches += 1
                match_type = "direct_multiple"
                logger.debug(f"Step 34d: - 📝 Issue {issue_key}: Multiple direct sprints matched, selected sprint with latest end date")
        
        else:
            # FALLBACK MATCHING: Find previous sprint (most recent sprint that ended before issue creation)
            previous_sprints = project_sprints_all[project_sprints_all['sprint_end_date'] < issue_create_date]
            
            if not previous_sprints.empty:
                # Select the sprint with the most recent end date (closest to issue creation)
                selected_sprint = previous_sprints.loc[previous_sprints['sprint_end_date'].idxmax()]
                fallback_matches += 1
                match_type = "fallback_previous"
                logger.debug(f"Step 34d: - 🔄 Issue {issue_key}: No direct match, assigned to previous sprint (created {issue_create_date.strftime('%Y-%m-%d')}, sprint ended {selected_sprint['sprint_end_date'].strftime('%Y-%m-%d')})")
            else:
                # No matches at all (no previous sprints either)
                no_matches += 1
                continue
        
        # Update the fact_df with selected sprint info
        if selected_sprint is not None:
            fact_df.at[idx, 'issue_sprint_id_created_calculated'] = selected_sprint['sprint_id']
            fact_df.at[idx, 'issue_sprint_name_created_calculated'] = selected_sprint['sprint_name']
            
            # Add verification metadata (for validation)
            fact_df.at[idx, 'sprint_match_type_temp'] = match_type
    
    # Enhanced statistics logging
    total_matches = primary_matches + fallback_matches
    total_processed = len(fact_df) - invalid_create_dates
    logger.info(f"Step 34d: - 📊 Enhanced sprint matching statistics:")
    logger.info(f"         - ✅ Total matches: {total_matches}/{total_processed} ({total_matches/max(total_processed,1)*100:.1f}%)")
    logger.info(f"         - 🎯 Direct matches: {primary_matches} ({primary_matches/max(total_processed,1)*100:.1f}%)")
    logger.info(f"         - 🔄 Fallback matches: {fallback_matches} ({fallback_matches/max(total_processed,1)*100:.1f}%)")
    logger.info(f"         - 🔍 Multiple direct matches resolved: {multiple_matches}")
    logger.info(f"         - ❌ No matches: {no_matches}")
    logger.info(f"         - ⚠️  Invalid create dates: {invalid_create_dates}")
    
    # Additional validation
    non_null_sprint_ids = fact_df['issue_sprint_id_created_calculated'].notna().sum()
    non_null_sprint_names = fact_df['issue_sprint_name_created_calculated'].notna().sum()
    
    if non_null_sprint_ids != non_null_sprint_names:
        logger.warning(f"Step 34d: - ⚠️  Mismatch between sprint_id and sprint_name counts: {non_null_sprint_ids} vs {non_null_sprint_names}")
    
    # Enhanced verification logic for fallback matches
    if fallback_matches > 0:
        logger.info(f"Step 34d: - 🔍 Performing enhanced verification of {fallback_matches} fallback matches...")
        _verify_fallback_sprint_assignments(fact_df, valid_sprints)
    
    # Clean up temporary columns
    if 'ado_project_name_upper' in fact_df.columns:
        fact_df = fact_df.drop(columns=['ado_project_name_upper'])
    if 'sprint_match_type_temp' in fact_df.columns:
        fact_df = fact_df.drop(columns=['sprint_match_type_temp'])
    
    logger.info(f"Step 34d: - ✅ Enhanced sprint created columns added successfully")
    
    return fact_df


def _add_sprint_closed_columns(fact_df, sprints_schedule_df):
    """
    Add calculated sprint columns based on sprint completion date according to DOD.
    
    For each issue, finds the sprint where issue_sprint_completed_event_date_according_dod falls between
    sprint_start_date and sprint_end_date (inclusive). If multiple sprints match,
    selects the one with the most recent sprint_end_date.
    
    Uses same fallback logic as creation columns: if no direct match, assigns to
    the most recent sprint that ended before the sprint completion date.
    
    Args:
        fact_df (DataFrame): The fact_issue_level_measures DataFrame
        sprints_schedule_df (DataFrame): The sprints_schedule DataFrame
        
    Returns:
        DataFrame: fact_df with two new columns:
                  - issue_sprint_id_closed_calculated
                  - issue_sprint_name_closed_calculated
    """
    logger.info("Step 34e: - 🧮 Calculating sprint closed columns based on sprint completion date according to DOD...")
    
    if sprints_schedule_df is None or sprints_schedule_df.empty:
        logger.warning("Step 34e: - ⚠️  sprints_schedule_df is empty, setting sprint closed columns to None")
        fact_df['issue_sprint_id_closed_calculated'] = None
        fact_df['issue_sprint_name_closed_calculated'] = None
        return fact_df
    
    # Initialize new columns with None
    fact_df['issue_sprint_id_closed_calculated'] = None
    fact_df['issue_sprint_name_closed_calculated'] = None
    
    # Pre-filter sprints to only valid ones
    valid_sprints = sprints_schedule_df[sprints_schedule_df['valid_sprint'] == True].copy()
    
    if valid_sprints.empty:
        logger.warning("Step 34e: - ⚠️  No valid sprints found in sprints_schedule_df")
        return fact_df
    
    # Ensure date columns are datetime
    fact_df['issue_sprint_completed_event_date_according_dod'] = pd.to_datetime(fact_df['issue_sprint_completed_event_date_according_dod'], errors='coerce')
    valid_sprints['sprint_start_date'] = pd.to_datetime(valid_sprints['sprint_start_date'], errors='coerce')
    valid_sprints['sprint_end_date'] = pd.to_datetime(valid_sprints['sprint_end_date'], errors='coerce')
    
    # Remove sprints with invalid dates
    valid_sprints = valid_sprints.dropna(subset=['sprint_start_date', 'sprint_end_date'])
    
    # Check if required columns exist
    required_cols = ['ado_project_name']
    missing_cols = [col for col in required_cols if col not in valid_sprints.columns]
    if missing_cols:
        logger.error(f"Step 34e: - ❌ Missing required columns in sprints_schedule_df: {missing_cols}")
        logger.error(f"Step 34e: - 📋 Available columns: {list(valid_sprints.columns)}")
        fact_df['issue_sprint_id_closed_calculated'] = None
        fact_df['issue_sprint_name_closed_calculated'] = None
        return fact_df
    
    # Create case-insensitive project name columns for matching
    fact_df['ado_project_name_upper'] = fact_df['ado_project_name'].str.upper()
    valid_sprints['ado_project_name_upper'] = valid_sprints['ado_project_name'].str.upper()
    
    logger.info(f"Step 34e: - 📊 Processing {len(fact_df)} issues against {len(valid_sprints)} valid sprints")
    
    # Statistics tracking
    primary_matches = 0
    fallback_matches = 0
    no_matches = 0
    multiple_matches = 0
    null_closed_dates = 0
    
    # Enhanced matching logic: Primary + Fallback
    for idx, row in fact_df.iterrows():
        issue_key = row['key']
        sprint_completion_date = row['issue_sprint_completed_event_date_according_dod']
        ado_project_name_upper = row['ado_project_name_upper']
        

        
        # Skip if sprint completion date is null (open/in-progress issues or non-sprint projects)
        if pd.isna(sprint_completion_date):
            null_closed_dates += 1
            continue
        
        # Get all sprints for this project (for both primary and fallback matching)
        project_sprints_all = valid_sprints[valid_sprints['ado_project_name_upper'] == ado_project_name_upper]
        
        if project_sprints_all.empty:
            no_matches += 1
            continue
            
        # PRIMARY MATCHING: Direct date range matching
        project_sprints_direct = project_sprints_all[
            (project_sprints_all['sprint_start_date'] <= sprint_completion_date) &
            (project_sprints_all['sprint_end_date'] >= sprint_completion_date)
        ]
        
        selected_sprint = None
        match_type = None
        
        if not project_sprints_direct.empty:
            # Primary match found
            if len(project_sprints_direct) == 1:
                selected_sprint = project_sprints_direct.iloc[0]
                primary_matches += 1
                match_type = "direct"
            else:
                # Multiple direct matches - select the one with most recent sprint_end_date
                selected_sprint = project_sprints_direct.loc[project_sprints_direct['sprint_end_date'].idxmax()]
                multiple_matches += 1
                primary_matches += 1
                match_type = "direct_multiple"
                logger.debug(f"Step 34e: - 📝 Issue {issue_key}: Multiple direct sprints matched, selected sprint with latest end date")
        
        else:
            # FALLBACK MATCHING: Find previous sprint (most recent sprint that ended before sprint completion)
            previous_sprints = project_sprints_all[project_sprints_all['sprint_end_date'] < sprint_completion_date]
            
            if not previous_sprints.empty:
                # Select the sprint with the most recent end date (closest to issue closure)
                selected_sprint = previous_sprints.loc[previous_sprints['sprint_end_date'].idxmax()]
                fallback_matches += 1
                match_type = "fallback_previous"
                logger.debug(f"Step 34e: - 🔄 Issue {issue_key}: No direct match, assigned to previous sprint (completed {sprint_completion_date.strftime('%Y-%m-%d')}, sprint ended {selected_sprint['sprint_end_date'].strftime('%Y-%m-%d')})")
            else:
                # No matches at all (no previous sprints either)
                no_matches += 1
                continue
        
        # Update the fact_df with selected sprint info
        if selected_sprint is not None:
            fact_df.at[idx, 'issue_sprint_id_closed_calculated'] = selected_sprint['sprint_id']
            fact_df.at[idx, 'issue_sprint_name_closed_calculated'] = selected_sprint['sprint_name']
            
            # Add verification metadata (for validation)
            fact_df.at[idx, 'sprint_closed_match_type_temp'] = match_type
    
    # Enhanced statistics logging
    total_matches = primary_matches + fallback_matches
    total_processed = len(fact_df) - null_closed_dates
    logger.info(f"Step 34e: - 📊 Enhanced sprint closed matching statistics:")
    logger.info(f"         - ✅ Total matches: {total_matches}/{total_processed} ({total_matches/max(total_processed,1)*100:.1f}%)")
    logger.info(f"         - 🎯 Direct matches: {primary_matches} ({primary_matches/max(total_processed,1)*100:.1f}%)")
    logger.info(f"         - 🔄 Fallback matches: {fallback_matches} ({fallback_matches/max(total_processed,1)*100:.1f}%)")
    logger.info(f"         - 🔍 Multiple direct matches resolved: {multiple_matches}")
    logger.info(f"         - ❌ No matches: {no_matches}")
    logger.info(f"         - ⚠️  Null sprint completion dates (open issues or non-sprint projects): {null_closed_dates}")
    
    # Additional validation
    non_null_sprint_ids = fact_df['issue_sprint_id_closed_calculated'].notna().sum()
    non_null_sprint_names = fact_df['issue_sprint_name_closed_calculated'].notna().sum()
    
    if non_null_sprint_ids != non_null_sprint_names:
        logger.warning(f"Step 34e: - ⚠️  Mismatch between sprint_id and sprint_name counts: {non_null_sprint_ids} vs {non_null_sprint_names}")
    

    
    # Enhanced verification logic for fallback matches
    if fallback_matches > 0:
        logger.info(f"Step 34e: - 🔍 Performing enhanced verification of {fallback_matches} closed date fallback matches...")
        _verify_fallback_sprint_assignments_closed(fact_df, valid_sprints)
    
    # Clean up temporary columns
    if 'ado_project_name_upper' in fact_df.columns:
        fact_df = fact_df.drop(columns=['ado_project_name_upper'])
    if 'sprint_closed_match_type_temp' in fact_df.columns:
        fact_df = fact_df.drop(columns=['sprint_closed_match_type_temp'])
    
    # Clean up temporary comparison column
    if 'issue_sprint_id_closed_old_logic' in fact_df.columns:
        fact_df = fact_df.drop(columns=['issue_sprint_id_closed_old_logic'])
    
    logger.info(f"Step 34e: - ✅ Enhanced sprint closed columns added successfully")
    
    return fact_df


def _verify_fallback_sprint_assignments_closed(fact_df, valid_sprints):
    """
    Verify that fallback sprint assignments for closed dates are reasonable.
    Provides detailed analysis of fallback matches to help ensure data quality.
    
    Args:
        fact_df (DataFrame): The fact_issue_level_measures DataFrame
        valid_sprints (DataFrame): The valid sprints DataFrame
    """
    logger.info("Step 34e: - 🔍 Verifying fallback sprint assignments for closed dates...")
    
    # Filter to only fallback matches
    fallback_issues = fact_df[fact_df['sprint_closed_match_type_temp'] == 'fallback_previous'].copy()
    
    if fallback_issues.empty:
        logger.info("Step 34e: - ✅ No fallback assignments to verify")
        return
    
    logger.info(f"Step 34e: - 📊 Analyzing {len(fallback_issues)} fallback assignments:")
    
    # Create case-insensitive project name column for matching
    valid_sprints_copy = valid_sprints.copy()
    valid_sprints_copy['ado_project_name_upper'] = valid_sprints_copy['ado_project_name'].str.upper()
    
    # Detailed analysis
    verification_results = []
    warning_count = 0
    excessive_gap_count = 0
    
    for idx, issue in fallback_issues.iterrows():
        issue_key = issue['key']
        sprint_completion_date = issue['issue_sprint_completed_event_date_according_dod']
        assigned_sprint_id = issue['issue_sprint_id_closed_calculated']
        ado_project_name_upper = issue['ado_project_name'].upper()
        
        # Find the assigned sprint details
        assigned_sprint = valid_sprints_copy[valid_sprints_copy['sprint_id'] == assigned_sprint_id]
        
        if assigned_sprint.empty:
            logger.warning(f"Step 34e: - ⚠️  Issue {issue_key}: Assigned sprint {assigned_sprint_id} not found in valid sprints")
            warning_count += 1
            continue
            
        assigned_sprint = assigned_sprint.iloc[0]
        sprint_end_date = assigned_sprint['sprint_end_date']
        
        # Calculate gap between sprint end and sprint completion
        gap_days = (sprint_completion_date - sprint_end_date).days
        
        # Check if this is the best possible fallback
        project_sprints = valid_sprints_copy[valid_sprints_copy['ado_project_name_upper'] == ado_project_name_upper]
        previous_sprints = project_sprints[project_sprints['sprint_end_date'] < sprint_completion_date]
        
        if not previous_sprints.empty:
            best_fallback = previous_sprints.loc[previous_sprints['sprint_end_date'].idxmax()]
            is_optimal = (best_fallback['sprint_id'] == assigned_sprint_id)
        else:
            is_optimal = False
            logger.warning(f"Step 34e: - ⚠️  Issue {issue_key}: No previous sprints found, assignment may be incorrect")
            warning_count += 1
        
        # Flag excessive gaps (more than 60 days)
        if gap_days > 60:
            excessive_gap_count += 1
            logger.debug(f"Step 34e: - 🔔 Issue {issue_key}: Large gap of {gap_days} days between sprint end and sprint completion")
        
        verification_results.append({
            'issue_key': issue_key,
            'gap_days': gap_days,
            'is_optimal': is_optimal,
            'sprint_name': assigned_sprint['sprint_name']
        })
    
    # Summary statistics
    if verification_results:
        gaps = [r['gap_days'] for r in verification_results]
        optimal_count = sum(1 for r in verification_results if r['is_optimal'])
        
        logger.info(f"Step 34e: - 📈 Gap analysis (sprint end to sprint completion):")
        logger.info(f"         - Average gap: {sum(gaps)/len(gaps):.1f} days")
        logger.info(f"         - Median gap: {sorted(gaps)[len(gaps)//2]:.1f} days")
        logger.info(f"         - Max gap: {max(gaps)} days")
        logger.info(f"         - Min gap: {min(gaps)} days")
        logger.info(f"         - Optimal assignments: {optimal_count}/{len(verification_results)} ({optimal_count/len(verification_results)*100:.1f}%)")
        
        if excessive_gap_count > 0:
            logger.warning(f"Step 34e: - ⚠️  {excessive_gap_count} issues have gaps > 60 days")
        
        if warning_count > 0:
            logger.warning(f"Step 34e: - ⚠️  {warning_count} issues had verification warnings")
    
    # Quality assessment for closed dates
    if len(verification_results) > 0:
        optimal_rate = sum(1 for r in verification_results if r['is_optimal']) / len(verification_results)
        avg_gap = sum(r['gap_days'] for r in verification_results) / len(verification_results)
        warning_rate = warning_count / len(verification_results)
        
        if optimal_rate >= 0.95 and avg_gap <= 14 and warning_rate <= 0.05:
            quality_rating = "EXCELLENT"
        elif optimal_rate >= 0.90 and avg_gap <= 30 and warning_rate <= 0.10:
            quality_rating = "GOOD"
        elif optimal_rate >= 0.80 and avg_gap <= 60 and warning_rate <= 0.20:
            quality_rating = "ACCEPTABLE"
        else:
            quality_rating = "NEEDS_REVIEW"
    else:
        quality_rating = "NO_DATA"
    
    logger.info(f"Step 34e: - ✅ Overall quality: {quality_rating}")


def _verify_fallback_sprint_assignments(fact_df, valid_sprints):
    """
    Verification logic for fallback sprint assignments.
    
    Validates that fallback matches (issues assigned to previous sprints) are reasonable
    by checking business rules and data quality.
    
    Args:
        fact_df (DataFrame): The fact dataframe with sprint assignments
        valid_sprints (DataFrame): The sprints dataframe for validation
    """
    logger.info("Step 34d: - 🔍 Starting verification of fallback sprint assignments...")
    
    # Get fallback matches (temporary column should still exist at this point)
    if 'sprint_match_type_temp' not in fact_df.columns:
        logger.warning("Step 34d: - ⚠️  Cannot verify fallback matches - temporary column not found")
        return
    
    fallback_issues = fact_df[fact_df['sprint_match_type_temp'] == 'fallback_previous'].copy()
    
    if fallback_issues.empty:
        logger.info("Step 34d: - ℹ️  No fallback matches to verify")
        return
    
    logger.info(f"Step 34d: - 🔍 Verifying {len(fallback_issues)} fallback sprint assignments...")
    
    # Verification 1: Check time gaps between sprint end and issue creation
    suspicious_gaps = []
    reasonable_gaps = []
    
    for _, row in fallback_issues.iterrows():
        issue_key = row['key']
        issue_create_date = pd.to_datetime(row['issue_create_date'])
        sprint_id = row['issue_sprint_id_created_calculated']
        
        # Find sprint info
        sprint_info = valid_sprints[valid_sprints['sprint_id'] == sprint_id]
        if not sprint_info.empty:
            sprint_end_date = pd.to_datetime(sprint_info.iloc[0]['sprint_end_date'])
            sprint_name = sprint_info.iloc[0]['sprint_name']
            
            gap_days = (issue_create_date - sprint_end_date).days
            
            if gap_days <= 7:  # Within a week - reasonable
                reasonable_gaps.append((issue_key, gap_days, sprint_name))
            elif gap_days <= 21:  # 1-3 weeks - questionable but acceptable
                logger.debug(f"Step 34d: - ⚠️  Issue {issue_key}: {gap_days} day gap from sprint end (may be between-sprint planning)")
            else:  # More than 3 weeks - suspicious
                suspicious_gaps.append((issue_key, gap_days, sprint_name))
    
    # Report verification results
    logger.info(f"Step 34d: - 📊 Fallback verification results:")
    logger.info(f"         - ✅ Reasonable gaps (≤7 days): {len(reasonable_gaps)}")
    logger.info(f"         - ⚠️  Long gaps (>21 days): {len(suspicious_gaps)}")
    
    if suspicious_gaps:
        logger.warning(f"Step 34d: - 🚨 Found {len(suspicious_gaps)} issues with suspicious long gaps:")
        for issue_key, gap_days, sprint_name in suspicious_gaps[:5]:  # Show first 5
            logger.warning(f"         - {issue_key}: {gap_days} days after '{sprint_name}' ended")
        if len(suspicious_gaps) > 5:
            logger.warning(f"         - ... and {len(suspicious_gaps) - 5} more")
    
    # Verification 2: Check for next sprint availability (could we have assigned to a better sprint?)
    potential_improvements = []
    
    for _, row in fallback_issues.iterrows():
        issue_key = row['key']
        issue_create_date = pd.to_datetime(row['issue_create_date'])
        assigned_sprint_id = row['issue_sprint_id_created_calculated']
        project_name = row['ado_project_name_upper']
        
        # Find next sprint after the assigned one for this project
        project_sprints = valid_sprints[valid_sprints['ado_project_name_upper'] == project_name]
        assigned_sprint = project_sprints[project_sprints['sprint_id'] == assigned_sprint_id]
        
        if not assigned_sprint.empty:
            assigned_sprint_end = pd.to_datetime(assigned_sprint.iloc[0]['sprint_end_date'])
            
            # Find sprints that start after the assigned sprint ends but before/during issue creation
            next_sprints = project_sprints[
                (project_sprints['sprint_start_date'] > assigned_sprint_end) &
                (project_sprints['sprint_start_date'] <= issue_create_date)
            ]
            
            if not next_sprints.empty:
                # There was a better sprint available!
                best_next_sprint = next_sprints.loc[next_sprints['sprint_start_date'].idxmax()]
                potential_improvements.append((issue_key, best_next_sprint['sprint_name']))
    
    if potential_improvements:
        logger.info(f"Step 34d: - 💡 Found {len(potential_improvements)} potential assignment improvements:")
        for issue_key, better_sprint in potential_improvements[:3]:  # Show first 3
            logger.info(f"         - {issue_key} could potentially be assigned to '{better_sprint}'")
        if len(potential_improvements) > 3:
            logger.info(f"         - ... and {len(potential_improvements) - 3} more")
    
    # Verification 3: Overall business rule validation
    total_fallback = len(fallback_issues)
    reasonable_percentage = (len(reasonable_gaps) / total_fallback * 100) if total_fallback > 0 else 0
    
    logger.info(f"Step 34d: - 📈 Fallback quality score: {reasonable_percentage:.1f}% assignments within reasonable gap")
    
    if reasonable_percentage >= 80:
        logger.info("Step 34d: - ✅ Fallback assignments quality: EXCELLENT")
    elif reasonable_percentage >= 60:
        logger.info("Step 34d: - ✅ Fallback assignments quality: GOOD")
    elif reasonable_percentage >= 40:
        logger.warning("Step 34d: - ⚠️  Fallback assignments quality: FAIR - some issues may need manual review")
    else:
        logger.warning("Step 34d: - 🚨 Fallback assignments quality: POOR - manual review recommended")
    
    logger.info("Step 34d: - ✅ Fallback verification completed")


def _add_standard_issue_type_column(fact_df, ado_history_df):
    """
    Add standard_issue_type column to fact_issue_level_measures by mapping from jira_history.
    
    Args:
        fact_df (DataFrame): The fact_issue_level_measures DataFrame
        ado_history_df (DataFrame): The jira_history DataFrame with standard_issue_type mappings
        
    Returns:
        DataFrame: Updated fact_df with standard_issue_type column, or None if error
    """
    try:
        # Validate inputs
        if ado_history_df is None:
            logger.error("Step 34f: - ❌ ado_history_df is None, cannot add standard_issue_type column")
            return None
            
        if 'standard_issue_type' not in ado_history_df.columns:
            logger.error("Step 34f: - ❌ 'standard_issue_type' column not found in ado_history_df")
            return None
            
        # Get unique mappings from jira_history (key -> standard_issue_type)
        # Use drop_duplicates to get one mapping per issue key
        mapping_df = ado_history_df[['key', 'standard_issue_type']].drop_duplicates(subset=['key'])
        
        # Check for any issues in fact_df that don't have mappings
        fact_keys = set(fact_df['key'].unique())
        mapping_keys = set(mapping_df['key'].unique())
        missing_keys = fact_keys - mapping_keys
        
        if missing_keys:
            logger.error(f"Step 34f: - ❌ Found {len(missing_keys)} issues in fact_df without standard_issue_type mappings")
            logger.error(f"Step 34f: - 🔍 Missing keys: {sorted(list(missing_keys))[:10]}{'...' if len(missing_keys) > 10 else ''}")
            return None
            
        # Check for multiple mappings per key (should not happen if jira_history is consistent)
        duplicate_keys = mapping_df['key'].duplicated().sum()
        if duplicate_keys > 0:
            logger.warning(f"Step 34f: - ⚠️  Found {duplicate_keys} issues with multiple standard_issue_type values in jira_history")
            # Keep the first occurrence for each key
            mapping_df = mapping_df.drop_duplicates(subset=['key'], keep='first')
            logger.warning(f"Step 34f: - 🔧 Using first occurrence for duplicate keys")
        
        # Merge the mapping
        fact_df_with_mapping = fact_df.merge(
            mapping_df[['key', 'standard_issue_type']], 
            on='key', 
            how='left'
        )
        
        # Validate the merge
        unmapped_count = fact_df_with_mapping['standard_issue_type'].isna().sum()
        if unmapped_count > 0:
            logger.error(f"Step 34f: - ❌ Found {unmapped_count} unmapped standard_issue_type values after merge")
            return None
            
        # Count mapping statistics
        total_rows = len(fact_df_with_mapping)
        unique_standard_types = fact_df_with_mapping['standard_issue_type'].nunique()
        
        # Get distribution
        distribution = fact_df_with_mapping['standard_issue_type'].value_counts()
        
        logger.info(f"Step 34f: - ✅ Added 'standard_issue_type' column to fact_issue_level_measures successfully")
        logger.info(f"Step 34f: - 📊 Mapping statistics:")
        logger.info(f"Step 34f: -    Total issues: {total_rows}")
        logger.info(f"Step 34f: -    Successfully mapped: {total_rows} (100.0%)")
        logger.info(f"Step 34f: -    Unique standard issue types: {unique_standard_types}")
        
        # Log distribution
        logger.info(f"Step 34f: - 📋 Standard issue type distribution:")
        for std_type, count in distribution.items():
            percentage = count / total_rows * 100
            logger.info(f"Step 34f: -    {std_type}: {count} issues ({percentage:.1f}%)")
        
        return fact_df_with_mapping
        
    except Exception as e:
        logger.error(f"Step 34f: - ❌ Error adding standard_issue_type column: {str(e)}")
        return None
