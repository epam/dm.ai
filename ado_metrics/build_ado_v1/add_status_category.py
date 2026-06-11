# add_status_category.py

import sys
import os
from config import logger, RESOURCE_PLAN_FILE, APPROVED_STATUS_CATEGORIES
import pandas as pd
import re


def parse_multi_value_status(status_value):
    """
    Parse comma or semicolon separated status values with proper case handling and whitespace trimming.
    
    Args:
        status_value: String containing single or multiple status values (e.g., "implemented, done" or "implemented;Done")
    
    Returns:
        list: List of normalized status values (lowercase, stripped)
    
    Examples:
        parse_multi_value_status("implemented, done") -> ["implemented", "done"]
        parse_multi_value_status("implemented; Done") -> ["implemented", "done"]
        parse_multi_value_status("implemented") -> ["implemented"]
        parse_multi_value_status("") -> []
        parse_multi_value_status(None) -> []
    """
    if not status_value or pd.isna(status_value):
        return []
    
    # Convert to string and handle case
    status_str = str(status_value).strip()
    
    if not status_str:
        return []
    
    # Split by comma or semicolon, then clean each value
    # Use regex to split on comma or semicolon
    values = re.split(r'[,;]', status_str)
    
    # Clean each value: strip whitespace and convert to lowercase
    cleaned_values = [val.strip().lower() for val in values if val.strip()]
    
    return cleaned_values


def check_status_match_multi_value(actual_status, expected_statuses_list):
    """
    Check if actual status matches any of the expected statuses (OR logic).
    
    Args:
        actual_status: Single status value to check
        expected_statuses_list: List of expected status values
    
    Returns:
        bool: True if actual_status matches any expected status, False otherwise
    """
    if not actual_status or not expected_statuses_list:
        return False
    
    # Normalize actual status
    actual_normalized = str(actual_status).strip().lower()
    
    # Check if actual status matches any expected status
    return actual_normalized in expected_statuses_list


def extract_unique_statuses(df, status_column):
    """Extracts and cleans unique status values from the specified column."""
    # print(f"printing input df: {df}")
    # print(f"printing input status_column name: {status_column}")
    if status_column not in df.columns:
        logger.info(f"Step 19: - ❌ Column '{status_column}' not found in the provided DataFrame.")
        sys.exit(1)

    try:
        unique_statuses = (
            df[status_column]
            .dropna()
            .astype(str)
            .str.strip()
            .str.lower()
            .drop_duplicates()
            .sort_values()
            .tolist()
        )
        logger.info(f"Step 19: - ✅ Extracted {len(unique_statuses)} unique values from column '{status_column}'")
        return unique_statuses
    except Exception as e:
        logger.info(f"Step 19: - ❌ Error processing column '{status_column}': {str(e)}")
        sys.exit(1)


def extract_unique_combinations(df, status_col, issue_type_col, project_col):
    """Extracts unique combinations of status, issue_type, and ado_project_name as a DataFrame."""

    for col in [project_col, issue_type_col, status_col]:
        if col not in df.columns:
            logger.error(f"Step 19: - ❌ Column '{col}' not found in the provided DataFrame.")
            sys.exit(1)

    try:
        combinations_df = (
            df[[project_col, issue_type_col, status_col]]
            .dropna()
            .astype(str)
            .apply(lambda col: col.str.strip().str.lower())
            .drop_duplicates()
            .sort_values(by=[issue_type_col, status_col, project_col])
            .reset_index(drop=True)
        )

        logger.info(f"Step 19: - ✅ Extracted {len(combinations_df)} unique (status, issue_type, ado_project_name) combinations.")
        return combinations_df

    except Exception as e:
        logger.error(f"Step 19: - ❌ Error extracting unique combinations: {str(e)}")
        sys.exit(1)


def extract_unique_sprint_dod_combinations(df, issue_type_col, project_col):
    """Extracts unique combinations of issue_type and ado_project_name for sprint_dod sheet."""

    for col in [project_col, issue_type_col]:
        if col not in df.columns:
            logger.error(f"Step 19: - ❌ Column '{col}' not found in the provided DataFrame.")
            sys.exit(1)

    try:
        combinations_df = (
            df[[project_col, issue_type_col]]
            .dropna()
            .astype(str)
            .apply(lambda col: col.str.strip().str.lower())
            .drop_duplicates()
            .sort_values(by=[issue_type_col, project_col])
            .reset_index(drop=True)
        )

        logger.info(f"Step 19: - ✅ Extracted {len(combinations_df)} unique (issue_type, ado_project_name) combinations for sprint_dod.")
        return combinations_df

    except Exception as e:
        logger.error(f"Step 19: - ❌ Error extracting unique sprint_dod combinations: {str(e)}")
        sys.exit(1)


def extract_sprint_usage_by_project(issues, sprint_field_name='System.IterationPath'):
    """
    Analyzes raw ADO work items to determine which projects use sprints.

    A project is considered to use sprints when at least one work item has an
    IterationPath containing a path separator ('\\'), i.e., placed in a sub-iteration
    rather than the root project path.

    Args:
        issues: List of raw ADO work item dicts (from Stage 1 JSON)
        sprint_field_name: ADO iteration path field (default: System.IterationPath)

    Returns:
        DataFrame with columns: ado project name, sprints_are_used, notes
    """
    try:
        project_sprint_usage = {}

        for issue in issues:
            # ADO stores project name in System.TeamProject
            project_key = issue.get('fields', {}).get('System.TeamProject', 'Unknown')

            # Sprint used when IterationPath has a sub-level (contains backslash)
            iteration_path = issue.get('fields', {}).get(sprint_field_name, '') or ''
            has_sprint_data = '\\' in iteration_path
            
            # Track sprint usage per project
            if project_key not in project_sprint_usage:
                project_sprint_usage[project_key] = {'has_sprints': False, 'issues_checked': 0}
            
            project_sprint_usage[project_key]['issues_checked'] += 1
            if has_sprint_data:
                project_sprint_usage[project_key]['has_sprints'] = True
        
        # Create DataFrame
        sprint_usage_data = []
        for project, usage_info in project_sprint_usage.items():
            sprints_used = usage_info['has_sprints']
            issues_count = usage_info['issues_checked']
            
            # Generate descriptive notes
            if sprints_used:
                notes = f"Auto-detected: Sprint data found in {issues_count} issues. Project uses sprints."
            else:
                notes = f"Auto-detected: No sprint data found in {issues_count} issues. Change to True if project actually uses sprints."
            
            sprint_usage_data.append({
                'ado project name': project.lower(),
                'sprints_are_used': sprints_used,
                'notes': notes
            })
        
        sprint_usage_df = pd.DataFrame(sprint_usage_data)
        sprint_usage_df = sprint_usage_df.sort_values('ado project name').reset_index(drop=True)
        
        logger.info(f"Step 19: - ✅ Analyzed sprint usage for {len(sprint_usage_df)} projects")
        return sprint_usage_df
        
    except Exception as e:
        logger.error(f"Step 19: - ❌ Error analyzing sprint usage: {str(e)}")
        sys.exit(1)


def create_sprints_usage_sheet_data(sprint_usage_df):
    """
    Prepares the sprints_usage sheet data with proper formatting and instructions.
    
    Args:
        sprint_usage_df: DataFrame from extract_sprint_usage_by_project()
    
    Returns:
        DataFrame ready for Excel export
    """
    try:
        # Create a copy and ensure proper column order
        usage_df = sprint_usage_df.copy()
        
        # Add header instructions as first row (will be handled in Excel creation)
        instructions = {
            'ado project name': 'INSTRUCTIONS: Review auto-detected values below',
            'sprints_are_used': 'Change True/False as needed',
            'notes': 'Auto-generated notes explaining detection logic'
        }
        
        # Create final dataframe with instruction row
        final_df = pd.DataFrame([instructions])
        final_df = pd.concat([final_df, usage_df], ignore_index=True)
        
        logger.info(f"Step 19: - ✅ Created sprints_usage sheet data with {len(usage_df)} projects")
        return final_df
        
    except Exception as e:
        logger.error(f"Step 19: - ❌ Error creating sprints_usage sheet data: {str(e)}")
        sys.exit(1)


def create_complete_status_template(status_combinations_df, sprint_dod_combinations_df, output_file, status_sheet, sprint_dod_sheet, issue_types_sheet, sprints_usage_df=None, sprints_usage_sheet="sprints_usage"):
    """
    Creates a complete Excel file with status_category, sprint_dod, issue_types_categories, and sprints_usage sheets.
    """
    try:
        # Prepare status_category sheet data
        status_df = status_combinations_df.copy().rename(columns={
            "ado_project_name": "ado project name",
            "issue_type": "issue type",
            "issue_status_history_name": "status name"
        })
        status_df["Status Category"] = ""

        # Prepare sprint_dod sheet data
        sprint_dod_df = sprint_dod_combinations_df.copy().rename(columns={
            "ado_project_name": "ado project name",
            "issue_type": "issue type"
        })
        sprint_dod_df["status name at sprint completion"] = ""

        # Prepare issue_types_categories sheet data (same structure as sprint_dod but different column name)
        issue_types_df = sprint_dod_combinations_df.copy().rename(columns={
            "ado_project_name": "ado project name",
            "issue_type": "issue type"
        })
        issue_types_df["standard issue type"] = ""

        # Create Excel file with all sheets
        with pd.ExcelWriter(output_file, engine='openpyxl') as writer:
            status_df.to_excel(writer, sheet_name=status_sheet, index=False)
            sprint_dod_df.to_excel(writer, sheet_name=sprint_dod_sheet, index=False)
            issue_types_df.to_excel(writer, sheet_name=issue_types_sheet, index=False)
            
            # Add sprints_usage sheet if provided
            if sprints_usage_df is not None:
                sprints_usage_formatted_df = create_sprints_usage_sheet_data(sprints_usage_df)
                sprints_usage_formatted_df.to_excel(writer, sheet_name=sprints_usage_sheet, index=False)

        logger.info(f"Step 19: - ✅ Created complete template with all sheets: '{output_file}'")
        return output_file

    except Exception as e:
        logger.error(f"Step 19: - ❌ Error creating complete Excel template '{output_file}': {str(e)}")
        sys.exit(1)


def validate_status_categories_file(
    file_path,
    STATUS_CATEGORY_SHEET,
    APPROVED_STATUS_CATEGORIES,
    status_column,
    unique_statuses
):
    """
    Validates the status categories mapping file:
    - Ensures all 'Status Category' values are from the approved list (case-insensitive)
    - Ensures all unique_statuses are present in the mapping file
    """

    # Normalize approved categories to lowercase for comparison
    approved_status_categories_lower = {cat.lower() for cat in APPROVED_STATUS_CATEGORIES}

    try:
        df = pd.read_excel(file_path, sheet_name=STATUS_CATEGORY_SHEET)
        df.columns = df.columns.str.strip()

        # Check required columns exist
        required_columns = {"ado project name", "issue type", "status name", "Status Category"}
        missing_cols = required_columns - set(df.columns)
        if missing_cols:
            logger.info(f"Step 20: - ❌ Missing required columns in file: {missing_cols}")
            return False

        # Check for missing 'Status Category' values
        empty = df[df["Status Category"].isna() | (df["Status Category"].astype(str).str.strip() == "")]
        if not empty.empty:
            logger.info(f"Step 20: - WARNING: {len(empty)} unassigned categories:")
            for status in empty[status_column].values[:5]:
                logger.info(f"  - {status}")
            if len(empty) > 5:
                logger.info(f"  - ... and {len(empty) - 5} more")
            logger.error(f"Step 20: - ⚠️  Please fill in all 'Status Category' values from this list: {APPROVED_STATUS_CATEGORIES} and rerun.")

            return False  # Treat missing mappings as blocking

        # Normalize values in the file
        df["Status Category Lowercased"] = df["Status Category"].astype(str).str.strip().str.lower()

        # Identify invalid values
        invalid = df[~df["Status Category Lowercased"].isin(approved_status_categories_lower)]

        if not invalid.empty:
            logger.info(f"Step 20: - ❌ Found {len(invalid)} invalid status categories:")
            for _, row in invalid.head(5).iterrows():
                status = row.get(status_column, "N/A")
                category = row.get("Status Category", "N/A")
                logger.info(f"  - {status} → {category}")
            if len(invalid) > 5:
                logger.info(f"  - ... and {len(invalid) - 5} more")
            logger.info("Step 20: - ⚠️ Please fix these invalid 'Status Category' values. Allowed values are:")
            for cat in sorted(approved_status_categories_lower):
                logger.info(f"  - {cat}")
            return False  # Treat invalid values as blocking

        # Check all unique_statuses are present in 'Status Categories prepared.xlsx' file
        mapped_statuses = set(df[status_column].astype(str).str.strip().str.lower())
        unique_statuses_lower = set(str(s).strip().lower() for s in unique_statuses)
        missing_statuses = unique_statuses_lower - mapped_statuses
        if missing_statuses:
            logger.info(f"Step 20: - ❌ The following statuses are missing from the mapping file:")
            for status in list(missing_statuses)[:5]:
                logger.info(f"           - {status}")
            if len(missing_statuses) > 5:
                logger.info(f"           - ... and {len(missing_statuses) - 5} more")
            logger.info("Step 20: - ⚠️  ensure all statuses are present in the mapping file - I recommend to delete 'Status Categories prepared.xlsx', rerun this script and fill the file from scratch.")
            return False

        return True

    except Exception as e:
        logger.info(f"Step 20: - ❌ Error validating status categories file: {str(e)}")
        return False


def validate_sprint_dod_file(
    file_path,
    SPRINT_DOD_SHEET,
    unique_statuses,
    status_category_df=None,
    status_column="status name at sprint completion"
):
    """
    Validates the sprint_dod mapping file:
    - Ensures all 'status name at sprint completion' values are from the actual status names in the data
    - Ensures no missing values in the mapping
    - Ensures all combinations from actual data are present in the mapping
    """
    try:
        df = pd.read_excel(file_path, sheet_name=SPRINT_DOD_SHEET)
        df.columns = df.columns.str.strip()

        # Check required columns exist
        required_columns = {"ado project name", "issue type", status_column}
        missing_cols = required_columns - set(df.columns)
        if missing_cols:
            logger.info(f"Step 20b: - ❌ Missing required columns in sprint_dod sheet: {missing_cols}")
            return False

        # Check for missing values
        empty = df[df[status_column].isna() | (df[status_column].astype(str).str.strip() == "")]
        if not empty.empty:
            logger.info(f"Step 20b: - ❌ Found {len(empty)} unassigned values in sprint_dod sheet:")
            for _, row in empty.head(5).iterrows():
                project = row.get("ado project name", "N/A")
                issue_type = row.get("issue type", "N/A")
                logger.info(f"  - {project} → {issue_type}")
            if len(empty) > 5:
                logger.info(f"  - ... and {len(empty) - 5} more")
            logger.error(f"Step 20b: - ⚠️  Please fill in all '{status_column}' values and rerun.")
            return False

        # Normalize values for comparison
        unique_statuses_lower = set(str(s).strip().lower() for s in unique_statuses)
        
        # Parse all mapped statuses (including multi-value entries) and validate each individual status
        all_mapped_statuses = set()
        invalid_status_entries = []
        
        for _, row in df.iterrows():
            status_value = row[status_column]
            project = row.get('ado project name', 'Unknown')
            issue_type = row.get('issue type', 'Unknown')
            
            # Parse multi-value status (handles both single and multi-value)
            parsed_statuses = parse_multi_value_status(status_value)
            
            if not parsed_statuses:
                # Empty or invalid status
                continue
                
            # Check each individual status
            for individual_status in parsed_statuses:
                all_mapped_statuses.add(individual_status)
                
                # Check if this individual status exists in actual data
                if individual_status not in unique_statuses_lower:
                    invalid_status_entries.append({
                        'project': project,
                        'issue_type': issue_type,
                        'full_status_value': status_value,
                        'invalid_individual_status': individual_status
                    })
        
        # Report invalid statuses
        if invalid_status_entries:
            logger.info(f"Step 20b: - ❌ Found {len(invalid_status_entries)} invalid individual status names in sprint_dod sheet:")
            for entry in invalid_status_entries[:5]:
                logger.info(f"  - Project: {entry['project']}, Issue Type: {entry['issue_type']}")
                logger.info(f"    Full value: \"{entry['full_status_value']}\"")
                logger.info(f"    Invalid status: \"{entry['invalid_individual_status']}\"")
            if len(invalid_status_entries) > 5:
                logger.info(f"  - ... and {len(invalid_status_entries) - 5} more")
            logger.info(f"Step 20b: - ℹ️  Multi-value entries should use comma (,) or semicolon (;) separators")
            logger.info(f"Step 20b: - ⚠️  Valid status names are: {sorted(unique_statuses_lower)}")
            return False

        # Check if all combinations from actual data are present in sprint_dod mapping
        if status_category_df is not None:
            # Extract unique combinations from actual data
            actual_combinations = set()
            for _, row in status_category_df.iterrows():
                project = str(row['ado_project_name']).strip().lower()
                issue_type = str(row['issue_type']).strip().lower()
                actual_combinations.add((project, issue_type))
            
            # Extract combinations from sprint_dod mapping
            mapped_combinations = set()
            for _, row in df.iterrows():
                project = str(row['ado project name']).strip().lower()
                issue_type = str(row['issue type']).strip().lower()
                mapped_combinations.add((project, issue_type))
            
            # Check for missing combinations
            missing_combinations = actual_combinations - mapped_combinations
            if missing_combinations:
                logger.info(f"Step 20b: - ❌ Found {len(missing_combinations)} missing combinations in sprint_dod sheet:")
                for project, issue_type in list(missing_combinations)[:5]:
                    logger.info(f"  - {project} → {issue_type}")
                if len(missing_combinations) > 5:
                    logger.info(f"  - ... and {len(missing_combinations) - 5} more")
                logger.error(f"Step 20b: - ⚠️  Please add these missing combinations to sprint_dod sheet and rerun.")
                return False

        return True

    except Exception as e:
        logger.info(f"Step 20b: - ❌ Error validating sprint_dod file: {str(e)}")
        return False


def validate_issue_types_categories_file(
    file_path,
    ISSUE_TYPES_CATEGORIES_SHEET,
    APPROVED_STANDARD_ISSUE_TYPES,
    mapping_column="standard issue type"
):
    """
    Validates the issue_types_categories mapping file:
    - Ensures all 'standard issue type' values are from the approved list
    - Ensures no missing values in the mapping
    """
    
    # Normalize approved types to lowercase for comparison
    approved_types_lower = {cat.lower() for cat in APPROVED_STANDARD_ISSUE_TYPES}
    
    try:
        df = pd.read_excel(file_path, sheet_name=ISSUE_TYPES_CATEGORIES_SHEET)
        df.columns = df.columns.str.strip()

        # Check required columns exist
        required_columns = {"ado project name", "issue type", mapping_column}
        missing_cols = required_columns - set(df.columns)
        if missing_cols:
            logger.info(f"Step 20c: - ❌ Missing required columns in issue_types_categories sheet: {missing_cols}")
            return False

        # Check for missing values
        empty = df[df[mapping_column].isna() | (df[mapping_column].astype(str).str.strip() == "")]
        if not empty.empty:
            logger.info(f"Step 20c: - ❌ Found {len(empty)} unassigned values in issue_types_categories sheet:")
            for _, row in empty.head(5).iterrows():
                project = row.get("ado project name", "N/A")
                issue_type = row.get("issue type", "N/A")
                logger.info(f"  - {project} → {issue_type}")
            if len(empty) > 5:
                logger.info(f"  - ... and {len(empty) - 5} more")
            logger.error(f"Step 20c: - ⚠️  Please fill in all '{mapping_column}' values and rerun.")
            return False

        # Normalize values in the file
        df["mapping_lowercased"] = df[mapping_column].astype(str).str.strip().str.lower()

        # Identify invalid values
        invalid = df[~df["mapping_lowercased"].isin(approved_types_lower)]
        if not invalid.empty:
            logger.info(f"Step 20c: - ❌ Found {len(invalid)} invalid standard issue types:")
            for _, row in invalid.head(5).iterrows():
                project = row.get("ado project name", "N/A")
                issue_type = row.get("issue type", "N/A")
                mapping = row.get(mapping_column, "N/A")
                logger.info(f"  - {project} → {issue_type} → {mapping}")
            if len(invalid) > 5:
                logger.info(f"  - ... and {len(invalid) - 5} more")
            logger.info("Step 20c: - ⚠️ Please fix these invalid values. Allowed values are:")
            for cat in sorted(approved_types_lower):
                logger.info(f"  - {cat}")
            return False

        return True

    except Exception as e:
        logger.info(f"Step 20c: - ❌ Error validating issue_types_categories file: {str(e)}")
        return False


def map_status_category_multi(
    status_category_df,
    mapping_df,
    source_col_1,
    source_col_2,
    source_col_3,
    map_col_1,
    map_col_2,
    map_col_3,
    map_col_4,
    target_col
):

    try:
        # Normalize join columns: strip and lowercase
        for col in [source_col_1, source_col_2, source_col_3]:
            status_category_df[col] = status_category_df[col].astype(str).str.strip().str.lower()

        for col in [map_col_1, map_col_2, map_col_3]:
            mapping_df[col] = mapping_df[col].astype(str).str.strip().str.lower()

        # Perform left merge
        merged_df = pd.merge(
            status_category_df,
            mapping_df[[map_col_1, map_col_2, map_col_3, map_col_4]],
            how='left',
            left_on=[source_col_1, source_col_2, source_col_3],
            right_on=[map_col_1, map_col_2, map_col_3],
            suffixes=('', '_drop')
        )

        # Drop any columns that were suffixed (i.e., duplicates from right DataFrame)
        merged_df = merged_df[[col for col in merged_df.columns if not col.endswith('_drop')]]

        # Rename new column
        merged_df = merged_df.rename(columns={map_col_4: target_col})

        return merged_df

    except Exception as e:
        logger.error(f"❌ ERROR in map_status_category_multi: {str(e)}")
        raise


def handle_missing_categories(history_df):
    # Checks for any rows in the main task data where Status Category is still empty or NaN and replaces them with "Unknown"

    missing = (
        history_df.loc[history_df["Status Category"].isna() | (history_df["Status Category"] == ""), "Status"]
        .dropna()
        .astype(str)
        .str.strip()
        .str.lower()
        .drop_duplicates()
        .tolist()
    )

    if len(missing) > 0:
        logger.info(f"WARNING: {len(missing)} statuses missing mapping:")
        for status in missing[:5]:
            logger.info(f"  - {status}")
        if len(missing) > 5:
            logger.info(f"  - ... and {len(missing) - 5} more")

        # Ensure column is of type 'object' so strings can safely be assigned
        history_df["Status Category"] = history_df["Status Category"].astype(object)

        # Replace empty strings and NaN with "Unknown"
        history_df["Status Category"] = history_df["Status Category"].replace("", "Unknown")
        history_df["Status Category"] = history_df["Status Category"].fillna("Unknown")

    return history_df


def add_status_category_sequence(df, source_col, output_col, sequence_df):
    """
    Adds a new column 'issue_current_status_category_seq_no' to df,
    by mapping values in `source_col` to 'SeqNo' from sequence_df.
    """

    # Normalize mapping DataFrame for reliable lookup
    sequence_df_clean = sequence_df.copy()
    sequence_df_clean.columns = sequence_df_clean.columns.str.strip().str.lower()
    sequence_df_clean["status categories"] = sequence_df_clean["status categories"].astype(str).str.strip().str.lower()

    # Create mapping dictionary
    mapping_dict = dict(zip(sequence_df_clean["status categories"], sequence_df_clean["seqno"]))

    # Normalize source column in df before mapping
    df[source_col] = df[source_col].where(df[source_col].isna(), df[source_col].str.strip().str.lower())

    # Map to new column
    df[output_col] = df[source_col].map(mapping_dict)

    return df


def load_sprints_usage_config(file_path, sprints_usage_sheet="sprints_usage"):
    """
    Loads sprint usage configuration from the sprints_usage sheet.
    
    Args:
        file_path: Path to the Status Categories prepared.xlsx file
        sprints_usage_sheet: Name of the sprints_usage sheet
    
    Returns:
        Dictionary mapping project names to sprint usage flags
        Format: {'project_name': True/False}
    """
    try:
        df = pd.read_excel(file_path, sheet_name=sprints_usage_sheet)
        df.columns = df.columns.str.strip()
        
        # Skip instruction row (first row) if it exists
        if len(df) > 0 and 'INSTRUCTIONS' in str(df.iloc[0]['ado project name']).upper():
            df = df.iloc[1:].reset_index(drop=True)
        
        # Create mapping dictionary
        sprint_config = {}
        for _, row in df.iterrows():
            project_name = str(row['ado project name']).strip().lower()
            sprints_used = bool(row['sprints_are_used'])
            sprint_config[project_name] = sprints_used
        
        logger.info(f"Step 20d: - ✅ Loaded sprint usage configuration for {len(sprint_config)} projects")
        return sprint_config
        
    except Exception as e:
        logger.error(f"Step 20d: - ❌ Error loading sprint usage configuration: {str(e)}")
        return {}


def validate_sprints_usage_file(file_path, sprints_usage_sheet="sprints_usage", status_category_df=None):
    """
    Validates the sprints_usage sheet:
    - Ensures required columns exist
    - Ensures no missing values
    - Ensures all projects from actual data are present
    """
    try:
        df = pd.read_excel(file_path, sheet_name=sprints_usage_sheet)
        df.columns = df.columns.str.strip()

        # Check required columns exist
        required_columns = {"ado project name", "sprints_are_used", "notes"}
        missing_cols = required_columns - set(df.columns)
        if missing_cols:
            logger.info(f"Step 20d: - ❌ Missing required columns in sprints_usage sheet: {missing_cols}")
            return False

        # Skip instruction row if it exists
        if len(df) > 0 and 'INSTRUCTIONS' in str(df.iloc[0]['ado project name']).upper():
            df = df.iloc[1:].reset_index(drop=True)

        # Check for missing values in critical columns
        empty_project = df[df['ado project name'].isna() | (df['ado project name'].astype(str).str.strip() == "")]
        empty_usage = df[df['sprints_are_used'].isna()]
        
        if not empty_project.empty:
            logger.info(f"Step 20d: - ❌ Found {len(empty_project)} rows with missing project names")
            return False
            
        if not empty_usage.empty:
            logger.info(f"Step 20d: - ❌ Found {len(empty_usage)} rows with missing sprints_are_used values")
            return False

        # Validate sprints_are_used values are boolean-like
        invalid_usage = df[~df['sprints_are_used'].isin([True, False, 'True', 'False', 'TRUE', 'FALSE', 1, 0])]
        if not invalid_usage.empty:
            logger.info(f"Step 20d: - ❌ Found {len(invalid_usage)} rows with invalid sprints_are_used values")
            logger.info("Step 20d: - ⚠️  Valid values are: True, False")
            return False

        # Check if all projects from actual data are present in sprints_usage
        if status_category_df is not None:
            actual_projects = set(status_category_df['ado_project_name'].astype(str).str.strip().str.lower().unique())
            configured_projects = set(df['ado project name'].astype(str).str.strip().str.lower())
            
            missing_projects = actual_projects - configured_projects
            if missing_projects:
                logger.info(f"Step 20d: - ❌ Found {len(missing_projects)} projects missing from sprints_usage sheet:")
                for project in list(missing_projects)[:5]:
                    logger.info(f"  - {project}")
                if len(missing_projects) > 5:
                    logger.info(f"  - ... and {len(missing_projects) - 5} more")
                logger.error(f"Step 20d: - ⚠️  Please add these missing projects to sprints_usage sheet and rerun.")
                return False

        return True

    except Exception as e:
        logger.info(f"Step 20d: - ❌ Error validating sprints_usage file: {str(e)}")
        return False


def add_status_category_column(status_category_df, mapping_df):
    try:
        # 1 add current (most recent) status_category column to status_category_df
        status_category_df = map_status_category_multi(
            status_category_df,
            mapping_df,
            source_col_1="ado_project_name",
            source_col_2="issue_type",
            source_col_3="issue_current_status_name",
            map_col_1="ado project name",
            map_col_2="issue type",
            map_col_3="status name",
            map_col_4="Status Category",
            target_col="issue_current_status_category"
        )

        # 2 Read the 'sequence' sheet from the Excel file.
        # If resource plan is not available, fallback to approved status category order.
        try:
            sequence_df = pd.read_excel(RESOURCE_PLAN_FILE, sheet_name="sequence", usecols="A:B")
        except Exception:
            sequence_df = pd.DataFrame(
                {
                    "status categories": APPROVED_STATUS_CATEGORIES,
                    "seqno": list(range(1, len(APPROVED_STATUS_CATEGORIES) + 1)),
                }
            )
        
        # 3 add current (most recent) category_sequence column to status_category_df
        status_category_df = add_status_category_sequence(status_category_df, "issue_current_status_category", "issue_current_status_category_seq_no", sequence_df)

        # 4 add historic (as it changed through each status change) status_category column
        status_category_df = map_status_category_multi(
            status_category_df,
            mapping_df,
            source_col_1="ado_project_name",
            source_col_2="issue_type",
            source_col_3="issue_status_history_name",
            map_col_1="ado project name",
            map_col_2="issue type",
            map_col_3="status name",
            map_col_4="Status Category",
            target_col="status_category_history_name"
        )

        # 5 add historic (as it changed through each status change) category sequence column
        status_category_df = add_status_category_sequence(status_category_df, "status_category_history_name", "status_category_history_seq_no", sequence_df)

        # status_category_df = handle_missing_categories(status_category_df)
        return status_category_df, sequence_df

    except Exception as e:
        logger.info(f"Error adding Status Category: {str(e)}")
        return None, None


def add_sprint_dod_mapping_column(history_df, sprint_dod_mapping_df, sprint_usage_config=None):
    """
    Adds 'issue_status_expected_at_sprint_completion' column to history_df
    by mapping ado_project_name and issue_type to sprint_dod mapping.
    For projects where sprints are disabled, sets value to 'N/A'.
    
    Args:
        history_df: DataFrame with history data
        sprint_dod_mapping_df: DataFrame with sprint DOD mappings
        sprint_usage_config: Dict mapping project names to sprint usage flags
    """
    try:
        # Initialize the new column with default value
        history_df['issue_status_expected_at_sprint_completion'] = 'N/A'
        
        # If no sprint configuration provided, use original logic for all projects
        if sprint_usage_config is None:
            logger.warning("Step 21: - ⚠️  No sprint usage configuration provided, applying sprint logic to all projects")
            sprint_usage_config = {}
        
        # Process only projects that use sprints
        projects_with_sprints = []
        projects_without_sprints = []
        
        for project in history_df['ado_project_name'].unique():
            project_norm = str(project).strip().lower()
            uses_sprints = sprint_usage_config.get(project_norm, True)  # Default to True for backward compatibility
            
            if uses_sprints:
                projects_with_sprints.append(project)
            else:
                projects_without_sprints.append(project)
        
        # Log project categorization
        if projects_with_sprints:
            logger.info(f"Step 21: - ℹ️  Projects using sprints: {projects_with_sprints}")
        if projects_without_sprints:
            logger.info(f"Step 21: - ℹ️  Projects NOT using sprints: {projects_without_sprints}")
        
        # Process projects that use sprints
        if projects_with_sprints and sprint_dod_mapping_df is not None:
            sprint_projects_df = history_df[history_df['ado_project_name'].isin(projects_with_sprints)].copy()
            
            # Normalize join columns for sprint projects
            sprint_projects_df['ado_project_name_norm'] = sprint_projects_df['ado_project_name'].astype(str).str.strip().str.lower()
            sprint_projects_df['issue_type_norm'] = sprint_projects_df['issue_type'].astype(str).str.strip().str.lower()

            sprint_dod_mapping_df['ado_project_name_norm'] = sprint_dod_mapping_df['ado project name'].astype(str).str.strip().str.lower()
            sprint_dod_mapping_df['issue_type_norm'] = sprint_dod_mapping_df['issue type'].astype(str).str.strip().str.lower()

            # Perform left merge for sprint projects
            merged_sprint_df = pd.merge(
                sprint_projects_df,
                sprint_dod_mapping_df[['ado_project_name_norm', 'issue_type_norm', 'status name at sprint completion']],
                how='left',
                left_on=['ado_project_name_norm', 'issue_type_norm'],
                right_on=['ado_project_name_norm', 'issue_type_norm'],
                suffixes=('', '_drop')
            )

            # Update the column for sprint projects only
            sprint_status_mapping = merged_sprint_df['status name at sprint completion'].fillna('N/A')
            history_df.loc[history_df['ado_project_name'].isin(projects_with_sprints), 'issue_status_expected_at_sprint_completion'] = sprint_status_mapping.values

            # Check for missing mappings in sprint projects
            missing_mappings = merged_sprint_df[merged_sprint_df['status name at sprint completion'].isna()]
            if not missing_mappings.empty:
                unique_missing = missing_mappings[['ado_project_name', 'issue_type']].drop_duplicates()
                logger.error(f"Step 21: - ❌ Found {len(unique_missing)} combinations without sprint_dod mapping in sprint-enabled projects:")
                for _, row in unique_missing.head(5).iterrows():
                    logger.error(f"  - {row['ado_project_name']} → {row['issue_type']}")
                if len(unique_missing) > 5:
                    logger.error(f"  - ... and {len(unique_missing) - 5} more")
                logger.error(f"Step 21: - ⚠️  Please add these combinations to sprint_dod sheet and rerun.")
                return None
        elif projects_with_sprints and sprint_dod_mapping_df is None:
            logger.error(f"Step 21: - ❌ Projects using sprints found but no sprint_dod mapping provided")
            logger.error(f"Step 21: - ⚠️  Sprint-enabled projects: {projects_with_sprints}")
            logger.error(f"Step 21: - ⚠️  Please provide sprint_dod mapping data and rerun.")
            return None

        logger.info(f"Step 21: - ✅ Added 'issue_status_expected_at_sprint_completion' column successfully")
        logger.info(f"Step 21: - ℹ️  Sprint-enabled projects: {len(projects_with_sprints)}, Non-sprint projects: {len(projects_without_sprints)}")
        return history_df

    except Exception as e:
        logger.error(f"Step 21: - ❌ Error adding sprint_dod mapping column: {str(e)}")
        return None


def add_sprint_completion_flag_column(history_df, sprint_usage_config=None):
    """
    Adds 'is_issue_history_status_at_sprint_completion_flag' column to history_df.
    Flag calculated as comparison of 'issue_status_expected_at_sprint_completion' and 'issue_status_history_name'.
    Returns TRUE if they match (case-insensitive), FALSE otherwise.
    For projects without sprints, sets to 'FALSE'.
    
    Args:
        history_df: DataFrame with history data
        sprint_usage_config: Dict mapping project names to sprint usage flags
    """
    try:
        # Ensure both columns exist
        required_columns = ['issue_status_expected_at_sprint_completion', 'issue_status_history_name']
        missing_columns = [col for col in required_columns if col not in history_df.columns]
        if missing_columns:
            logger.error(f"Step 22: - ❌ Missing required columns: {missing_columns}")
            return None

        # Initialize flag column with default value
        history_df['is_issue_history_status_at_sprint_completion_flag'] = 'FALSE'
        
        # If no sprint configuration provided, use original logic for all projects  
        if sprint_usage_config is None:
            logger.warning("Step 22: - ⚠️  No sprint usage configuration provided, applying sprint logic to all projects")
            sprint_usage_config = {}

        # Separate projects by sprint usage
        projects_with_sprints = []
        projects_without_sprints = []
        
        for project in history_df['ado_project_name'].unique():
            project_norm = str(project).strip().lower()
            uses_sprints = sprint_usage_config.get(project_norm, True)  # Default to True for backward compatibility
            
            if uses_sprints:
                projects_with_sprints.append(project)
            else:
                projects_without_sprints.append(project)

        # Process projects with sprints
        if projects_with_sprints:
            sprint_projects_mask = history_df['ado_project_name'].isin(projects_with_sprints)
            sprint_projects_df = history_df[sprint_projects_mask]
            
            # Process each row individually to handle multi-value expected statuses
            flag_values_list = []
            
            for _, row in sprint_projects_df.iterrows():
                expected_status_raw = row['issue_status_expected_at_sprint_completion']
                actual_status = row['issue_status_history_name']
                
                # Parse expected status (could be single or multi-value)
                expected_statuses_list = parse_multi_value_status(expected_status_raw)
                
                # Check if actual status matches any expected status (OR logic)
                # Also exclude 'N/A' values
                if expected_statuses_list and 'n/a' not in expected_statuses_list:
                    is_match = check_status_match_multi_value(actual_status, expected_statuses_list)
                    flag_values_list.append('TRUE' if is_match else 'FALSE')
                else:
                    flag_values_list.append('FALSE')
            
            # Update only sprint project rows
            history_df.loc[sprint_projects_mask, 'is_issue_history_status_at_sprint_completion_flag'] = flag_values_list

        # Projects without sprints keep 'FALSE' value (already set)
        
        # Log some statistics
        total_count = len(history_df)
        true_count = (history_df['is_issue_history_status_at_sprint_completion_flag'] == 'TRUE').sum()
        false_count = total_count - true_count
        
        logger.info(f"Step 22: - ✅ Added 'is_issue_history_status_at_sprint_completion_flag' column successfully")
        logger.info(f"Step 22: - 📊 Flag distribution: TRUE={true_count} ({true_count/total_count*100:.1f}%), FALSE={false_count} ({false_count/total_count*100:.1f}%)")
        logger.info(f"Step 22: - ℹ️  Sprint-enabled projects: {len(projects_with_sprints)}, Non-sprint projects: {len(projects_without_sprints)}")
        
        return history_df

    except Exception as e:
        logger.error(f"Step 22: - ❌ Error adding sprint completion flag column: {str(e)}")
        return None


def add_sprint_completion_date_column(history_df, sprint_usage_config=None):
    """
    Adds 'issue_sprint_completed_event_date_according_dod' column to history_df.
    
    This column contains the date when an issue first reached the status defined as 
    'issue_status_expected_at_sprint_completion' (from sprint_dod mapping).
    For projects without sprints, sets to None.
    
    Logic:
    1. For each issue in sprint-enabled projects, find all history records where the status matches the expected completion status
    2. Return the earliest date (minimum date) when this status was reached
    3. If the issue never reached the expected status, return empty/null
    4. For non-sprint projects, return None
    
    Args:
        history_df: DataFrame with history data
        sprint_usage_config: Dict mapping project names to sprint usage flags
    
    Returns the updated history_df with the new date column.
    """
    try:
        # Ensure required columns exist
        required_columns = ['issue_status_expected_at_sprint_completion', 'issue_status_history_name', 
                          'event_date', 'key', 'ado_project_name']
        missing_columns = [col for col in required_columns if col not in history_df.columns]
        if missing_columns:
            logger.error(f"Step 23: - ❌ Missing required columns: {missing_columns}")
            return None

        # Initialize the completion date column with None
        history_df['issue_sprint_completed_event_date_according_dod'] = None
        
        # If no sprint configuration provided, use original logic for all projects
        if sprint_usage_config is None:
            logger.warning("Step 23: - ⚠️  No sprint usage configuration provided, applying sprint logic to all projects")
            sprint_usage_config = {}

        # Separate projects by sprint usage
        projects_with_sprints = []
        projects_without_sprints = []
        
        for project in history_df['ado_project_name'].unique():
            project_norm = str(project).strip().lower()
            uses_sprints = sprint_usage_config.get(project_norm, True)  # Default to True for backward compatibility
            
            if uses_sprints:
                projects_with_sprints.append(project)
            else:
                projects_without_sprints.append(project)

        # Process only projects with sprints
        if projects_with_sprints:
            sprint_projects_df = history_df[history_df['ado_project_name'].isin(projects_with_sprints)].copy()
            
            # Find completion records using multi-value logic
            completion_records = []
            
            for _, row in sprint_projects_df.iterrows():
                expected_status_raw = row['issue_status_expected_at_sprint_completion']
                actual_status = row['issue_status_history_name']
                
                # Parse expected status (could be single or multi-value)
                expected_statuses_list = parse_multi_value_status(expected_status_raw)
                
                # Check if actual status matches any expected status (OR logic)
                # Also exclude 'N/A' values
                if expected_statuses_list and 'n/a' not in expected_statuses_list:
                    is_match = check_status_match_multi_value(actual_status, expected_statuses_list)
                    if is_match:
                        completion_records.append(row)
            
            if completion_records:
                # Convert list back to DataFrame
                completion_records_df = pd.DataFrame(completion_records)
                
                # Convert date column to datetime if it's not already
                completion_records_df['event_date'] = pd.to_datetime(
                    completion_records_df['event_date'], errors='coerce'
                )
                
                # Group by key and find the minimum (earliest) date for each issue
                earliest_completion_dates = completion_records_df.groupby('key')['event_date'].min().reset_index()
                earliest_completion_dates.columns = ['key', 'completion_date_temp']
                
                # Update only the rows for sprint projects
                sprint_project_keys = sprint_projects_df['key'].unique()
                for _, row in earliest_completion_dates.iterrows():
                    key = row['key']
                    completion_date = row['completion_date_temp']
                    if key in sprint_project_keys:
                        history_df.loc[history_df['key'] == key, 'issue_sprint_completed_event_date_according_dod'] = completion_date

        # Projects without sprints keep None value (already set)
        
        # Log statistics
        total_issues = history_df['key'].nunique()
        issues_with_completion = history_df[history_df['issue_sprint_completed_event_date_according_dod'].notna()]['key'].nunique()
        total_rows_with_completion = history_df['issue_sprint_completed_event_date_according_dod'].notna().sum()
        
        logger.info(f"Step 23: - ✅ Added 'issue_sprint_completed_event_date_according_dod' column successfully")
        logger.info(f"Step 23: - 📊 Completion date statistics:")
        logger.info(f"Step 23: -    Total unique issues: {total_issues}")
        logger.info(f"Step 23: -    Issues with completion dates: {issues_with_completion} ({issues_with_completion/total_issues*100:.1f}%)")
        logger.info(f"Step 23: -    Total rows with completion dates: {total_rows_with_completion}")
        logger.info(f"Step 23: - ℹ️  Sprint-enabled projects: {len(projects_with_sprints)}, Non-sprint projects: {len(projects_without_sprints)}")
        
        return history_df

    except Exception as e:
        logger.error(f"Step 23: - ❌ Error adding sprint completion date column: {str(e)}")
        return None


def validate_issue_types_mapping_coverage(
    history_df,
    file_path,
    ISSUE_TYPES_CATEGORIES_SHEET
):
    """
    Validates that all unique combinations of ado_project_name + issue_type 
    from history_df exist in the issue_types_categories mapping.
    
    Args:
        history_df: DataFrame with jira_history data
        file_path: Path to Status Categories Excel file
        ISSUE_TYPES_CATEGORIES_SHEET: Sheet name for issue types mapping
    
    Returns:
        bool: True if all combinations are covered, False otherwise
    """
    try:
        # Get unique combinations from history_df
        history_combinations = (
            history_df[['ado_project_name', 'issue_type']]
            .dropna()
            .drop_duplicates()
        )
        
        # Normalize for case-insensitive comparison
        history_combinations['project_lower'] = history_combinations['ado_project_name'].str.strip().str.lower()
        history_combinations['issue_type_lower'] = history_combinations['issue_type'].str.strip().str.lower()
        
        # Read mapping file
        mapping_df = pd.read_excel(file_path, sheet_name=ISSUE_TYPES_CATEGORIES_SHEET)
        mapping_df.columns = mapping_df.columns.str.strip()
        
        # Normalize mapping data for case-insensitive comparison
        mapping_df['project_lower'] = mapping_df['ado project name'].str.strip().str.lower()
        mapping_df['issue_type_lower'] = mapping_df['issue type'].str.strip().str.lower()
        
        # Create sets for comparison
        history_set = set(zip(history_combinations['project_lower'], history_combinations['issue_type_lower']))
        mapping_set = set(zip(mapping_df['project_lower'], mapping_df['issue_type_lower']))
        
        # Find missing combinations
        missing_combinations = history_set - mapping_set
        
        if missing_combinations:
            logger.error(f"Step 18f: - ❌ Found {len(missing_combinations)} missing combinations in issue_types_categories mapping:")
            logger.error("Step 18f: - Missing combinations (project → issue_type):")
            
            # Get original case for display
            for project_lower, issue_type_lower in sorted(missing_combinations):
                # Find original case from history_df
                original_row = history_combinations[
                    (history_combinations['project_lower'] == project_lower) & 
                    (history_combinations['issue_type_lower'] == issue_type_lower)
                ].iloc[0]
                
                original_project = original_row['ado_project_name']
                original_issue_type = original_row['issue_type']
                logger.error(f"Step 18f: -   {original_project} → {original_issue_type}")
            
            logger.error("Step 18f: - 🔧 To fix this issue, you have two options:")
            logger.error("Step 18f: -   1. Add the missing combinations to the 'issue_types_categories' sheet and fill in 'standard issue type' values")
            logger.error("Step 18f: -   2. Delete the entire 'Status Categories prepared.xlsx' file to force recreation with all current combinations")
            logger.error(f"Step 18f: - 📁 File location: {file_path}")
            
            return False
        
        logger.info(f"Step 18f: - ✅ All {len(history_set)} combinations from jira_history are covered in issue_types_categories mapping")
        return True
        
    except Exception as e:
        logger.error(f"Step 18f: - ❌ Error validating issue types mapping coverage: {str(e)}")
        return False


def add_standard_issue_type_column(history_df, file_path, ISSUE_TYPES_CATEGORIES_SHEET):
    """
    Adds 'standard_issue_type' column to history_df by mapping from issue_types_categories sheet.
    
    Args:
        history_df: DataFrame with jira_history data
        file_path: Path to Status Categories Excel file
        ISSUE_TYPES_CATEGORIES_SHEET: Sheet name for issue types mapping
    
    Returns:
        DataFrame: Updated history_df with standard_issue_type column, or None if error
    """
    try:
        # Read mapping file
        mapping_df = pd.read_excel(file_path, sheet_name=ISSUE_TYPES_CATEGORIES_SHEET)
        mapping_df.columns = mapping_df.columns.str.strip()
        
        # Check required columns
        required_columns = {'ado project name', 'issue type', 'standard issue type'}
        missing_cols = required_columns - set(mapping_df.columns)
        if missing_cols:
            logger.error(f"Step 18g: - ❌ Missing required columns in issue_types_categories sheet: {missing_cols}")
            return None
        
        # Prepare mapping data with case-insensitive keys
        mapping_df['mapping_key'] = (
            mapping_df['ado project name'].str.strip().str.lower() + '|' + 
            mapping_df['issue type'].str.strip().str.lower()
        )
        
        # Create mapping dictionary
        mapping_dict = dict(zip(
            mapping_df['mapping_key'], 
            mapping_df['standard issue type'].str.strip()
        ))
        
        # Create mapping key for history_df
        history_df['mapping_key'] = (
            history_df['ado_project_name'].str.strip().str.lower() + '|' + 
            history_df['issue_type'].str.strip().str.lower()
        )
        
        # Apply mapping
        history_df['standard_issue_type'] = history_df['mapping_key'].map(mapping_dict)
        
        # Remove temporary mapping key column
        history_df = history_df.drop('mapping_key', axis=1)
        
        # Check for any unmapped values (should not happen if validation passed)
        unmapped_count = history_df['standard_issue_type'].isna().sum()
        if unmapped_count > 0:
            logger.warning(f"Step 18g: - ⚠️  Found {unmapped_count} unmapped values in standard_issue_type column")
        
        # Count mapping statistics
        total_rows = len(history_df)
        mapped_rows = total_rows - unmapped_count
        unique_mappings = history_df['standard_issue_type'].nunique()
        
        logger.info(f"Step 18g: - ✅ Added 'standard_issue_type' column successfully")
        logger.info(f"Step 18g: - 📊 Mapping statistics:")
        logger.info(f"Step 18g: -    Total rows: {total_rows}")
        logger.info(f"Step 18g: -    Successfully mapped: {mapped_rows} ({mapped_rows/total_rows*100:.1f}%)")
        logger.info(f"Step 18g: -    Unique standard issue types: {unique_mappings}")
        
        return history_df
        
    except Exception as e:
        logger.error(f"Step 18g: - ❌ Error adding standard_issue_type column: {str(e)}")
        return None
