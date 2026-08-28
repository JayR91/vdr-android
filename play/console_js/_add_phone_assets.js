(() => {
  const names = ['vdrsss1.jpg', 'vdrss2.jpg', 'vdrss3.jpg', 'vdrss4.jpg'];
  const panel = Array.from(document.querySelectorAll('*')).find(e => {
    const t = e.innerText || '';
    return t.includes('Search assets') && (t.includes('vdrss') || t.includes('vdrsss')) && t.length < 8000;
  });
  if (!panel) return 'NO_PANEL';

  let selected = 0;
  for (const name of names) {
    const tile = Array.from(panel.querySelectorAll('div')).find(d => {
      const t = (d.innerText || '').trim();
      return t.startsWith(name) && t.includes('576x1280');
    });
    if (tile) {
      tile.click();
      const radio = tile.querySelector('button, [role="button"]');
      if (radio && (radio.innerText || '').includes('unchecked')) radio.click();
      selected++;
    }
  }

  const unchecked = Array.from(panel.querySelectorAll('button, [role="button"]')).filter(b => {
    if ((b.innerText || '').trim() !== 'radio_button_unchecked') return false;
    let p = b.parentElement;
    for (let i = 0; i < 8 && p; i++) {
      const t = p.innerText || '';
      if (names.some(n => t.includes(n))) return true;
      p = p.parentElement;
    }
    return false;
  });
  for (const r of unchecked) r.click();

  const overlay = Array.from(document.querySelectorAll('.cdk-overlay-pane, console-asset-picker, [class*="asset-picker"], [class*="overlay"]')).find(e => (e.innerText || '').includes('Search assets')) || panel;

  const confirmNames = ['Add 4 assets', 'Add 3 assets', 'Add 2 assets', 'Add 1 asset', 'Add assets', 'Add', 'Insert', 'Apply'];
  for (const n of confirmNames) {
    const btn = Array.from(overlay.querySelectorAll('button, [role="button"]')).find(b => {
      const t = (b.innerText || '').replace(/\s+/g, ' ').trim();
      return t === n || (t.startsWith('Add ') && t.includes('asset'));
    });
    if (btn && !btn.disabled) {
      btn.click();
      return 'OK selected=' + selected + ' confirm=' + (btn.innerText || '').trim();
    }
  }

  const arrows = Array.from(document.querySelectorAll('button, [role="button"]')).filter(b => {
    if ((b.innerText||'').trim() !== 'arrow_right_alt') return false;
    let p = b.parentElement;
    for (let i=0;i<8&&p;i++) {
      const t = p.innerText||'';
      if (names.some(n => t.includes(n))) return true;
      p = p.parentElement;
    }
    return false;
  });
  let n = 0;
  for (const a of arrows) { a.click(); n++; }
  if (n) return 'ARROWS=' + n + ' selected=' + selected;

  const checked = Array.from(panel.querySelectorAll('button, [role="button"]')).filter(b => (b.innerText || '').trim() === 'radio_button_checked').length;
  const btns = Array.from(overlay.querySelectorAll('button, [role="button"]')).map(b => (b.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean);
  return 'PARTIAL selected=' + selected + ' checked=' + checked + ' btns=' + btns.slice(0, 25).join('|');
})()
