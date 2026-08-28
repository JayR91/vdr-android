(() => {
  // Click radio_button_unchecked buttons in asset picker to select all
  const radios = Array.from(document.querySelectorAll('button, [role="button"]')).filter(b => (b.innerText||'').trim() === 'radio_button_unchecked');
  let n = 0;
  for (const r of radios) {
    r.click();
    n++;
  }
  
  // Also click asset div tiles
  const tiles = Array.from(document.querySelectorAll('div')).filter(d => {
    const t = d.innerText||'';
    return t.startsWith('vdr-tablet10-') && t.includes('1200x1920');
  });
  for (const t of tiles) { t.click(); }
  
  // Find bottom confirm in asset panel - look for buttons with Add/Select and count
  const allBtns = Array.from(document.querySelectorAll('button, [role="button"]')).map(b => (b.innerText||'').replace(/\s+/g,' ').trim()).filter(t => t && t.length < 60);
  const addBtn = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => {
    const t = (b.innerText||'').replace(/\s+/g,' ').trim();
    return /^Add \d/.test(t) || t === 'Add' || t.includes('Add selected') || t.includes('Insert');
  });
  if (addBtn) { addBtn.click(); return 'RADIOS:' + n + ' ADD:' + addBtn.innerText.trim(); }
  
  // try arrow_right_alt on each selected asset?
  const arrows = Array.from(document.querySelectorAll('button, [role="button"]')).filter(b => (b.innerText||'').trim() === 'arrow_right_alt');
  if (arrows.length) { arrows[0].click(); return 'RADIOS:' + n + ' ARROW clicked, btns=' + allBtns.slice(-15).join('|'); }
  
  return 'RADIOS:' + n + ' tiles=' + tiles.length + ' btns=' + allBtns.filter(t => /add|select|insert|apply|done/i.test(t)).join('|') + ' unchecked=' + Array.from(document.querySelectorAll('button')).filter(b=>(b.innerText||'').includes('unchecked')).length;
})()
