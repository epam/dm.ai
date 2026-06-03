# fetch_pullrequests.py
#
# Fetch Pull Requests (+ reviewer votes) from all Git repositories in the project.
# Uses the azure-devops SDK GitClient.

from config import logger


# Numeric vote values returned by ADO API
_VOTE_LABELS = {
    10: 'approved',
    5:  'approved_with_suggestions',
    0:  'no_vote',
    -5: 'waiting_for_author',
    -10: 'rejected',
}


def _serialize_reviewer(reviewer, pr_id):
    identity = reviewer if isinstance(reviewer, dict) else {}
    if hasattr(reviewer, 'unique_name'):
        email        = reviewer.unique_name or ''
        display_name = reviewer.display_name or ''
        vote         = reviewer.vote if hasattr(reviewer, 'vote') else 0
        is_required  = getattr(reviewer, 'is_required', False) or False
    else:
        email        = identity.get('uniqueName', identity.get('unique_name', ''))
        display_name = identity.get('displayName', identity.get('display_name', ''))
        vote         = identity.get('vote', 0)
        is_required  = identity.get('isRequired', identity.get('is_required', False))

    return {
        'pr_id':        pr_id,
        'email':        email,
        'display_name': display_name,
        'vote':         vote,
        'vote_label':   _VOTE_LABELS.get(int(vote or 0), 'unknown'),
        'is_required':  bool(is_required),
    }


def _serialize_pr(pr, repo_name):
    """Convert SDK GitPullRequest to a plain dict."""
    def _identity_email(obj):
        if obj is None: return ''
        if hasattr(obj, 'unique_name'): return obj.unique_name or ''
        return obj.get('uniqueName', obj.get('unique_name', '')) if isinstance(obj, dict) else ''

    def _identity_name(obj):
        if obj is None: return ''
        if hasattr(obj, 'display_name'): return obj.display_name or ''
        return obj.get('displayName', obj.get('display_name', '')) if isinstance(obj, dict) else ''

    def _isoformat(dt):
        if dt is None: return ''
        return dt.isoformat() if hasattr(dt, 'isoformat') else str(dt)

    creation_date   = getattr(pr, 'creation_date', None)
    completion_date = getattr(pr, 'completion_date', None)
    closed_date     = getattr(pr, 'closed_date', None)

    reviewers = getattr(pr, 'reviewers', []) or []
    reviewer_list = [_serialize_reviewer(r, pr.pull_request_id) for r in reviewers]

    return {
        'pr_id':             pr.pull_request_id,
        'title':             getattr(pr, 'title', '') or '',
        'status':            getattr(pr, 'status', '') or '',
        'repo_name':         repo_name,
        'repo_id':           getattr(getattr(pr, 'repository', None), 'id', '') or '',
        'source_ref':        getattr(pr, 'source_ref_name', '') or '',
        'target_ref':        getattr(pr, 'target_ref_name', '') or '',
        'created_by_email':  _identity_email(getattr(pr, 'created_by', None)),
        'created_by_name':   _identity_name(getattr(pr, 'created_by', None)),
        'creation_date':     _isoformat(creation_date),
        'completion_date':   _isoformat(completion_date),
        'closed_date':       _isoformat(closed_date),
        'is_draft':          getattr(pr, 'is_draft', False) or False,
        'merge_status':      getattr(pr, 'merge_status', '') or '',
        'description':       (getattr(pr, 'description', '') or '')[:500],
        'reviewers':         reviewer_list,
    }


def _fetch_pr_threads(git_client, repo_id, pr_id, project):
    """Fetch comment threads for a single PR. Returns list of thread dicts."""
    try:
        threads = git_client.get_threads(repository_id=repo_id, pull_request_id=pr_id, project=project)
        result = []
        for t in (threads or []):
            comments = getattr(t, 'comments', []) or []
            for c in comments:
                if getattr(c, 'comment_type', '') == 'system':
                    continue
                result.append({
                    'author_email': (getattr(getattr(c, 'author', None), 'unique_name', '') or ''),
                    'author_name': (getattr(getattr(c, 'author', None), 'display_name', '') or ''),
                    'published_date': (c.published_date.isoformat() if getattr(c, 'published_date', None) and hasattr(c.published_date, 'isoformat') else ''),
                })
        return result
    except Exception as e:
        logger.debug(f"  threads fetch failed for PR {pr_id}: {e}")
        return []


def _fetch_pr_commits(git_client, repo_id, pr_id, project):
    """Fetch commits for a single PR. Returns list of commit dicts."""
    try:
        commits = git_client.get_pull_request_commits(repository_id=repo_id, pull_request_id=pr_id, project=project)
        result = []
        for c in (commits or []):
            author = getattr(c, 'author', None)
            committer = getattr(c, 'committer', None)
            result.append({
                'commit_id': getattr(c, 'commit_id', ''),
                'author_name': (getattr(author, 'name', '') or '') if author else '',
                'author_email': (getattr(author, 'email', '') or '') if author else '',
                'author_date': (author.date.isoformat() if getattr(author, 'date', None) and hasattr(author.date, 'isoformat') else '') if author else '',
                'committer_name': (getattr(committer, 'name', '') or '') if committer else '',
                'committer_email': (getattr(committer, 'email', '') or '') if committer else '',
                'committer_date': (committer.date.isoformat() if getattr(committer, 'date', None) and hasattr(committer.date, 'isoformat') else '') if committer else '',
            })
        return result
    except Exception as e:
        logger.debug(f"  commits fetch failed for PR {pr_id}: {e}")
        return []


def fetch_pull_requests(git_client, project, repos):
    """
    Fetch all pull requests across all repos in the project,
    including per-PR commits and comment threads.

    Args:
        git_client: SDK GitClient
        project:    project name
        repos:      list of repo dicts from fetch_repos.fetch_repositories

    Returns:
        list of PR dicts (each includes 'reviewers', 'threads', and 'commits' sub-lists)
    """
    from azure.devops.v7_1.git.models import GitPullRequestSearchCriteria

    all_prs = []
    for repo in repos:
        repo_id   = repo['id']
        repo_name = repo['name']
        try:
            # status='all' returns active + completed + abandoned
            criteria = GitPullRequestSearchCriteria(status='all')
            skip = 0
            batch_size = 1000
            while True:
                batch = git_client.get_pull_requests(
                    repository_id=repo_id,
                    search_criteria=criteria,
                    project=project,
                    top=batch_size,
                    skip=skip,
                )
                if not batch:
                    break
                for pr in batch:
                    pr_dict = _serialize_pr(pr, repo_name)
                    pr_id = pr.pull_request_id
                    pr_dict['threads'] = _fetch_pr_threads(git_client, repo_id, pr_id, project)
                    pr_dict['commits'] = _fetch_pr_commits(git_client, repo_id, pr_id, project)
                    all_prs.append(pr_dict)
                if len(batch) < batch_size:
                    break
                skip += batch_size
            logger.info(f"  → {repo_name}: {sum(1 for p in all_prs if p['repo_name'] == repo_name)} PRs")
        except Exception as e:
            logger.warning(f"  → {repo_name}: PR fetch failed — {e}")

    logger.info(f"_______  - ✅ Total PRs fetched: {len(all_prs)}")
    return all_prs
