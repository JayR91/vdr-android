(() => {
  const add = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => {
    const t = (b.innerText||'').trim();
    return t === 'Add' || t === 'add_photo_alternate\nAdd';
  });
  if (add) { add.click(); return 'ADDED'; }
  const add2 = Array.from(document.querySelectorAll('button, [role="button"]')).find(b => (b.innerText||'').includes('Add') && !(b.innerText||'').includes('Add assets') && !(b.innerText||'').includes('Add from'));
  if (add2) { add2.click(); return 'ADDED2:' + add2.innerText.trim().slice(0,30); }
  return 'NO_ADD:' + Array.from(document.querySelectorAll('button,[role="button"]')).map(b=>(b.innerText||'').trim()).filter(t=>t.includes('Add')).join('|');
})()
