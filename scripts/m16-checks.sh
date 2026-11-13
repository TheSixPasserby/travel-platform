#!/usr/bin/env bash
# Explicit quality checks only; no commit/push/deploy or migrations.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"
mode="${1:-help}"
case "$mode" in
  frontend)
    echo '[M16] Frontend tests'
    npm --prefix frontend test
    echo '[M16] Frontend TypeScript + Vite production build'
    npm --prefix frontend run build
    ;;
  full)
    echo '[M16] Full checks: confirm isolated MySQL and backend test configuration before proceeding.'
    echo '[M16] No migration or database reset is executed by this script.'
    npm --prefix frontend test
    npm --prefix frontend run build
    python3 scripts/backend.py package
    python3 -m unittest discover -s scripts/tests
    ;;
  help|-h|--help)
    cat <<'HELP'
Usage:
  bash scripts/m16-checks.sh frontend    # npm frontend tests + build
  bash scripts/m16-checks.sh full        # also backend package tests + Python tests

This script does NOT implement M16, commit, push, deploy, run SQL migrations,
or execute mobile builds. It only runs existing project tests after explicit invocation.
HELP
    ;;
  *) echo "Unknown mode: $mode" >&2;exit 2;;
esac
