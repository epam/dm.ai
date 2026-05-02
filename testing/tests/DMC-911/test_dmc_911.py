from __future__ import annotations

import sys
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from testing.components.services.dmtools_cli_service import DmtoolsCliService  # noqa: E402


def test_dmc_911_codegenerator_shim_has_no_external_side_effects(
    dmtools_cli_service: DmtoolsCliService,
) -> None:
    execution = dmtools_cli_service.run_job("CodeGenerator")

    assert execution.returncode == 0, (
        "CodeGenerator CLI execution failed.\n"
        f"stdout:\n{execution.stdout}\n\nstderr:\n{execution.stderr}"
    )
    assert not execution.stderr.strip(), f"Expected no stderr output, got:\n{execution.stderr}"

    payload = dmtools_cli_service.parse_result(execution)

    assert payload == [
        {
            "key": "CodeGenerator",
            "result": DmtoolsCliService.COMPATIBILITY_RESPONSE,
        }
    ], f"Unexpected CLI payload:\n{execution.stdout}"

    outbound_network = dmtools_cli_service.outbound_network_lines(execution)
    assert not outbound_network, (
        "Expected the compatibility shim to avoid outbound Jira/AI traffic, "
        "but traced outbound network activity was detected:\n"
        + "\n".join(outbound_network)
    )
