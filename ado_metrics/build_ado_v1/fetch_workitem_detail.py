# fetch_workitem_detail.py
#
# Processes raw ADO work items (from ado_raw_issues.json) into:
#   - detailed_data : one row per work item × timeline event (mirrors Jira fetch_issue_detail.py)
#   - history_data  : same rows enriched for status-category pipeline
#
# ADO has no embedded changelog like Jira. Instead we compute field diffs between
# consecutive revisions to reconstruct status and assignee change history.

from collections import defaultdict
from datetime import datetime
import pandas as pd

from config import logger, ADO_FIELDS, RESOLVED_STATES
from utils import parse_date, calculate_days_excluding_weekends
from assignee_history import (
    extract_assignee_history, build_assignee_timetable, get_user_info_with_normalization
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _get_identity_email(identity_obj, user_map):
    """Return (email, displayName) from an ADO identity dict, with user_map fallback."""
    if not identity_obj or not isinstance(identity_obj, dict):
        return 'Unassigned', 'Unassigned'
    email = (identity_obj.get('uniqueName') or identity_obj.get('unique_name') or '').strip()
    display_name = (identity_obj.get('displayName') or identity_obj.get('display_name') or '').strip()
    if not email and display_name:
        info = get_user_info_with_normalization(display_name, user_map)
        email = info.get('email', 'Unassigned')
    return email or 'Unassigned', display_name or 'Unassigned'


def _build_synthetic_changelog(revisions, user_map):
    """
    Convert ADO revision snapshots into a Jira-style changelog list so the
    same timeline-processing logic can be reused.

    Each entry:
        {
            'created': <ISO date string>,
            'author':  {'emailAddress': ..., 'displayName': ...},
            'items':   [{'field': 'status'|'assignee', 'fromString': ..., 'toString': ...}, ...]
        }
    """
    if not revisions or len(revisions) < 2:
        return []

    changelog = []
    state_field = ADO_FIELDS['state']
    assignee_field = ADO_FIELDS['assigned_to']

    for i in range(1, len(revisions)):
        prev_fields = revisions[i - 1].get('fields', {})
        curr_fields = revisions[i].get('fields', {})

        changed_date = curr_fields.get(ADO_FIELDS['changed_date'])
        if not changed_date:
            continue

        changed_by = curr_fields.get(ADO_FIELDS['changed_by']) or {}
        author_email = (changed_by.get('uniqueName') or changed_by.get('unique_name') or '').strip()
        author_name = (changed_by.get('displayName') or changed_by.get('display_name') or '').strip()

        items = []

        # Detect status change
        prev_state = prev_fields.get(state_field) or ''
        curr_state = curr_fields.get(state_field) or ''
        if prev_state != curr_state:
            items.append({
                'field': 'status',
                'fromString': prev_state,
                'toString': curr_state,
            })

        # Detect assignee change
        prev_assignee = prev_fields.get(assignee_field) or {}
        curr_assignee = curr_fields.get(assignee_field) or {}
        prev_name = (prev_assignee.get('displayName') or prev_assignee.get('display_name') or 'Unassigned').strip()
        curr_name = (curr_assignee.get('displayName') or curr_assignee.get('display_name') or 'Unassigned').strip()
        if prev_name != curr_name:
            items.append({
                'field': 'assignee',
                'fromString': prev_name,
                'toString': curr_name,
            })

        if items:
            changelog.append({
                'created': changed_date if isinstance(changed_date, str) else changed_date.isoformat(),
                'author': {'emailAddress': author_email, 'displayName': author_name},
                'items': items,
            })

    return changelog


def _detect_initial_assignee(changelog, user_map):
    """Return (email, displayName) of the initial assignee from the first assignee change."""
    for entry in changelog:
        for item in entry.get('items', []):
            if item.get('field') == 'assignee' and item.get('fromString'):
                from_name = item['fromString']
                info = get_user_info_with_normalization(from_name, user_map)
                return info.get('email'), info.get('displayName')
    return None, None


def _count_child_relations(relations):
    """Count child work item relations."""
    if not relations:
        return 0
    return sum(
        1 for r in relations
        if isinstance(r, dict) and 'child' in (r.get('rel') or '').lower()
    )


def _find_resolution_date(fields, revisions):
    """Return the date when the item first entered a resolved/done state."""
    resolved_date_val = fields.get(ADO_FIELDS.get('resolved_date', 'Microsoft.VSTS.Common.ResolvedDate'))
    if resolved_date_val:
        try:
            return parse_date(resolved_date_val)
        except Exception:
            pass
    state_field = ADO_FIELDS['state']
    for rev in revisions:
        state = (rev.get('fields', {}).get(state_field) or '').lower()
        if state in RESOLVED_STATES:
            changed = rev.get('fields', {}).get(ADO_FIELDS['changed_date'])
            if changed:
                try:
                    return parse_date(changed)
                except Exception:
                    pass
    return None


def _find_closed_date(fields, revisions):
    """Return the date the item transitioned to 'Closed' state."""
    closed_date_val = fields.get(ADO_FIELDS.get('closed_date', 'Microsoft.VSTS.Common.ClosedDate'))
    if closed_date_val:
        try:
            return parse_date(closed_date_val)
        except Exception:
            pass
    state_field = ADO_FIELDS['state']
    for rev in reversed(revisions):
        state = (rev.get('fields', {}).get(state_field) or '').lower()
        if state == 'closed':
            changed = rev.get('fields', {}).get(ADO_FIELDS['changed_date'])
            if changed:
                try:
                    return parse_date(changed)
                except Exception:
                    pass
    return None


# ---------------------------------------------------------------------------
# Main function
# ---------------------------------------------------------------------------

def fetch_workitem_details(work_items, user_map):
    """
    Process raw ADO work item data into detailed_data and history_data lists.

    Mirrors Jira fetch_issue_detail.py output column names so that Stage 2
    processing is reusable (jira_project_name → ado_project_name).

    Args:
        work_items: list of raw work item dicts (from ado_raw_issues.json)
        user_map:   dict {displayName: {email, displayName}}

    Returns:
        (detailed_data, history_data)
    """
    detailed_data = []
    history_data = []
    items_with_no_status_change = 0

    state_field = ADO_FIELDS['state']
    project_field = ADO_FIELDS['project']
    title_field = ADO_FIELDS['title']
    type_field = ADO_FIELDS['work_item_type']
    iteration_field = ADO_FIELDS['iteration_path']
    tags_field = ADO_FIELDS['tags']
    sp_field = ADO_FIELDS['story_points']
    estimate_field = ADO_FIELDS['original_estimate']
    completed_field = ADO_FIELDS['completed_work']
    created_date_field = ADO_FIELDS['created_date']
    changed_date_field = ADO_FIELDS['changed_date']
    assigned_to_field = ADO_FIELDS['assigned_to']
    parent_field = ADO_FIELDS['parent']

    for item in work_items:
        if not isinstance(item, dict) or 'fields' not in item:
            continue

        fields = item['fields']
        revisions = item.get('revisions', [])
        relations = item.get('relations', [])

        item_id = item['id']
        item_key = item.get('key', f"{fields.get(project_field, 'ADO')}#{item_id}")
        issue_project = fields.get(project_field, '')
        issue_summary = fields.get(title_field, '')
        issue_type = fields.get(type_field, '')
        issue_state = fields.get(state_field, '')
        issue_sprint = fields.get(iteration_field, '')
        issue_labels = fields.get(tags_field, '') or ''
        issue_story_points = fields.get(sp_field)
        issue_estimate_hours = fields.get(estimate_field)
        issue_actual_hours = fields.get(completed_field)
        issue_subtasks_count = _count_child_relations(relations)

        parent_id = fields.get(parent_field)
        issue_parent_key = f"{issue_project}#{parent_id}" if parent_id else None
        issue_parent_type = None  # ADO doesn't embed parent type in child

        created_raw = fields.get(created_date_field)
        changed_raw = fields.get(changed_date_field)
        issue_created = parse_date(created_raw) if created_raw else None
        issue_updated = parse_date(changed_raw) if changed_raw else None
        issue_resolution_date = _find_resolution_date(fields, revisions)
        issue_closed_date = _find_closed_date(fields, revisions)
        is_issue_closed = issue_state.lower() == 'closed'
        issue_lead_time_for_ticket = None  # calculated later in Stage 2

        # Build synthetic changelog from revision diffs
        changelog = _build_synthetic_changelog(revisions, user_map)

        # Assignee history (reuse Jira assignee_history functions with synthetic changelog)
        assignee_history = extract_assignee_history(changelog, user_map)

        current_assignee_obj = fields.get(assigned_to_field) or {}
        curr_email, curr_display = _get_identity_email(current_assignee_obj, user_map)

        initial_email, initial_name = _detect_initial_assignee(changelog, user_map)
        fallback_email = initial_email or curr_email or 'Unassigned'
        fallback_displayName = initial_name or curr_display or 'Unassigned'

        assignee_timetable = build_assignee_timetable(
            assignee_history, item_key,
            issue_created=issue_created,
            fallback_email=fallback_email,
            fallback_displayName=fallback_displayName,
        )
        assignee_timetable_df = pd.DataFrame(assignee_timetable)

        # Build timeline events from synthetic changelog
        entry_counter = defaultdict(int)
        timeline_events = []
        for entry in sorted(changelog, key=lambda x: x.get('created', '')):
            created_time_dt = parse_date(entry['created'])
            for ch_item in entry.get('items', []):
                if ch_item.get('field') == 'status':
                    timeline_events.append({
                        'type': 'status_change',
                        'time': created_time_dt,
                        'from': ch_item.get('fromString'),
                        'to': ch_item.get('toString'),
                        'author': entry.get('author', {}),
                    })
                elif ch_item.get('field') == 'assignee':
                    timeline_events.append({
                        'type': 'assignee_change',
                        'time': created_time_dt,
                        'from': ch_item.get('fromString') or 'Unassigned',
                        'to': ch_item.get('toString') or 'Unassigned',
                        'author': entry.get('author', {}),
                    })
        timeline_events.sort(key=lambda x: x['time'])

        exit_dates_dict = [
            timeline_events[i + 1]['time'] if i + 1 < len(timeline_events) else None
            for i in range(len(timeline_events))
        ]

        last_time = issue_created
        current_status = None
        status_entry_time = issue_created
        status_change_found = False

        if not assignee_timetable_df.empty:
            latest = assignee_timetable_df.sort_values('assignee_chng_timestamp', ascending=False).iloc[0]
            last_assignee_email = latest['assignee_email']
            last_assignee_displayName = latest['assignee_displayName']
        else:
            last_assignee_email = fallback_email
            last_assignee_displayName = fallback_displayName

        for i, event in enumerate(timeline_events):
            current_time = event['time']
            duration = max(calculate_days_excluding_weekends(last_time, current_time), 0)
            entry_counter[item_key] += 1

            author = event['author']
            changed_by_email = author.get('emailAddress', 'Unknown')
            changed_by_name = author.get('displayName', 'Unknown')

            if i == 0:
                if event['type'] == 'assignee_change':
                    row_status = 'Initial item status'
                    current_status = 'Initial item status'
                    row_entry_time = issue_created
                elif event['type'] == 'status_change':
                    row_status = event['from'] or 'Initial item status'
                    current_status = event['to']
                    row_entry_time = issue_created
                    status_entry_time = current_time
                else:
                    row_status = 'Initial item status'
                    current_status = 'Initial item status'
                    row_entry_time = issue_created
            else:
                row_status = current_status or 'Initial item status'
                if event['type'] == 'status_change':
                    row_entry_time = status_entry_time
                    status_entry_time = current_time
                else:
                    row_entry_time = last_time

            if event['type'] == 'status_change':
                current_status = event['to']

            if not assignee_timetable_df.empty:
                adf = assignee_timetable_df[assignee_timetable_df['assignee_chng_timestamp'] <= last_time]
                if not adf.empty:
                    row_assignee = adf.sort_values('assignee_chng_timestamp', ascending=False).iloc[0]
                    last_assignee_email = row_assignee['assignee_email']
                    last_assignee_displayName = row_assignee['assignee_displayName']

            causality_event_type = None if i == 0 else timeline_events[i - 1]['type']

            _common = dict(
                ado_project_name=issue_project,
                key=item_key,
                issue_summary=issue_summary,
                issue_current_status_name=issue_state,
                issue_type=issue_type,
                issue_no_of_subtasks=issue_subtasks_count,
                issue_story_points=issue_story_points,
                issue_estimate_hours=issue_estimate_hours,
                issue_actual_spent_hours=issue_actual_hours,
                parent_issue_id=issue_parent_key,
                parent_issue_type=issue_parent_type,
                issue_release=None,
                issue_sprint=issue_sprint,
                issue_labels=issue_labels,
                issue_create_date=issue_created,
                issue_update_date=issue_updated,
                issue_resolution_date=issue_resolution_date,
                issue_closed_date=issue_closed_date,
                issue_lead_time_for_ticket=issue_lead_time_for_ticket,
                is_issue_closed=is_issue_closed,
            )

            detailed_data.append({
                **_common,
                'changed_by_name': changed_by_name,
                'changed_by_email': changed_by_email,
                'entry_count': entry_counter[item_key],
                'event_type': causality_event_type,
                'event_date': row_entry_time,
                'duration': duration,
            })

            history_data.append({
                **_common,
                'standard_issue_type': '',
                'issue_vendor': None,
                'issue_status_history_name': row_status,
                'duration_in_status': duration,
                'changed_by_email': changed_by_email,
                'assignee_email_at_status': last_assignee_email,
                'assignee_name_at_status': last_assignee_displayName,
                'event_date': row_entry_time,
                'entry_count': entry_counter[item_key],
                'event_type': causality_event_type,
                'event_exit_date': exit_dates_dict[i],
                'data_quality_flag': '',
            })

            last_time = current_time
            status_change_found = True

        # Final status record
        if timeline_events and current_status:
            final_time = issue_closed_date if issue_closed_date else parse_date(datetime.now().isoformat())
            final_duration = max(calculate_days_excluding_weekends(last_time, final_time), 0)
            entry_counter[item_key] += 1

            if not assignee_timetable_df.empty:
                adf = assignee_timetable_df[assignee_timetable_df['assignee_chng_timestamp'] <= last_time]
                if not adf.empty:
                    row_assignee = adf.sort_values('assignee_chng_timestamp', ascending=False).iloc[0]
                    last_assignee_email = row_assignee['assignee_email']
                    last_assignee_displayName = row_assignee['assignee_displayName']

            history_data.append({
                'ado_project_name': issue_project,
                'key': item_key,
                'issue_summary': issue_summary,
                'issue_current_status_name': issue_state,
                'issue_type': issue_type,
                'standard_issue_type': '',
                'issue_no_of_subtasks': issue_subtasks_count,
                'issue_story_points': issue_story_points,
                'issue_estimate_hours': issue_estimate_hours,
                'issue_actual_spent_hours': issue_actual_hours,
                'parent_issue_id': issue_parent_key,
                'parent_issue_type': issue_parent_type,
                'issue_release': None,
                'issue_sprint': issue_sprint,
                'issue_labels': issue_labels,
                'issue_vendor': None,
                'issue_create_date': issue_created,
                'issue_update_date': issue_updated,
                'issue_resolution_date': issue_resolution_date,
                'issue_closed_date': issue_closed_date,
                'issue_lead_time_for_ticket': issue_lead_time_for_ticket,
                'issue_status_history_name': current_status,
                'duration_in_status': final_duration,
                'changed_by_email': 'ETL_Calculated',
                'assignee_email_at_status': last_assignee_email,
                'assignee_name_at_status': last_assignee_displayName,
                'event_date': last_time,
                'entry_count': entry_counter[item_key],
                'event_type': 'status_change',
                'event_exit_date': None,
                'data_quality_flag': '',
                'is_issue_closed': is_issue_closed,
            })

        if not status_change_found:
            items_with_no_status_change += 1

    logger.info(
        f"Step 10: - ✅ fetch_workitem_details processed {len(work_items)} items. "
        f"No status changes in {items_with_no_status_change} items."
    )

    # Post-process: recalculate exit dates
    history_df_temp = pd.DataFrame(history_data)
    if not history_df_temp.empty:
        history_df_temp['event_date'] = pd.to_datetime(history_df_temp['event_date'])
        history_df_temp = history_df_temp.sort_values(['key', 'event_date', 'entry_count']).reset_index(drop=True)

        def recalculate_exit_dates(group):
            group = group.copy()
            group['event_exit_date'] = group['event_date'].shift(-1)
            return group

        history_df_temp = history_df_temp.groupby('key', group_keys=False).apply(recalculate_exit_dates).reset_index(drop=True)
        history_data = history_df_temp.to_dict('records')
        events_with_exits = sum(1 for r in history_data if r.get('event_exit_date') is not None and pd.notna(r['event_exit_date']))
        logger.info(f"Step 10.1: - ✅ Recalculated exit dates: {events_with_exits}/{len(history_data)} events have exit dates.")

    return detailed_data, history_data
