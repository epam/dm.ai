# fetch_pipelines.py
#
# Fetch pipeline definitions and their runs from Azure DevOps.
# Uses the azure-devops SDK pipelines_client (PipelinesClient).

from config import logger, MAX_PIPELINE_RUNS


def _serialize_pipeline(pipeline):
    """Convert SDK Pipeline object to a plain dict."""
    return {
        'id':     getattr(pipeline, 'id', ''),
        'name':   getattr(pipeline, 'name', '') or '',
        'folder': getattr(pipeline, 'folder', '') or '',
        'revision': getattr(pipeline, 'revision', None),
    }


def _serialize_run(run, pipeline_id, pipeline_name):
    """Convert SDK Run object to a plain dict."""
    def _isoformat(dt):
        if dt is None: return ''
        return dt.isoformat() if hasattr(dt, 'isoformat') else str(dt)

    created_date  = getattr(run, 'created_date', None)
    finished_date = getattr(run, 'finished_date', None)

    # Duration in minutes
    duration_min = None
    if created_date and finished_date:
        try:
            delta = finished_date - created_date
            duration_min = round(delta.total_seconds() / 60, 2)
        except Exception:
            pass

    # Triggered by: extract source type from resources
    trigger_type = ''
    try:
        resources = getattr(run, 'resources', None)
        if resources:
            repos = getattr(resources, 'repositories', None) or {}
            if repos:
                self_repo = repos.get('self', repos.get('Self'))
                if self_repo:
                    trigger_type = getattr(getattr(self_repo, 'trigger', None), 'type', '') or ''
    except Exception:
        pass

    # Branch from pipeline run
    branch = ''
    try:
        variables = getattr(run, 'variables', None) or {}
        branch = (variables.get('Build.SourceBranch', {}) or {}).get('value', '')
    except Exception:
        pass

    return {
        'pipeline_id':   pipeline_id,
        'pipeline_name': pipeline_name,
        'run_id':        getattr(run, 'id', ''),
        'run_name':      getattr(run, 'name', '') or '',
        'state':         getattr(run, 'state', '') or '',
        'result':        getattr(run, 'result', '') or '',
        'created_date':  _isoformat(created_date),
        'finished_date': _isoformat(finished_date),
        'duration_min':  duration_min,
        'trigger_type':  trigger_type,
        'branch':        branch,
    }


def fetch_pipeline_runs(pipelines_client, project):
    """
    Fetch all pipeline definitions and their runs for the project.

    Args:
        pipelines_client: SDK PipelinesClient
        project:          project name

    Returns:
        list of dicts:
            {
                'pipeline': { id, name, folder, ... },
                'runs':     [ { run fields }, ... ]
            }
    """
    results = []
    try:
        pipelines = pipelines_client.list_pipelines(project)
    except Exception as e:
        logger.error(f"_______  - ❌ list_pipelines failed: {e}")
        return results

    logger.info(f"_______  - Found {len(pipelines)} pipeline definitions.")

    for pipeline in pipelines:
        pipeline_dict = _serialize_pipeline(pipeline)
        runs = []
        try:
            top = MAX_PIPELINE_RUNS if MAX_PIPELINE_RUNS > 0 else None
            raw_runs = pipelines_client.list_runs(project=project, pipeline_id=pipeline.id, top=top)
            runs = [_serialize_run(r, pipeline.id, pipeline.name) for r in (raw_runs or [])]
            logger.info(f"  → {pipeline.name}: {len(runs)} runs")
        except Exception as e:
            logger.warning(f"  → {pipeline.name}: run fetch failed — {e}")

        results.append({'pipeline': pipeline_dict, 'runs': runs})

    total_runs = sum(len(p['runs']) for p in results)
    logger.info(f"_______  - ✅ Total pipeline runs fetched: {total_runs} across {len(results)} pipelines.")
    return results
