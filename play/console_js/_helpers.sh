#!/bin/bash
# Shared Chrome helpers for Play Console (window 1, tab 1 = Play; leave BillDesk alone).
set -euo pipefail
JS_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE="https://play.google.com/console/u/0/developers/5667084395209045347/app/4975586487357388428"

chrome_js() {
  /usr/bin/osascript <<EOF
tell application "Google Chrome"
  set active tab index of window 1 to 1
  set jsText to read POSIX file "$1" as «class utf8»
  return execute active tab of window 1 javascript jsText
end tell
EOF
}

chrome_goto() {
  /usr/bin/osascript <<EOF
tell application "Google Chrome"
  set active tab index of window 1 to 1
  set URL of active tab of window 1 to "$1"
end tell
EOF
  /bin/sleep "${2:-9}"
}

chrome_url() {
  /usr/bin/osascript -e 'tell application "Google Chrome" to get URL of active tab of window 1'
}

chrome_dump() { chrome_js "$JS_DIR/dump.js"; }

write_js() {
  # $1 = path, rest via stdin
  cat > "$1"
}

click_exact() {
  local want="$1"
  python3 - "$want" <<'PY'
import json, sys
from pathlib import Path
want = sys.argv[1]
Path('/tmp/vdr_click.js').write_text('''(() => {
  const want = %s;
  const nodes = Array.from(document.querySelectorAll(
    'label, mat-radio-button, button, a, [role="button"], span.mdc-label, .mdc-form-field, mat-checkbox, mat-option, li'
  ));
  let el = nodes.find(e => {
    const t = (e.innerText || '').replace(/\\s+/g, ' ').trim();
    return t === want || t.startsWith(want + ' ') || t.startsWith(want + '\\n');
  });
  if (!el) {
    el = Array.from(document.querySelectorAll('div, span, label, p')).find(e => {
      const t = (e.innerText || '').replace(/\\s+/g, ' ').trim();
      return t === want;
    });
  }
  if (!el) return 'NOT_FOUND:' + want;
  el.click();
  return 'OK:' + (el.innerText || '').trim().slice(0, 140);
})()''' % json.dumps(want))
PY
  chrome_js /tmp/vdr_click.js
}

click_contains() {
  local want="$1"
  python3 - "$want" <<'PY'
import json, sys
from pathlib import Path
want = sys.argv[1]
Path('/tmp/vdr_click.js').write_text('''(() => {
  const want = %s;
  const nodes = Array.from(document.querySelectorAll(
    'label, mat-radio-button, button, a, [role="button"], span, div, mat-checkbox'
  ));
  const el = nodes.find(e => {
    const t = (e.innerText || '').replace(/\\s+/g, ' ').trim();
    return t.includes(want) && t.length < 200;
  });
  if (!el) return 'NOT_FOUND:' + want;
  el.click();
  return 'OK:' + (el.innerText || '').trim().slice(0, 140);
})()''' % json.dumps(want))
PY
  chrome_js /tmp/vdr_click.js
}

click_save() {
  python3 <<'PY'
from pathlib import Path
Path('/tmp/vdr_save.js').write_text('''(() => {
  const btns = Array.from(document.querySelectorAll('button, [role="button"], a'));
  for (const n of ['Save', 'Next', 'Continue', 'Submit', 'Save and continue', 'Start questionnaire', 'Apply']) {
    const b = btns.find(e => (e.innerText || '').trim() === n && !e.disabled);
    if (b) { b.click(); return 'CLICKED:' + n; }
  }
  return 'NO_BTN:' + btns.map(b => (b.innerText || '').trim()).filter(Boolean).slice(0, 30).join('|');
})()''')
PY
  chrome_js /tmp/vdr_save.js
}

start_section() {
  local section="$1"
  chrome_goto "$BASE/app-content/overview" 8
  python3 - "$section" <<'PY'
import json, sys
from pathlib import Path
section = sys.argv[1]
Path('/tmp/vdr_start.js').write_text('''(() => {
  const want = %s;
  const els = Array.from(document.querySelectorAll('h1,h2,h3,h4,div,span'));
  const heading = els.find(e => ((e.innerText || '').trim() === want));
  if (!heading) return 'NO_HEADING:' + want;
  let card = heading;
  for (let i = 0; i < 10; i++) {
    if (!card.parentElement) break;
    card = card.parentElement;
    const t = card.innerText || '';
    if ((t.includes('Start declaration') || t.includes('Manage')) && t.length < 1600) break;
  }
  const btn = Array.from(card.querySelectorAll('a,button,[role="button"]')).find(e => {
    const t = (e.innerText || '').trim();
    return t === 'Start declaration' || t === 'Manage';
  });
  if (!btn) return 'NO_BTN:' + want;
  btn.click();
  return 'STARTED:' + want;
})()''' % json.dumps(section))
PY
  echo "$(chrome_js /tmp/vdr_start.js)"
  sleep 8
  echo "URL=$(chrome_url)"
}

accept_dialogs() {
  # Dismiss common Chrome/Play dialogs via JS
  python3 <<'PY'
from pathlib import Path
Path('/tmp/vdr_accept.js').write_text('''(() => {
  const names = ['OK', 'Got it', 'Continue', 'Close', 'Dismiss', 'Yes', 'Confirm', 'Accept', 'I understand', 'Not now'];
  const btns = Array.from(document.querySelectorAll('button, [role="button"]'));
  const hits = [];
  for (const n of names) {
    const b = btns.find(e => (e.innerText || '').trim() === n && e.offsetParent !== null);
    if (b) { b.click(); hits.push(n); }
  }
  return hits.length ? 'ACCEPTED:' + hits.join(',') : 'NONE';
})()''')
PY
  chrome_js /tmp/vdr_accept.js
}
