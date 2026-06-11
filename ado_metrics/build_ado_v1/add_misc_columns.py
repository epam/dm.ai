# add_misc_columns.py

import pandas as pd
from config import logger


def add_status_category_flag(df, category_current_col, category_history_col, target_col):
    # Compare if category current equals to category history then True otherwise False

    df[target_col] = df[category_current_col] == df[category_history_col]


    return df

def add_status_category_duration(df, groupby_cols, sum_col, target_col):
    # Calculate duration for each Status Category for specific issue Key as a SUM of Durations in Issue Status

    duration_sum = df.groupby(groupby_cols)[sum_col].sum().reset_index()
    duration_sum = duration_sum.rename(columns={sum_col: target_col})

    df = df.merge(duration_sum, on=groupby_cols, how="left")
    return df


def add_last_ai_enabled_flag(df, key_col, entry_count_col, ai_flag_col, target_col):
    # New logic: Check if ANY rows have AI-enabled flag set to TRUE,
    # and if so, get the LAST row with TRUE values
    if df.empty:
        df[target_col] = pd.Series(dtype="object")
        return df
    
    def get_last_ai_enabled_flag(group):
        # Convert ai_flag_col to boolean for comparison
        ai_enabled_rows = group[group[ai_flag_col].astype(bool) == True]
        
        if len(ai_enabled_rows) > 0:
            # If there are AI-enabled rows, get the last one (highest entry count)
            last_ai_row = ai_enabled_rows.loc[ai_enabled_rows[entry_count_col].idxmax()]
            return True
        else:
            # If no AI-enabled rows found, return False
            return False
    
    # Group by issue key and apply the logic
    last_ai_map = df.groupby(key_col).apply(get_last_ai_enabled_flag)

    # Map this back to the original DataFrame
    df[target_col] = df[key_col].map(last_ai_map)

    # Convert boolean True/False to string TRUE/FALSE - more compatible for excel
    df[target_col] = df[target_col].apply(lambda x: "TRUE" if x else "FALSE")

    return df


def get_ai_enabled_team_name(group):
    """
    Custom aggregation function for assignee_ai_team_name that:
    1. If ANY rows have is_ai_enabled_flag = TRUE, get team name from LAST AI-enabled row
    2. If NO AI-enabled rows, fall back to last team name (original behavior)
    
    This function is designed to be used with pandas groupby.apply()
    
    This function operates on groups defined by ['key', 'status_category_history_name']
    So it checks for AI-enabled work within the same issue AND same status category.
    """
    # Debug: Print what we have in the group
    print(f"DEBUG: Group columns: {group.columns.tolist()}")
    print(f"DEBUG: Group shape: {group.shape}")
    
    # Check if there are any AI-enabled rows in this status category group
    ai_enabled_rows = group[group['is_ai_enabled_flag'].astype(bool) == True]
    
    if len(ai_enabled_rows) > 0:
        # If there are AI-enabled rows in this status category, get the team name from the last one
        last_ai_row = ai_enabled_rows.loc[ai_enabled_rows['entry_count'].idxmax()]
        return last_ai_row['assignee_ai_team_name']
    else:
        # If no AI-enabled rows in this status category, fall back to the last team name
        return group['assignee_ai_team_name'].iloc[-1]


def add_category_entry_exit_dates(
        df,
        issue_col,
        status_category_col,
        current_flag_col,
        entry_date_col,
        exit_date_col
    ):
    """
    Adds two columns:
      - 'first_entry_date_to_category': earliest event_date for each (issue, status_category)
      - 'last_exit_date_from_category': latest event_date where is_current_status_category_flag == False for each (issue, status_category)
        OR for closed issues, use issue_closed_date for final status categories
        
    Updated logic to handle final status categories correctly using is_issue_closed flag and issue_closed_date.
    """
    # First entry: min date per group
    first_entry = (
        df.groupby([issue_col, status_category_col])[entry_date_col]
        .min()
        .rename('first_entry_date_to_category')
        .reset_index()
    )

    # Last exit: max date per group where is_current_status_category_flag == False
    exits = df[df[current_flag_col] == False]
    last_exit = (
        exits.groupby([issue_col, status_category_col])[exit_date_col]
        .max()
        .rename('last_exit_date_from_category')
        .reset_index()
    )
    
    # For closed issues, add exit dates for final status categories using issue_closed_date
    if 'is_issue_closed' in df.columns and 'issue_closed_date' in df.columns:
        # Find current status categories for closed issues
        closed_current_status = df[
            (df[current_flag_col] == True) & 
            (df['is_issue_closed'] == True) & 
            (df['issue_closed_date'].notna())
        ].copy()
        
        if not closed_current_status.empty:
            # Create exit dates for final status categories using issue_closed_date
            closed_exits = (
                closed_current_status.groupby([issue_col, status_category_col])['issue_closed_date']
                .max()
                .rename('last_exit_date_from_category')
                .reset_index()
            )
            
            # Combine regular exits with closed issue exits
            # Use concat and then group by to get the max date for each combination
            all_exits = pd.concat([last_exit, closed_exits], ignore_index=True)
            last_exit = (
                all_exits.groupby([issue_col, status_category_col])['last_exit_date_from_category']
                .max()
                .reset_index()
            )
            
            logger.info(f"Added exit dates for {len(closed_exits)} final status categories in closed issues")
    else:
        if 'is_issue_closed' not in df.columns:
            logger.warning("is_issue_closed column not found, using legacy exit date logic")
        if 'issue_closed_date' not in df.columns:
            logger.warning("issue_closed_date column not found, using legacy exit date logic")
    
    # Merge both back to original DataFrame
    df = df.merge(first_entry, on=[issue_col, status_category_col], how='left')
    df = df.merge(last_exit, on=[issue_col, status_category_col], how='left')
    
    return df


def add_count_entries_and_exist_to_status_category_per_issue(
        df,
        issue_col,
        status_category_col,
        current_flag_col,
        date_col
    ):
    """
    For each issue, counts how many times it enters and exits a given status_category.
    Entry = when a status is different from the previous one.
    Exit = when a status is different from the next one, OR when the issue is closed.
    
    Updated logic to handle final status categories correctly using is_issue_closed flag.
    """
    # Sort by issue and date to ensure proper sequencing
    df = df.sort_values(by=[issue_col, date_col]).copy()

    # Shift status_category within each issue to get previous and next
    df['prev_status'] = df.groupby(issue_col)[status_category_col].shift(1)
    df['next_status'] = df.groupby(issue_col)[status_category_col].shift(-1)

    # An entry happens when current != previous OR previous is NaN
    df['entry_flag'] = (df[status_category_col] != df['prev_status']) | df['prev_status'].isna()
    
    # An exit happens in two cases:
    # 1. Normal exit: current != next AND next is not NaN
    # 2. Final exit: next is NaN AND issue is closed (using is_issue_closed flag)
    normal_exit = (df[status_category_col] != df['next_status']) & df['next_status'].notna()
    
    # Check if is_issue_closed column exists, if not fall back to old logic
    if 'is_issue_closed' in df.columns:
        final_exit = df['next_status'].isna() & (df['is_issue_closed'] == True)
        df['exit_flag'] = normal_exit | final_exit
    else:
        # Fallback to old logic if is_issue_closed is not available
        df['exit_flag'] = normal_exit
        logger.warning("is_issue_closed column not found, using legacy exit counting logic")

    # Count entries and exits by grouping issue + status
    entry_counts = df[df['entry_flag']].groupby([issue_col, status_category_col]).size().reset_index(name='count_entries_to_status_category_per_issue')
    exit_counts = df[df['exit_flag']].groupby([issue_col, status_category_col]).size().reset_index(name='count_exits_from_status_category_per_issue')

    # Merge counts back to original DataFrame
    df = df.merge(entry_counts, on=[issue_col, status_category_col], how='left')
    df = df.merge(exit_counts, on=[issue_col, status_category_col], how='left')

    # Fill NaN with 0 for counts
    df['count_entries_to_status_category_per_issue'] = df['count_entries_to_status_category_per_issue'].fillna(0).astype(int)
    df['count_exits_from_status_category_per_issue'] = df['count_exits_from_status_category_per_issue'].fillna(0).astype(int)

    # Clean up temporary columns
    df.drop(columns=['prev_status', 'next_status', 'entry_flag', 'exit_flag'], inplace=True)

    return df


def add_date_parts(df, date_col_name):
    # Adds ISO week, year-week, year-month and year columns

    try:
        # Ensure the column is datetime
        df[date_col_name] = pd.to_datetime(df[date_col_name], errors='coerce')

        # Extract ISO calendar components (year, week, weekday)
        iso_calendar = df[date_col_name].dt.isocalendar()

        # Create derived columns
        df[f'{date_col_name}_week_iso'] = iso_calendar['week']

        df[f'{date_col_name}_year_week_iso'] = (
            iso_calendar['year'].astype('Int64').astype(str) + '-' +
            iso_calendar['week'].astype('Int64').astype(str).str.zfill(2)
        ).where(df[date_col_name].notna(), None)

        df[f'{date_col_name}_year_month_iso'] = (
            df[date_col_name].dt.to_period('M').astype(str)
        ).where(df[date_col_name].notna(), None)

        df[f'{date_col_name}_year_iso'] = (
            df[date_col_name].dt.year
        ).where(df[date_col_name].notna(), None)

        return df

    except Exception as e:
        logger.error(f"Error processing column '{date_col_name}': {e}")
        return df


def add_is_forward_category_transition_flag(df, history_seq_col, current_seq_col, category_col, done_category):

    # Initialize with NaN (will be skipped for Done)
    df['is_forward_category_transition_flag'] = pd.NA

    # Mask for non-Done categories
    mask = df[category_col] != done_category

    # Apply the logic only for non-Done rows
    df.loc[mask, 'is_forward_category_transition_flag'] = (
        df.loc[mask, history_seq_col] < df.loc[mask, current_seq_col]
    ).map({True: "TRUE", False: "FALSE"})

    return df


def add_is_productive_efforts_flag(df, category_col, target_col):

    # Treat NaNs as 'waiting' so they're considered non-productive
    normalized = df[category_col].fillna('waiting').str.lower()

    # Boolean Series: True if not productive (starts with 'waiting')
    is_not_productive = normalized.str.startswith('waiting')

    # Map boolean to string labels
    df[target_col] = is_not_productive.map({True: "FALSE", False: "TRUE"})

    return df


def calculate_lead_time_median(lead_time_df):
    # Calculate the median lead time (in hours) for resolved, AI-enabled issues

    # Check if the DataFrame is empty
    if lead_time_df.empty:
        lead_time_df['Median Lead Time (hours)'] = None
        return lead_time_df

    # Calculate the median lead time per month
    median_per_month_df = lead_time_df.groupby('Month')['Lead time (hours)'].median().reset_index()
    median_per_month_df.rename(columns={'Lead time (hours)': 'Median Lead Time (hours)'}, inplace=True)

    # Merge the median values back into the original DataFrame
    lead_time_df = lead_time_df.merge(median_per_month_df, on='Month', how='left')

    # Drop the original 'Lead time (hours)' column
    lead_time_df = lead_time_df.drop(columns=['Lead time (hours)'])

    # Reorder columns
    lead_time_df = lead_time_df[
        ['Account', 'Project Code', 'Team', 'issue_type', 'Month', 'Median Lead Time (hours)', '# of issues completed']
    ]

    return lead_time_df


def add_sprint_id_last_exit_from_status_category(status_category_level_agg_df, sprints_df):
    """
    Add sprint_id_last_exit_from_status_category column by finding which sprint 
    was active when the issue exited the status category.
    
    Args:
        status_category_level_agg_df: DataFrame with status category data including 
                                      'last_exit_date_from_category'
        sprints_df: DataFrame with sprint information including 
                    'sprint_name', 'sprint_id', 'sprint_start_date', 'sprint_end_date'
    
    Returns:
        DataFrame with added 'sprint_id_last_exit_from_status_category' column
    """
    
    # Create a copy to avoid modifying the original
    df = status_category_level_agg_df.copy()
    
    # Ensure datetime columns are timezone-naive for comparison
    if 'last_exit_date_from_category' in df.columns:
        df['last_exit_date_from_category'] = pd.to_datetime(df['last_exit_date_from_category'], utc=True).dt.tz_convert(None)
    
    # Create a copy of sprints_df and ensure datetime columns are timezone-naive
    sprints_df_copy = sprints_df.copy()
    for col in ['sprint_start_date', 'sprint_end_date']:
        if col in sprints_df_copy.columns:
            # Handle timezone-aware datetime conversion properly
            try:
                sprints_df_copy[col] = pd.to_datetime(sprints_df_copy[col], utc=True).dt.tz_convert(None)
            except Exception:
                # If conversion fails, try direct conversion and then localize
                sprints_df_copy[col] = pd.to_datetime(sprints_df_copy[col], errors='coerce')
                if sprints_df_copy[col].dt.tz is not None:
                    sprints_df_copy[col] = sprints_df_copy[col].dt.tz_convert(None)
                else:
                    sprints_df_copy[col] = sprints_df_copy[col].dt.tz_localize(None)
    
    # Create a unique identifier for each row
    df['_row_id'] = df.index
    
    # Create a subset containing only rows that have exit dates
    df_with_dates = df[df['last_exit_date_from_category'].notna()].copy()
    
    # Create Cross Join (Cartesian Product) to check all sprint combinations (optimization, instead of looping through each row)
    # Adds a dummy column _key=1 to both dataframes and merges on this key, creating every possible combination of status_category rows × sprints
    if not df_with_dates.empty and not sprints_df_copy.empty:
        # Create cross join
        df_with_dates['_key'] = 1
        sprints_df_copy['_key'] = 1
        
        cross_df = df_with_dates[['_row_id', 'last_exit_date_from_category', '_key']].merge(
            sprints_df_copy[['sprint_id', 'sprint_name', 'sprint_start_date', 'sprint_end_date', '_key']],
            on='_key'
        ).drop('_key', axis=1)
        
        # Filter to keep only rows where the exit date falls within the sprint dates
        # Uses vectorized comparison (much faster than loops)
        valid_sprints = cross_df[
            (cross_df['last_exit_date_from_category'] > cross_df['sprint_start_date']) &
            (cross_df['last_exit_date_from_category'] <= cross_df['sprint_end_date'])
        ].copy()
        
        # Handle Multiple Matches
        if not valid_sprints.empty:
            # Get sprint with earliest end date for each row
            valid_sprints = valid_sprints.sort_values(['_row_id', 'sprint_end_date'])
            earliest_sprints = valid_sprints.groupby('_row_id').first().reset_index()
            sprint_mapping = dict(zip(earliest_sprints['_row_id'], earliest_sprints['sprint_id'].astype(str)))
        else:
            sprint_mapping = {}
    else:
        sprint_mapping = {}
    
    # Apply Results (the mapping) Back
    df['sprint_id_last_exit_from_status_category'] = df['_row_id'].map(sprint_mapping).fillna('-999')
    
    # Clean up
    df = df.drop(columns=['_row_id'])
    
    # Log summary statistics
    total_rows = len(df)
    no_exit_date = df['last_exit_date_from_category'].isna().sum()
    found_sprints = (df['sprint_id_last_exit_from_status_category'] != '-999').sum()
    
    logger.info(f"Step 31: - ✅ Added sprint_id_last_exit_from_status_category: {found_sprints}/{total_rows} rows matched to sprints")
    logger.info(f"Step 31: - ⚠️  Rows with no exit date: {no_exit_date} and rows with exit outside any sprint: {total_rows - no_exit_date - found_sprints}")
    
    return df


def add_derived_sprint_columns(status_category_level_agg_df, sprints_df):
    """
    Add multiple derived columns related to sprints and data availability flags.
    
    Columns added:
    - sprint_name_last_exit_from_status_category
    - is_sprint_data_available_for_category_flag
    - is_sprint_data_available_for_issue_flag
    - is_story_points_available_at_issue_flag
    - issue_sprint_id_last
    
    Args:
        status_category_level_agg_df: DataFrame with existing sprint and issue data
        sprints_df: DataFrame with sprint_id and sprint_name columns
    
    Returns:
        DataFrame with all new columns added
    """
    
    # Create a copy to avoid modifying the original
    df = status_category_level_agg_df.copy()
    
    # 1. Add sprint_name_last_exit_from_status_category
    sprint_id_to_name = dict(zip(
        sprints_df['sprint_id'].astype(str), 
        sprints_df['sprint_name']
    ))
    sprint_id_to_name['-999'] = 'Undefined'
    df['sprint_name_last_exit_from_status_category'] = (
        df['sprint_id_last_exit_from_status_category']
        .map(sprint_id_to_name)
        .fillna('Undefined')
    )
    
    # 2. Add is_sprint_data_available_for_category_flag
    # TRUE if sprint_id_last_exit_from_status_category is not '-999'
    df['is_sprint_data_available_for_category_flag'] = (
        df['sprint_id_last_exit_from_status_category'] != '-999'
    )
    
    # 3. Add is_sprint_data_available_for_issue_flag
    # TRUE if issue_sprint has data (not null/empty)
    df['is_sprint_data_available_for_issue_flag'] = (
        df['issue_sprint'].notna() & 
        (df['issue_sprint'].astype(str).str.strip() != '')
    )
    
    # 4. Add is_story_points_available_at_issue_flag
    # TRUE if issue_story_points has a value
    df['is_story_points_available_at_issue_flag'] = df['issue_story_points'].notna()
    
    # 5. Add issue_sprint_id_last (optimized version using vectorized operations)
    # Create a sprint name to ID mapping
    sprint_name_to_id = dict(zip(
        sprints_df['sprint_name'], 
        sprints_df['sprint_id'].astype(str)
    ))
    
    # Extract last sprint name using vectorized string operations
    # Handle null values by filling with empty string
    df['_last_sprint_name'] = (
        df['issue_sprint']
        .fillna('')
        .str.split(',')
        .str[-1]
        .str.strip()
    )
    
    # Map to sprint ID
    df['issue_sprint_id_last'] = (
        df['_last_sprint_name']
        .map(sprint_name_to_id)
        .fillna('-999')
    )
    
    # Handle empty strings
    df.loc[df['_last_sprint_name'] == '', 'issue_sprint_id_last'] = '-999'
    
    # Clean up temporary column
    df = df.drop(columns=['_last_sprint_name'])

    logger.info("Step 32: - ✅ Added derived sprint columns.")
    # logger.info(f"         -    sprint_name_last_exit_from_status_category: {(df['sprint_name_last_exit_from_status_category'] != 'Undefined').sum()} rows with sprint names")
    # logger.info(f"         -    is_sprint_data_available_for_category_flag: {df['is_sprint_data_available_for_category_flag'].sum()} TRUE values")
    # logger.info(f"         -    is_sprint_data_available_for_issue_flag: {df['is_sprint_data_available_for_issue_flag'].sum()} TRUE values")
    # logger.info(f"         -    is_story_points_available_at_issue_flag: {df['is_story_points_available_at_issue_flag'].sum()} TRUE values")
    # logger.info(f"         -    issue_sprint_id_last: {(df['issue_sprint_id_last'] != '-999').sum()} rows with valid sprint IDs")
    
    return df


def propagate_ai_work_from_subtasks_to_parents(status_category_level_agg_df):
    """
    Propagate AI-enabled work flags and team names from subtasks to their parent issues.
    
    This function:
    1. Identifies subtasks with AI-enabled work (issue_type contains 'subtask'/'sub-task' and is_ai_enabled_work_flag = TRUE)
    2. Finds their parent issues using parent_issue_id
    3. Updates parent issues that have is_ai_enabled_work_flag = FALSE to TRUE
    4. Copies the assignee_ai_team_name from the subtask to the parent issue
    
    Args:
        status_category_level_agg_df: DataFrame with status category aggregated data
    
    Returns:
        DataFrame with updated AI-enabled work flags and team names for parent issues
    """
    
    # Create a copy to avoid modifying the original
    df = status_category_level_agg_df.copy()
    
    # Step 1: Identify AI-enabled subtasks
    subtask_mask = (
        df['issue_type'].str.lower().str.contains('sub-task|subtask', na=False) &
        (df['is_ai_enabled_work_flag'] == 'TRUE') &
        df['parent_issue_id'].notna()
    )
    
    ai_subtasks = df[subtask_mask].copy()
    
    if ai_subtasks.empty:
        logger.info("Step 28.5: - ⚠️  No AI-enabled subtasks found. No parent updates needed.")
        return df
    
    logger.info(f"Step 28.5: - 📊 Found {len(ai_subtasks)} AI-enabled subtasks with parent issues.")
    
    # Step 2: Create mapping from parent_issue_id to subtask team name
    # If multiple subtasks exist for the same parent, we'll use the first one found
    parent_to_team_map = ai_subtasks.groupby('parent_issue_id')['assignee_ai_team_name'].first().to_dict()
    
    # Step 3: Identify parent issues that need updates
    parent_mask = (
        df['key'].isin(parent_to_team_map.keys()) &
        (df['is_ai_enabled_work_flag'] == 'FALSE')
    )
    
    parents_to_update = df[parent_mask].copy()
    
    if parents_to_update.empty:
        logger.info("Step 28.5: - ⚠️  No parent issues with FALSE AI flags found that need updates.")
        return df
    
    logger.info(f"Step 28.5: - 🔄 Found {len(parents_to_update)} parent issues that will be updated to AI-enabled.")
    
    # Step 4: Update parent issues
    updates_made = 0
    
    for parent_key in parents_to_update['key'].unique():
        if parent_key in parent_to_team_map:
            # Update is_ai_enabled_work_flag to TRUE
            df.loc[df['key'] == parent_key, 'is_ai_enabled_work_flag'] = 'TRUE'
            
            # Update assignee_ai_team_name with the team name from the subtask
            df.loc[df['key'] == parent_key, 'assignee_ai_team_name'] = parent_to_team_map[parent_key]
            
            updates_made += 1
    
    logger.info(f"Step 28.5: - ✅ Successfully updated {updates_made} parent issues with AI-enabled work flags and team names.")
    
    # Log summary of what was changed
    unique_teams = set(parent_to_team_map.values())
    logger.info(f"Step 28.5: - 📋 Teams propagated to parents: {', '.join(sorted(unique_teams))}")
    
    return df


def add_completion_date_column(df, history_df, status_category_sheets):
    """
    Add issue_lead_time_completion_date column based on LAST entry to Done-mapped statuses.
    
    This function:
    1. Identifies statuses mapped to 'Done' category from status_category configuration
    2. Finds the LAST time each issue entered any Done-mapped status
    3. Only processes issues currently in Done category
    4. Uses case-insensitive status matching
    
    Args:
        df: status_category_level_agg DataFrame
        history_df: jira_history DataFrame with status transitions
        status_category_sheets: Dict containing status category configuration
        
    Returns:
        DataFrame with new issue_lead_time_completion_date column
    """
    logger.info("Step XX.1: - 🔄 Adding completion date column for Done category issues...")
    
    # Get status category mapping
    if 'status_category' not in status_category_sheets:
        logger.error("Step XX.1: - ❌ Status category configuration not found")
        df['issue_lead_time_completion_date'] = None
        return df
    
    status_mapping_df = status_category_sheets['status_category']
    
    # Find all statuses mapped to Done category (case-insensitive)
    done_statuses = status_mapping_df[
        status_mapping_df['Status Category'].str.upper() == 'DONE'
    ]['status name'].str.upper().unique()
    
    logger.info(f"Step XX.1: - 📋 Found {len(done_statuses)} statuses mapped to Done category")
    
    # Initialize completion date column
    df['issue_lead_time_completion_date'] = None
    
    # Only process issues currently in Done category
    current_done_issues = df[
        (df['issue_current_status_category'].str.upper() == 'DONE') & 
        (df['is_current_status_category_flag'] == True)
    ]['key'].unique()
    
    logger.info(f"Step XX.1: - 🎯 Processing {len(current_done_issues)} currently Done issues")
    
    completion_dates = {}
    
    for issue_key in current_done_issues:
        # Get history for this issue
        issue_history = history_df[history_df['key'] == issue_key].copy()
        
        if len(issue_history) == 0:
            continue
            
        # Find entries to Done-mapped statuses (case-insensitive)
        # Look for status_change events where the status is mapped to Done category
        status_change_events = issue_history[issue_history['event_type'] == 'status_change']
        
        # Filter for transitions TO Done-category statuses
        done_transitions = status_change_events[
            status_change_events['issue_status_history_name'].str.upper().isin(done_statuses)
        ]
        
        logger.debug(f"Issue {issue_key}: Found {len(status_change_events)} status changes, {len(done_transitions)} transitions to Done-category statuses")
        
        if len(done_transitions) > 0:
            # Get the LAST transition to any Done-category status (latest event_date)
            last_done_transition = done_transitions.loc[done_transitions['event_date'].idxmax()]
            completion_dates[issue_key] = last_done_transition['event_date']
            logger.debug(f"Issue {issue_key}: Completion date set to {last_done_transition['event_date']} (status: {last_done_transition['issue_status_history_name']})")
        else:
            # Fallback: If no status_change events found, look for any Done-category status records
            # This handles cases where event_type might be missing or ETL-calculated records
            done_entries = issue_history[
                issue_history['issue_status_history_name'].str.upper().isin(done_statuses)
            ]
            
            if len(done_entries) > 0:
                logger.warning(f"Issue {issue_key}: No status transitions to Done found, using fallback to any Done entry")
                last_done_entry = done_entries.loc[done_entries['event_date'].idxmax()]
                completion_dates[issue_key] = last_done_entry['event_date']
            else:
                logger.warning(f"Issue {issue_key}: No Done-category entries found at all")
    
    # Map completion dates back to the DataFrame
    df['issue_lead_time_completion_date'] = df['key'].map(completion_dates)
    
    completed_count = df['issue_lead_time_completion_date'].notna().sum()
    logger.info(f"Step XX.1: - ✅ Added completion dates for {completed_count} issues")
    
    return df


def calculate_completion_based_lead_time(df):
    """
    Calculate new lead time based on completion date for Done category issues.
    
    Formula: completion_date - create_date (in hours)
    Only calculates for issues that:
    - Are currently in Done category
    - Have a completion date
    - Have not been reopened (still in Done)
    
    Args:
        df: DataFrame with issue_lead_time_completion_date column
        
    Returns:
        DataFrame with updated issue_lead_time_for_ticket column
    """
    logger.info("Step XX.2: - 🔄 Calculating completion-based lead time...")
    
    # Reset all lead times to None first
    df['issue_lead_time_for_ticket'] = None
    
    # Only calculate for currently Done issues with completion dates
    eligible_mask = (
        (df['issue_current_status_category'].str.upper() == 'DONE') & 
        (df['is_current_status_category_flag'] == True) &
        (df['issue_lead_time_completion_date'].notna()) &
        (df['issue_create_date'].notna())
    )
    
    eligible_issues = df[eligible_mask].copy()
    logger.info(f"Step XX.2: - 🎯 Calculating lead time for {len(eligible_issues)} eligible issues")
    
    if len(eligible_issues) > 0:
        # Ensure datetime types
        eligible_issues['issue_lead_time_completion_date'] = pd.to_datetime(eligible_issues['issue_lead_time_completion_date'])
        eligible_issues['issue_create_date'] = pd.to_datetime(eligible_issues['issue_create_date'])
        
        # Calculate lead time in hours
        lead_times = (
            eligible_issues['issue_lead_time_completion_date'] - 
            eligible_issues['issue_create_date']
        ).dt.total_seconds() / 3600
        
        # Update the main DataFrame
        df.loc[eligible_mask, 'issue_lead_time_for_ticket'] = lead_times
        
        # Log statistics
        valid_lead_times = lead_times.dropna()
        if len(valid_lead_times) > 0:
            logger.info(f"Step XX.2: - 📊 Lead time statistics:")
            logger.info(f"Step XX.2: -    Min: {valid_lead_times.min():.2f} hours")
            logger.info(f"Step XX.2: -    Max: {valid_lead_times.max():.2f} hours")
            logger.info(f"Step XX.2: -    Mean: {valid_lead_times.mean():.2f} hours")
            logger.info(f"Step XX.2: -    Count: {len(valid_lead_times)} issues")
    
    logger.info("Step XX.2: - ✅ Completion-based lead time calculation completed")
    
    return df
