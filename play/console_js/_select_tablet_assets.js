(() => {
  // Select all vdr-tablet10 assets in asset picker
  const assets = Array.from(document.querySelectorAll('*')).filter(e => {
    const t = (e.innerText||'').trim();
    return t === 'vdr-tablet10-01.jpg' || t === 'vdr-tablet10-02.jpg' || t === 'vdr-tablet10-03.jpg' ||
           t === 'vdr-tablet10-04.jpg' || t === 'vdr-tablet10-05.jpg' || t === 'vdr-tablet10-06.jpg';
  });
  let clicked = 0;
  for (const name of ['vdr-tablet10-01.jpg','vdr-tablet10-02.jpg','vdr-tablet10-03.jpg','vdr-tablet10-04.jpg','vdr-tablet10-05.jpg','vdr-tablet10-06.jpg']) {
    const el = Array.from(document.querySelectorAll('*')).find(e => (e.innerText||'').trim() === name && e.offsetParent !== null);
    if (el) {
      // click parent tile if deselected
      let tile = el;
      for (let i=0;i<6&&tile;i++) {
        if ((tile.innerText||'').includes('Deselected') || tile.getAttribute('role')==='checkbox' || (tile.className||'').toString().includes('asset')) {
          tile.click(); clicked++; break;
        }
        tile = tile.parentElement;
      }
      if (!tile) { el.click(); clicked++; }
    }
  }
  
  // Click Add/Apply/Done button
  const confirmBtns = ['Add', 'Apply', 'Done', 'Select', 'Save'];
  let confirm = null;
  for (const n of confirmBtns) {
    confirm = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => (b.innerText||'').trim() === n && !b.disabled);
    if (confirm) { confirm.click(); return 'SELECTED:' + clicked + ' CONFIRM:' + n; }
  }
  return 'SELECTED:' + clicked + ' NO_CONFIRM. btns=' + Array.from(document.querySelectorAll('button,[role="button"]')).map(b=>(b.innerText||'').trim()).filter(Boolean).slice(0,20).join('|');
})()
