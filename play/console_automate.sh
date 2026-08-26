#!/bin/bash
# Chrome Play Console helpers via AppleScript + external JS files.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
JS_DIR="$ROOT/console_js"
mkdir -p "$JS_DIR"

chrome_js_file() {
  local file="$1"
  osascript <<EOF
tell application "Google Chrome"
  activate
  set jsText to read POSIX file "$file" as «class utf8»
  return execute active tab of front window javascript jsText
end tell
EOF
}

chrome_goto() {
  local url="$1"
  osascript <<EOF
tell application "Google Chrome"
  activate
  set URL of active tab of front window to "$url"
end tell
EOF
  sleep "${2:-7}"
}

chrome_url() {
  osascript -e 'tell application "Google Chrome" to get URL of active tab of front window'
}

chrome_dump() {
  chrome_js_file "$JS_DIR/dump.js"
}

# Click first button/link whose visible text includes the needle (case-sensitive).
cat > "$JS_DIR/click_contains.js" <<'JS'
(() => {
  const want = WANT_PLACEHOLDER;
  const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], material-button'));
  const el = candidates.find((e) => (e.innerText || '').replace(/\s+/g, ' ').includes(want));
  if (!el) return 'NOT_FOUND:' + want;
  el.click();
  return 'CLICKED:' + (el.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 120);
})()
JS

click_contains() {
  local want="$1"
  python3 - <<PY
from pathlib import Path
import json
p = Path("$JS_DIR/click_contains.js")
tpl = Path("$JS_DIR/click_contains.js").read_text()
# rewrite from template each time
tpl = '''(() => {
  const want = WANT;
  const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], material-button'));
  const el = candidates.find((e) => (e.innerText || '').replace(/\\s+/g, ' ').includes(want));
  if (!el) return 'NOT_FOUND:' + want;
  el.click();
  return 'CLICKED:' + (el.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 120);
})()'''
  Path("$JS_DIR/_click.js").write_text(tpl.replace('WANT', json.dumps(want)))
PY
  chrome_js_file "$JS_DIR/_click.js"
}

# Click radio/label whose text equals or starts with needle
cat > "$JS_DIR/click_radio.js.tpl" <<'JS'
(() => {
  const want = WANT;
  const labels = Array.from(document.querySelectorAll('label, mat-radio-button, .mdc-form-field, span.mdc-label'));
  const el = labels.find((e) => {
    const t = (e.innerText || '').replace(/\s+/g, ' ').trim();
    return t === want || t.startsWith(want + ' ') || t.startsWith(want + '\n');
  });
  if (!el) {
    return 'NOT_FOUND_RADIO:' + want + ' :: ' + labels.slice(0, 30).map(e => (e.innerText||'').trim().slice(0,40)).join(' | ');
  }
  el.click();
  return 'RADIO:' + (el.innerText || '').trim().slice(0, 80);
})()
JS

click_radio() {
  local want="$1"
  python3 - <<PY
from pathlib import Path
import json
tpl = Path("$JS_DIR/click_radio.js.tpl").read_text()
Path("$JS_DIR/_radio.js").write_text(tpl.replace('WANT', json.dumps(want)))
PY
  chrome_js_file "$JS_DIR/_radio.js"
}

wait_for() {
  local needle="$1"
  local max="${2:-40}"
  local i=0
  while [ "$i" -lt "$max" ]; do
    local t
    t="$(chrome_dump || true)"
    if echo "$t" | grep -q "$needle"; then
      echo "$t"
      return 0
    fi
    sleep 1
    i=$((i+1))
  done
  echo "$t"
  return 1
}

BASE="https://play.google.com/console/u/0/developers/5667084395209045347/app/4975586487357388428"

cmd="${1:-}"
case "$cmd" in
  ads)
    chrome_goto "$BASE/app-content/ads" 8
    wait_for "Ads" 30 >/tmp/vdr-ads.txt || true
    head -c 2000 /tmp/vdr-ads.txt
    echo
    echo "CLICK: $(click_contains 'No, my application does not contain ads' || true)"
    echo "CLICK2: $(click_radio 'No' || true)"
    echo "SAVE: $(click_contains 'Save' || true)"
    sleep 2
    chrome_dump | head -c 2500
    ;;
  dump)
    chrome_dump | head -c "${2:-8000}"
    ;;
  goto)
    chrome_goto "$2" "${3:-7}"
    chrome_url
    chrome_dump | head -c 6000
    ;;
  *)
    echo "usage: $0 ads|dump|goto <url>"
    ;;
esac
