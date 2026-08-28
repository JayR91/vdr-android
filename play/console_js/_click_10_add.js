(() => {
  const label = Array.from(document.querySelectorAll('*')).find(e => (e.innerText||'').trim() === '10-inch tablet screenshots *');
  if (!label) return 'NO_LABEL';
  let container = label;
  for (let i=0;i<12&&container;i++) {
    const btn = Array.from(container.querySelectorAll('button, a, [role="button"], material-button, div')).find(e => (e.innerText||'').trim() === 'Add assets');
    if (btn) { btn.click(); return 'CLICKED_ADD in level ' + i; }
    container = container.parentElement;
  }
  const btns = Array.from(document.querySelectorAll('button, [role="button"]')).filter(b => (b.innerText||'').trim() === 'Add assets');
  const withY = btns.map(b => ({top: Math.round(b.getBoundingClientRect().top), el: b}));
  withY.sort((a,b) => a.top - b.top);
  if (withY.length >= 5) { withY[4].el.click(); return 'INDEX5 top=' + withY[4].top; }
  return 'FAIL btns=' + withY.length;
})()
