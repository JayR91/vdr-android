(() => {
  const want = 'Save';
  const btns = Array.from(document.querySelectorAll('button, [role=button], a'));
  const el = btns.find(b => (b.innerText||'').trim() === want || (b.innerText||'').replace(/\s+/g,' ').trim() === want);
  if (!el) {
    // text walker
    const tw = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = tw.nextNode())) {
      if ((node.textContent||'').trim() !== want) continue;
      let el2 = node.parentElement;
      for (let i=0;i<6&&el2;i++){ if (el2.tagName==='BUTTON'||el2.getAttribute('role')==='button'){ el2.click(); return 'SAVE_UP'; } el2=el2.parentElement; }
      node.parentElement.click(); return 'SAVE_PARENT';
    }
    return 'NO_SAVE';
  }
  el.click(); return 'SAVE_BTN';
})()
