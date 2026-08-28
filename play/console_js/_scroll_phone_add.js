(() => {
  const label = Array.from(document.querySelectorAll('*')).find(e => (e.innerText||'').trim() === 'Phone screenshots *');
  if (!label) return 'NO_LABEL';
  label.scrollIntoView({block: 'center'});
  const btn = Array.from(document.querySelectorAll('button, [role="button"]')).filter(b => (b.innerText||'').trim() === 'Add assets')[2];
  if (!btn) return 'NO_BTN';
  btn.scrollIntoView({block: 'center'});
  btn.click();
  return 'CLICKED top=' + Math.round(btn.getBoundingClientRect().top);
})()
