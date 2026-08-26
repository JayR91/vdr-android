(() => {
  const anchors = Array.from(document.querySelectorAll('a[href]'));
  const a = anchors.find(x => /billdesk|pa-cb|aggregator|verification/i.test(x.href) || ((x.innerText||'').includes('Learn more') && x.querySelector('[aria-hidden]') ));
  // Prefer external learn more near the verification banner
  const all = anchors.filter(x => (x.innerText||'').replace(/\s+/g,' ').includes('Learn more'));
  return JSON.stringify({
    billish: anchors.filter(x=>/bill|verify|pa-cb|merchant/i.test(x.href)).map(x=>x.href),
    learnMores: all.map(x=>({href:x.href, text:x.innerText.trim().slice(0,60)}))
  }, null, 2);
})()
