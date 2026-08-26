(() => {
  const want = "Save";
  const all = Array.from(document.querySelectorAll('button, a, [role="button"], label, mat-radio-button, span, div, p'));
  // Prefer interactive exact match
  let el = all.find(e => (e.innerText || '').trim() === want && (e.tagName==='BUTTON' || e.tagName==='A' || e.getAttribute('role')==='button' || e.tagName==='LABEL' || e.tagName==='MAT-RADIO-BUTTON'));
  if (!el) {
    el = all.find(e => (e.innerText || '').trim() === want && e.childElementCount === 0);
  }
  if (!el) {
    el = all.find(e => (e.innerText || '').replace(/\s+/g,' ').trim() === want);
  }
  if (!el) return 'NOT_FOUND_EXACT:' + want;
  el.click();
  // also click associated input
  const inp = el.querySelector('input') || (el.htmlFor ? document.getElementById(el.htmlFor) : null);
  if (inp) inp.click();
  return 'OK:' + want + ':' + el.tagName;
})()
