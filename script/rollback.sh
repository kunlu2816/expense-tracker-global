#!/bin/bash
# script/rollback.sh
# Usage:
#   ./script/rollback.sh                -> Automatically rolls back to the last successful deployment
#   ./script/rollback.sh <git_sha>      -> Rolls back to a specific commit
set -e
ENV_FILE=".env"
LAST_SUCCESS_FILE=".last_successful_deploy"
# 1. Determine the target version (Target SHA)
if [[ -n "$1" ]]; then
    TARGET_SHA="$1"
    echo "🔄 Manual rollback requested to Git SHA: $TARGET_SHA"
else
    if [[ ! -f "$LAST_SUCCESS_FILE" ]]; then
        echo "❌ Error: Status file $LAST_SUCCESS_FILE not found. Please provide a specific Git SHA."
        exit 1
    fi
    TARGET_SHA=$(cat "$LAST_SUCCESS_FILE")
    echo "🔄 Auto rollback triggered to last successful deploy: $TARGET_SHA"
fi
# 2. Extract Repository name from the current .env to construct the correct GHCR URL
if grep -q "BACKEND_IMAGE" "$ENV_FILE"; then
    REPO_PATH=$(grep "BACKEND_IMAGE" "$ENV_FILE" | cut -d'=' -f2 | awk -F'/' '{print $2"/"$3}' | cut -d':' -f1)
else
    echo "❌ Error: Could not find BACKEND_IMAGE in $ENV_FILE"
    exit 1
fi
echo "📦 Using GHCR repository path: ghcr.io/$REPO_PATH"
# 3. Update the .env file to point to the older Image version
sed -i "s|BACKEND_IMAGE=.*|BACKEND_IMAGE=ghcr.io/${REPO_PATH}:$TARGET_SHA|g" "$ENV_FILE"
sed -i "s|FRONTEND_IMAGE=.*|FRONTEND_IMAGE=ghcr.io/${REPO_PATH}:$TARGET_SHA|g" "$ENV_FILE"
echo "✅ Updated .env to point to version $TARGET_SHA."
# 4. Pull the old Images and Restart (Extremely fast since there is no building involved)
echo "⬇️ Pulling images for version $TARGET_SHA..."
docker compose pull
echo "🚀 Restarting containers..."
docker compose up -d
# 5. Health check after Rollback
echo "⏳ Waiting 15 seconds for the system to stabilize..."
sleep 15
bash script/smoke-test.sh || {
    echo "❌ ROLLBACK FAILED! The older version ($TARGET_SHA) also failed the Smoke Test."
    exit 1
}
echo "✅ Rollback to version $TARGET_SHA completed successfully!"