#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! command -v python3 >/dev/null 2>&1; then
  printf '需要 Python 3，请先安装后再运行启动脚本。\n' >&2
  exit 2
fi
exec python3 "$PROJECT_ROOT/scripts/start.py" "$@"
