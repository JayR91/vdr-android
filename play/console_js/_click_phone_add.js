(() => {
  const label = Array.from(document.querySelectorAll('*')).find(e => (e.innerText||'').trim() === 'Phone screenshots *');
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
  // icon=0, feature=1, phone=2 typically
  if (withY.length >= 3) { withY[2].el.click(); return 'INDEX2 top=' + withY[2].top + ' all=' + withY.map(x=>x.top).join(','); }
  return 'FAIL btns=' + withY.length;
})()
