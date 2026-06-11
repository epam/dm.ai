# export_and_load_files.py

import os
import json
from datetime import datetime, date
import pandas as pd
import numpy as np
from config import logger
import openpyxl

class DateTimeEncoder(json.JSONEncoder):
    """Custom JSON encoder that handles datetime objects."""
    def default(self, obj):
        if isinstance(obj, (datetime, date, pd.Timestamp)):
            return obj.isoformat()
        elif isinstance(obj, pd.Timedelta):
            return str(obj)
        elif isinstance(obj, np.integer):
            return int(obj)
        elif isinstance(obj, np.floating):
            return float(obj)
        elif isinstance(obj, np.ndarray):
            return obj.tolist()
        return super(DateTimeEncoder, self).default(obj)

def export_to_json(fetched_raw_data, EXPORT_JSON_FILE_NAME, step):
    """Export data to JSON file with custom datetime handling."""
    try:
        with open(EXPORT_JSON_FILE_NAME, "w", encoding="utf-8") as f:
            json.dump(fetched_raw_data, f, ensure_ascii=False, indent=4, cls=DateTimeEncoder)
        logger.info(f"Step {step}: - 📁 Exported {len(fetched_raw_data)} records into {EXPORT_JSON_FILE_NAME}")
    except Exception as e:
        logger.error(f"❌ Failed to export raw fetched_raw_data to JSON: {e}")

def export_to_excel(
    detailed_df=None,
    history_df=None,
    status_category_df=None,
    status_category_level_agg_df=None,
    fact_issue_level_measures_df=None,
    lead_time_df=None,
    sequence_df=None,
    sprints_schedule_df=None,
    version_info_df=None,
    status_category_sheets=None,
    resource_plan_sheets=None,
    # Phase 2 — Repos / PRs / Pipelines (all optional)
    repos_df=None,
    commits_df=None,
    pr_df=None,
    pr_reviewers_df=None,
    pipeline_runs_df=None,
    # Phase 3 — PR Analytics (all optional)
    pr_staging_df=None,
    fact_cr_lead_time_df=None,
    cr_lead_time_report_df=None,
    cr_lead_time_report_by_weeks_df=None,
    user_data_df=None,
    output_excel_file_name="output.xlsx",
    show_skip_logs=True
):
    try:
        # Map sheet names to DataFrames
        sheets = {
            "raw_data": detailed_df,
            "ado_history": history_df,
            "status_category_level": status_category_df,
            "status_category_level_agg": status_category_level_agg_df,
            "fact_issue_level_measures": fact_issue_level_measures_df,
            "lead_time_report": lead_time_df,
            "status_category_sequence": sequence_df,
            "sprints_schedule": sprints_schedule_df,
            "version_info": version_info_df,
            # Phase 2 sheets
            "repos": repos_df,
            "commits": commits_df,
            "pull_requests": pr_df,
            "pr_reviewers": pr_reviewers_df,
            "pipeline_runs": pipeline_runs_df,
            # Phase 3 — PR Analytics sheets
            "staging": pr_staging_df,
            "fact_cr_lead_time": fact_cr_lead_time_df,
            "cr_lead_time_report": cr_lead_time_report_df,
            "cr_lead_time_report_by_weeks": cr_lead_time_report_by_weeks_df,
            "user_data": user_data_df,
        }
        
        # Add status category sheets at the end (if provided)
        if status_category_sheets:
            for sheet_name, sheet_df in status_category_sheets.items():
                if sheet_df is not None and not sheet_df.empty:
                    # Add sheets with original names from Status Categories file
                    sheets[sheet_name] = sheet_df
                    logger.debug(f"         - 📋 Added status category sheet: '{sheet_name}' with {len(sheet_df)} rows")
                elif show_skip_logs:
                    logger.debug(f"         - ⚠️  Skipped empty status category sheet: '{sheet_name}'")
        
        # Add resource plan sheets at the end (if provided)
        if resource_plan_sheets:
            for sheet_name, sheet_df in resource_plan_sheets.items():
                if sheet_df is not None and not sheet_df.empty:
                    # Add sheets with original names from Resource Plan file
                    # Prefix with 'rp_' to avoid potential naming conflicts
                    prefixed_name = f"rp_{sheet_name}"
                    sheets[prefixed_name] = sheet_df
                    logger.debug(f"         - 📋 Added resource plan sheet: '{prefixed_name}' with {len(sheet_df)} rows")
                elif show_skip_logs:
                    logger.debug(f"         - ⚠️  Skipped empty resource plan sheet: '{sheet_name}'")
        
        # Remove timezone information from all DataFrames
        for sheet_name, df in sheets.items():
            if df is not None and not df.empty:
                # Create a copy to avoid modifying the original
                df_copy = df.copy()
                
                # Remove timezone from datetime columns
                for col in df_copy.columns:
                    if pd.api.types.is_datetime64_any_dtype(df_copy[col]):
                        # Check if column has timezone information
                        if df_copy[col].dt.tz is not None:
                            # Convert timezone-aware to timezone-naive
                            df_copy[col] = df_copy[col].dt.tz_convert(None)
                        # If already timezone-naive, leave as is
                    elif isinstance(df_copy[col].dtype, pd.DatetimeTZDtype):
                        # Handle timezone-aware datetime columns
                        df_copy[col] = df_copy[col].dt.tz_convert(None)
                
                # Update the dictionary with the modified copy
                sheets[sheet_name] = df_copy
        
        # Write to Excel
        with pd.ExcelWriter(output_excel_file_name, engine='openpyxl') as writer:
            sheets_written = 0
            
            for sheet_name, df in sheets.items():
                if df is not None and not df.empty:
                    df.to_excel(writer, sheet_name=sheet_name, index=False)
                    sheets_written += 1
                elif show_skip_logs:
                    if df is None:
                        logger.debug(f"         -    Skipped '{sheet_name}' sheet (DataFrame is None)")
                    else:
                        logger.debug(f"         -    Skipped '{sheet_name}' sheet (DataFrame is empty)")
            
            # Auto-adjust column widths for better readability
            for sheet_name in writer.sheets:
                worksheet = writer.sheets[sheet_name]
                for column in worksheet.columns:
                    max_length = 0
                    column_letter = column[0].column_letter
                    
                    for cell in column:
                        try:
                            if cell.value:
                                max_length = max(max_length, len(str(cell.value)))
                        except:
                            pass
                    
                    # Set a reasonable max width
                    adjusted_width = min(max_length + 2, 50)
                    worksheet.column_dimensions[column_letter].width = adjusted_width
        
        # Re-open the file to modify sheet visibility
        wb = openpyxl.load_workbook(output_excel_file_name)
        if "status_category_level" in wb.sheetnames:
            wb["status_category_level"].sheet_state = "hidden"
        wb.save(output_excel_file_name)
        
        logger.info(f"         - ✅ Successfully exported {sheets_written} sheets to {output_excel_file_name}")
        return True

    except Exception as e:
        logger.error(f"❌ Failed to export to Excel: {str(e)}")
        logger.error(f"   Error type: {type(e).__name__}")
        import traceback
        logger.error(f"   Traceback: {traceback.format_exc()}")
        return False

def load_json_file(filename):
    if not os.path.exists(filename):
        logger.error(f"File not found: {filename}")
        return None
    with open(filename, "r", encoding="utf-8") as f:
        return json.load(f)

def load_all_resource_plan_sheets(resource_plan_file):
    """
    Dynamically load all sheets from the Resource Plan and Info file.
    
    Args:
        resource_plan_file (str): Path to the Resource Plan and Info Excel file
        
    Returns:
        dict: Dictionary with sheet names as keys and DataFrames as values
        
    Raises:
        FileNotFoundError: If the resource plan file doesn't exist
        Exception: If there's an error reading the file
    """
    try:
        if not os.path.exists(resource_plan_file):
            raise FileNotFoundError(f"Resource plan file not found: {resource_plan_file}")
        
        # Get all sheet names from the Excel file
        excel_file = pd.ExcelFile(resource_plan_file)
        sheet_names = excel_file.sheet_names
        
        # Load all sheets into a dictionary
        resource_plan_sheets = {}
        for sheet_name in sheet_names:
            try:
                df = pd.read_excel(resource_plan_file, sheet_name=sheet_name)
                resource_plan_sheets[sheet_name] = df
                logger.info(f"         - ✅ Loaded '{sheet_name}' sheet with {len(df)} rows")
            except Exception as e:
                logger.warning(f"         - ⚠️  Failed to load '{sheet_name}' sheet: {e}")
                continue
        
        logger.info(f"         - 📋 Successfully loaded {len(resource_plan_sheets)} sheets from {resource_plan_file}")
        return resource_plan_sheets
        
    except Exception as e:
        logger.error(f"❌ Error loading resource plan sheets: {e}")
        raise

def load_all_status_category_sheets(status_categories_file):
    """
    Dynamically load all sheets from the Status Categories prepared file.
    
    Args:
        status_categories_file (str): Path to the Status Categories prepared Excel file
        
    Returns:
        dict: Dictionary with sheet names as keys and DataFrames as values
        
    Raises:
        FileNotFoundError: If the status categories file doesn't exist
        Exception: If there's an error reading the file
    """
    try:
        if not os.path.exists(status_categories_file):
            raise FileNotFoundError(f"Status categories file not found: {status_categories_file}")
        
        # Get all sheet names from the Excel file
        excel_file = pd.ExcelFile(status_categories_file)
        sheet_names = excel_file.sheet_names
        
        # Load all sheets into a dictionary
        status_category_sheets = {}
        for sheet_name in sheet_names:
            try:
                df = pd.read_excel(status_categories_file, sheet_name=sheet_name)
                status_category_sheets[sheet_name] = df
                logger.info(f"         - ✅ Loaded '{sheet_name}' sheet with {len(df)} rows")
            except Exception as e:
                logger.warning(f"         - ⚠️  Failed to load '{sheet_name}' sheet: {e}")
                continue
        
        logger.info(f"         - 📋 Successfully loaded {len(status_category_sheets)} sheets from {status_categories_file}")
        return status_category_sheets
        
    except Exception as e:
        logger.error(f"❌ Error loading status category sheets: {e}")
        raise

def load_raw_data_from_excel(path):
    detailed_df = pd.read_excel(path, sheet_name='raw_data', parse_dates=['issue_create_date', 'issue_update_date', 'issue_resolution_date'])
    history_df = pd.read_excel(path, sheet_name='ado_history', parse_dates=['issue_create_date', 'issue_update_date', 'issue_resolution_date'])
    version_info_df = pd.read_excel(path, sheet_name='version_info')

    return detailed_df, history_df, version_info_df
