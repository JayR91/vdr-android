(() => {
  const rows = Array.from(document.querySelectorAll('tr, [role="row"]'));
  let row = rows.find(r => (r.innerText||'').includes('8488-6695-2592-8969'));
  if (!row) {
    // particle table rows may be div-based
    row = Array.from(document.querySelectorAll('a')).find(a => (a.innerText||'').includes('8488-6695') || (a.getAttribute('href')||'').includes('8488'));
    if (row) { row.click(); return 'CLICKED_A:'+row.href; }
    return 'NO_INDIA_ROW count='+rows.length;
  }
  const links = Array.from(row.querySelectorAll('a'));
  const target = links[links.length-1] || row;
  target.click();
  return 'CLICKED:'+ (target.href||target.tagName);
})()
