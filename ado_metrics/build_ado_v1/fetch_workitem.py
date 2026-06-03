# fetch_workitem.py

import time
import random
from config import logger, ADO_PROJECT, ADO_FIELDS, ADO_TEAM

from azure.devops.v7_1.work_item_tracking.models import Wiql, TeamContext as WitTeamContext
from azure.devops.v7_1.work.models import TeamContext as WorkTeamContext


BATCH_SIZE = 200  # ADO API max work items per GET request


def _serialize_identity(identity_ref):
    """Convert ADO IdentityRef object or dict to plain dict."""
    if identity_ref is None:
        return None
    if isinstance(identity_ref, dict):
        return identity_ref
    return {
        'uniqueName': getattr(identity_ref, 'unique_name', '') or '',
        'displayName': getattr(identity_ref, 'display_name', '') or '',
        'id': getattr(identity_ref, 'id', '') or '',
    }


def _serialize_fields(fields):
    """Convert an ADO fields dict (possibly containing SDK objects) to plain python dict."""
    if not fields:
        return {}
    result = {}
    for key, value in fields.items():
        if value is None:
            result[key] = None
        elif hasattr(value, 'unique_name') or hasattr(value, 'display_name'):
            # IdentityRef object
            result[key] = _serialize_identity(value)
        elif hasattr(value, '__dict__'):
            result[key] = str(value)
        else:
            result[key] = value
    return result


def _serialize_work_item(item):
    """Convert SDK WorkItem object to plain dict suitable for JSON serialization."""
    fields = _serialize_fields(item.fields or {})
    relations = []
    if item.relations:
        for rel in item.relations:
            relations.append({
                'rel': rel.rel,
                'url': rel.url,
                'attributes': rel.attributes or {},
            })
    return {
        'id': item.id,
        'key': f"{fields.get(ADO_FIELDS['project'], ADO_PROJECT)}#{item.id}",
        'rev': item.rev,
        'fields': fields,
        'relations': relations,
        'revisions': [],  # populated separately
    }


def _serialize_revision(rev_item):
    """Convert SDK revision WorkItem object to plain dict."""
    return {
        'rev': rev_item.rev,
        'id': rev_item.id,
        'fields': _serialize_fields(rev_item.fields or {}),
    }


def fetch_work_item_ids(wit_client, wiql_query, project):
    """
    Execute a WIQL query and return the list of work item IDs.
    Handles the 20,000-result limit of WIQL by logging a warning.
    """
    logger.info(f"Step 02: - 🔍 Executing WIQL query for project '{project}'...")
    wiql = Wiql(query=wiql_query)
    try:
        # SDK compatibility: newer SDKs use team_context, older ones accepted project directly.
        try:
            result = wit_client.query_by_wiql(wiql, team_context=WitTeamContext(project=project))
        except TypeError:
            result = wit_client.query_by_wiql(wiql, project=project)
        ids = [ref.id for ref in (result.work_items or [])]
        logger.info(f"Step 02: - ✅ WIQL returned {len(ids)} work item IDs.")
        if len(ids) == 20000:
            logger.warning(
                "Step 02: - ⚠️  WIQL result is exactly 20,000 items — this is the API limit. "
                "Some items may have been truncated. Consider refining your WIQL query with date filters."
            )
        return ids
    except Exception as e:
        logger.error(f"Step 02: - ❌ WIQL query failed: {e}")
        raise


def fetch_work_items_batch(wit_client, ids_chunk, retries=3, delay=5):
    """
    Fetch a batch of work items (max 200) with full field expansion and retry logic.
    """
    max_delay = 30
    last_exception = None
    for attempt in range(retries):
        try:
            items = wit_client.get_work_items(
                ids=ids_chunk,
                expand='All',
                error_policy='omit',  # skip items that fail individually
            )
            return [i for i in (items or []) if i is not None]
        except Exception as e:
            last_exception = e
            wait = min(delay * (2 ** attempt) + random.uniform(0, 1), max_delay)
            logger.warning(f"Step 02: - ⚠️  Batch fetch attempt {attempt + 1}/{retries} failed: {e}. Retrying in {wait:.1f}s...")
            if attempt < retries - 1:
                time.sleep(wait)
    raise RuntimeError(f"Step 02: - ❌ Failed to fetch batch after {retries} attempts: {last_exception}")


def fetch_revisions(wit_client, work_item_id, project, retries=3, delay=5):
    """
    Fetch all revisions for a single work item, with retry logic.
    Revisions are the ADO equivalent of Jira's changelog.histories.
    """
    max_delay = 30
    last_exception = None
    for attempt in range(retries):
        try:
            revisions = wit_client.get_revisions(
                id=work_item_id,
                project=project,
                expand='All',
            )
            return revisions or []
        except Exception as e:
            last_exception = e
            wait = min(delay * (2 ** attempt) + random.uniform(0, 1), max_delay)
            logger.warning(
                f"Step 02: - ⚠️  Revision fetch for item {work_item_id} attempt {attempt + 1}/{retries} failed: {e}. "
                f"Retrying in {wait:.1f}s..."
            )
            if attempt < retries - 1:
                time.sleep(wait)
    logger.error(f"Step 02: - ❌ Failed to fetch revisions for item {work_item_id} after {retries} attempts: {last_exception}")
    return []


def fetch_iterations(work_client, project, team):
    """
    Fetch all iterations (sprints) for the given project/team.
    Returns a list of plain dicts with iteration metadata.
    """
    try:
        try:
            iterations = work_client.get_team_iterations(
                team_context=WorkTeamContext(project=project, team=team)
            )
        except TypeError:
            iterations = work_client.get_team_iterations(project=project, team=team)
        result = []
        for it in (iterations or []):
            attrs = it.attributes or type('obj', (object,), {'start_date': None, 'finish_date': None, 'time_frame': None})()
            result.append({
                'id': it.id,
                'name': it.name,
                'path': it.path,
                'start_date': getattr(attrs, 'start_date', None),
                'finish_date': getattr(attrs, 'finish_date', None),
                'time_frame': getattr(attrs, 'time_frame', None),
            })
        logger.info(f"Step 02: - ✅ Fetched {len(result)} iterations for team '{team}'.")
        return result
    except Exception as e:
        logger.warning(f"Step 02: - ⚠️  Could not fetch iterations for team '{team}': {e}. Sprint data will be missing.")
        return []


def fetch_work_items(wit_client, work_client, wiql_query, project, team, fetch_revisions_flag=True):
    """
    Full Stage 1 fetch:
      1. WIQL query → IDs
      2. Batch fetch work items (max 200/request)
      3. Fetch revisions for each work item
      4. Fetch iterations (sprints)
    Returns (work_items_list, iterations_list).
    """
    # Step 1: WIQL
    all_ids = fetch_work_item_ids(wit_client, wiql_query, project)
    if not all_ids:
        logger.warning("Step 02: - ⚠️  No work item IDs returned by WIQL query.")
        return [], []

    # Step 2: Batch fetch work items
    all_items_raw = []
    chunks = [all_ids[i:i + BATCH_SIZE] for i in range(0, len(all_ids), BATCH_SIZE)]
    logger.info(f"Step 02: - 🔄 Fetching {len(all_ids)} work items in {len(chunks)} batch(es)...")
    for idx, chunk in enumerate(chunks, 1):
        batch = fetch_work_items_batch(wit_client, chunk)
        all_items_raw.extend(batch)
        logger.info(f"Step 02: -    Batch {idx}/{len(chunks)}: fetched {len(batch)} items (total so far: {len(all_items_raw)})")

    logger.info(f"Step 02: - ✅ Fetched {len(all_items_raw)} work items total.")

    # Step 3: Serialize + fetch revisions
    serialized_items = []
    for item in all_items_raw:
        serialized = _serialize_work_item(item)
        if fetch_revisions_flag:
            raw_revisions = fetch_revisions(wit_client, item.id, project)
            serialized['revisions'] = [_serialize_revision(r) for r in raw_revisions]
        serialized_items.append(serialized)

    logger.info(f"Step 02: - ✅ Serialized {len(serialized_items)} work items with revisions.")

    # Step 4: Fetch iterations
    iterations = fetch_iterations(work_client, project, team)

    return serialized_items, iterations
