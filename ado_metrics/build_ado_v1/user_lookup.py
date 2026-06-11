# user_lookup.py
#
# Builds a user map from ADO work items.
# In ADO, user info (uniqueName = email, displayName) is embedded directly in
# work item fields such as System.AssignedTo, System.CreatedBy, System.ChangedBy.
# No separate "get all users" API call is needed.

from config import logger, ADO_FIELDS


def _extract_identity(identity_obj):
    """
    Extract (email, displayName) from an ADO identity dict.
    ADO IdentityRef has 'uniqueName' (= email) and 'displayName'.
    """
    if not identity_obj or not isinstance(identity_obj, dict):
        return None, None
    email = identity_obj.get('uniqueName') or identity_obj.get('unique_name') or ''
    display_name = identity_obj.get('displayName') or identity_obj.get('display_name') or ''
    return email.strip(), display_name.strip()


def build_user_map_from_work_items(work_items):
    """
    Build a user map by scanning all identity fields in work items and their revisions.

    Returns:
        dict: {displayName: {'email': email, 'displayName': displayName}}
    """
    user_map = {}
    identity_fields = [
        ADO_FIELDS['assigned_to'],
        ADO_FIELDS['created_by'],
        ADO_FIELDS['changed_by'],
    ]

    def _register(identity_obj):
        email, display_name = _extract_identity(identity_obj)
        if email and display_name and display_name not in user_map:
            user_map[display_name] = {'email': email, 'displayName': display_name}
        # Also index by email for reverse lookups
        if email and email not in user_map:
            user_map[email] = {'email': email, 'displayName': display_name or email}

    for item in work_items:
        fields = item.get('fields', {})
        for field_name in identity_fields:
            identity = fields.get(field_name)
            if identity:
                _register(identity)

        # Also scan revisions
        for rev in item.get('revisions', []):
            rev_fields = rev.get('fields', {})
            for field_name in identity_fields:
                identity = rev_fields.get(field_name)
                if identity:
                    _register(identity)

    unique_emails = {v['email'] for v in user_map.values() if v.get('email')}
    logger.info(f"Step 05: - ✅ User map contains {len(unique_emails)} unique user emails from work items.")
    return user_map
