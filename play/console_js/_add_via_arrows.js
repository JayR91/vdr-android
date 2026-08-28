(() => {
  const unchecked = Array.from(document.querySelectorAll('button, [role="button"]')).filter(b => {
    if ((b.innerText||'').trim() !== 'radio_button_unchecked') return false;
    const tile = b.closest('div');
    return tile && (tile.innerText||'').includes('vdr-tablet10') && !(tile.innerText||'').includes('Cropped');
  });
  for (const r of unchecked) r.click();

  const arrows = Array.from(document.querySelectorAll('button, [role="button"]')).filter(b => {
    if ((b.innerText||'').trim() !== 'arrow_right_alt') return false;
    let p = b.parentElement;
    for (let i=0;i<8&&p;i++) {
      const t = p.innerText||'';
      if (t.includes('vdr-tablet10') && !t.includes('Cropped')) return true;
      p = p.parentElement;
    }
    return false;
  });
  let n = 0;
  for (const a of arrows) { a.click(); n++; }
  return 'arrows=' + n + ' unchecked=' + unchecked.length;
})()
