from __future__ import annotations

import json
import threading
import time
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import cast
from urllib.parse import parse_qs, urlparse


@dataclass(frozen=True)
class RecordedRequest:
    method: str
    path: str
    timestamp: float


class _HarnessHttpServer(ThreadingHTTPServer):
    def __init__(
        self,
        server_address: tuple[str, int],
        request_handler_class: type[BaseHTTPRequestHandler],
        harness: "ReportGeneratorRateLimitHarness",
    ) -> None:
        super().__init__(server_address, request_handler_class)
        self.harness = harness


class _HarnessRequestHandler(BaseHTTPRequestHandler):
    server: _HarnessHttpServer

    def do_GET(self) -> None:  # noqa: N802
        harness = self.server.harness
        harness.record_request("GET", self.path)

        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        expected_path = (
            f"/repos/{harness.workspace}/{harness.repository}/commits"
        )
        page = query.get("page", ["1"])[0]

        if parsed.path != expected_path:
            self._write_json(HTTPStatus.NOT_FOUND, {"message": "Not Found"})
            return

        if page != "1":
            self._write_json(HTTPStatus.OK, [])
            return

        if harness.consume_rate_limit():
            reset_epoch_seconds = int(time.time()) + harness.reset_after_seconds
            self.send_response(HTTPStatus.TOO_MANY_REQUESTS)
            self.send_header("Content-Type", "application/json")
            self.send_header("Retry-After", str(harness.reset_after_seconds))
            self.send_header("X-RateLimit-Reset", str(reset_epoch_seconds))
            self.end_headers()
            self.wfile.write(
                json.dumps({"message": "API rate limit exceeded"}).encode("utf-8")
            )
            return

        self._write_json(HTTPStatus.OK, [harness.commit_payload])

    def log_message(self, format: str, *args: object) -> None:  # noqa: A003
        del format, args

    def _write_json(self, status: HTTPStatus, payload: object) -> None:
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


class ReportGeneratorRateLimitHarness:
    def __init__(
        self,
        *,
        workspace: str,
        repository: str,
        branch: str,
        reset_after_seconds: int = 2,
    ) -> None:
        self.workspace = workspace
        self.repository = repository
        self.branch = branch
        self.reset_after_seconds = reset_after_seconds
        self._rate_limit_remaining = 1
        self._requests: list[RecordedRequest] = []
        self._server = _HarnessHttpServer(
            ("127.0.0.1", 0),
            _HarnessRequestHandler,
            self,
        )
        self._thread = threading.Thread(
            target=self._server.serve_forever,
            name="report-generator-rate-limit-harness",
            daemon=True,
        )

        self.commit_payload = {
            "node_id": "commit-node-1",
            "sha": "abc123",
            "html_url": "https://example.test/commit/abc123",
            "author": {"id": "1", "login": "rate-limit-tester"},
            "commit": {
                "message": "Rate limit retry commit",
                "committer": {"date": "2026-05-15T12:00:00Z"},
            },
        }

    def __enter__(self) -> "ReportGeneratorRateLimitHarness":
        self.start()
        return self

    def __exit__(self, exc_type, exc, tb) -> None:  # type: ignore[override]
        del exc_type, exc, tb
        self.stop()

    @property
    def base_url(self) -> str:
        host, port = cast(tuple[str, int], self._server.server_address)
        return f"http://{host}:{port}"

    @property
    def requests(self) -> tuple[RecordedRequest, ...]:
        return tuple(self._requests)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._server.shutdown()
        self._server.server_close()
        self._thread.join(timeout=5)

    def record_request(self, method: str, path: str) -> None:
        self._requests.append(
            RecordedRequest(method=method, path=path, timestamp=time.monotonic())
        )

    def consume_rate_limit(self) -> bool:
        if self._rate_limit_remaining <= 0:
            return False
        self._rate_limit_remaining -= 1
        return True
