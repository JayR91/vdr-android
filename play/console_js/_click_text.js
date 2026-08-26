(() => {
  const want = 'No';
  const tw = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = tw.nextNode())) {
    if ((node.textContent || '').trim() !== want) continue;
    let el = node.parentElement;
    for (let i = 0; i < 8 && el; i++) {
      const role = el.getAttribute && el.getAttribute('role');
      if (el.tagName === 'BUTTON' || el.tagName === 'A' || el.tagName === 'LABEL' || el.tagName === 'MAT-RADIO-BUTTON' || role === 'button' || role === 'radio' || el.onclick) {
        el.click();
        return 'CLICKED_UP:' + el.tagName + ':' + (el.className||'').toString().slice(0,40);
      }
      el = el.parentElement;
    }
    node.parentElement && node.parentElement.click();
    return 'CLICKED_PARENT';
  }
  // fallback: mat-radio-button with value
  const mats = Array.from(document.querySelectorAll('mat-radio-button, [role=radio], input[type=radio]'));
  return 'FAIL mats=' + mats.length + ' sample=' + mats.slice(0,5).map(m => (m.innerText||m.value||m.getAttribute('aria-label')||'').slice(0,30)).join('|');
})()
