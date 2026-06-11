# process_pr_analytics.py
#
# Enrich raw PR data with code-review lead time, commit analysis,
# date parts, and build staging / fact / report DataFrames
# (analogous to github_build_v15's github_processing + github_analytics).

import pandas as pd
from datetime import datetime
from dateutil import parser as dtparser
from config import logger


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _parse_dt(value):
    """Parse an ISO-8601 string to a timezone-naive datetime (or None)."""
    if not value:
        return None
    try:
        dt = dtparser.isoparse(str(value))
        if dt.tzinfo is not None:
            dt = dt.replace(tzinfo=None)
        return dt
    except Exception:
        return None


def _iso_month(dt):
    return dt.strftime('%Y-%m') if dt else None


def _iso_week(dt):
    if dt is None:
        return None
    iso = dt.isocalendar()
    return f"{iso[0]}-W{iso[1]:02d}"


# ---------------------------------------------------------------------------
# build_pr_staging  (equivalent to GitHub "staging" sheet)
# ---------------------------------------------------------------------------

def build_pr_staging(raw_prs):
    """
    Convert raw PR JSON list into an enriched staging DataFrame.

    Columns produced mirror the GitHub example:
      repo_name, pr_id, pr_title, pr_status, pr_merge_status,
      pr_author_login, pr_author_name, pr_author_email,
      pr_source_branch, pr_target_branch,
      pr_created_at, pr_completion_date, pr_closed_at,
      pr_first_comment_date,
      total_commits, comments_count, reviewer_count,
      code_review_lead_time_hours,
      is_valid_lead_time,
      pr_created_at_month_iso, pr_created_at_week_iso,
      pr_completion_date_month_iso, pr_completion_date_week_iso,
      is_draft, merge_status_raw
    """
    if not raw_prs:
        logger.warning("process_pr_analytics: no PRs to stage.")
        return pd.DataFrame()

    rows = []
    for pr in raw_prs:
        created_at  = _parse_dt(pr.get('creation_date'))
        completion   = _parse_dt(pr.get('completion_date')) or _parse_dt(pr.get('closed_date'))
        closed_at    = _parse_dt(pr.get('closed_date'))

        # threads → comment analysis
        threads = pr.get('threads', [])
        comments_count = len(threads)
        first_comment_date = None
        if threads:
            dates = [_parse_dt(t.get('published_date')) for t in threads]
            dates = [d for d in dates if d is not None]
            if dates:
                first_comment_date = min(dates)

        # commits
        commits_list = pr.get('commits', [])
        total_commits = len(commits_list)

        # merge status classification (use ADO status directly)
        status_raw = str(pr.get('status', '')).lower()
        if status_raw == 'completed' or completion:
            pr_merge_status = 'Completed'
        elif status_raw == 'abandoned':
            pr_merge_status = 'Abandoned'
        else:
            pr_merge_status = 'Active'

        # code-review lead time (creation → completion, in hours)
        end_dt = completion or closed_at
        cr_lead_time = None
        if created_at and end_dt and end_dt > created_at:
            cr_lead_time = round((end_dt - created_at).total_seconds() / 3600, 2)

        is_valid_lt = cr_lead_time is not None and cr_lead_time >= 0.05

        rows.append({
            'repo_name':                   pr.get('repo_name', ''),
            'pr_id':                       pr.get('pr_id', ''),
            'pr_title':                    pr.get('title', ''),
            'pr_status':                   status_raw,
            'pr_merge_status':             pr_merge_status,
            'pr_author_email':             pr.get('created_by_email', ''),
            'pr_author_name':              pr.get('created_by_name', ''),
            'pr_source_branch':            (pr.get('source_ref', '') or '').replace('refs/heads/', ''),
            'pr_target_branch':            (pr.get('target_ref', '') or '').replace('refs/heads/', ''),
            'pr_created_at':               created_at,
            'pr_completion_date':          completion,
            'pr_closed_at':                closed_at,
            'pr_first_comment_date':       first_comment_date,
            'total_commits':               total_commits,
            'comments_count':              comments_count,
            'reviewer_count':              len(pr.get('reviewers', [])),
            'code_review_lead_time_hours': cr_lead_time,
            'is_valid_lead_time':          is_valid_lt,
            'is_draft':                    pr.get('is_draft', False),
            'merge_status_raw':            pr.get('merge_status', ''),
            # date-part columns
            'pr_created_at_month_iso':     _iso_month(created_at),
            'pr_created_at_week_iso':      _iso_week(created_at),
            'pr_completion_date_month_iso': _iso_month(completion),
            'pr_completion_date_week_iso':  _iso_week(completion),
        })

    df = pd.DataFrame(rows).sort_values('pr_created_at', ascending=False).reset_index(drop=True)
    logger.info(f"_______  - ✅ PR staging DataFrame: {len(df)} rows, {len(df.columns)} cols.")
    return df


# ---------------------------------------------------------------------------
# build_fact_cr_lead_time  (equivalent to GitHub "fact_cr_lead_time" sheet)
# ---------------------------------------------------------------------------

def build_fact_cr_lead_time(staging_df):
    """Filter to completed PRs with valid lead time → fact table."""
    if staging_df.empty:
        return pd.DataFrame()
    fact = staging_df[
        (staging_df['pr_merge_status'] == 'Completed') &
        (staging_df['is_valid_lead_time'] == True)
    ].copy()
    logger.info(f"_______  - ✅ fact_cr_lead_time: {len(fact)} rows from {len(staging_df)} staged PRs.")
    return fact


# ---------------------------------------------------------------------------
# build_cr_lead_time_report  (monthly aggregation)
# ---------------------------------------------------------------------------

def build_cr_lead_time_report(fact_df):
    """Monthly summary: median/avg lead time, PR count."""
    if fact_df.empty or 'pr_completion_date_month_iso' not in fact_df.columns:
        return pd.DataFrame(columns=[
            'pr_merge_date_month_iso', 'median_code_review_lead_time',
            'average_code_review_lead_time', 'prs_count'
        ])
    grp = fact_df.groupby('pr_completion_date_month_iso').agg(
        median_code_review_lead_time=('code_review_lead_time_hours', 'median'),
        average_code_review_lead_time=('code_review_lead_time_hours', 'mean'),
        prs_count=('pr_id', 'count'),
    ).reset_index().rename(columns={'pr_completion_date_month_iso': 'pr_merge_date_month_iso'})
    logger.info(f"_______  - ✅ cr_lead_time_report (monthly): {len(grp)} rows.")
    return grp


# ---------------------------------------------------------------------------
# build_cr_lead_time_report_by_weeks  (weekly aggregation)
# ---------------------------------------------------------------------------

def build_cr_lead_time_report_by_weeks(fact_df):
    """Weekly summary: median/avg lead time, PR count."""
    if fact_df.empty or 'pr_completion_date_week_iso' not in fact_df.columns:
        return pd.DataFrame(columns=[
            'pr_merge_date_week_iso', 'median_code_review_lead_time',
            'average_code_review_lead_time', 'prs_count'
        ])
    grp = fact_df.groupby('pr_completion_date_week_iso').agg(
        median_code_review_lead_time=('code_review_lead_time_hours', 'median'),
        average_code_review_lead_time=('code_review_lead_time_hours', 'mean'),
        prs_count=('pr_id', 'count'),
    ).reset_index().rename(columns={'pr_completion_date_week_iso': 'pr_merge_date_week_iso'})
    logger.info(f"_______  - ✅ cr_lead_time_report (weekly): {len(grp)} rows.")
    return grp


# ---------------------------------------------------------------------------
# build_user_data  (contributor metadata)
# ---------------------------------------------------------------------------

def build_user_data(raw_prs):
    """Deduplicated contributor list from PR authors, commit authors, and reviewers."""
    if not raw_prs:
        return pd.DataFrame(columns=['user_email', 'user_name', 'source'])

    seen = {}  # keyed by email.lower()
    for pr in raw_prs:
        # PR author
        email = pr.get('created_by_email', '')
        name  = pr.get('created_by_name', '')
        if email:
            key = email.lower()
            if key not in seen:
                seen[key] = {'user_email': email, 'user_name': name, 'source': 'pr_author'}

        # Commit authors
        for c in pr.get('commits', []):
            for prefix in ('author', 'committer'):
                em = c.get(f'{prefix}_email', '')
                nm = c.get(f'{prefix}_name', '')
                if em:
                    k = em.lower()
                    if k not in seen:
                        seen[k] = {'user_email': em, 'user_name': nm, 'source': f'commit_{prefix}'}

        # Reviewers
        for rv in pr.get('reviewers', []):
            em = rv.get('email', '')
            nm = rv.get('display_name', '')
            if em:
                k = em.lower()
                if k not in seen:
                    seen[k] = {'user_email': em, 'user_name': nm, 'source': 'reviewer'}

        # Thread commenters
        for t in pr.get('threads', []):
            em = t.get('author_email', '')
            nm = t.get('author_name', '')
            if em:
                k = em.lower()
                if k not in seen:
                    seen[k] = {'user_email': em, 'user_name': nm, 'source': 'commenter'}

    df = pd.DataFrame(list(seen.values())).sort_values('user_email').reset_index(drop=True)
    logger.info(f"_______  - ✅ user_data: {len(df)} unique contributors.")
    return df
