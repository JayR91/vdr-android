#!/bin/bash
# Dedicated Play Console Chrome helpers — never touch BillDesk/Gmail windows.
set -euo pipefail
export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin:/Applications/Cursor.app/Contents/Resources/app/node_modules/@vscode/ripgrep/bin"

JS_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE="https://play.google.com/console/u/0/developers/5667084395209045347/app/4975586487357388428"
WIN_FILE="/tmp/vdr_chrome_win.txt"

pick_win() {
  # Prefer a 1-tab Play Console window (dedicated). Never pick BillDesk/Gmail.
  /usr/bin/osascript <<'EOF'
tell application "Google Chrome"
  set wi to 0
  set singleTab to 0
  repeat with w in windows
    set wi to wi + 1
    set u to URL of active tab of w
    set tc to count of tabs of w
    if u contains "billdesk" or u contains "mail.google.com" then
      -- skip
    else if u contains "play.google.com/console" and tc is 1 then
      set singleTab to wi
    end if
  end repeat
  if singleTab is not 0 then return singleTab
  -- create dedicated window
  set newWin to make new window
  set URL of active tab of newWin to "https://play.google.com/console/u/0/developers/5667084395209045347/app/4975586487357388428/app-content/overview"
  delay 10
  -- re-find 1-tab console window
  set wi to 0
  repeat with w in windows
    set wi to wi + 1
    set u to URL of active tab of w
    if (count of tabs of w) is 1 and u contains "play.google.com/console" then return wi
  end repeat
  return 1
end tell
EOF
}

ensure_win() {
  local w
  w="$(pick_win)"
  echo "$w" > "$WIN_FILE"
  echo "$w"
}

win() { /bin/cat "$WIN_FILE" 2>/dev/null || ensure_win; }

chrome_goto() {
  local url="$1" wait="${2:-10}" w
  w="$(win)"
  /usr/bin/osascript <<EOF
tell application "Google Chrome"
  set URL of active tab of window $w to "$url"
end tell
EOF
  /bin/sleep "$wait"
}

chrome_js_file() {
  local file="$1" w
  w="$(win)"
  /usr/bin/osascript <<EOF
tell application "Google Chrome"
  set jsText to read POSIX file "$file" as «class utf8»
  return execute active tab of window $w javascript jsText
end tell
EOF
}

chrome_js_inline() {
  local js="$1" w
  w="$(win)"
  printf '%s' "$js" > /tmp/vdr_inline.js
  chrome_js_file /tmp/vdr_inline.js
}

chrome_url() {
  local w
  w="$(win)"
  /usr/bin/osascript -e "tell application \"Google Chrome\" to get URL of active tab of window $w"
}

chrome_dump() {
  chrome_js_file "$JS_DIR/dump.js"
}

case "${1:-}" in
  ensure) ensure_win ;;
  goto) ensure_win >/dev/null; chrome_goto "$2" "${3:-10}"; chrome_url ;;
  dump) chrome_dump | /usr/bin/head -c "${2:-8000}" ;;
  url) chrome_url ;;
  js) chrome_js_file "$2" ;;
  *) echo "usage: $0 ensure|goto <url> [wait]|dump [n]|url|js <file>" ;;
esac
