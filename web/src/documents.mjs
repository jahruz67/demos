export const COLORS = ['#c74440', '#2d70da', '#388c46', '#6042a6', '#fa7e19', '#111111'];
export const colorHex = value => typeof value === 'number' ? `#${(value >>> 0).toString(16).padStart(8, '0').slice(-6)}` : COLORS.includes(value) || /^#[\da-f]{6}$/i.test(value || '') ? value : COLORS[0];
export const colorInt = value => (0xff000000 | Number.parseInt(colorHex(value).slice(1), 16));
export const row = (text = '', index = 0) => ({ id: Date.now() + index, text, color: colorInt(COLORS[index % COLORS.length]), hidden: false });

export function newDocument() {
  return {
    formatVersion: 1, id: crypto.randomUUID(), title: 'Untitled Graph',
    expressions: [row('')], is3D: false, degreeMode: false, showGrid: true, showAxes: true,
    showGrid3D: true, showAxes3D: true, precisionDigits: 12, complexMode: false,
    parameters: {}, viewBounds: {}, viewBounds3D: {}, sliderSettings: {}, updatedAt: Date.now(),
  };
}

export function decodeDocument(source) {
  if (source.length > 5_000_000) throw Error('This graph file is too large (maximum 5 MB).');
  const value = JSON.parse(source);
  if (!value || value.formatVersion !== 1 || !Array.isArray(value.expressions)) throw Error('Choose a Demos graph document (format version 1).');
  if (value.expressions.length > 2000) throw Error('A graph can contain at most 2,000 expressions.');
  const ids = new Set();
  for (const item of value.expressions) {
    if (!item || !Number.isSafeInteger(item.id) || ids.has(item.id) || typeof item.text !== 'string' || item.text.length > 100_000) throw Error('The graph contains an invalid or duplicate expression.');
    ids.add(item.id);
  }
  if (typeof value.title !== 'string' || value.title.length > 200) throw Error('The graph title is invalid.');
  for (const key of ['is3D', 'degreeMode', 'showGrid', 'showAxes', 'showGrid3D', 'showAxes3D', 'complexMode']) {
    if (value[key] != null && typeof value[key] !== 'boolean') throw Error(`Invalid ${key}.`);
  }
  for (const name of ['parameters', 'viewBounds', 'viewBounds3D']) {
    if (value[name] == null) continue;
    if (typeof value[name] !== 'object' || Array.isArray(value[name]) || Object.values(value[name]).some(n => typeof n !== 'number' || !Number.isFinite(n))) throw Error(`Invalid ${name}.`);
  }
  if (value.sliderSettings != null && (typeof value.sliderSettings !== 'object' || Array.isArray(value.sliderSettings))) throw Error('Invalid slider settings.');
  for (const settings of Object.values(value.sliderSettings || {})) {
    if (!settings || !Number.isFinite(settings.min ?? -10) || !Number.isFinite(settings.max ?? 10) || (settings.min ?? -10) >= (settings.max ?? 10) || !Number.isFinite(settings.step ?? 0) || (settings.step ?? 0) < 0) throw Error('Invalid slider range.');
  }
  const result = { ...newDocument(), ...value, id: typeof value.id === 'string' ? value.id : crypto.randomUUID() };
  result.parameters ??= {}; result.viewBounds ??= {}; result.sliderSettings ??= {};
  result.viewBounds3D = value.viewBounds3D ?? (value.is3D ? value.viewBounds : {}) ?? {};
  result.precisionDigits = Math.max(2, Math.min(15, Number(result.precisionDigits) || 12));
  return result;
}

export function splitArguments(source) {
  const result = [];
  let depth = 0, quoted = false, escaped = false, start = 0;
  for (let i = 0; i < source.length; i++) {
    const c = source[i];
    if (quoted) {
      if (escaped) escaped = false;
      else if (c === '\\') escaped = true;
      else if (c === '"') quoted = false;
    } else if (c === '"') quoted = true;
    else if ('([{'.includes(c)) depth++;
    else if (')]}'.includes(c)) depth--;
    else if (c === ',' && depth === 0) { result.push(source.slice(start, i).trim()); start = i + 1; }
  }
  result.push(source.slice(start).trim());
  return result;
}

export function tableData(source) {
  if (!source.trim().startsWith('table(') || !source.trim().endsWith(')')) return null;
  try {
    const parts = splitArguments(source.trim().slice(6, -1));
    const list = text => {
      if (!text?.startsWith('[') || !text.endsWith(']')) throw Error('Invalid table');
      return text.slice(1, -1).trim() ? splitArguments(text.slice(1, -1)).map(cell => cell === 'undef' ? '' : cell.startsWith('"') ? String(JSON.parse(cell)) : cell) : [];
    };
    if (parts.length < 2 || parts.length > 3) return null;
    const options = parts[2] ? JSON.parse(parts[2]) : {};
    if (!options || typeof options !== 'object' || Array.isArray(options)) return null;
    if (options.extraColumns != null && (!Array.isArray(options.extraColumns) || options.extraColumns.some(c => !c || typeof c.header !== 'string' || !Array.isArray(c.values) || c.values.some(v => typeof v !== 'string')))) return null;
    return { x: list(parts[0]), y: list(parts[1]), options };
  } catch { return null; }
}

export function encodeTable(table) {
  const list = values => `[${values.map(v => v === '' ? 'undef' : Number.isFinite(Number(v)) ? String(Number(v)) : JSON.stringify(v)).join(',')}]`;
  return `table(${list(table.x)},${list(table.y)},${JSON.stringify(table.options || {})})`;
}

export function noteText(source) {
  if (!source.startsWith('note(') || !source.endsWith(')')) return null;
  try { const text = JSON.parse(source.slice(5, -1)); return typeof text === 'string' ? text : null; } catch { return null; }
}

export function parseDelimited(source, delimiter = ',') {
  const rows = [];
  let record = [], cell = '', quoted = false;
  for (let i = 0; i < source.length; i++) {
    const c = source[i];
    if (c === '"') {
      if (quoted && source[i + 1] === '"') { cell += '"'; i++; }
      else if (quoted || cell === '') quoted = !quoted;
      else cell += c;
    } else if (!quoted && (c === delimiter || c === '\n' || c === '\r')) {
      record.push(cell); cell = '';
      if (c !== delimiter) {
        if (c === '\r' && source[i + 1] === '\n') i++;
        rows.push(record); record = [];
      }
    } else cell += c;
  }
  if (quoted) throw Error('The table has an unclosed quoted cell.');
  if (cell || record.length) { record.push(cell); rows.push(record); }
  return rows.filter(record => record.some(value => value.trim() !== ''));
}

export function importTable(source, extension) {
  let rows;
  if (extension === 'json') {
    const value = JSON.parse(source);
    if (Array.isArray(value)) rows = value.map(item => Array.isArray(item) ? item : [item.x, item.y]);
    else if (value && Array.isArray(value.x) && Array.isArray(value.y)) return { x: value.x.map(String), y: value.y.map(String), options: value.options || {} };
    else throw Error('A JSON table needs rows of [x,y] values or x and y arrays.');
    rows = rows.map(record => record.map(value => String(value ?? '')));
  } else rows = parseDelimited(source.replace(/^\uFEFF/, ''), extension === 'tsv' ? '\t' : ',');
  if (!rows.length || rows.some(record => record.length < 2)) throw Error('The table needs at least two columns.');
  const hasHeader = rows[0].slice(0, 2).some(v => v !== '' && !Number.isFinite(Number(v)));
  const header = hasHeader ? rows.shift() : ['x', 'y'];
  return { x: rows.map(r => r[0]), y: rows.map(r => r[1]), options: { xHeader: header[0], yHeader: header[1], extraColumns: header.slice(2).map((name, i) => ({ header: name, values: rows.map(r => r[i + 2] || '') })) } };
}

export function tableCSV(table, delimiter = ',') {
  const extra = table.options.extraColumns || [];
  const columns = [table.x, table.y, ...extra.map(c => c.values)];
  const headers = [table.options.xHeader || 'x', table.options.yHeader || 'y', ...extra.map(c => c.header)];
  const escape = value => `"${String(value ?? '').replaceAll('"', '""')}"`;
  const rows = [headers, ...Array.from({ length: Math.max(...columns.map(c => c.length)) }, (_, i) => columns.map(c => c[i] || ''))];
  return rows.map(r => r.map(escape).join(delimiter)).join('\r\n');
}

export function download(contents, name, type = 'application/json') {
  const url = URL.createObjectURL(new Blob([contents], { type }));
  const link = document.createElement('a');
  link.href = url; link.download = name; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

const RECOVERY = 'demos.web.recovery.v1', LIBRARY = 'demos.web.library.v1';
export const saveRecovery = doc => localStorage.setItem(RECOVERY, JSON.stringify(doc));
export const restoreRecovery = () => { const source = localStorage.getItem(RECOVERY); return source ? decodeDocument(source) : null; };
export function loadLibrary() {
  const value = JSON.parse(localStorage.getItem(LIBRARY) || '[]');
  if (!Array.isArray(value)) throw Error('The local graph library is unreadable. Export your current document before clearing browser storage.');
  return value.map(doc => decodeDocument(JSON.stringify(doc)));
}
export function saveToLibrary(doc) {
  const graphs = loadLibrary().filter(item => item.id !== doc.id);
  localStorage.setItem(LIBRARY, JSON.stringify([{ ...doc, updatedAt: Date.now() }, ...graphs]));
}
export function deleteFromLibrary(id) {
  localStorage.setItem(LIBRARY, JSON.stringify(loadLibrary().filter(item => item.id !== id)));
}
