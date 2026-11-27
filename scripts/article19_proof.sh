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
run() {  # $1 = code folder, $2 = stub mode, $3 = label, $4 = log name
  python3 "$WORK/stub.py" "$2" 8099 & local pid=$!
  sleep 1
  ( cd "$1" && BASE_URL=http://127.0.0.1:8099 ./mvnw -q -B -pl api -am test \
      -Dtest='PetTests#getPetById_notFound' -Dsurefire.failIfNoSpecifiedTests=false ) > "$WORK/$4.log" 2>&1
  local rc=$?
  kill $pid 2>/dev/null; wait $pid 2>/dev/null
  if [ $rc -eq 0 ]; then echo "$3: PASSED"; else echo "$3: FAILED"; fi
}
echo "Building and running. The first run downloads dependencies and may take a few minutes."
run "$WORK/old" buggy   "1. Old test, buggy server   (deleted pet returns 200)" old_buggy
run "$REPO"     buggy   "2. New test, buggy server   (deleted pet returns 200)" new_buggy
run "$REPO"     correct "3. New test, correct server (deleted pet returns 404)" new_correct
run "$WORK/old" correct "4. Old test, correct server (deleted pet returns 404)" old_correct
echo
echo "Failure reason from run 2:"
grep -h -i -m3 "expected.*404\|status code" "$WORK/new_buggy.log" || echo "(see $WORK/new_buggy.log)"
git worktree remove --force "$WORK/old" >/dev/null 2>&1
echo
echo "Logs kept in: $WORK"
