#!/bin/bash
# Installs monitor cron jobs from etc/monitor-cron into /etc/cron.d/
# Run automatically by deploy.yml after pulling latest code

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CRON_SOURCE="$PROJECT_DIR/etc/monitor-cron"
CRON_TARGET="/etc/cron.d/expense-monitor"

# Only install if the source file exists
if [[ -f "$CRON_SOURCE" ]]; then
    cp "$CRON_SOURCE" "$CRON_TARGET"
    chmod 644 "$CRON_TARGET"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cron jobs installed from $CRON_SOURCE"
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] WARNING: $CRON_SOURCE not found — skipping cron install"
fi
