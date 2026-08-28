(() => {
  const btn = Array.from(document.querySelectorAll('button, a, [role="button"]')).find(b => (b.innerText||'').trim() === 'Go to dashboard');
  if (btn) { btn.click(); return 'GO'; }
  return 'NO';
})()
