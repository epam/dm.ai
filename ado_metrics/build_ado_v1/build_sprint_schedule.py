# build_sprint_schedule.py
#
# ADO iteration (sprint) schedule builder.
# Unlike Jira, ADO sprints (iterations) are fetched from a separate API call
# and stored alongside work items in ado_raw_issues.json.
# Each work item has System.IterationPath like "MyProject\\Sprint 1".

import pandas as pd
from datetime import datetime
from config import logger, MIN_SPRINT_DURATION_DAYS, MAX_SPRINT_DURATION_DAYS, ADO_FIELDS
from utils import parse_date


def validate_sprint(sprint_data):
    """Validate a sprint: both dates present, start < end, duration in range."""
    try:
        start_date = sprint_data.get('sprint_start_date')
        end_date = sprint_data.get('sprint_end_date')

        if (pd.isna(start_date) or start_date is None or
                pd.isna(end_date) or end_date is None):
            return False

        if isinstance(start_date, str):
            start_date = parse_date(start_date)
        if isinstance(end_date, str):
            end_date = parse_date(end_date)

        if pd.isna(start_date) or pd.isna(end_date):
            return False
        if start_date >= end_date:
            return False

        duration_days = (end_date - start_date).days
        if duration_days < MIN_SPRINT_DURATION_DAYS or duration_days > MAX_SPRINT_DURATION_DAYS:
            return False

        return True
    except Exception as e:
        logger.debug(f"Error validating sprint: {e}")
        return False


def _safe_parse(date_val):
    """Parse a date value, returning NaT on failure."""
    if date_val is None or (isinstance(date_val, float) and pd.isna(date_val)):
        return pd.NaT
    try:
        return parse_date(date_val) if isinstance(date_val, str) else parse_date(date_val.isoformat())
    except Exception:
        return pd.NaT


def extract_unique_sprints(work_items, iterations, account_name=None, epam_project_name=None):
    """
    Build a unique sprints DataFrame from the ADO iterations list fetched in Stage 1.

    ADO iteration object (from fetch_workitem.py):
        {
            'id':           <guid>,
            'name':         'Sprint 1',
            'path':         'MyProject\\Sprint 1',
            'start_date':   datetime | None,
            'finish_date':  datetime | None,
            'time_frame':   'past'|'current'|'future'|None,
        }

    Args:
        work_items:        list of raw work item dicts
        iterations:        list of iteration dicts from ADO API
        account_name:      from resource plan file
        epam_project_name: from resource plan file

    Returns:
        pd.DataFrame with columns matching the Jira sprints_df schema
    """
    if not iterations:
        logger.warning("_______  - ⚠️  No iterations provided — returning empty sprints DataFrame.")
        return pd.DataFrame(columns=[
            'account_name', 'epam_project_name', 'ado_project_name',
            'sprint_id', 'sprint_name', 'sprint_start_date',
            'sprint_end_date', 'sprint_state', 'update_date', 'valid_sprint',
        ])

    # Determine ADO project name from first work item
    ado_project = ''
    if work_items:
        ado_project = (work_items[0].get('fields', {}).get(ADO_FIELDS['project'], '') or '')

    rows = []
    for it in iterations:
        rows.append({
            'account_name': account_name or '',
            'epam_project_name': epam_project_name or '',
            'ado_project_name': ado_project,
            'sprint_id': it.get('id', ''),
            'sprint_name': it.get('name', ''),
            'sprint_start_date': _safe_parse(it.get('start_date')),
            'sprint_end_date': _safe_parse(it.get('finish_date')),
            'sprint_state': (it.get('time_frame') or '').lower(),
            'update_date': datetime.now().isoformat(),
        })

    sprints_df = pd.DataFrame(rows)
    sprints_df['valid_sprint'] = sprints_df.apply(lambda r: validate_sprint(r.to_dict()), axis=1)

    column_order = [
        'account_name', 'epam_project_name', 'ado_project_name',
        'sprint_id', 'sprint_name', 'sprint_start_date', 'sprint_end_date',
        'sprint_state', 'valid_sprint', 'update_date',
    ]
    existing = [c for c in column_order if c in sprints_df.columns]
    sprints_df = sprints_df[existing].sort_values('sprint_start_date', na_position='last').reset_index(drop=True)

    logger.info(f"_______  - ✅ Extracted {len(sprints_df)} unique iterations (sprints).")
    return sprints_df


def create_sprints_schedule(work_items, sprints_df, account_name, epam_project_name):
    """
    Map each work item to its iteration(s) and return a sprint-schedule DataFrame.

    ADO work items have System.IterationPath like "MyProject\\Sprint 1".
    We match this against sprint_name in sprints_df.

    Returns:
        pd.DataFrame — one row per (iteration) with sprint metadata
    """
    if sprints_df is None or sprints_df.empty:
        logger.warning("_______  - ⚠️  No sprints — returning empty sprint schedule.")
        return pd.DataFrame()

    iteration_field = ADO_FIELDS['iteration_path']

    # Build lookup: sprint_name (last path segment) → sprint_id
    sprints_df = sprints_df.copy()
    sprints_df['_sprint_name_lower'] = sprints_df['sprint_name'].str.strip().str.lower()

    issue_sprint_records = []
    for item in work_items:
        item_key = item.get('key', '')
        iteration_path = item.get('fields', {}).get(iteration_field, '') or ''
        if not iteration_path:
            continue

        # Last segment of path is the sprint name
        sprint_name_raw = iteration_path.split('\\')[-1].strip()
        sprint_name_lower = sprint_name_raw.lower()

        matching = sprints_df[sprints_df['_sprint_name_lower'] == sprint_name_lower]
        if matching.empty:
            continue

        sprint_id = matching.iloc[0]['sprint_id']
        issue_sprint_records.append({
            'account_name': account_name,
            'epam_project_name': epam_project_name,
            'ado_project_name': item.get('fields', {}).get(ADO_FIELDS['project'], ''),
            'key': item_key,
            'sprint_id': sprint_id,
        })

    if not issue_sprint_records:
        logger.warning("_______  - ⚠️  No work-item → iteration relationships found.")
        return pd.DataFrame()

    issue_sprint_df = pd.DataFrame(issue_sprint_records)
    merge_cols = ['account_name', 'epam_project_name', 'ado_project_name', 'sprint_id']

    sprints_df_clean = sprints_df.drop(columns=['_sprint_name_lower'], errors='ignore')
    sprints_schedule_df = issue_sprint_df.merge(sprints_df_clean, on=merge_cols, how='left')

    unique_items = sprints_schedule_df['key'].nunique()

    column_order = [
        'account_name', 'epam_project_name', 'ado_project_name',
        'sprint_id', 'sprint_name', 'sprint_start_date', 'sprint_end_date',
        'sprint_state', 'valid_sprint',
    ]
    existing = [c for c in column_order if c in sprints_schedule_df.columns]
    sprints_schedule_df = sprints_schedule_df[existing].drop_duplicates().sort_values(
        'sprint_start_date', na_position='last'
    ).reset_index(drop=True)

    logger.info(
        f"_______  - ✅ Sprint schedule: {len(sprints_schedule_df)} rows covering {unique_items} unique items."
    )
    return sprints_schedule_df
