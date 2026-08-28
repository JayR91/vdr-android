(() => {
  const add = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => (b.innerText||'').trim() === 'Add');
  if (add) { add.click(); return 'CLICKED_ADD'; }
  const crop = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => (b.innerText||'').includes('Crop'));
  if (crop) { crop.click(); return 'CLICKED_CROP'; }
  return 'NO_BTN';
})()
