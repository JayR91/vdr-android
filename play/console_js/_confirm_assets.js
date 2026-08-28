(() => {
  const btns = Array.from(document.querySelectorAll('button, [role="button"]')).map(b => ({
    text: (b.innerText||'').replace(/\s+/g,' ').trim(),
    disabled: b.disabled,
    top: Math.round(b.getBoundingClientRect().top)
  })).filter(b => b.text && b.top > 0 && b.top < 200);
  
  // Top bar Add button near close in asset picker
  const add = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => {
    const t = (b.innerText||'').replace(/\s+/g,' ').trim();
    const top = b.getBoundingClientRect().top;
    return (t === 'Add' || t.startsWith('Add ')) && top < 150 && top > 0;
  });
  if (add) { add.click(); return 'ADD:' + add.innerText.trim(); }
  
  // chevron_right might be confirm
  const chev = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => (b.innerText||'').trim() === 'chevron_right' && b.getBoundingClientRect().top < 150);
  if (chev) { chev.click(); return 'CHEVRON'; }
  
  return 'FAIL topBtns=' + btns.map(b=>b.text).join('|');
})()
