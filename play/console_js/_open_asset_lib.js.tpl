(() => {
  const idx = parseInt('INDEX_PLACEHOLDER', 10);
  const name = 'vdr-tablet10-0' + idx + '.jpg';

  // Open asset library if not open
  if (!(document.body.innerText||'').includes('Search assets')) {
    const label = Array.from(document.querySelectorAll('*')).find(e => (e.innerText||'').trim() === '10-inch tablet screenshots *');
    if (label) {
      let c = label;
      for (let i=0;i<12&&c;i++) {
        const btn = Array.from(c.querySelectorAll('button,[role="button"]')).find(b => (b.innerText||'').trim() === 'Add assets');
        if (btn) { btn.click(); break; }
        c = c.parentElement;
      }
    }
  }

  return 'OPENED';
})()
