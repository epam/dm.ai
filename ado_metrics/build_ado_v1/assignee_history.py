# assignee_history.py

import unicodedata
import re
from utils import parse_date

def normalize_user_lookup_key(user_key):
    """
    Normalize user lookup keys to handle Unicode whitespace characters and other variations.
    This ensures consistent lookups regardless of the type of whitespace characters used in Jira data.
    """
    if not user_key or user_key == 'Unassigned':
        return user_key
    
    # Unicode normalization (NFC - canonical decomposition followed by canonical composition)
    normalized = unicodedata.normalize('NFC', user_key)
    
    # Replace all types of whitespace with regular spaces
    # This handles non-breaking spaces (\xa0), tabs, multiple spaces, etc.
    normalized = re.sub(r'\s+', ' ', normalized)
    
    # Strip leading/trailing whitespace
    normalized = normalized.strip()
    
    return normalized

def get_user_info_with_normalization(user_key, user_map):
    """
    Get user info with robust lookup that handles Unicode whitespace variations.
    """
    if not user_key:
        return {'email': 'Unassigned', 'displayName': 'Unassigned'}
    
    # First try exact lookup (for backward compatibility)
    if user_key in user_map:
        return user_map[user_key]
    
    # If exact lookup fails, try normalized lookup
    normalized_key = normalize_user_lookup_key(user_key)
    if normalized_key in user_map:
        return user_map[normalized_key]
    
    # If still not found, try to find by normalized comparison
    for map_key, user_info in user_map.items():
        if normalize_user_lookup_key(map_key) == normalized_key:
            return user_info
    
    # If no match found, return unassigned
    return {'email': 'Unassigned', 'displayName': 'Unassigned'}

def extract_assignee_history(full_history, user_map):
    assignee_history = []
    if not full_history:
        return assignee_history

    for history in full_history:
        changed_time_dt = parse_date(history['created'])

        for item in history.get('items', []):
            if item.get('field') == 'assignee':
                new_assignee = item.get('toString') or item.get('to')
                user_info = get_user_info_with_normalization(new_assignee, user_map)

                assignee_history.append({
                    'time': changed_time_dt,
                    'assignee': new_assignee,
                    'email': user_info['email'],
                    'displayName': user_info['displayName']
                })

    assignee_history = sorted(assignee_history, key=lambda x: x['time'])

    return assignee_history


def build_assignee_timetable(assignee_history, issue_key, issue_created=None, fallback_email='Unassigned', fallback_displayName='Unassigned'):
    """
    Build a list of assignee change records for a given issue.
    Always inject an initial assignee entry at issue_created timestamp, then add all changes.
    """

    assignee_timetable = []

    # Always inject initial assignee at issue creation time
    if issue_created:
        assignee_timetable.append({
            'Key': issue_key,
            'assignee_email': fallback_email,
            'assignee_displayName': fallback_displayName,
            'assignee_chng_timestamp': issue_created
        })

    # Add all assignee changes
    for change in assignee_history:
        assignee_timetable.append({
            'Key': issue_key,
            'assignee_email': change['email'],
            'assignee_displayName': change['displayName'],
            'assignee_chng_timestamp': change['time']
        })

    return assignee_timetable