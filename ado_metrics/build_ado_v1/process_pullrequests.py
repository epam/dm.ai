# process_pullrequests.py
#
# Flatten raw PR JSON (from Stage 1) into pandas DataFrames.

import pandas as pd
from config import logger


def flatten_pull_requests(raw_prs):
    """
    Flatten list of PR dicts into a DataFrame (one row per PR).

    Args:
        raw_prs: list of dicts from fetch_pullrequests.fetch_pull_requests

    Returns:
        pd.DataFrame
    """
    if not raw_prs:
        logger.warning("_______  - ⚠️  No PRs to flatten.")
        return pd.DataFrame()

    rows = []
    for pr in raw_prs:
        rows.append({
            'pr_id':             pr.get('pr_id', ''),
            'title':             pr.get('title', ''),
            'status':            pr.get('status', ''),
            'repo_name':         pr.get('repo_name', ''),
            'repo_id':           pr.get('repo_id', ''),
            'source_ref':        (pr.get('source_ref', '') or '').replace('refs/heads/', ''),
            'target_ref':        (pr.get('target_ref', '') or '').replace('refs/heads/', ''),
            'created_by_name':   pr.get('created_by_name', ''),
            'created_by_email':  pr.get('created_by_email', ''),
            'creation_date':     pd.to_datetime(pr.get('creation_date', ''), errors='coerce'),
            'completion_date':   pd.to_datetime(pr.get('completion_date', ''), errors='coerce'),
            'closed_date':       pd.to_datetime(pr.get('closed_date', ''), errors='coerce'),
            'is_draft':          pr.get('is_draft', False),
            'merge_status':      pr.get('merge_status', ''),
            'reviewer_count':    len(pr.get('reviewers', [])),
        })

    df = pd.DataFrame(rows).sort_values('creation_date', ascending=False).reset_index(drop=True)
    logger.info(f"_______  - ✅ PR DataFrame: {len(df)} rows.")
    return df


def flatten_pr_reviewers(raw_prs):
    """
    Flatten reviewer sub-lists into a long DataFrame (one row per PR × reviewer).

    Args:
        raw_prs: list of dicts from fetch_pullrequests.fetch_pull_requests

    Returns:
        pd.DataFrame
    """
    if not raw_prs:
        return pd.DataFrame()

    rows = []
    for pr in raw_prs:
        pr_id = pr.get('pr_id', '')
        for rv in pr.get('reviewers', []):
            rows.append({
                'pr_id':        pr_id,
                'repo_name':    pr.get('repo_name', ''),
                'email':        rv.get('email', ''),
                'display_name': rv.get('display_name', ''),
                'vote':         rv.get('vote', 0),
                'vote_label':   rv.get('vote_label', ''),
                'is_required':  rv.get('is_required', False),
            })

    if not rows:
        logger.warning("_______  - ⚠️  No PR reviewer rows.")
        return pd.DataFrame()

    df = pd.DataFrame(rows).sort_values(['pr_id', 'email']).reset_index(drop=True)
    logger.info(f"_______  - ✅ PR reviewers DataFrame: {len(df)} rows.")
    return df
