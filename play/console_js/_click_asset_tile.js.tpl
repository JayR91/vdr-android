(() => {
  const name = 'ASSET_NAME_PLACEHOLDER';
  const tile = Array.from(document.querySelectorAll('div, span')).find(e => {
    const t = (e.innerText||'').trim();
    return t.startsWith(name) && t.includes('1200x1920') && !t.includes('Cropped');
  });
  if (!tile) return 'NO_TILE:' + name;
  tile.click();
  return 'CLICKED_TILE:' + name;
})()
