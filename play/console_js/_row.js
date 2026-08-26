(() => {
  const el = Array.from(document.querySelectorAll('*')).find(e => (e.innerText||'').includes('7695-7184-9564-3355') && e.childElementCount < 5);
  if (!el) return 'NO_ROW';
  // climb to clickable
  let n = el;
  for (let i=0;i<8 && n;i++) {
    if (n.getAttribute && (n.getAttribute('role')==='link' || n.tagName==='A' || n.onclick)) { n.click(); return 'CLICKED_ANCESTOR'; }
    n = n.parentElement;
  }
  el.click();
  return 'CLICKED_EL:'+(el.tagName);
})()
