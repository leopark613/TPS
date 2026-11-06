"""Actual HTTP + SQL Server tests; fixed local TPS endpoint, no production fallback.

No third-party Python packages required. Secrets stay in process environment only.
See README.md for invocation and exact cleanup boundaries.
"""
from __future__ import annotations

import argparse
import base64
import concurrent.futures
import copy
import datetime as dt
import getpass
import hashlib
import json
import os
import re
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid


def uid():
    return str(uuid.uuid4())


def document(title="TPS_IT 합성 보고서"):
    return {
        "format": "tps.document", "schemaVersion": "0.1.0-draft", "units": "um",
        "title": title, "locale": "ko-KR", "timeZone": "Asia/Seoul",
        "requiredFeatures": [{"id": "tps.text", "version": "1"}],
        "sections": [{"id": "section-main", "page": {"width": 210000, "height": 297000,
            "margins": {"top": 10000, "right": 10000, "bottom": 10000, "left": 10000}},
            "bands": [{"id": "band-detail", "kind": "detail", "height": 277000,
                "properties": {}, "elements": [{"id": "text-title", "type": "tps.text", "typeVersion": "1",
                    "frame": {"x": 10000, "y": 12000, "width": 80000, "height": 14000},
                    "properties": {"text": "저장·재열기 검증", "fontSizePt": "14", "color": "#17324d", "customPreserved": {"flag": True}},
                    "bindings": [], "children": []}], "childBands": []}]}],
        "datasets": [], "assets": [], "provenance": {"origin": "native", "elementOrigins": []},
        "extensions": {"test": {"preserve": "  한글 값  "}},
    }


def canonical(value):
    """Object key order follows Java UTF-16 String order, not Python code points."""
    if isinstance(value, dict):
        return {key: canonical(value[key]) for key in sorted(value, key=lambda key: key.encode("utf-16-be"))}
    if isinstance(value, list):
        return [canonical(item) for item in value]
    if isinstance(value, float) and value.is_integer():
        return int(value)
    return value


def body_hash(doc):
    return hashlib.sha256(json.dumps(canonical(doc), ensure_ascii=False, separators=(",", ":")).encode("utf-8")).hexdigest()


class Checks:
    def __init__(self):
        self.items = []

    def check(self, name, condition):
        self.items.append({"name": name, "passed": bool(condition)})
        if not condition:
            raise AssertionError(name)


class ObserverFailure(RuntimeError):
    def __init__(self, diagnostic):
        super().__init__("Local DB observer failed")
        self.diagnostic = diagnostic


def safe_observer_diagnostic(stderr):
    """Trust only our bounded diagnostic envelope, never arbitrary Java/SQL output."""
    for line in reversed(stderr.splitlines()):
        try:
            data = json.loads(line)
        except (ValueError, TypeError):
            continue
        if not isinstance(data, dict) or data.get("diagnosticKind") != "tps-observer-v1":
            continue
        stage, kind = data.get("stage", ""), data.get("type", "")
        if not isinstance(stage, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,100}", stage):
            continue
        if not isinstance(kind, str) or not re.fullmatch(r"[A-Za-z0-9_$]{1,100}", kind):
            continue
        safe = {"stage":stage, "type":kind, "sqlErrors":[]}
        errors = data.get("sqlErrors", [])
        if not isinstance(errors, list):
            continue
        for item in errors[:8]:
            if not isinstance(item, dict):
                continue
            t, state, code = item.get("type"), item.get("sqlState"), item.get("errorCode")
            if isinstance(t, str) and re.fullmatch(r"[A-Za-z0-9_$]{1,100}", t) and (state is None or isinstance(state, str) and re.fullmatch(r"[A-Za-z0-9]{5}", state)) and type(code) is int:
                safe["sqlErrors"].append({"type":t, "sqlState":state, "errorCode":code})
        return safe
    return {"stage":"observer-process", "type":"UnclassifiedFailure", "sqlErrors":[]}


class Api:
    def __init__(self, port, password):
        self.base = f"http://127.0.0.1:{port}/api/tps"
        self.auth = "Basic " + base64.b64encode(("tps-developer:" + password).encode()).decode()

    def request(self, method, path, data=None, *, headers=None, raw=None, auth=True):
        if not path.startswith("/") or "://" in path:
            raise ValueError("Only fixed local relative API paths allowed")
        h = {"Accept": "application/json"}
        if auth:
            h["Authorization"] = self.auth
        if method not in ("GET", "HEAD"):
            h.update({"Content-Type": "application/json", "X-TPS-Client": "designer"})
        h.update(headers or {})
        payload = raw if raw is not None else (json.dumps(data, ensure_ascii=False).encode() if data is not None else None)
        req = urllib.request.Request(self.base + path, data=payload, headers=h, method=method)
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open(req, timeout=25) as res:
                content, code, rh = res.read(), res.status, dict(res.headers)
        except urllib.error.HTTPError as e:
            content, code, rh = e.read(), e.code, dict(e.headers)
        try:
            result = json.loads(content)
        except (ValueError, UnicodeError):
            result = None
        return code, result, rh


class OwnedServer:
    def __init__(self, java, jar, port, env):
        self.java, self.jar, self.port, self.env = java, jar, port, env
        self.process = None
        self.pids = []

    def start(self, api):
        with socket.socket() as sock:
            if sock.connect_ex(("127.0.0.1", self.port)) == 0:
                raise RuntimeError("Selected test port is already in use; no existing process was changed")
        self.process = subprocess.Popen([self.java, "-jar", str(self.jar), f"--server.port={self.port}"],
            env=self.env, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        self.pids.append(self.process.pid)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError("Owned backend exited during startup")
            try:
                if api.request("GET", "/session")[0] == 200:
                    return
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(0.5)
        raise TimeoutError("Backend readiness timed out")

    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                self.process.kill()  # Only this Popen-owned PID, never process-name cleanup.
                self.process.wait(timeout=10)


def parallel(callables):
    barrier = threading.Barrier(len(callables))
    def start(fn):
        barrier.wait(timeout=10)
        return fn()
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(callables)) as pool:
        return list(pool.map(start, callables))


def execute(args):
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env["TPS_DB_USER"] = env.get("TPS_DB_USER") or input("Local TPS SQL user: ").strip()
    env["TPS_DB_PASSWORD"] = env.get("TPS_DB_PASSWORD") or getpass.getpass("Local TPS SQL password (not saved): ")
    # The CLI itself never broadens certificate-validation exceptions implicitly.
    if env.get("TPS_DB_TRUST_SERVER_CERTIFICATE", "false").lower() != "true":
        raise ValueError("For the authorized local test, explicitly set TPS_DB_TRUST_SERVER_CERTIFICATE=true")
    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d%H%M%S") + "_" + secrets.token_hex(3)
    site = "TPS_IT_" + run_id
    password = secrets.token_urlsafe(32)
    env.update({"TPS_DB_HOST": "10.121.13.159", "TPS_DB_PORT": "1433", "TPS_DB_NAME": "TPS",
        "TPS_SITE_ID": site, "TPS_DEV_PASSWORD": password, "TPS_ENABLED_MODULES": "RPS,LPS",
        "SPRING_PROFILES_ACTIVE": "local"})
    java, jdbc, jar = str(Path(args.java).resolve()), str(Path(args.jdbc).resolve()), Path(args.jar).resolve()
    helper = Path(__file__).with_name("LocalDbObserver.java").resolve()
    api, checks = Api(args.port, password), Checks()
    server = OwnedServer(args.runtime_java or java, jar, args.port, env)
    templates, drafts = set(), set()
    plan_lock = threading.RLock()
    report = {"runId": run_id, "siteId": site, "startedUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "jdbcSha256": hashlib.sha256(Path(jdbc).read_bytes()).hexdigest(), "checks": checks.items,
        "database": "fixed-local-TPS", "certificateException": "local-development-test-only", "encrypted": True}
    plan = output / "owned-cleanup-plan.txt"

    def save_plan():
        with plan_lock:
            plan.write_text(site + "\n" + "".join("T="+i+"\n" for i in sorted(templates)) + "".join("C="+i+"\n" for i in sorted(drafts)), encoding="utf-8")

    def observer(action="snapshot"):
        arg = site if action == "snapshot" else str(plan)
        p = subprocess.run([java, "-cp", jdbc, str(helper), action, arg], env=env,
            stdin=subprocess.DEVNULL, capture_output=True, text=True, encoding="utf-8", timeout=60,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if p.returncode:
            raise ObserverFailure(safe_observer_diagnostic(p.stderr))
        return json.loads(p.stdout)

    def post(doc, owner="RPS", draft=None, key=None):
        draft, key = draft or uid(), key or uid()
        with plan_lock:
            drafts.add(draft); save_plan()
        result = api.request("POST", "/templates", {"clientDraftId": draft, "ownerModule": owner, "document": doc}, headers={"Idempotency-Key": key})
        if result[0] == 201 and isinstance(result[1], dict) and result[1].get("templateId"):
            with plan_lock:
                templates.add(result[1]["templateId"].lower()); save_plan()
        return result, draft, key

    def put(view, doc, key=None, etag=None):
        return api.request("PUT", "/templates/" + view["templateId"] + "/head", {"document": doc},
            headers={"Idempotency-Key": key or uid(), "If-Match": etag or view["headEtag"]})

    before = None
    try:
        before = observer()
        report["before"] = before
        checks.check("dedicated_test_site_initially_empty", all(v["rows"] == 0 for v in before["site"].values()))
        server.start(api)
        checks.check("unauthenticated_session_is_401", api.request("GET", "/session", auth=False)[0] == 401)
        status, session, _ = api.request("GET", "/session")
        checks.check("authenticated_server_owned_site_and_actor", status == 200 and session["siteId"] == site and session["actorId"] == "tps-developer" and set(session["enabledModules"]) == {"RPS", "LPS"})
        doc1 = document()
        (status, first, _), first_draft, first_key = post(doc1)
        checks.check("create_revision_one", status == 201 and first["sequence"] == "1" and first["headToken"] == "1" and first["editable"] is True)
        checks.check("create_document_hash_and_no_activation", first["document"] == doc1 and first["bodyHash"] == body_hash(doc1) and first["activeRevisionId"] is None)
        status, opened, headers = api.request("GET", f'/templates/{first["templateId"]}/head')
        checks.check("reopen_exact_document_and_etag", status == 200 and opened == first and {k.lower():v for k,v in headers.items()}.get("etag") == first["headEtag"])
        (status, replay, _), _, _ = post(doc1, draft=first_draft, key=first_key)
        checks.check("create_idempotent_exact_result", status == 201 and replay == first)
        changed = copy.deepcopy(doc1); changed["title"] += " 수정"; changed["sections"][0]["bands"][0]["elements"][0]["properties"]["text"] = "저장된 두 번째 내용"
        key2 = uid()
        status, second, _ = put(first, changed, key2)
        checks.check("save_creates_second_immutable_revision", status == 200 and second["sequence"] == "2" and second["headToken"] == "2" and second["revisionId"] != first["revisionId"] and second["document"] == changed and second["activeRevisionId"] is None)
        status, replay, _ = put(first, changed, key2)
        checks.check("committed_save_retry_precedes_stale_etag_check", status == 200 and replay == second)
        checks.check("request_key_changed_payload_is_409", put(first, doc1, key2)[0] == 409)
        checks.check("stale_write_is_412", put(first, doc1)[0] == 412)
        status, hist, _ = api.request("GET", f'/templates/{first["templateId"]}/revisions/{first["revisionId"]}')
        checks.check("historical_revision_readonly_and_original_content", status == 200 and hist["document"] == doc1 and hist["sequence"] == "1" and hist["editable"] is False and hist["headEtag"] is None)
        status, histories, _ = api.request("GET", f'/templates/{first["templateId"]}/revisions')
        checks.check("revision_list_two_in_descending_order", status == 200 and [r["sequence"] for r in histories["items"]] == ["2", "1"])
        status, resolved, _ = api.request("POST", "/save-requests/resolve", {"operation":"SAVE", "scopeId":first["templateId"], "requestKey":key2})
        checks.check("resolve_exact_committed_result", status == 200 and resolved["status"] == "completed" and resolved["result"] == second)
        status, unresolved, _ = api.request("POST", "/save-requests/resolve", {"operation":"SAVE", "scopeId":first["templateId"], "requestKey":uid()})
        checks.check("unknown_request_resolve", status == 200 and unresolved["status"] == "unknown")

        # Two concurrent identical creates must point at one immutable revision.
        shared_draft, shared_key = uid(), uid(); drafts.add(shared_draft); save_plan()
        shared_doc = document("TPS_IT 동시 신규")
        results = parallel([lambda: post(shared_doc, draft=shared_draft, key=shared_key)[0] for _ in range(2)])
        checks.check("concurrent_identical_create_one_result", all(r[0] == 201 for r in results) and results[0][1] == results[1][1])
        concurrent = results[0][1]
        changed2 = copy.deepcopy(shared_doc); changed2["title"] += " 저장"
        same_save_key = uid()
        results = parallel([lambda: put(concurrent, changed2, same_save_key) for _ in range(2)])
        checks.check("concurrent_identical_save_one_revision", all(r[0] == 200 for r in results) and results[0][1] == results[1][1] and results[0][1]["sequence"] == "2")
        base = results[0][1]
        variant_a, variant_b = copy.deepcopy(changed2), copy.deepcopy(changed2)
        variant_a["title"] += " A"; variant_b["title"] += " B"
        results = parallel([lambda: put(base, variant_a), lambda: put(base, variant_b)])
        checks.check("concurrent_distinct_save_exactly_one_cas_winner", sorted(r[0] for r in results) == [200, 412])
        status, h, _ = api.request("GET", f'/templates/{concurrent["templateId"]}/revisions')
        checks.check("no_orphan_or_duplicate_concurrent_revisions", status == 200 and len(h["items"]) == 3)

        (status, lps, _), _, _ = post(document("TPS_IT LPS 독립 양식"), owner="LPS")
        checks.check("lps_native_template_supported", status == 201 and lps["ownerModule"] == "LPS")
        status, listing, _ = api.request("GET", "/templates?module=RPS&page=0&size=50")
        checks.check("module_catalog_isolated_rps", status == 200 and listing["total"] == 2 and all(v["ownerModule"] == "RPS" for v in listing["items"]))
        status, listing, _ = api.request("GET", "/templates?module=LPS&page=0&size=50")
        checks.check("module_catalog_isolated_lps", status == 200 and listing["total"] == 1 and listing["items"][0]["templateId"] == lps["templateId"])

        unknown = document("TPS_IT 미지원 요소 보존")
        unknown["requiredFeatures"] = [{"id":"vendor.future", "version":"42"}]
        unknown["sections"][0]["bands"][0]["elements"][0]["type"] = "vendor.future"
        unknown["sections"][0]["bands"][0]["elements"][0]["typeVersion"] = "42"
        (status, future, _), _, _ = post(unknown)
        checks.check("unknown_element_preserved_readonly_with_diagnostic", status == 201 and future["document"] == unknown and future["editable"] is False and bool(future["diagnostics"]))

        state_before_invalid = observer()["site"]
        invalid = document(); invalid["title"] = ""
        checks.check("empty_title_rejected_422", post(invalid)[0][0] == 422)
        duplicate = document(); duplicate["sections"][0]["bands"][0]["elements"].append(copy.deepcopy(duplicate["sections"][0]["bands"][0]["elements"][0]))
        checks.check("duplicate_element_id_rejected_422", post(duplicate)[0][0] == 422)
        bad_ref = document(); bad_ref["sections"][0]["bands"][0]["datasetId"] = "not-found"
        checks.check("dangling_dataset_rejected_422", post(bad_ref)[0][0] == 422)
        assets = document(); assets["assets"] = [{"id":"fake","sha256":"1"*64,"mediaType":"image/png","byteLength":1,"objectRef":"fixture:missing"}]
        checks.check("unsupported_asset_not_silently_saved", post(assets)[0][0] == 422)
        raw_path = "/templates"
        checks.check("duplicate_json_keys_rejected_400", api.request("POST", raw_path, raw=b'{"clientDraftId":"x","clientDraftId":"y"}', headers={"Idempotency-Key":uid()})[0] == 400)
        checks.check("malformed_utf8_rejected_400", api.request("POST", raw_path, raw=b'{"document":"\xff"}', headers={"Idempotency-Key":uid()})[0] == 400)
        checks.check("oversized_request_rejected_413", api.request("POST", raw_path, raw=b'{"large":"'+b'x'*(1048576+1)+b'"}', headers={"Idempotency-Key":uid()})[0] == 413)
        checks.check("missing_if_match_rejected_428", api.request("PUT", f'/templates/{first["templateId"]}/head', {"document":changed}, headers={"Idempotency-Key":uid()})[0] == 428)
        checks.check("invalid_request_leaves_all_site_rows_unchanged", observer()["site"] == state_before_invalid)

        # Durable storage: kill only our process, start a fresh JVM, reopen exact head.
        server.stop(); server.start(api)
        status, restarted, _ = api.request("GET", f'/templates/{first["templateId"]}/head')
        checks.check("fresh_jvm_reopens_exact_saved_head", status == 200 and restarted == second)
        server.stop(); env["TPS_ENABLED_MODULES"] = "RPS"; server.start(api)
        status, single_module, _ = api.request("GET", "/session")
        checks.check("configured_single_module_session", status == 200 and single_module["enabledModules"] == ["RPS"])
        checks.check("disabled_module_catalog_denied", api.request("GET", "/templates?module=LPS")[0] == 403)
        checks.check("disabled_module_existing_template_denied", api.request("GET", f'/templates/{lps["templateId"]}/head')[0] == 403)
        checks.check("disabled_module_save_denied", put(lps, document("TPS_IT 거부되어야 할 변경"))[0] == 403)
        after = observer(); report["afterTests"] = after
        checks.check("all_templates_have_committed_heads_and_no_active_change", after["activeCount"] == 0 and after["uncommittedHeadCount"] == 0)
        checks.check("legacy_rps_rows_unchanged", before["rps"] == after["rps"])
        checks.check("legacy_rps_schema_and_identity_unchanged", before["rpsCatalogHash"] == after["rpsCatalogHash"])
        checks.check("other_sites_unchanged", before["otherSites"] == after["otherSites"])
        report["result"] = "PASS"
    except Exception as exc:
        # Avoid unexpected SQL / response / credentials in evidence.
        report["result"] = "FAIL"
        report["failureType"] = type(exc).__name__
        if isinstance(exc, ObserverFailure):
            report["observerDiagnostic"] = exc.diagnostic
        if isinstance(exc, AssertionError):
            report["failedCheck"] = str(exc)
    finally:
        server.stop()
        report["ownedProcessIds"] = server.pids
        save_plan()
        if before is not None:
            try:
                report["cleanup"] = observer("cleanup")
                final = observer(); report["afterCleanup"] = final
                checks.check("cleanup_restores_test_site_to_zero", all(v["rows"] == 0 for v in final["site"].values()))
                checks.check("cleanup_preserves_rps_and_other_sites", before["rps"] == final["rps"] and before["rpsCatalogHash"] == final["rpsCatalogHash"] and before["otherSites"] == final["otherSites"])
            except Exception as exc:
                report["result"] = "FAIL"
                report["cleanupFailureType"] = type(exc).__name__
                if isinstance(exc, ObserverFailure):
                    report["cleanupObserverDiagnostic"] = exc.diagnostic
        report["completedUtc"] = dt.datetime.now(dt.timezone.utc).isoformat()
        report["passedChecks"] = sum(item["passed"] for item in checks.items)
        report["totalChecks"] = len(checks.items)
        (output / "integration-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        env.pop("TPS_DB_PASSWORD", None); env.pop("TPS_DEV_PASSWORD", None)
    print(json.dumps({"result":report["result"], "passedChecks":report["passedChecks"], "totalChecks":report["totalChecks"], "siteId":site, "failedCheck":report.get("failedCheck"), "cleanup":report.get("cleanup", {}).get("cleaned", False)}))
    return 0 if report["result"] == "PASS" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, help="Packaged Spring Boot JAR")
    parser.add_argument("--java", required=True, help="Java 21 executable")
    parser.add_argument("--runtime-java", help="Optional packaged JRE for the application; observer still uses --java JDK")
    parser.add_argument("--jdbc", required=True, help="Microsoft SQL Server JDBC JAR")
    parser.add_argument("--output", required=True, help="Evidence directory, never Git business dumps")
    parser.add_argument("--port", type=int, default=18761)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error("Use an unprivileged local port")
    try:
        return execute(args)
    except Exception as exc:
        print("Integration setup failed: " + type(exc).__name__, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
