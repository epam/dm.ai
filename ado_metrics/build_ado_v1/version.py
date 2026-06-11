# version.py

import pandas as pd
from config import logger, SCRIPT_VERSION, ACCOUNT_NAME, ADO_ORG_URL, WIQL_QUERY, TARGET_TIMEZONE, VALID_ISSUE_LOGIC
from utils import parse_date
from datetime import datetime


def extract_unique_project_keys(detailed_df):
    """
    Extract unique ADO project names from detailed_df.
    """
    try:
        unique_projects = detailed_df['ado_project_name'].dropna().unique()
        unique_projects = [p for p in unique_projects if p and str(p).strip()]
        if not unique_projects:
            logger.warning("No valid project names found in detailed_df")
            return None
        project_string = ", ".join(unique_projects)
        logger.info(f"Extracted {len(unique_projects)} unique project(s): {project_string}")
        return project_string
    except KeyError:
        logger.error("'ado_project_name' column not found in detailed_df")
        return None
    except Exception as e:
        logger.error(f"Error extracting project keys: {str(e)}")
        return None


def version_info():
    """Create version information DataFrame with configuration details."""
    return pd.DataFrame([{
        "Script Version": SCRIPT_VERSION,
        "Account Info": ACCOUNT_NAME,
        "ADO Org URL": ADO_ORG_URL,
        "Raw Data Extraction Time": None,
        "Processing Time": datetime.now().strftime('%Y%m%d_%H%M%S'),
        "Timezone": TARGET_TIMEZONE,
        "WIQL Query": WIQL_QUERY,
        "Logic Expression": VALID_ISSUE_LOGIC,
    }])


def load_info_sheet(resource_plan_file):
    """
    Loads the 'Info' sheet and extracts key configuration values from the resource plan file.

    Returns:
        Tuple: (account_name, epam_project_name, data_analysis_start_date, data_analysis_end_date)
        or None on failure.
    """
    try:
        info_df = pd.read_excel(resource_plan_file, sheet_name='Info')
        logger.info(
            f"Step 08: - ✅ Loaded 'info' sheet: "
            f"account='{info_df.loc[0, 'Account Name']}', project='{info_df.loc[0, 'Project Name']}'"
        )
    except FileNotFoundError:
        logger.error(f"Step 08: - ❌ File '{resource_plan_file}' not found.")
        return None
    except ValueError as ve:
        logger.error(f"Step 08: - ❌ Sheet 'Info' not found in {resource_plan_file}: {ve}")
        return None
    except Exception as e:
        logger.error(f"Step 08: - ❌ Unexpected error reading resource plan file: {e}")
        return None

    try:
        account_name = info_df['Account Name'].iloc[0]
        epam_project_name = info_df['Project Name'].iloc[0]
        data_analysis_start_date = pd.to_datetime(info_df['Data analysis start'].iloc[0])
        data_analysis_end_date = pd.to_datetime(datetime(datetime.today().year + 1, 12, 31))
        return account_name, epam_project_name, data_analysis_start_date, data_analysis_end_date
    except Exception as e:
        logger.error(f"Step 08: - ❌ Error extracting fields from Info sheet: {e}")
        return None
