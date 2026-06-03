# add_ai_developers.py

from config import logger
import pandas as pd


# Read the AI developer list from 'resource plan and info.xlsx' file, and extract team info
def load_ai_developers(resource_plan_file, use_resource_plan_flag):

    # If the resource plan is not used (USE_RESOURCE_PLAN set to False in .env) we skip loading the Excel file and return an empty dictionary
    if not use_resource_plan_flag:
        logger.info("Step 15: -    in .env use_resource_plan is set to False -> all developers will be considered to be AI developers.")
        return {}  # Empty dict: all will default to 'unknown_team' and True in add_is_ai_developer_column function

    try:
        # Load the full Excel file
        resource_plan_df = pd.read_excel(resource_plan_file, sheet_name='Resource Plan')

        # Extract emails (column B) and team info (column J)
        emails = resource_plan_df.iloc[:, 1]  # Column B
        teams = resource_plan_df.iloc[:, 9]  # Column J (0-indexed, so J is 9)

        # Create a DataFrame with clean email and team info
        ai_df = pd.DataFrame({
            'email': emails.astype(str).str.strip().str.lower(),
            'team': teams
        }).dropna(subset=['email'])  # Drop rows with missing emails

        # Remove duplicates to keep only the first team per email
        ai_df = ai_df.drop_duplicates(subset='email', keep='first')

        ai_developers_dict = ai_df.set_index('email')['team'].to_dict()

        logger.info(f"Step 15: - ✅ Loaded {len(ai_developers_dict)} AI developer emails with teams.")

        return ai_developers_dict

    except Exception as e:
        logger.info(f"Error loading AI developer emails and teams: {str(e)}")
        return {}


def add_is_ai_developer_column(history_df, ai_developer_dict, use_resource_plan_flag, df_name):

    try:
        is_ai_col_name = 'is_ai_enabled_flag'
        team_col_name = 'assignee_team_name_at_status'

        if not use_resource_plan_flag:
            # All developers set to AI enabled, and all teams set to 'unknown_team'
            history_df[is_ai_col_name] = True
            history_df[team_col_name] = 'unknown_team'
            logger.info(f"Step 16: - ⚠️  Resource plan not used -> setting all '{is_ai_col_name}' to True and '{team_col_name}' to 'unknown_team'.")
            return history_df

        # Normalize the emails
        normalized_emails = (
            history_df['assignee_email_at_status']
            .fillna('')                   # replace NaN with empty string
            .astype(str)           # ensure all entries are strings
            .str.strip()              # remove leading/trailing whitespace
            .str.lower()            # lowercase everything
        )

        # Create 'is_ai_enabled_flag' flag
        is_ai = normalized_emails.isin(ai_developer_dict.keys())

        # Map team values using the normalized email, defaulting to 'unknown_team' if not found
        team_values = normalized_emails.map(ai_developer_dict).fillna('unknown_team')

        # Insert or update the 'is_ai_enabled_flag' column

        if is_ai_col_name in history_df.columns:
            # If the 'is_ai_enabled_flag' column already exists, update its values
            history_df[is_ai_col_name] = is_ai
            logger.info(f"'{is_ai_col_name}' column already exists in '{df_name}' sheet. Updating values.")
        else:
            insert_position = history_df.columns.get_loc('assignee_name_at_status') + 1
            history_df.insert(loc=insert_position, column=is_ai_col_name, value=is_ai)
            logger.info(f"Step 16: - ✅ Added '{is_ai_col_name}' column to '{df_name}' successfully.")

        # Insert 'assignee_team_name_at_status' column right after 'assignee_name_at_status'

        if team_col_name in history_df.columns:
            history_df[team_col_name] = team_values
        else:
            insert_position = history_df.columns.get_loc(is_ai_col_name)
            history_df.insert(loc=insert_position, column=team_col_name, value=team_values)
            logger.info(f"Step 16: - ✅ Added '{team_col_name}' column to '{df_name}' successfully.")

        return history_df

    except Exception as e:
        logger.info(f"Error updating '{df_name}' with AI Developer info: {e}")
        return None

