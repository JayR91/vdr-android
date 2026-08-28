(() => {
  // Select 9:16 portrait preset if available
  const preset = Array.from(document.querySelectorAll('button, [role="button"], label, div, span')).find(e => {
    const t = (e.innerText||'').replace(/\s+/g,' ').trim();
    return t.includes('9') && t.includes('16') && t.includes('portrait');
  });
  if (preset) preset.click();
  
  // Save as copy
  const saveCopy = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => (b.innerText||'').trim() === 'Save as copy');
  if (saveCopy) { saveCopy.click(); return 'SAVE_COPY'; }
  
  const save = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => (b.innerText||'').trim() === 'Save');
  if (save) { save.click(); return 'SAVE'; }
  return 'NO_SAVE preset=' + !!preset;
})()
