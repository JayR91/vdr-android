(() => {
  const btn = Array.from(document.querySelectorAll('button, a, [role="button"]')).find(b => (b.innerText||'').trim() === 'View tasks');
  if (btn) { btn.click(); return 'VIEW'; }
  return 'NO';
})()
