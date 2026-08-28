(() => {
  const names = ['vdrsss1.jpg', 'vdrss2.jpg', 'vdrss3.jpg', 'vdrss4.jpg'];
  let arrows = 0;
  for (const name of names) {
    const tiles = Array.from(document.querySelectorAll('div')).filter(d => {
      const t = (d.innerText || '');
      return t.includes(name) && t.includes('576x1280') && t.length < 200;
    });
    const tile = tiles.find(d => Array.from(d.querySelectorAll('button, [role="button"]')).some(b => (b.innerText||'').trim() === 'arrow_right_alt')) || tiles[0];
    if (!tile) continue;
    const arrowBtn = Array.from(tile.querySelectorAll('button, [role="button"]')).find(b => (b.innerText||'').trim() === 'arrow_right_alt');
    if (arrowBtn) { arrowBtn.click(); arrows++; }
  }
  return 'arrows=' + arrows;
})()
