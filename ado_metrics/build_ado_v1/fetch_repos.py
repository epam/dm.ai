# fetch_repos.py
#
# Fetch Git repositories and commits from Azure DevOps.
# Uses the azure-devops SDK git_client (GitClient).

from datetime import datetime, timedelta, timezone
from config import logger, DATA_FETCH_FROM_DATE, MAX_COMMITS_PER_REPO


def _resolve_from_date():
    """Return a UTC-aware datetime for the commit fetch window."""
    if DATA_FETCH_FROM_DATE:
        try:
            dt = datetime.fromisoformat(DATA_FETCH_FROM_DATE)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt
        except ValueError:
            logger.warning(f"Invalid DATA_FETCH_FROM_DATE '{DATA_FETCH_FROM_DATE}' — defaulting to 1 year back.")
    return datetime.now(tz=timezone.utc) - timedelta(days=365)


def _serialize_repo(repo):
    """Convert SDK GitRepository object to a plain dict."""
    try:
        remote_url = repo.remote_url or ''
    except AttributeError:
        remote_url = ''
    return {
        'id':              getattr(repo, 'id', ''),
        'name':            getattr(repo, 'name', ''),
        'default_branch':  getattr(repo, 'default_branch', '') or '',
        'remote_url':      remote_url,
        'project_name':    getattr(getattr(repo, 'project', None), 'name', '') or '',
        'size':            getattr(repo, 'size', None),
        'is_disabled':     getattr(repo, 'is_disabled', False),
    }


def _serialize_commit(commit, repo_id, repo_name):
    """Convert SDK GitCommitRef to a plain dict."""
    author = getattr(commit, 'author', None) or {}
    committer = getattr(commit, 'committer', None) or {}
    if hasattr(author, 'name'):
        author_name  = author.name or ''
        author_email = author.email or ''
        author_date  = author.date.isoformat() if getattr(author, 'date', None) else ''
    else:
        author_name  = author.get('name', '')
        author_email = author.get('email', '')
        author_date  = author.get('date', '')
    if hasattr(committer, 'date'):
        committer_date = committer.date.isoformat() if committer.date else ''
    else:
        committer_date = committer.get('date', '') if isinstance(committer, dict) else ''

    return {
        'repo_id':        repo_id,
        'repo_name':      repo_name,
        'commit_id':      getattr(commit, 'commit_id', ''),
        'author_name':    author_name,
        'author_email':   author_email,
        'author_date':    author_date,
        'committer_date': committer_date,
        'comment':        (getattr(commit, 'comment', '') or '').split('\n')[0][:500],  # first line, truncated
        'change_counts':  dict(getattr(commit, 'change_counts', {}) or {}),
    }


def fetch_repositories(git_client, project):
    """
    Fetch all Git repositories in the project.

    Returns:
        list of repo dicts
    """
    try:
        repos = git_client.get_repositories(project)
        result = [_serialize_repo(r) for r in repos if not getattr(r, 'is_disabled', False)]
        logger.info(f"_______  - ✅ Fetched {len(result)} repositories.")
        return result
    except Exception as e:
        logger.error(f"_______  - ❌ fetch_repositories failed: {e}")
        return []


def fetch_commits(git_client, project, repos, from_date=None):
    """
    Fetch commits for all repos from `from_date` onward.

    Args:
        git_client: SDK GitClient
        project:    project name
        repos:      list of repo dicts (from fetch_repositories)
        from_date:  datetime | None — defaults to DATA_FETCH_FROM_DATE or 1 year back

    Returns:
        list of commit dicts
    """
    from azure.devops.v7_1.git.models import GitQueryCommitsCriteria

    if from_date is None:
        from_date = _resolve_from_date()

    all_commits = []
    for repo in repos:
        repo_id   = repo['id']
        repo_name = repo['name']
        try:
            criteria = GitQueryCommitsCriteria(
                from_date=from_date.strftime('%m/%d/%Y %H:%M:%S'),
            )
            top = MAX_COMMITS_PER_REPO if MAX_COMMITS_PER_REPO > 0 else 50000
            commits = git_client.get_commits(
                repository_id=repo_id,
                search_criteria=criteria,
                project=project,
                top=top,
            )
            batch = [_serialize_commit(c, repo_id, repo_name) for c in (commits or [])]
            all_commits.extend(batch)
            logger.info(f"  → {repo_name}: {len(batch)} commits (since {from_date.date()})")
        except Exception as e:
            logger.warning(f"  → {repo_name}: commit fetch failed — {e}")

    logger.info(f"_______  - ✅ Total commits fetched: {len(all_commits)}")
    return all_commits
