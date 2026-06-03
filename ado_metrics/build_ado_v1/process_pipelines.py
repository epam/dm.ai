# process_pipelines.py
#
# Flatten raw pipeline JSON (from Stage 1) into a pandas DataFrame.

import pandas as pd
from config import logger


def flatten_pipeline_runs(raw_pipelines):
    """
    Flatten list of {pipeline, runs} dicts into a single DataFrame of pipeline runs.

    Args:
        raw_pipelines: list of dicts from fetch_pipelines.fetch_pipeline_runs

    Returns:
        pd.DataFrame — one row per pipeline run
    """
    if not raw_pipelines:
        logger.warning("_______  - ⚠️  No pipeline data to flatten.")
        return pd.DataFrame()

    rows = []
    for entry in raw_pipelines:
        pipeline = entry.get('pipeline', {})
        for run in entry.get('runs', []):
            rows.append({
                'pipeline_id':   pipeline.get('id', ''),
                'pipeline_name': pipeline.get('name', ''),
                'pipeline_folder': (pipeline.get('folder', '') or '').strip('\\'),
                'run_id':        run.get('run_id', ''),
                'run_name':      run.get('run_name', ''),
                'state':         run.get('state', ''),
                'result':        run.get('result', ''),
                'created_date':  pd.to_datetime(run.get('created_date', ''), errors='coerce'),
                'finished_date': pd.to_datetime(run.get('finished_date', ''), errors='coerce'),
                'duration_min':  run.get('duration_min', None),
                'trigger_type':  run.get('trigger_type', ''),
                'branch':        (run.get('branch', '') or '').replace('refs/heads/', ''),
            })

    if not rows:
        logger.warning("_______  - ⚠️  No pipeline run rows after flatten.")
        return pd.DataFrame()

    df = pd.DataFrame(rows).sort_values(
        ['pipeline_name', 'created_date'], ascending=[True, False]
    ).reset_index(drop=True)

    logger.info(f"_______  - ✅ Pipeline runs DataFrame: {len(df)} rows across "
                f"{df['pipeline_name'].nunique()} pipelines.")
    return df
