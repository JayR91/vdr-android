(() => {
  const picker = Array.from(document.querySelectorAll('*')).find(e => (e.innerText||'').includes('Search assets') && (e.innerText||'').includes('vdr-tablet10'));
  if (!picker) return 'NO_PICKER';
  const btns = Array.from(picker.querySelectorAll('button, [role="button"]')).map(b => ({
    text: (b.innerText||'').trim(),
    disabled: b.disabled
  }));
  const tiles = Array.from(picker.querySelectorAll('[class*="asset"], [role="checkbox"], img, .tile, material-checkbox')).slice(0,15).map(t => ({
    tag: t.tagName,
    text: (t.innerText||'').slice(0,60),
    aria: t.getAttribute('aria-label'),
    selected: t.getAttribute('aria-checked') || t.getAttribute('aria-selected')
  }));
  return JSON.stringify({btns, tiles, pickerText: picker.innerText.slice(0,800)}, null, 2);
})()
