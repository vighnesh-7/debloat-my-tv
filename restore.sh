#!/usr/bin/env bash
# Convenience wrapper: restore the latest timestamped backup.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
LATEST="$(ls -1d "$ROOT"/backups/backup_* 2>/dev/null | tail -1)"
if [ -z "${LATEST:-}" ] || [ ! -x "$LATEST/restore.sh" ]; then
  echo "No backup restore.sh found under backups/"
  exit 1
fi
echo "Using $LATEST/restore.sh"
exec bash "$LATEST/restore.sh"
