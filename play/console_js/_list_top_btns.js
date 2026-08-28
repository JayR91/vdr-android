(() => {
  return JSON.stringify(Array.from(document.querySelectorAll('button, [role="button"]')).filter(b => {
    const r = b.getBoundingClientRect();
    return r.top >= 0 && r.top < 120 && r.width > 0;
  }).map(b => ({
    text: (b.innerText||'').replace(/\s+/g,' ').trim().slice(0,40),
    top: Math.round(b.getBoundingClientRect().top),
    left: Math.round(b.getBoundingClientRect().left),
    disabled: b.disabled
  })), null, 2);
})()
