(() => {
  const want = "Save draft";
  const candidates = Array.from(document.querySelectorAll('button, a, [role="button"], material-button'));
  const el = candidates.find((e) => (e.innerText || '').replace(/\s+/g, ' ').includes(want));
  if (!el) return 'NOT_FOUND:' + want;
  el.click();
  return 'CLICKED:' + (el.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 120);
})()