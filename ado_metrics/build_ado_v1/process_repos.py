# process_repos.py
#
# Flatten raw repos / commits JSON (from Stage 1) into pandas DataFrames.

import pandas as pd
from config import logger


def flatten_repos(raw_repos):
    """
    Flatten list of repo dicts into a DataFrame.

    Args:
        raw_repos: list of dicts from fetch_repos.fetch_repositories

    Returns:
        pd.DataFrame with columns: id, name, default_branch, remote_url, project_name, size
    """
    if not raw_repos:
        logger.warning("_______  - ⚠️  No repos to flatten.")
        return pd.DataFrame()

    rows = []
    for r in raw_repos:
        rows.append({
            'repo_id':         r.get('id', ''),
            'repo_name':       r.get('name', ''),
            'default_branch':  (r.get('default_branch', '') or '').replace('refs/heads/', ''),
            'remote_url':      r.get('remote_url', ''),
            'project_name':    r.get('project_name', ''),
            'size_bytes':      r.get('size', None),
        })

    df = pd.DataFrame(rows)
    logger.info(f"_______  - ✅ Repos DataFrame: {len(df)} rows.")
    return df


def flatten_commits(raw_commits):
    """
    Flatten list of commit dicts into a DataFrame.

    Args:
        raw_commits: list of dicts from fetch_repos.fetch_commits

    Returns:
        pd.DataFrame with columns: repo_name, commit_id, author_name, author_email,
                                   author_date, committer_date, comment
    """
    if not raw_commits:
        logger.warning("_______  - ⚠️  No commits to flatten.")
        return pd.DataFrame()

    rows = []
    for c in raw_commits:
        change_counts = c.get('change_counts', {}) or {}
        rows.append({
            'repo_id':        c.get('repo_id', ''),
            'repo_name':      c.get('repo_name', ''),
            'commit_id':      c.get('commit_id', ''),
            'author_name':    c.get('author_name', ''),
            'author_email':   c.get('author_email', ''),
            'author_date':    pd.to_datetime(c.get('author_date', ''), errors='coerce'),
            'committer_date': pd.to_datetime(c.get('committer_date', ''), errors='coerce'),
            'comment':        c.get('comment', ''),
            'adds':           change_counts.get('Add', 0),
            'edits':          change_counts.get('Edit', 0),
            'deletes':        change_counts.get('Delete', 0),
        })

    df = pd.DataFrame(rows).sort_values('author_date', ascending=False).reset_index(drop=True)
    logger.info(f"_______  - ✅ Commits DataFrame: {len(df)} rows.")
    return df
