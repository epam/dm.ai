# auth.py

import socket
import subprocess
from config import logger, ADO_ORG_URL, ADO_PAT, ADO_PROJECT

from azure.devops.connection import Connection
from msrest.authentication import BasicAuthentication


def get_connection():
    """
    Returns an authenticated Azure DevOps Connection object using PAT.
    """
    if not ADO_PAT:
        raise ValueError("ADO_PAT is missing from the .env file!")
    if not ADO_ORG_URL:
        raise ValueError("ADO_ORG_URL is missing from the .env file!")

    credentials = BasicAuthentication('', ADO_PAT)
    connection = Connection(base_url=ADO_ORG_URL, creds=credentials)
    logger.info("________ - ℹ️  Created Azure DevOps connection using PAT.")
    return connection


def test_ado_access(connection):
    """
    Tests ADO authentication by listing projects.
    Returns True if successful, False otherwise.
    """
    try:
        core_client = connection.clients.get_core_client()
        projects = core_client.get_projects()
        project_names = [p.name for p in projects]
        logger.info(f"________ - ✅ ADO authentication test succeeded. Found {len(project_names)} project(s).")
        if ADO_PROJECT and ADO_PROJECT not in project_names:
            logger.warning(
                f"________ - ⚠️  Project '{ADO_PROJECT}' not found in the organization. "
                f"Available projects: {project_names}"
            )
        return True
    except Exception as e:
        logger.error(
            f"_______ - ❌ ADO authentication test failed: {e}. "
            f"Check that ADO_ORG_URL and ADO_PAT are correct and the PAT has 'Work Items (Read)' scope."
        )
        return False


def test_dns_resolution():
    try:
        hostname = ADO_ORG_URL.split("//")[-1].split("/")[0]
        ip = socket.gethostbyname(hostname)
        logger.info(f"________ - ✅ DNS resolution succeeded: {hostname} -> {ip}")
        return True
    except Exception as e:
        hostname = ADO_ORG_URL.split("//")[-1].split("/")[0] if ADO_ORG_URL else "unknown"
        logger.error(f"________ - ❌ DNS resolution failed for {hostname}: {e}")
        return False


def test_ping(hostname):
    try:
        result = subprocess.run(['ping', '-c', '2', hostname], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if result.returncode == 0:
            logger.info(f"________ - ✅ Ping to {hostname} succeeded")
            return True
        else:
            logger.error(f"________ - ⚠️ Non-blocking failure - Ping to {hostname} failed:\n{result.stderr}")
            return False
    except Exception as e:
        logger.error(f"________ - ⚠️ Non-blocking failure - Ping test failed: {e}")
        return False
