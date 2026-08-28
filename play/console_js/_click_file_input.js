(() => {
  const inputs = Array.from(document.querySelectorAll('input[type="file"]'));
  const inp = inputs[inputs.length - 1];
  if (!inp) return 'NO_INPUT:' + inputs.length;
  inp.click();
  return 'CLICKED_INPUT accept=' + inp.accept + ' multiple=' + inp.multiple;
})()
