(() => {
  const btn = Array.from(document.querySelectorAll('button, a, [role="button"]')).find(b => (b.innerText||'').includes('View tasks'));
  if (btn) { btn.click(); return 'VIEW:' + btn.innerText.trim().slice(0,40); }
  return 'NO';
})()
