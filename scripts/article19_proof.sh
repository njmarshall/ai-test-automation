#!/bin/bash
# Article 19 controlled proof for getPetById_notFound (commit 0c4f116).
# Runs the OLD test (before 0c4f116) and the NEW test against a local
# PetStore stub in two modes:
#   buggy   : a deleted pet is still returned with 200 (the defect)
#   correct : a deleted pet returns 404
# The stand-in answers the OLD test's fixed id (999999999) and the NEW test's
# deleted pet the same way: buggy mode returns 200 for any GET /pet/{id};
# correct mode returns 404 for any id it does not currently hold. The stand-in
# hands out ids from 9,000,000,001 upward, so it never collides with 999999999.
# The 200 is a seeded response that simulates the defect. It is not an
# observation of the public PetStore.
# Both revisions are pinned: OLD = 0c4f116^ (the parent of the fix), NEW = 0c4f116.
# This compares two test revisions against the same stand-in. Everything else
# (framework code, stand-in) is identical between the two revisions except the test.
# A run is only reported PASSED or FAILED when the Surefire report shows that
# PetTests#getPetById_notFound actually ran. Anything else is reported as NOT VERIFIED,
# so a dependency or setup error can never be mistaken for a test result.
# Surefire reports are copied to the results folder for each run.
# Usage: run from the repo root:  bash scripts/article19_proof.sh
set -u
REPO="$PWD"
[ -f "$REPO/mvnw" ] && [ -d "$REPO/api" ] || { echo "Run this from the ai-test-automation (Java) repo folder."; exit 1; }
WORK="$(mktemp -d)"
cat > "$WORK/stub.py" <<'PY'
"""Minimal PetStore stub. MODE=buggy: GET /pet/{id} always returns 200.
MODE=correct: GET returns 404 for ids that were never created or were deleted."""
import json, os, sys, itertools
from http.server import BaseHTTPRequestHandler, HTTPServer
MODE = sys.argv[1]; PORT = int(sys.argv[2])
pets, ids = {}, itertools.count(9_000_000_001)
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def send(self, code, body):
        b = json.dumps(body).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0)); data = json.loads(self.rfile.read(n) or b"{}")
        data["id"] = next(ids); pets[data["id"]] = data; self.send(200, data)
    def do_GET(self):
        pid = int(self.path.rstrip("/").split("/")[-1])
        if pid in pets or MODE == "buggy":
            self.send(200, pets.get(pid, {"id": pid, "name": "ghost", "status": "available"}))
        else:
            self.send(404, {"code": 1, "type": "error", "message": "Pet not found"})
    def do_DELETE(self):
        pid = int(self.path.rstrip("/").split("/")[-1])
        if pets.pop(pid, None) is None: self.send(404, {"message": "Pet not found"})
        else: self.send(200, {"code": 200, "message": str(pid)})
HTTPServer(("127.0.0.1", PORT), H).serve_forever()
PY
git worktree add -f "$WORK/old" 0c4f116^ >/dev/null 2>&1 || { echo "Could not check out the old version"; exit 1; }
git worktree add -f "$WORK/new" 0c4f116  >/dev/null 2>&1 || { echo "Could not check out the new version"; exit 1; }
echo "OLD revision: $(git rev-parse --short 0c4f116^)   NEW revision: $(git rev-parse --short 0c4f116)"
mkdir -p "$WORK/reports"
cat > "$WORK/check.py" <<'PY'
import sys, glob, xml.etree.ElementTree as ET
# usage: check.py <code folder>   prints: PASSED | FAILED<TAB>message | NOT_VERIFIED
hits = []
for f in glob.glob(sys.argv[1] + "/**/surefire-reports/TEST-*.xml", recursive=True):
    try:
        for tc in ET.parse(f).getroot().iter("testcase"):
            if tc.get("name") == "getPetById_notFound" and (tc.get("classname") or "").endswith("PetTests"):
                hits.append(tc)
    except ET.ParseError:
        pass
if len(hits) != 1:
    print("NOT_VERIFIED"); sys.exit(0)
tc = hits[0]
bad = tc.find("failure") if tc.find("failure") is not None else tc.find("error")
if tc.find("skipped") is not None:
    print("NOT_VERIFIED")
elif bad is not None:
    print("FAILED\t" + " ".join((bad.get("message") or "").split())[:200])
else:
    print("PASSED")
PY
run() {  # $1 = code folder, $2 = stub mode, $3 = label, $4 = log name
  rm -rf "$1/api/target/surefire-reports"
  python3 "$WORK/stub.py" "$2" 8099 & local pid=$!
  sleep 1
  ( cd "$1" && BASE_URL=http://127.0.0.1:8099 ./mvnw -q -B -pl api -am test \
      -Dtest='PetTests#getPetById_notFound' -Dsurefire.failIfNoSpecifiedTests=false ) > "$WORK/$4.log" 2>&1
  kill $pid 2>/dev/null; wait $pid 2>/dev/null
  mkdir -p "$WORK/reports/$4"; cp -r "$1/api/target/surefire-reports/." "$WORK/reports/$4/" 2>/dev/null
  local res; res="$(python3 "$WORK/check.py" "$1")"
  case "$res" in
    PASSED)       echo "$3: PASSED (the test ran and passed)" ;;
    FAILED*)      echo "$3: FAILED"; echo "      reason: ${res#FAILED$'\t'}" ;;
    *)            echo "$3: NOT VERIFIED (the test did not run as expected; see $WORK/$4.log)" ;;
  esac
}
echo "Building and running. The first run downloads dependencies and may take a few minutes."
run "$WORK/old" buggy   "1. Old test, buggy server   (deleted pet returns 200)" old_buggy
run "$WORK/new" buggy   "2. New test, buggy server   (deleted pet returns 200)" new_buggy
run "$WORK/new" correct "3. New test, correct server (deleted pet returns 404)" new_correct
run "$WORK/old" correct "4. Old test, correct server (deleted pet returns 404)" old_correct
echo
git worktree remove --force "$WORK/old" >/dev/null 2>&1; git worktree remove --force "$WORK/new" >/dev/null 2>&1
echo
echo "Logs and Surefire reports kept in: $WORK (reports in $WORK/reports)"
