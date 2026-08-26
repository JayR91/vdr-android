(() => {
  const links = Array.from(document.querySelectorAll('a[href]')).map(a => ({text:(a.innerText||'').trim().slice(0,80), href:a.href}));
  const uniq = [];
  const seen = new Set();
  for (const l of links) {
    if (seen.has(l.href)) continue;
    seen.add(l.href);
    if (/pay|bill|merchant|verify|payment|issue|profile|support/i.test(l.href+l.text)) uniq.push(l);
  }
  return JSON.stringify(uniq.slice(0,40), null, 2);
})()
