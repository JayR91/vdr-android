#!/usr/bin/env bash
# Upload VDR AAB to Internal testing via Google Play Developer API.
# Requires a Play Console service account JSON with Android Publisher access.
# Usage:
#   PLAY_JSON=/path/to/api-key.json ./play/upload-internal.sh
# Never commit the JSON.

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AAB="${AAB:-$ROOT/app/build/outputs/bundle/release/app-release.aab}"
PACKAGE="${PACKAGE:-com.jayr91.vdr}"
TRACK="${TRACK:-internal}"
JSON="${PLAY_JSON:-${GOOGLE_APPLICATION_CREDENTIALS:-}}"

if [[ -z "$JSON" || ! -f "$JSON" ]]; then
  echo "Set PLAY_JSON to a Play Console service-account JSON path." >&2
  exit 1
fi
if [[ ! -f "$AAB" ]]; then
  echo "Missing AAB: $AAB" >&2
  exit 1
fi

export GOOGLE_APPLICATION_CREDENTIALS="$JSON"
export AAB PACKAGE TRACK
python3 - <<'PY'
import os, sys
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

package = os.environ.get("PACKAGE", "com.jayr91.vdr")
aab = os.environ["AAB"]
track = os.environ.get("TRACK", "internal")
json_path = os.environ["GOOGLE_APPLICATION_CREDENTIALS"]

creds = service_account.Credentials.from_service_account_file(
    json_path,
    scopes=["https://www.googleapis.com/auth/androidpublisher"],
)
svc = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
edit = svc.edits().insert(body={}, packageName=package).execute()
edit_id = edit["id"]
print("edit", edit_id)
media = MediaFileUpload(aab, mimetype="application/octet-stream", resumable=True)
bundle = svc.edits().bundles().upload(
    packageName=package, editId=edit_id, media_body=media
).execute()
version_code = bundle["versionCode"]
print("uploaded versionCode", version_code)
svc.edits().tracks().update(
    packageName=package,
    editId=edit_id,
    track=track,
    body={"track": track, "releases": [{"versionCodes": [str(version_code)], "status": "completed"}]},
).execute()
svc.edits().commit(packageName=package, editId=edit_id).execute()
print(f"Committed {package} versionCode {version_code} to track={track}")
PY
