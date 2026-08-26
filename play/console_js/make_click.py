import json, sys
from pathlib import Path
want = sys.argv[1]
kind = sys.argv[2] if len(sys.argv) > 2 else 'button'
out = Path(sys.argv[3])
if kind == 'button':
    js = f'''(() => {{
  const want = {json.dumps(want)};
  const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], material-button'));
  const el = candidates.find((e) => (e.innerText || '').replace(/\\s+/g, ' ').includes(want));
  if (!el) return 'NOT_FOUND:' + want;
  el.click();
  return 'CLICKED:' + (el.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 120);
}})()'''
else:
    js = f'''(() => {{
  const want = {json.dumps(want)};
  const labels = Array.from(document.querySelectorAll('label, mat-radio-button, .mdc-form-field, span.mdc-label, mat-checkbox, .particle-radio-button, div[role="radio"]'));
  const el = labels.find((e) => {{
    const t = (e.innerText || '').replace(/\\s+/g, ' ').trim();
    return t === want || t.startsWith(want);
  }});
  if (!el) return 'NOT_FOUND_RADIO:' + want + ' :: ' + labels.slice(0, 50).map(e => (e.innerText||'').trim().slice(0,60)).join(' | ');
  el.click();
  return 'RADIO:' + (el.innerText || '').trim().slice(0, 120);
}})()'''
out.write_text(js)
print(out)
