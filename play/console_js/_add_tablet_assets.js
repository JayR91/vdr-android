(() => {
  const panel = Array.from(document.querySelectorAll('*')).find(e => {
    const t = e.innerText || '';
    return t.includes('Search assets') && t.includes('vdr-tablet10-01.jpg') && t.length < 5000;
  });
  if (!panel) return 'NO_PANEL';

  // Select each tablet asset tile
  let selected = 0;
  for (let i = 1; i <= 6; i++) {
    const name = 'vdr-tablet10-0' + i + '.jpg';
    const tile = Array.from(panel.querySelectorAll('div')).find(d => {
      const t = (d.innerText || '').trim();
      return t.startsWith(name) && t.includes('1200x1920');
    });
    if (tile) {
      tile.click();
      const radio = tile.querySelector('button, [role="button"]');
      if (radio && (radio.innerText || '').includes('unchecked')) radio.click();
      selected++;
    }
  }

  // Re-click unchecked radios in panel
  const unchecked = Array.from(panel.querySelectorAll('button, [role="button"]')).filter(b => (b.innerText || '').trim() === 'radio_button_unchecked');
  for (const r of unchecked) r.click();

  // Bottom bar buttons in overlay (not page Save)
  const overlay = Array.from(document.querySelectorAll('.cdk-overlay-pane, console-asset-picker, [class*="asset-picker"], [class*="overlay"]')).find(e => (e.innerText || '').includes('Search assets')) || panel;

  const confirmNames = ['Add 6 assets', 'Add 5 assets', 'Add 4 assets', 'Add 3 assets', 'Add 2 assets', 'Add 1 asset', 'Add assets', 'Add', 'Insert', 'Apply'];
  for (const n of confirmNames) {
    const btn = Array.from(overlay.querySelectorAll('button, [role="button"]')).find(b => {
      const t = (b.innerText || '').replace(/\s+/g, ' ').trim();
      return t === n || t.startsWith('Add ') && t.includes('asset');
    });
    if (btn && !btn.disabled) {
      btn.click();
      return 'OK selected=' + selected + ' confirm=' + (btn.innerText || '').trim();
    }
  }

  // Try close button area - sometimes need to click checkmark on each then close
  const checked = Array.from(panel.querySelectorAll('button, [role="button"]')).filter(b => (b.innerText || '').trim() === 'radio_button_checked').length;
  const btns = Array.from(overlay.querySelectorAll('button, [role="button"]')).map(b => (b.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean);
  return 'PARTIAL selected=' + selected + ' checked=' + checked + ' btns=' + btns.slice(0, 25).join('|');
})()
