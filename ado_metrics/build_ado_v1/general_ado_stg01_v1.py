# general_ado_stg01_v1.py
#
# Stage 1: Authenticate → fetch work items via WIQL → export raw JSON files.
# Run this first, then run general_ado_stg02_v1.py.

from config import (
    logger, ACCOUNT_NAME, ADO_ORG_URL, ADO_PROJECT, ADO_TEAM,
    WIQL_QUERY, RAW_ADO_JSON_FILE_NAME, USER_MAP_JSON_FILE_NAME,
    RAW_REPOS_JSON_FILE_NAME, RAW_PRS_JSON_FILE_NAME, RAW_PIPELINES_JSON_FILE_NAME,
)
from auth import get_connection, test_ado_access, test_dns_resolution, test_ping
from fetch_workitem import fetch_work_items
from fetch_repos import fetch_repositories, fetch_commits
from fetch_pullrequests import fetch_pull_requests
from fetch_pipelines import fetch_pipeline_runs
from user_lookup import build_user_map_from_work_items
from export_and_load_files import export_to_json

from azure.devops.exceptions import AzureDevOpsServiceError


def main():
    logger.info("________ - 🚀 Starting Azure DevOps Stage 1...")
    logger.info(f"________ - ℹ️  Account: {ACCOUNT_NAME}")
    logger.info(f"________ - ℹ️  Org URL: {ADO_ORG_URL}")
    logger.info(f"________ - ℹ️  Project: {ADO_PROJECT}")
    logger.info(f"________ - ℹ️  WIQL: {WIQL_QUERY}")

    # Step 1: DNS + ping check
    if not test_dns_resolution():
        logger.error("Step 01: - ❌ DNS resolution failed. Check network / VPN / ADO_ORG_URL.")
        return

    hostname = ADO_ORG_URL.split("//")[-1].split("/")[0]
    if not test_ping(hostname):
        logger.warning("Step 01: - ⚠️  Ping failed (ICMP may be blocked). Continuing anyway.")

    # Step 2: Authenticate + test access
    try:
        connection = get_connection()
        if not test_ado_access(connection):
            logger.error("Step 02: - ❌ Access test failed. Check ADO_PAT and ADO_ORG_URL.")
            return
        logger.info("Step 02: - ✅ Authentication and access test passed.")
    except Exception as e:
        logger.error(f"Step 02: - ❌ Connection failed: {e}")
        return

    # Step 3: Get SDK clients
    try:
        wit_client       = connection.clients.get_work_item_tracking_client()
        work_client      = connection.clients.get_work_client()
        git_client       = connection.clients.get_git_client()
        pipelines_client = connection.clients.get_pipelines_client()
    except Exception as e:
        logger.error(f"Step 03: - ❌ Could not get SDK clients: {e}")
        return

    # Step 4: Fetch work items + revisions + iterations
    try:
        work_items, iterations = fetch_work_items(
            wit_client, work_client,
            wiql_query=WIQL_QUERY,
            project=ADO_PROJECT,
            team=ADO_TEAM,
            fetch_revisions_flag=True,
        )
        logger.info(
            f"Step 04: - ✅ Fetched {len(work_items)} work items and {len(iterations)} iterations."
        )
    except AzureDevOpsServiceError as e:
        logger.error(f"Step 04: - ❌ ADO service error during fetch: {e}")
        return
    except Exception as e:
        logger.error(f"Step 04: - ❌ Work item fetch failed: {e}")
        return

    if not work_items:
        logger.warning("Step 04: - ⚠️  No work items returned. Check your WIQL query.")
        return

    # Step 5: Export raw work items to JSON
    # Wrap in a dict so iterations travel with the work items in the same file
    raw_export = {
        'work_items': work_items,
        'iterations': iterations,
    }
    export_to_json(raw_export, RAW_ADO_JSON_FILE_NAME, "05")

    # Step 6: Build user map
    try:
        user_map = build_user_map_from_work_items(work_items)
        logger.info("Step 06: - ✅ User map built.")
    except Exception as e:
        logger.error(f"Step 06: - ❌ User map build failed: {e}")
        return

    # Step 7: Export user map to JSON
    export_to_json(user_map, USER_MAP_JSON_FILE_NAME, "07")

    # Step 8: Fetch repositories + date-filtered commits
    try:
        repos = fetch_repositories(git_client, ADO_PROJECT)
        commits = fetch_commits(git_client, ADO_PROJECT, repos)
        export_to_json({'repos': repos, 'commits': commits}, RAW_REPOS_JSON_FILE_NAME, "08")
    except Exception as e:
        logger.error(f"Step 08: - ❌ Repos/commits fetch failed: {e}")

    # Step 9: Fetch pull requests (all repos)
    try:
        prs = fetch_pull_requests(git_client, ADO_PROJECT, repos)
        export_to_json(prs, RAW_PRS_JSON_FILE_NAME, "09")
    except Exception as e:
        logger.error(f"Step 09: - ❌ Pull requests fetch failed: {e}")

    # Step 10: Fetch pipeline definitions + runs
    try:
        pipelines = fetch_pipeline_runs(pipelines_client, ADO_PROJECT)
        export_to_json(pipelines, RAW_PIPELINES_JSON_FILE_NAME, "10")
    except Exception as e:
        logger.error(f"Step 10: - ❌ Pipeline runs fetch failed: {e}")

    logger.info("________ - ✅ Stage 1 complete. You can now run general_ado_stg02_v1.py.")


if __name__ == "__main__":
    main()
