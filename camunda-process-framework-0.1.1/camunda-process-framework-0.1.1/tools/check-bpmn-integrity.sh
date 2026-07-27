#!/usr/bin/env bash
# check-bpmn-integrity.sh
# Validates BPMN files in the repository against several integrity rules.
# Compatible with Git Bash, WSL, and Linux CI.

set -euo pipefail

# ---------------------------------------------------------------------------
# Help
# ---------------------------------------------------------------------------
usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Runs integrity checks against every *.bpmn file found under the current
working directory.

Checks performed:
  1. XML well-formedness via xmllint (WARN if xmllint absent).
  2. Cross-reference between <zeebe:taskDefinition type="X"> and
     @JobWorker(type = "X") annotations in Java sources.
  3. BPMN DI completeness — every flow node must have a BPMNShape and
     every sequenceFlow must have a BPMNEdge.
  4. Candidate groups from <zeebe:assignmentDefinition> are printed for
     manual IdP verification.

Exit codes:
  0  All checks passed (warnings do not affect the exit code).
  1  One or more FAIL conditions detected.

Options:
  -h, --help   Show this help message and exit.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

FAIL_COUNT=0
WARN_COUNT=0

fail() { echo "FAIL: $*"; (( FAIL_COUNT++ )) || true; }
warn() { echo "WARN: $*"; (( WARN_COUNT++ )) || true; }
info() { echo "INFO: $*"; }
pass() { echo "PASS: $*"; }

# ---------------------------------------------------------------------------
# Collect BPMN files
# ---------------------------------------------------------------------------
mapfile -t BPMN_FILES < <(find "${REPO_ROOT}" \
  -not -path "${REPO_ROOT}/.git/*" \
  -name "*.bpmn" 2>/dev/null | sort)

if [[ ${#BPMN_FILES[@]} -eq 0 ]]; then
  echo "No BPMN files found; nothing to validate."
  exit 0
fi

info "Found ${#BPMN_FILES[@]} BPMN file(s)."
echo ""

# ---------------------------------------------------------------------------
# CHECK 1 — XML well-formedness
# ---------------------------------------------------------------------------
echo "=== Check 1: XML well-formedness ==="
if ! command -v xmllint >/dev/null 2>&1; then
  warn "xmllint not installed; skipping XML validation."
else
  for bpmn in "${BPMN_FILES[@]}"; do
    rel="${bpmn#${REPO_ROOT}/}"
    if xmllint --noout "${bpmn}" 2>/dev/null; then
      pass "XML valid: ${rel}"
    else
      fail "XML invalid: ${rel}"
    fi
  done
fi
echo ""

# ---------------------------------------------------------------------------
# CHECK 2 — Zeebe task definitions vs @JobWorker annotations
# ---------------------------------------------------------------------------
echo "=== Check 2: Task definition / @JobWorker cross-reference ==="

# Collect all Java source files under src/main/java
mapfile -t JAVA_FILES < <(find "${REPO_ROOT}" \
  -not -path "${REPO_ROOT}/.git/*" \
  -path "*/src/main/java/*.java" 2>/dev/null | sort)

# Extract task types from BPMN files
declare -A BPMN_TASK_SOURCES  # type -> bpmn file list (space-separated)
for bpmn in "${BPMN_FILES[@]}"; do
  rel="${bpmn#${REPO_ROOT}/}"
  while IFS= read -r type_val; do
    existing="${BPMN_TASK_SOURCES[$type_val]:-}"
    if [[ -z "$existing" ]]; then
      BPMN_TASK_SOURCES[$type_val]="${rel}"
    else
      BPMN_TASK_SOURCES[$type_val]="${existing}, ${rel}"
    fi
  done < <(grep -oP '(?<=<zeebe:taskDefinition\s)[^>]*type="[^"]*"' "${bpmn}" 2>/dev/null \
           | grep -oP '(?<=type=")[^"]*' || true)
done

# Extract @JobWorker types from Java sources
declare -A WORKER_SOURCES  # type -> java file list (space-separated)
for jf in "${JAVA_FILES[@]}"; do
  rel="${jf#${REPO_ROOT}/}"
  while IFS= read -r type_val; do
    existing="${WORKER_SOURCES[$type_val]:-}"
    if [[ -z "$existing" ]]; then
      WORKER_SOURCES[$type_val]="${rel}"
    else
      WORKER_SOURCES[$type_val]="${existing}, ${rel}"
    fi
  done < <(grep -oP '(?<=@JobWorker\()[^)]*type\s*=\s*")[^"]*' "${jf}" 2>/dev/null \
           | grep -oP '[^"]+$' || true)
done

if [[ ${#BPMN_TASK_SOURCES[@]} -eq 0 && ${#WORKER_SOURCES[@]} -eq 0 ]]; then
  info "No zeebe:taskDefinition entries or @JobWorker annotations found."
else
  # Orphan service tasks — type in BPMN but no matching worker
  for type_val in "${!BPMN_TASK_SOURCES[@]}"; do
    if [[ -z "${WORKER_SOURCES[$type_val]:-}" ]]; then
      fail "Orphan service task type '${type_val}' defined in BPMN (${BPMN_TASK_SOURCES[$type_val]}) but no @JobWorker(type=\"${type_val}\") found in Java sources."
    else
      pass "Task type '${type_val}' matched to a @JobWorker."
    fi
  done

  # Orphan workers — @JobWorker with no matching BPMN task definition
  for type_val in "${!WORKER_SOURCES[@]}"; do
    if [[ -z "${BPMN_TASK_SOURCES[$type_val]:-}" ]]; then
      fail "Orphan @JobWorker(type=\"${type_val}\") in ${WORKER_SOURCES[$type_val]} has no corresponding <zeebe:taskDefinition type=\"${type_val}\"> in any BPMN file."
    fi
  done
fi
echo ""

# ---------------------------------------------------------------------------
# CHECK 3 — BPMN DI completeness
# ---------------------------------------------------------------------------
echo "=== Check 3: BPMN DI completeness ==="

for bpmn in "${BPMN_FILES[@]}"; do
  rel="${bpmn#${REPO_ROOT}/}"
  content="$(<"${bpmn}")"

  # Collect flow node IDs (serviceTask, userTask, gateway variants, events)
  mapfile -t NODE_IDS < <(grep -oP '(?<=<bpmn:(serviceTask|userTask|startEvent|endEvent|intermediateCatchEvent|intermediateThrowEvent|boundaryEvent|exclusiveGateway|parallelGateway|inclusiveGateway|eventBasedGateway|complexGateway)\s)[^>]*\bid="[^"]*"' \
    "${bpmn}" 2>/dev/null | grep -oP '(?<=\bid=")[^"]*' || true)

  # Collect BPMNShape bpmnElement values
  mapfile -t SHAPE_ELEMENTS < <(grep -oP '(?<=<bpmndi:BPMNShape\s)[^>]*bpmnElement="[^"]*"' \
    "${bpmn}" 2>/dev/null | grep -oP '(?<=bpmnElement=")[^"]*' || true)
  declare -A SHAPE_SET=()
  for s in "${SHAPE_ELEMENTS[@]}"; do SHAPE_SET[$s]=1; done

  for node_id in "${NODE_IDS[@]}"; do
    if [[ -z "${SHAPE_SET[$node_id]:-}" ]]; then
      fail "[${rel}] Flow node '${node_id}' has no <bpmndi:BPMNShape bpmnElement=\"${node_id}\">."
    fi
  done

  # Collect sequenceFlow IDs
  mapfile -t SF_IDS < <(grep -oP '(?<=<bpmn:sequenceFlow\s)[^>]*\bid="[^"]*"' \
    "${bpmn}" 2>/dev/null | grep -oP '(?<=\bid=")[^"]*' || true)

  # Collect BPMNEdge bpmnElement values
  mapfile -t EDGE_ELEMENTS < <(grep -oP '(?<=<bpmndi:BPMNEdge\s)[^>]*bpmnElement="[^"]*"' \
    "${bpmn}" 2>/dev/null | grep -oP '(?<=bpmnElement=")[^"]*' || true)
  declare -A EDGE_SET=()
  for e in "${EDGE_ELEMENTS[@]}"; do EDGE_SET[$e]=1; done

  for sf_id in "${SF_IDS[@]}"; do
    if [[ -z "${EDGE_SET[$sf_id]:-}" ]]; then
      fail "[${rel}] SequenceFlow '${sf_id}' has no <bpmndi:BPMNEdge bpmnElement=\"${sf_id}\">."
    fi
  done

  node_count=${#NODE_IDS[@]}
  sf_count=${#SF_IDS[@]}
  if [[ $node_count -eq 0 && $sf_count -eq 0 ]]; then
    info "[${rel}] No flow nodes or sequence flows found (possibly an empty/minimal diagram)."
  else
    pass "[${rel}] DI check: ${node_count} flow node(s), ${sf_count} sequence flow(s) — shapes and edges present."
  fi

  unset SHAPE_SET EDGE_SET
done
echo ""

# ---------------------------------------------------------------------------
# CHECK 4 — Candidate groups inventory
# ---------------------------------------------------------------------------
echo "=== Check 4: Candidate groups (manual IdP verification required) ==="

declare -A CANDIDATE_GROUP_SOURCES  # group -> bpmn file list
for bpmn in "${BPMN_FILES[@]}"; do
  rel="${bpmn#${REPO_ROOT}/}"
  while IFS= read -r group_val; do
    # candidateGroups can be comma-separated; split on comma
    IFS=',' read -ra parts <<< "${group_val}"
    for part in "${parts[@]}"; do
      trimmed="${part// /}"
      [[ -z "$trimmed" ]] && continue
      existing="${CANDIDATE_GROUP_SOURCES[$trimmed]:-}"
      if [[ -z "$existing" ]]; then
        CANDIDATE_GROUP_SOURCES[$trimmed]="${rel}"
      else
        CANDIDATE_GROUP_SOURCES[$trimmed]="${existing}, ${rel}"
      fi
    done
  done < <(grep -oP '(?<=<zeebe:assignmentDefinition\s)[^>]*candidateGroups="[^"]*"' \
             "${bpmn}" 2>/dev/null \
           | grep -oP '(?<=candidateGroups=")[^"]*' || true)
done

if [[ ${#CANDIDATE_GROUP_SOURCES[@]} -eq 0 ]]; then
  info "No candidateGroups found in any BPMN file."
else
  echo "The following candidate groups were referenced. Verify each exists in your IdP:"
  for group in $(printf '%s\n' "${!CANDIDATE_GROUP_SOURCES[@]}" | sort); do
    echo "  - '${group}'  (${CANDIDATE_GROUP_SOURCES[$group]})"
  done
fi
echo ""

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo "=== Summary ==="
echo "FAIL count : ${FAIL_COUNT}"
echo "WARN count : ${WARN_COUNT}"

if [[ ${FAIL_COUNT} -gt 0 ]]; then
  echo "STATUS: FAILED — ${FAIL_COUNT} integrity issue(s) detected."
  exit 1
else
  echo "STATUS: OK — all checks passed."
  exit 0
fi
