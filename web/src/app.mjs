import { newDocument, row, colorHex, colorInt, decodeDocument, tableData, encodeTable, noteText, importTable, tableCSV, download, saveRecovery, restoreRecovery, loadLibrary, saveToLibrary, deleteFromLibrary } from './documents.mjs';
import { Graph } from './plot.mjs';

const $ = selector => document.querySelector(selector);
const element = (tag, className, text) => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
};
const button = (text, action, className = '') => {
  const node = element('button', className, text);
  node.type = 'button'; node.addEventListener('click', action);
  return node;
};
let toastTimer;
function toast(message) {
  $('#toast').textContent = message; $('#toast').hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { $('#toast').hidden = true; }, 5500);
}
function report(error) { toast(error.message || String(error)); }

let doc;
try { doc = restoreRecovery() || newDocument(); }
catch (error) { doc = newDocument(); report(error); }
let activeId = doc.activeId ?? doc.expressions[0]?.id;
let refs = new Map(), undo = [], redo = [], lastEdit = { key: null, time: 0 };
let persistTimer, calculationTimer, workerTimer, worker, busy = false, queued = false, requestId = 0;
let lastResults = [], storageWarning = false, playing = new Map(), lastAnimation = 0, animationFrame;
const graph = new Graph($('#plot'), bounds => {
  doc[doc.is3D ? 'viewBounds3D' : 'viewBounds'] = bounds;
  persist(); scheduleCalculation();
}, error => {
  $('#graph-error').textContent = `Graph: ${error.message || error}`; $('#graph-error').hidden = false;
});

function checkpoint(key = '') {
  const now = Date.now();
  if (!key || key !== lastEdit.key || now - lastEdit.time > 700) {
    undo.push(JSON.stringify(doc));
    if (undo.length > 100) undo.shift();
  }
  redo = []; lastEdit = { key, time: now }; updateHistory();
}
function updateHistory() { $('#undo').disabled = !undo.length; $('#redo').disabled = !redo.length; }
function persist() {
  doc.updatedAt = Date.now(); doc.activeId = activeId;
  $('#save-state').textContent = 'Saving…';
  clearTimeout(persistTimer);
  persistTimer = setTimeout(() => {
    try { saveRecovery(doc); $('#save-state').textContent = 'Saved on this device'; storageWarning = false; }
    catch { $('#save-state').textContent = 'Not saved'; if (!storageWarning) toast('Browser storage is unavailable or full. Export your graph from the menu to keep it.'); storageWarning = true; }
  }, 350);
}
function changed({ render = false, reset = false } = {}) {
  persist();
  if (reset) graph.reset(doc);
  if (render) renderRows();
  syncControls(); scheduleCalculation();
}
function replaceDocument(next) {
  stopAnimation();
  doc = next; activeId = next.activeId ?? next.expressions[0]?.id;
  graph.reset(doc); changed({ render: true });
}
function history(direction) {
  const from = direction < 0 ? undo : redo, to = direction < 0 ? redo : undo;
  if (!from.length) return;
  to.push(JSON.stringify(doc));
  const next = decodeDocument(from.pop());
  lastEdit = { key: null, time: 0 }; replaceDocument(next); updateHistory();
}
function selectRow(id) {
  activeId = id;
  for (const [rowId, ref] of refs) ref.container.classList.toggle('active', rowId === id);
}
function addRow(text = '') {
  checkpoint();
  if (doc.expressions.length >= 2000) { toast('A graph can contain at most 2,000 expressions.'); return; }
  const item = row(text, doc.expressions.length);
  while (doc.expressions.some(r => r.id === item.id)) item.id++;
  doc.expressions.push(item); activeId = item.id;
  changed({ render: true });
  const input = refs.get(item.id)?.input;
  input?.focus(); refs.get(item.id)?.container.scrollIntoView({ block: 'nearest' });
}
function editRow(item, value, key = `row-${item.id}`) {
  checkpoint(key);
  const previousName = item.text.match(/^\s*([a-zA-Z][a-zA-Z0-9_]*)\s*=/)?.[1];
  if (previousName) { delete doc.parameters[previousName]; playing.delete(previousName); }
  item.text = value; changed();
}

function renderRows() {
  const host = $('#rows'); host.replaceChildren(); refs = new Map();
  doc.expressions.forEach((item, index) => {
    const container = element('article', 'expression-row');
    container.classList.toggle('active', item.id === activeId);
    container.style.setProperty('--row-color', colorHex(item.color));
    const aside = element('div', 'row-aside');
    aside.append(element('span', 'row-number', index + 1));
    const visibility = button('', () => {
      checkpoint(); item.hidden = !item.hidden;
      visibility.classList.toggle('off', item.hidden);
      visibility.setAttribute('aria-pressed', String(!item.hidden)); changed();
    }, 'visibility');
    visibility.classList.toggle('off', item.hidden);
    visibility.setAttribute('aria-label', `Show expression ${index + 1}`); visibility.setAttribute('aria-pressed', String(!item.hidden));
    const color = element('input', 'color-input'); color.type = 'color'; color.value = colorHex(item.color); color.setAttribute('aria-label', `Color for expression ${index + 1}`);
    color.addEventListener('input', () => { checkpoint(`color-${item.id}`); item.color = colorInt(color.value); container.style.setProperty('--row-color', color.value); changed(); });
    aside.append(visibility, color);
    const content = element('div', 'row-content');
    const remove = button('×', () => {
      checkpoint();
      const name = item.text.match(/^\s*([A-Za-z][A-Za-z0-9_]*)\s*=/)?.[1];
      if (name) { delete doc.parameters[name]; playing.delete(name); }
      doc.expressions = doc.expressions.filter(r => r.id !== item.id);
      if (!doc.expressions.length) doc.expressions.push(row(''));
      if (activeId === item.id) activeId = doc.expressions[0].id;
      changed({ render: true });
    }, 'delete-row');
    remove.setAttribute('aria-label', `Delete expression ${index + 1}`);
    const result = element('div', 'result'), preview = element('div', 'math-preview'), sliderHost = element('div');
    const ref = { container, content, result, preview, sliderHost, input: null };
    refs.set(item.id, ref);
    const table = tableData(item.text), note = noteText(item.text);
    if (table) renderTable(item, table, ref);
    else if (note != null) {
      const input = element('textarea', 'note-input'); input.value = note; input.setAttribute('aria-label', `Note ${index + 1}`);
      input.addEventListener('input', () => editRow(item, `note(${JSON.stringify(input.value)})`));
      input.addEventListener('focus', () => selectRow(item.id));
      content.append(input); ref.input = input;
    } else {
      if (/^(image|inference|folder)\(/.test(item.text.trim())) content.append(element('div', 'unsupported', 'This Android worksheet item is preserved in exports. Its editor is not available on the web.'));
      const input = element('textarea', 'math-input'); input.value = item.text; input.rows = 1;
      input.placeholder = index === 0 ? 'Try y = sin(x), or 1/3 + 1/6' : 'Enter an expression…';
      input.spellcheck = false; input.autocomplete = 'off'; input.maxLength = 16000;
      input.setAttribute('aria-label', `Expression ${index + 1}`);
      input.setAttribute('autocapitalize', 'off');
      input.addEventListener('focus', () => selectRow(item.id));
      input.addEventListener('input', () => editRow(item, input.value));
      input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); addRow(); }
        if (event.key === 'Escape') input.blur();
      });
      content.append(input, preview, result, sliderHost); ref.input = input;
    }
    container.append(aside, content, remove); host.append(container);
  });
  renderResults(lastResults);
}

function renderTable(item, table, ref, page = 0) {
  ref.content.replaceChildren();
  const scroll = element('div', 'table-scroll'), grid = element('table', 'data-table'), header = element('tr');
  const columns = [table.x, table.y, ...(table.options.extraColumns || []).map(c => c.values)];
  const headers = [table.options.xHeader || 'x', table.options.yHeader || 'y', ...(table.options.extraColumns || []).map(c => c.header)];
  const write = key => { checkpoint(key); item.text = encodeTable(table); changed(); };
  headers.forEach((name, column) => {
    const th = element('th'), input = element('input'); input.value = name; input.setAttribute('aria-label', `Table column ${column + 1} name`);
    input.addEventListener('change', () => {
      if (column < 2) table.options[column === 0 ? 'xHeader' : 'yHeader'] = input.value;
      else table.options.extraColumns[column - 2].header = input.value;
      write();
    });
    th.append(input); header.append(th);
  });
  grid.append(header);
  const count = Math.max(1, ...columns.map(c => c.length));
  const start = page * 50, end = Math.min(count, start + 50);
  for (let i = start; i < end; i++) {
    const tr = element('tr');
    columns.forEach((values, column) => {
      const td = element('td'), input = element('input'); input.value = values[i] || '';
      input.setAttribute('aria-label', `${headers[column]}, row ${i + 1}`); input.spellcheck = false;
      input.addEventListener('focus', () => selectRow(item.id));
      input.addEventListener('input', () => {
        while (values.length <= i) values.push('');
        values[i] = input.value; write(`cell-${item.id}-${i}-${column}`);
      });
      td.append(input); tr.append(td);
    });
    grid.append(tr);
  }
  scroll.append(grid);
  const tools = element('div', 'row-tools');
  tools.append(button('＋ Data row', () => {
    checkpoint(); columns.forEach(c => { while (c.length < count) c.push(''); c.push(''); });
    item.text = encodeTable(table); renderTable(item, table, ref, Math.floor(count / 50)); changed();
  }));
  if (page > 0) tools.append(button('← Previous', () => renderTable(item, table, ref, page - 1)));
  if (end < count) tools.append(button('Next →', () => renderTable(item, table, ref, page + 1)));
  tools.append(button('CSV ↓', () => download(tableCSV(table), `${safeTitle()}-table.csv`, 'text/csv')));
  const regression = element('select'); regression.setAttribute('aria-label', 'Regression model');
  for (const name of ['', 'Linear', 'Quadratic', 'Cubic', 'Quartic', 'Exponential', 'Logarithmic', 'Power']) {
    const option = element('option', '', name ? `${name} fit` : 'No regression'); option.value = name; regression.append(option);
  }
  if (table.options.regression && ![...regression.options].some(o => o.value === table.options.regression)) {
    const option = element('option', '', `${table.options.regression} (Android only)`); option.value = table.options.regression; regression.append(option);
  }
  regression.value = table.options.regression || '';
  regression.addEventListener('change', () => { table.options.regression = regression.value || null; write(); }); tools.append(regression);
  const summary = element('div', 'table-summary'); ref.summary = summary;
  ref.content.append(scroll, tools, summary, ref.result);
  renderResults(lastResults);
}

function tex(node, source, fallback) {
  node.replaceChildren();
  if (source && globalThis.katex) {
    try { katex.render(source, node, { throwOnError: true, trust: false, strict: 'ignore', maxExpand: 1000 }); return; } catch { /* Plain text remains usable for unsupported TeX. */ }
  }
  node.textContent = fallback || source || '';
}
function renderResults(results) {
  for (const result of results) {
    const ref = refs.get(result.id); if (!ref) continue;
    ref.result.classList.toggle('error', Boolean(result.error));
    if (result.error) ref.result.textContent = result.error;
    else {
      tex(ref.result, result.tex, result.exact || result.approximate);
      if (result.approximate && result.exact && result.approximate !== result.exact) ref.result.append(element('div', 'approximate', `≈ ${result.approximate}`));
    }
    tex(ref.preview, result.preview);
    if (ref.summary) ref.summary.textContent = `${result.summary || ''}${result.fit ? `\ny = ${result.fit}` : ''}`;
    if (result.slider) renderSlider(ref, result.slider);
    else { ref.sliderHost.replaceChildren(); delete ref.slider; }
  }
}
function renderSlider(ref, slider) {
  const name = slider.name;
  const configuration = doc.sliderSettings[name] || { min: Math.min(-10, slider.value), max: Math.max(10, slider.value), step: .1 };
  const step = configuration.step > 0 ? configuration.step : (configuration.max - configuration.min) / 200;
  if (ref.slider?.name === name) {
    ref.slider.input.min = configuration.min; ref.slider.input.max = configuration.max; ref.slider.input.step = step;
    ref.slider.input.value = slider.value; ref.slider.output.textContent = formatNumber(slider.value);
    ref.slider.play.textContent = playing.has(name) ? 'Ⅱ' : '▶'; return;
  }
  ref.sliderHost.replaceChildren();
  const host = element('div', 'slider'), output = element('output', '', formatNumber(slider.value));
  const input = element('input'); input.type = 'range'; input.min = configuration.min; input.max = configuration.max; input.step = step; input.value = slider.value;
  input.setAttribute('aria-label', `Value of ${name}`);
  input.addEventListener('input', () => {
    checkpoint(`slider-${name}`); doc.parameters[name] = Number(input.value);
    output.textContent = formatNumber(Number(input.value)); changed();
  });
  const play = button(playing.has(name) ? 'Ⅱ' : '▶', () => {
    if (playing.has(name)) playing.delete(name);
    else { checkpoint(); playing.set(name, 1); lastAnimation = performance.now(); if (!animationFrame) animationFrame = requestAnimationFrame(animate); }
    play.textContent = playing.has(name) ? 'Ⅱ' : '▶'; play.setAttribute('aria-label', `${playing.has(name) ? 'Pause' : 'Animate'} ${name}`);
  });
  play.setAttribute('aria-label', `Animate ${name}`);
  host.append(play, input, output);
  const bounds = element('div', 'slider-bounds');
  ['min', 'max', 'step'].forEach(key => {
    const field = element('input'); field.type = 'number'; field.step = 'any'; field.value = configuration[key] ?? .1;
    field.setAttribute('aria-label', `${name} slider ${key}`); field.title = key;
    field.addEventListener('change', () => {
      const next = { ...configuration, ...doc.sliderSettings[name], [key]: Number(field.value) };
      if (!Number.isFinite(next[key]) || next.min >= next.max || next.step < 0 || field.value === '') { toast('Use a finite minimum below the maximum, and a nonnegative step.'); field.value = configuration[key]; return; }
      checkpoint(); doc.sliderSettings[name] = next;
      doc.parameters[name] = Math.max(next.min, Math.min(next.max, Number(input.value)));
      ref.sliderHost.replaceChildren(); delete ref.slider; changed();
    });
    bounds.append(field);
  });
  ref.sliderHost.append(host, bounds); ref.slider = { name, input, output, play };
}
function formatNumber(value) { return Number(value.toPrecision(6)).toString(); }
function animate(now) {
  animationFrame = null;
  if (!playing.size) return;
  const elapsed = Math.min(.25, (now - lastAnimation) / 1000);
  if (!busy && now - lastAnimation >= 80) {
    for (const [name, direction] of playing) {
      const result = lastResults.find(r => r.slider?.name === name)?.slider;
      if (!result) { playing.delete(name); continue; }
      const settings = doc.sliderSettings[name] || { min: Math.min(-10, result.value), max: Math.max(10, result.value), step: .1 };
      let value = (doc.parameters[name] ?? result.value) + direction * elapsed * (settings.max - settings.min) / 5;
      if (value >= settings.max || value <= settings.min) { value = Math.max(settings.min, Math.min(settings.max, value)); playing.set(name, -direction); }
      doc.parameters[name] = value;
    }
    lastAnimation = now; persist(); scheduleCalculation(0);
  }
  animationFrame = requestAnimationFrame(animate);
}
function stopAnimation() {
  playing.clear(); if (animationFrame) cancelAnimationFrame(animationFrame); animationFrame = null;
}

function scheduleCalculation(delay = 180) {
  requestId++; clearTimeout(calculationTimer);
  calculationTimer = setTimeout(calculate, delay);
}
function makeWorker() {
  worker = new Worker(new URL('./engine-worker.js', import.meta.url));
  worker.onmessage = ({ data }) => {
    clearTimeout(workerTimer); busy = false;
    if (data.id === requestId) {
      if (data.error) { $('#engine-state').textContent = 'Calculation error'; toast(data.error); }
      else {
        lastResults = data.results; renderResults(data.results);
        $('#engine-state').textContent = data.results.some(r => r.error) ? 'Check the marked expressions' : 'Browser math engine · Ready';
        $('#graph-error').hidden = true; graph.render(data.traces, structuredClone(doc));
      }
    }
    if (queued) { queued = false; calculate(); }
  };
  worker.onerror = () => {
    clearTimeout(workerTimer); worker.terminate(); worker = null; busy = false; queued = false;
    stopAnimation();
    $('#engine-state').textContent = 'Math engine unavailable';
    toast('The math engine could not load. Reload the deployed website; for local use, assemble web/_site first.');
  };
}
function calculate() {
  if (busy) { queued = true; return; }
  try {
    if (!worker) makeWorker();
    busy = true; $('#engine-state').textContent = 'Calculating…';
    worker.postMessage({ id: requestId, document: doc, bounds: graph.bounds });
    workerTimer = setTimeout(() => {
      worker.terminate(); worker = null; busy = false;
      stopAnimation();
      $('#engine-state').textContent = 'Calculation stopped';
      toast('Calculation exceeded 12 seconds. Simplify the expression or use a smaller graph range.');
      if (queued) { queued = false; calculate(); }
    }, 12_000);
  } catch (error) { busy = false; report(error); }
}

function syncControls() {
  $('#title').value = doc.title;
  $('#mode-2d').classList.toggle('selected', !doc.is3D); $('#mode-3d').classList.toggle('selected', doc.is3D);
  $('#mode-2d').setAttribute('aria-pressed', String(!doc.is3D)); $('#mode-3d').setAttribute('aria-pressed', String(doc.is3D));
  $('#angle').textContent = doc.degreeMode ? 'DEG' : 'RAD';
  $('#graph-hint').textContent = doc.is3D ? 'Drag to rotate · Scroll to zoom · Shift-drag to pan' : 'Drag to pan · Scroll to zoom · Click a curve to inspect';
}
function dimension(is3D) { if (doc.is3D === is3D) return; checkpoint(); doc.is3D = is3D; changed({ reset: true }); }
function openDialog(title, content) {
  $('#dialog-title').textContent = title; $('#dialog-body').replaceChildren(content);
  if (!$('#dialog').open) $('#dialog').showModal();
}
const closeDialog = () => $('#dialog').close();
function safeTitle() { return doc.title.replace(/[^a-zA-Z0-9_-]/g, '_') || 'Demos'; }
function exportDocument() { download(JSON.stringify(doc, null, 2), `${safeTitle()}.json`); }
function saveGraph() {
  try { saveToLibrary(doc); saveRecovery(doc); toast('Graph saved in this browser’s library.'); }
  catch (error) { report(error); }
}
function openLibrary() {
  try {
    const host = element('div'), graphs = loadLibrary();
    if (!graphs.length) host.append(element('p', '', 'Your library is empty. Save a graph to keep a named copy here.'));
    for (const saved of graphs) {
      const item = element('div', 'library-item');
      const open = button(saved.title, () => { checkpoint(); replaceDocument(saved); closeDialog(); }, 'name');
      open.append(element('small', '', new Date(saved.updatedAt).toLocaleString()));
      const remove = button('Delete', () => {
        const confirmation = element('div'); confirmation.append(element('p', '', `Delete “${saved.title}” from this browser’s library?`));
        const actions = element('div', 'dialog-actions');
        actions.append(button('Cancel', openLibrary), button('Delete', () => { try { deleteFromLibrary(saved.id); openLibrary(); } catch (error) { report(error); } }, 'danger'));
        confirmation.append(actions); openDialog('Delete saved graph', confirmation);
      }, 'danger');
      item.append(open, remove); host.append(item);
    }
    host.append(element('p', '', 'Graphs stay on this device and browser. Export JSON to back them up or transfer them to Android.'));
    openDialog('Graph library', host);
  } catch (error) { report(error); }
}
function openMenu() {
  const host = element('div'), grid = element('div', 'menu-grid');
  const action = (label, fn) => grid.append(button(label, () => { closeDialog(); fn(); }));
  action('＋ New graph', () => { checkpoint(); replaceDocument(newDocument()); });
  action('Save to library', saveGraph);
  action('Open graph / import table', () => $('#file-input').click());
  action('Export graph document (.json)', exportDocument);
  action('Download graph PNG', () => graph.exportPNG(doc.title).catch(report));
  action('Print / save as PDF', () => window.print());
  action('Examples & help', openHelp);
  action('Graph settings', openSettings);
  host.append(grid, element('p', '', 'Your worksheet is automatically recovered in this browser. Use Save for named library copies and Export for portable files.'));
  openDialog('Graph menu', host);
}
function openSettings() {
  const form = element('form'), grid = element('div', 'settings-grid'), fields = {};
  for (const axis of ['x', 'y', ...(doc.is3D ? ['z'] : []), 't', ...(doc.is3D ? ['u', 'v'] : [])]) {
    for (const edge of ['Min', 'Max']) {
      const key = `${axis}${edge}`, label = element('label', '', `${axis} ${edge === 'Min' ? 'minimum' : 'maximum'}`), input = element('input');
      input.type = 'number'; input.step = 'any'; input.required = true; input.value = graph.bounds[key]; fields[key] = input;
      label.append(input); grid.append(label);
    }
  }
  const checks = element('div', 'settings-checks'), checkboxes = {};
  const gridKey = doc.is3D ? 'showGrid3D' : 'showGrid', axesKey = doc.is3D ? 'showAxes3D' : 'showAxes';
  for (const [key, title] of [[gridKey, 'Grid'], [axesKey, 'Axes'], ['complexMode', 'Complex numbers']]) {
    const label = element('label'), input = element('input'); input.type = 'checkbox'; input.checked = doc[key]; checkboxes[key] = input;
    label.append(input, document.createTextNode(title)); checks.append(label);
  }
  const label = element('label', '', 'Result precision (2–15 digits) '), precision = element('input'); precision.type = 'number'; precision.min = 2; precision.max = 15; precision.step = 1; precision.value = doc.precisionDigits; precision.required = true; label.append(precision);
  const actions = element('div', 'dialog-actions'), submit = button('Apply settings', () => {}, 'primary'); submit.type = 'submit';
  actions.append(button('Cancel', closeDialog), submit);
  form.append(grid, checks, label, actions);
  form.addEventListener('submit', event => {
    event.preventDefault();
    for (const axis of ['x', 'y', 'z', 't', 'u', 'v']) {
      if (fields[`${axis}Min`] && Number(fields[`${axis}Min`].value) >= Number(fields[`${axis}Max`].value)) { toast(`${axis} minimum must be below its maximum.`); return; }
    }
    const bounds = { ...graph.bounds };
    for (const [key, input] of Object.entries(fields)) { if (!Number.isFinite(Number(input.value))) { toast('Graph bounds must be finite numbers.'); return; } bounds[key] = Number(input.value); }
    checkpoint();
    doc[doc.is3D ? 'viewBounds3D' : 'viewBounds'] = bounds;
    for (const [key, input] of Object.entries(checkboxes)) doc[key] = input.checked;
    doc.precisionDigits = Number(precision.value); closeDialog(); changed({ reset: true });
  });
  openDialog(doc.is3D ? '3D graph settings' : 'Graph settings', form);
}
function openHelp() {
  const host = element('div');
  host.append(element('p', '', 'Enter expressions as plain text or paste LaTeX fractions and roots. Press Enter for a new row. Use the colored circle to hide a graph and the swatch below it to change its color. Examples replace the current worksheet; Undo restores it.'));
  host.append(element('h3', '', 'Try an example'));
  const examples = [
    ['Waves & a slider', ['a=2', 'y=a*sin(x)', 'y=cos(x)'], false],
    ['Circle & inequality', ['x^2+y^2=25', 'y>x/2'], false],
    ['Symbolic calculus', ['diff(x^3+sin(x),x)', 'integrate(x^2,x)', 'solve(x^2=4,x)', 'factor(x^2-1)', '1/3+1/6'], false],
    ['Polar rose', ['r=4*cos(3*theta)'], false],
    ['Parametric curve', ['(3*cos(t),2*sin(t))'], false],
    ['3D surfaces', ['z=sin(x)*cos(y)', 'z=(x^2+y^2)/12-3'], true],
    ['3D sphere', ['x^2+y^2+z^2=25'], true],
    ['Table & linear regression', ['table([0,1,2,3,4],[1,2.9,5.2,6.8,9.1],{"regression":"Linear"})'], false],
  ];
  for (const [label, expressions, is3D] of examples) host.append(button(label, () => {
    checkpoint(); const next = newDocument(); next.title = label; next.is3D = is3D;
    next.expressions = expressions.map((text, index) => row(text, index)); replaceDocument(next); closeDialog();
  }, 'example'));
  host.append(element('h3', '', 'Functions & notation'), element('p', '', 'Use + − * / ^, sqrt, abs, sin/cos/tan, inverse and hyperbolic trig, log (base 10), ln (natural log), exp, factorial, mean, median, std, matrices, and complex numbers. Define a=2 or f(x)=x^2. Calculus commands: diff(f,x), integrate(f,x), integrate(f,x,a,b), limit(f,x,a), solve(equation,x), factor(f), expand(f), simplify(f). Polar graphs use theta; parametric curves use t; parametric surfaces use (x(u,v),y(u,v),z(u,v)).'));
  host.append(element('h3', '', 'Browser and Android compatibility'), element('p', '', 'This website uses math.js and Nerdamer for calculation and Plotly for graphing. Android uses native Giac, so advanced commands, exact forms, and numerical edge cases can differ. Images, folders, statistical inference editors, logistic/sinusoidal fits, and 3D inequalities are not implemented here. Imported Android items are preserved in exported JSON. CSV/TSV and JSON tables can be imported through the menu. Graphs are sampled approximations; zoom in to inspect fine features.'));
  const links = element('p');
  for (const [name, href] of [['math.js', 'https://mathjs.org/'], ['Nerdamer', 'https://nerdamer.com/'], ['Plotly', 'https://plotly.com/javascript/'], ['KaTeX', 'https://katex.org/']]) {
    const link = element('a', '', name); link.href = href; link.target = '_blank'; link.rel = 'noopener noreferrer'; links.append(link, document.createTextNode(' · '));
  }
  host.append(links); openDialog('Help & examples', host);
}

function insertText(text) {
  const item = doc.expressions.find(item => item.id === activeId), ref = refs.get(activeId);
  if (!item || !ref?.input || tableData(item.text) || noteText(item.text) != null) { addRow(text === 'BACKSPACE' ? '' : text); return; }
  const input = ref.input, start = input.selectionStart, end = input.selectionEnd;
  const from = text === 'BACKSPACE' && start === end ? Math.max(0, start - 1) : start;
  const insertion = text === 'BACKSPACE' ? '' : text;
  const source = input.value.slice(0, from) + insertion + input.value.slice(end);
  editRow(item, source); input.value = source; input.focus();
  const cursor = from + insertion.length - (insertion.endsWith('()') ? 1 : 0);
  input.setSelectionRange(cursor, cursor);
}
function renderKeypad(tab = '123') {
  const host = $('#keypad'); host.replaceChildren();
  const tabs = element('div', 'keypad-tabs');
  ['123', 'Functions', 'ABC'].forEach(name => tabs.append(button(name, () => renderKeypad(name))));
  const keys = element('div', 'keys');
  const values = tab === '123' ? ['x', 'y', '(', ')', '^', 'BACKSPACE', '7', '8', '9', '/', 'sqrt()', 'pi', '4', '5', '6', '*', 'abs()', 'e', '1', '2', '3', '-', 'sin()', 'cos()', '0', '.', '=', '+', 'tan()', 'ENTER'] : tab === 'Functions' ? ['sin()', 'cos()', 'tan()', 'asin()', 'acos()', 'atan()', 'ln()', 'log()', 'exp()', 'sqrt()', 'abs()', '!', 'diff()', 'integrate()', 'solve()', 'factor()', 'expand()', 'simplify()', 'mean()', 'median()', 'std()', ',', '[', ']'] : ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'theta', ',', 'BACKSPACE', 'ENTER'];
  for (const value of values) {
    const key = button(value === 'BACKSPACE' ? '⌫' : value === 'ENTER' ? '↵' : value, () => value === 'ENTER' ? addRow() : insertText(value), value === 'ENTER' ? 'enter' : /^[+*/^=-]$/.test(value) ? 'operation' : '');
    key.addEventListener('pointerdown', event => event.preventDefault()); keys.append(key);
  }
  host.append(tabs, keys);
}

$('#add').onclick = () => addRow();
$('#add-table').onclick = () => addRow('table([0,1,2],[0,1,4])');
$('#add-note').onclick = () => addRow('note("")');
$('#undo').onclick = () => history(-1); $('#redo').onclick = () => history(1);
$('#mode-2d').onclick = () => dimension(false); $('#mode-3d').onclick = () => dimension(true);
$('#angle').onclick = () => { checkpoint(); doc.degreeMode = !doc.degreeMode; changed(); };
$('#home').onclick = () => { checkpoint(); doc[doc.is3D ? 'viewBounds3D' : 'viewBounds'] = {}; changed({ reset: true }); };
$('#title').addEventListener('input', event => { checkpoint('title'); doc.title = event.target.value; persist(); });
$('#save').onclick = saveGraph; $('#library').onclick = openLibrary;
$('#menu').onclick = openMenu; $('#help').onclick = openHelp; $('#settings').onclick = openSettings;
$('#close-dialog').onclick = closeDialog;
$('#dialog').addEventListener('click', event => { if (event.target === $('#dialog')) { const rect = $('#dialog').getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) closeDialog(); } });
$('#keyboard-toggle').onclick = () => { const visible = $('#keypad').hidden; $('#keypad').hidden = !visible; $('#keyboard-toggle').setAttribute('aria-pressed', String(visible)); if (visible) renderKeypad(); };
document.querySelectorAll('button[data-layout]').forEach(node => node.addEventListener('click', () => {
  $('#workspace').dataset.layout = node.dataset.layout;
  document.querySelectorAll('button[data-layout]').forEach(b => b.classList.toggle('selected', b === node));
}));
$('#file-input').addEventListener('change', async event => {
  const file = event.target.files[0]; event.target.value = ''; if (!file) return;
  try {
    if (file.size > 5_000_000) throw Error('Files must be smaller than 5 MB.');
    const text = await file.text(), extension = file.name.split('.').pop().toLowerCase();
    if (extension === 'json' && /"formatVersion"\s*:/.test(text)) {
      const next = decodeDocument(text); checkpoint(); replaceDocument(next); toast('Graph imported.');
    } else {
      const table = importTable(text, extension); addRow(encodeTable(table)); toast('Table imported.');
    }
  } catch (error) { report(error); }
});
document.addEventListener('keydown', event => {
  if (!(event.ctrlKey || event.metaKey) || $('#dialog').open) return;
  if (event.key.toLowerCase() === 'z') { event.preventDefault(); history(event.shiftKey ? 1 : -1); }
  if (event.key.toLowerCase() === 'y') { event.preventDefault(); history(1); }
  if (event.key.toLowerCase() === 's') { event.preventDefault(); saveGraph(); }
});
window.addEventListener('pagehide', () => { try { saveRecovery(doc); } catch { /* Storage errors were already shown while editing. */ } });
document.addEventListener('visibilitychange', () => { if (document.hidden) { stopAnimation(); for (const ref of refs.values()) if (ref.slider) ref.slider.play.textContent = '▶'; } });

graph.reset(doc); syncControls(); renderRows(); updateHistory();
if (globalThis.Plotly && globalThis.katex) { graph.render([], doc); scheduleCalculation(0); }
else { $('#engine-state').textContent = 'Website assets missing'; toast('Website assets are missing. Open the deployed GitHub Pages website, or assemble web/_site for local use.'); }
