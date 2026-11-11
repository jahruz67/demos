/* Calculations and sampling stay off the UI thread; the UI can terminate slow jobs. */
importScripts('../vendor/math.js', '../vendor/nerdamer.js');
const helpers = import('./documents.mjs');
const m = math.create(math.all);
const forbidden = new Set(['import', 'createUnit', 'reviver', 'evaluate', 'parse', 'resolve', 'compile', 'parser', 'create', 'config', '__proto__', 'constructor', 'prototype']);
let degrees = false;
for (const name of ['sin', 'cos', 'tan', 'sec', 'csc', 'cot']) {
  const original = m[name];
  m.import({ [name]: value => original(degrees ? m.multiply(value, Math.PI / 180) : value) }, { override: true });
}
for (const name of ['asin', 'acos', 'atan', 'asec', 'acsc', 'acot']) {
  const original = m[name];
  m.import({ [name]: value => degrees ? m.multiply(original(value), 180 / Math.PI) : original(value) }, { override: true });
}
const atan2 = m.atan2;
m.import({ atan2: (y, x) => atan2(y, x) * (degrees ? 180 / Math.PI : 1) }, { override: true });
let splitArguments, tableData, noteText, colorHex;

function normalize(source) {
  if (source.length > 16_000) throw Error('Expression is too long (maximum 16,000 characters).');
  let text = source.trim().replaceAll('−', '-').replaceAll('×', '*').replaceAll('·', '*').replaceAll('÷', '/').replaceAll('π', 'pi').replaceAll('θ', 'theta').replaceAll('∞', 'Infinity').replaceAll('≤', '<=').replaceAll('≥', '>=').replaceAll(':=', '=');
  if (text.includes('\\')) {
    // Nerdamer's LaTeX reader handles nested fractions and structured Android input.
    text = text.replace(/\\(?:left|right)/g, '').replace(/\\(?:dfrac|tfrac)/g, '\\frac');
    const rel = relation(text);
    const convert = value => value.includes('\\') || /[{}]/.test(value) ? nerdamer.convertFromLaTeX(value).toString().replace(/\blog\(/g, 'ln(') : value;
    text = rel ? `${convert(rel.left)}${rel.op}${convert(rel.right)}` : convert(text);
  }
  text = text.replace(/\^\{([^{}]+)\}/g, '^($1)').replace(/\bint\s*\(/g, 'integrate(').replace(/\bderivative\s*\(/g, 'diff(');
  if (text.startsWith('∫')) {
    const integral = text.match(/^∫\s*(?:_\{([^{}]+)\}\s*\^\{([^{}]+)\})?\s*(.+)\s*d\s*([a-zA-Z])$/);
    if (!integral) throw Error('Use integrate(expression,x) or integrate(expression,x,lower,upper).');
    text = `integrate(${integral[3]},${integral[4]}${integral[1] == null ? '' : `,${integral[1]},${integral[2]}`})`;
  }
  text = text.replace(/\blog\s*\(/g, 'log10(').replace(/\bln\s*\(/g, 'log(').replace(/\|([^|]+)\|/g, 'abs($1)');
  if (/^solve\(/.test(text) && text.endsWith(')')) {
    const args = splitArguments(text.slice(6, -1)), equation = relation(args[0]);
    if (equation?.op === '=') args[0] = `(${equation.left})-(${equation.right})`;
    text = `solve(${args.join(',')})`;
  }
  return text;
}

function relation(source) {
  let depth = 0;
  for (let i = 0; i < source.length; i++) {
    if ('([{'.includes(source[i])) depth++;
    else if (')]}'.includes(source[i])) depth--;
    else if (depth === 0 && '=<>'.includes(source[i])) {
      const op = source.slice(i, i + 2).endsWith('=') ? source.slice(i, i + 2) : source[i];
      return { left: source.slice(0, i).trim(), right: source.slice(i + op.length).trim(), op };
    }
  }
  return null;
}

function parsed(source, names) {
  // Conventional xy and 2xy inputs are products; declared multiletter names stay intact.
  source = source.replace(/\b[a-zA-Z]{2,}\b/g, name => !names.has(name) && !m[name] && /^[xyztruv]+$/.test(name) ? name.split('').join('*') : name);
  const node = m.parse(source);
  let size = 0;
  node.traverse(child => {
    if (++size > 4096) throw Error('Expression is too complex.');
    if (['AssignmentNode', 'FunctionAssignmentNode', 'BlockNode', 'ObjectNode', 'AccessorNode'].includes(child.type)) throw Error('Use one mathematical expression per row.');
    if (child.isSymbolNode && forbidden.has(child.name)) throw Error('This function is unavailable in the calculator.');
    if (child.isFunctionNode && !child.fn.isSymbolNode) throw Error('Invalid function call.');
  });
  return node;
}

function numeric(node, scope, complex = false) {
  const value = node.evaluate(scope);
  if (!complex && value?.isComplex && Math.abs(value.im) > 1e-12) throw Error('Enable complex numbers in settings to show this result.');
  return value;
}

function real(compiled, scope, variables = {}) {
  const previous = Object.keys(variables).map(key => [key, scope.has(key), scope.get(key)]);
  for (const [key, value] of Object.entries(variables)) scope.set(key, value);
  try {
    const value = compiled.evaluate(scope);
    const n = value?.isComplex ? Math.abs(value.im) < 1e-10 ? value.re : NaN : Number(value);
    return Number.isFinite(n) ? n : null;
  } catch { return null; }
  finally { for (const [key, existed, value] of previous) { if (existed) scope.set(key, value); else scope.delete(key); } }
}

function variables(node) {
  const names = new Set();
  node.traverse((child, path, parent) => {
    if (child.isSymbolNode && !(parent?.isFunctionNode && path === 'fn') && !(child.name in m)) names.add(child.name);
  });
  return names;
}

function symbolicSource(node, definitions, scope, depth = 0) {
  if (depth > 24) throw Error('A definition is recursive or too deeply nested.');
  return node.transform(child => {
    if (child.isFunctionNode && definitions.has(child.fn.name)) {
      const def = definitions.get(child.fn.name);
      if (def.args.length !== child.args.length) throw Error('Wrong number of function arguments.');
      const expanded = def.node.transform(n => n.isSymbolNode && def.args.includes(n.name) ? child.args[def.args.indexOf(n.name)].cloneDeep() : n);
      return m.parse(symbolicSource(expanded, definitions, scope, depth + 1));
    }
    if (child.isSymbolNode && scope.has(child.name) && typeof scope.get(child.name) === 'number') return new m.ConstantNode(scope.get(child.name));
    if (child.isFunctionNode && child.fn.name === 'log10') return m.parse(`log(${child.args[0].toString()})/log(10)`);
    if (degrees && child.isFunctionNode && ['sin', 'cos', 'tan', 'sec', 'csc', 'cot'].includes(child.fn.name)) return m.parse(`${child.fn.name}((${child.args[0].toString()})*pi/180)`);
    if (degrees && child.isFunctionNode && ['asin', 'acos', 'atan'].includes(child.fn.name)) return m.parse(`180/pi*${child.fn.name}(${child.args[0].toString()})`);
    return child;
  }).toString({ parenthesis: 'all', implicit: 'show' });
}

function calculate(source, scope, definitions, names, doc) {
  const node = parsed(source, names), compiled = node.compile();
  const result = { preview: node.toTex({ parenthesis: 'auto' }) };
  let numericError;
  try {
    if (/^(solve|diff|integrate|factor|expand|simplify|limit|sum|defint)\(/.test(source)) throw Error('Symbolic calculation');
    const value = numeric(compiled, scope, doc.complexMode);
    result.approximate = m.format(value, { precision: doc.precisionDigits });
  } catch (error) { numericError = error; }
  try {
    if (numericError?.message?.includes('Enable complex')) throw numericError;
    const symbolicScope = new Map(scope);
    if (node.isFunctionNode && ['diff', 'integrate', 'limit', 'sum', 'product'].includes(node.fn.name) && node.args[1]?.isSymbolNode) symbolicScope.delete(node.args[1].name);
    let symbolic = symbolicSource(node, definitions, symbolicScope);
    // Giac uses integrate(f,x,a,b); Nerdamer calls definite integrals defint(f,a,b,x).
    symbolic = symbolic.replace(/^integrate\((.*)\)$/, (_, body) => {
      const args = splitArguments(body);
      return args.length === 4 ? `defint(${args[0]},${args[2]},${args[3]},${args[1]})` : `integrate(${body})`;
    });
    const exact = nerdamer(symbolic);
    if (!doc.complexMode && /(^|[^A-Za-z0-9_])i($|[^A-Za-z0-9_])/.test(exact.toString())) throw Error('Enable complex numbers in settings to show this result.');
    result.exact = exact.toString();
    result.tex = exact.toTeX();
    if (/^integrate\(/.test(source) && splitArguments(source.slice(10, -1)).length === 2 && !result.exact.includes('integrate(')) {
      result.exact += ' + C'; result.tex += ' + C';
    }
    if (!result.approximate && !exact.variables().length) result.approximate = exact.evaluate().text('decimals', doc.precisionDigits);
  } catch (error) {
    if (!result.approximate) throw numericError?.message?.includes('complex') ? numericError : error;
  }
  if (!result.exact && !result.approximate) throw Error('This expression is not supported by the browser math engine.');
  return result;
}

function fitTable(table, scope, names) {
  const cells = values => values.map(value => {
    if (!value.trim()) return null;
    try { return real(parsed(normalize(value), names).compile(), scope); } catch { return null; }
  });
  const xs = cells(table.x), ys = cells(table.y);
  const points = xs.map((x, i) => [x, ys[i]]).filter(([x, y]) => x != null && y != null);
  const values = points.map(p => p[1]);
  const summary = values.length ? `n = ${values.length} · mean y = ${m.format(m.mean(values), 6)}${values.length > 1 ? ` · sample σ = ${m.format(m.std(values), 6)}` : ''}` : '';
  const model = table.options.regression;
  if (!model || points.length < 2) return { points, summary };
  const kind = String(model).toLowerCase();
  if (!['linear', 'quadratic', 'cubic', 'quartic', 'exponential', 'logarithmic', 'power'].includes(kind)) return { points, summary: `${summary} · ${model} fitting is available in Android only.` };
  const degree = kind === 'quadratic' ? 2 : kind === 'cubic' ? 3 : kind === 'quartic' ? 4 : 1;
  if (points.length < degree + 1) return { points, summary: `${summary} · Need ${degree + 1} points for this fit.` };
  const transformed = points.map(([x, y]) => [kind === 'logarithmic' || kind === 'power' ? Math.log(x) : x, kind === 'exponential' || kind === 'power' ? Math.log(y) : y]);
  if (transformed.some(p => p.some(v => !Number.isFinite(v)))) return { points, summary: `${summary} · This fit needs positive values.` };
  try {
    const matrix = transformed.map(([x]) => Array.from({ length: degree + 1 }, (_, i) => x ** i));
    const transpose = m.transpose(matrix);
    const coefficients = m.lusolve(m.multiply(transpose, matrix), m.multiply(transpose, transformed.map(p => [p[1]]))).map(r => r[0]);
    const [a, b] = coefficients;
    const expression = kind === 'exponential' ? `${Math.exp(a)}*exp(${b}*x)` : kind === 'power' ? `${Math.exp(a)}*x^(${b})` : kind === 'logarithmic' ? `${a}+${b}*log(x)` : coefficients.map((c, i) => `(${c})*x^${i}`).join('+');
    const compiled = parsed(expression, names).compile();
    const mean = m.mean(values), total = values.reduce((sum, y) => sum + (y - mean) ** 2, 0);
    const residual = points.reduce((sum, [x, y]) => sum + (y - real(compiled, scope, { x })) ** 2, 0);
    return { points, summary: `${summary} · R² = ${total === 0 ? 'undefined' : m.format(1 - residual / total, 6)}`, fit: expression, compiled };
  } catch { return { points, summary: `${summary} · Fit is singular; try distinct x values.` }; }
}

function buildContext(doc) {
  const scope = new Map([['C', 0]]), definitions = new Map(), names = new Set(['x', 'y', 'z', 't', 'u', 'v', 'r', 'theta', 'C']);
  const rows = [], constants = [];
  for (const item of doc.expressions) {
    const entry = { ...item };
    rows.push(entry);
    try {
      if (!item.text.trim() || tableData(item.text) || noteText(item.text) != null || /^(folder|image|inference)\(/.test(item.text)) continue;
      entry.source = normalize(item.text);
      const rel = relation(entry.source);
      const fn = rel?.op === '=' && rel.left.match(/^([A-Za-z][A-Za-z0-9_]*)\(([^()]*)\)$/);
      if (fn) {
        const args = splitArguments(fn[2]);
        if (args.some(a => !/^[A-Za-z][A-Za-z0-9_]*$/.test(a)) || new Set(args).size !== args.length || forbidden.has(fn[1]) || fn[1] in m) throw Error('Use a new function name and distinct parameter names.');
        names.add(fn[1]);
        entry.definition = { name: fn[1], args, body: rel.right };
      } else if (rel?.op === '=' && /^[A-Za-z][A-Za-z0-9_]*$/.test(rel.left) && !['x', 'y', 'z', 'r', 'theta'].includes(rel.left)) {
        if (forbidden.has(rel.left) || rel.left in m) throw Error('This name is reserved.');
        names.add(rel.left);
        entry.constant = rel.left; constants.push(entry);
      }
    } catch (error) { entry.error = error.message; }
  }
  for (const entry of rows.filter(row => row.definition)) {
    try {
      const def = entry.definition;
      def.node = parsed(def.body, new Set([...names, ...def.args]));
      definitions.set(def.name, def);
      const compiled = def.node.compile();
      let depth = 0;
      scope.set(def.name, (...values) => {
        if (values.length !== def.args.length) throw Error('Wrong number of function arguments.');
        if (depth >= 64) throw Error('Recursive function limit reached.');
        depth++;
        try { return compiled.evaluate(new Map([...scope, ...def.args.map((a, i) => [a, values[i]])])); }
        finally { depth--; }
      });
    } catch (error) { entry.error = error.message; }
  }
  // Resolve forward references without repeatedly evaluating already resolved rows.
  let unresolved = constants.slice();
  for (let pass = 0; unresolved.length && pass <= constants.length; pass++) {
    const remaining = [];
    for (const entry of unresolved) {
      try {
        const body = relation(entry.source).right;
        const value = Object.hasOwn(doc.parameters, entry.constant) ? doc.parameters[entry.constant] : numeric(parsed(body, names).compile(), scope, doc.complexMode);
        scope.set(entry.constant, value);
        entry.value = value;
      } catch (error) { entry.error = error.message; remaining.push(entry); }
    }
    if (remaining.length === unresolved.length) break;
    unresolved = remaining;
  }
  for (const entry of constants) if (scope.has(entry.constant)) delete entry.error;
  return { scope, definitions, names, rows };
}

const linspace = (a, b, count) => Array.from({ length: count }, (_, i) => a + (b - a) * i / (count - 1));
const lineTrace = (x, y, color, name, is3D = false, z) => ({ type: is3D ? 'scatter3d' : 'scatter', mode: 'lines', x, y, ...(is3D ? { z: z || x.map(() => 0) } : {}), line: { color, width: is3D ? 5 : 2.5 }, name, connectgaps: false, hovertemplate: is3D ? 'x: %{x:.6g}<br>y: %{y:.6g}<br>z: %{z:.6g}<extra>%{fullData.name}</extra>' : 'x: %{x:.6g}<br>y: %{y:.6g}<extra>%{fullData.name}</extra>' });

function sampleFunction(compiled, scope, bounds, color, name, vertical, is3D) {
  const axis = vertical ? 'y' : 'x';
  const samples = linspace(bounds[`${axis}Min`], bounds[`${axis}Max`], 801);
  const span = vertical ? bounds.xMax - bounds.xMin : bounds.yMax - bounds.yMin;
  const values = samples.map(n => real(compiled, scope, { [axis]: n }));
  for (let i = values.length - 1; i > 0; i--) {
    if (values[i] != null && values[i - 1] != null && Math.abs(values[i] - values[i - 1]) > span * 2) values[i] = null;
  }
  return lineTrace(vertical ? values : samples, vertical ? samples : values, color, name, is3D);
}

function surface(compiled, scope, bounds, color, name) {
  const x = linspace(bounds.xMin, bounds.xMax, 51), y = linspace(bounds.yMin, bounds.yMax, 51);
  const z = y.map(y => x.map(x => real(compiled, scope, { x, y })));
  return { type: 'surface', x, y, z, colorscale: [[0, color], [1, color]], opacity: .85, showscale: false, name, hovertemplate: 'x: %{x:.5g}<br>y: %{y:.5g}<br>z: %{z:.5g}<extra>%{fullData.name}</extra>' };
}

function implicit(compiled, scope, bounds, color, name, op, is3D) {
  if (is3D) {
    const x = [], y = [], z = [], value = [];
    for (const a of linspace(bounds.xMin, bounds.xMax, 25)) for (const b of linspace(bounds.yMin, bounds.yMax, 25)) for (const c of linspace(bounds.zMin, bounds.zMax, 25)) {
      x.push(a); y.push(b); z.push(c); value.push(real(compiled, scope, { x: a, y: b, z: c }));
    }
    return { type: 'isosurface', x, y, z, value, isomin: -1e-8, isomax: 1e-8, surface: { count: 1 }, caps: { x: { show: false }, y: { show: false }, z: { show: false } }, colorscale: [[0, color], [1, color]], showscale: false, opacity: .8, name };
  }
  const x = linspace(bounds.xMin, bounds.xMax, 121), y = linspace(bounds.yMin, bounds.yMax, 121);
  const z = y.map(y => x.map(x => real(compiled, scope, { x, y })));
  const contours = op === '=' ? { start: 0, end: 0, size: 1, coloring: 'lines', showlabels: false } : { type: 'constraint', operation: op, value: 0, coloring: 'fill' };
  return { type: 'contour', x, y, z, contours, line: { color, width: 2 }, colorscale: [[0, color], [1, color]], showscale: false, opacity: op === '=' ? 1 : .25, name, hoverinfo: 'skip', connectgaps: false };
}

function tracesFor(entry, context, doc, bounds) {
  const { scope, names } = context, color = colorHex(entry.color), name = entry.text;
  const compile = text => {
    const node = parsed(text, names);
    const missing = [...variables(node)].filter(v => !scope.has(v) && !['x', 'y', 'z', 't', 'u', 'v', 'r', 'theta'].includes(v));
    node.traverse(child => {
      if (child.isFunctionNode && !(child.fn.name in m) && !scope.has(child.fn.name)) missing.push(`${child.fn.name}(…)`);
    });
    if (missing.length) throw Error(`Define ${missing.join(', ')} in another expression first.`);
    return node.compile();
  };
  const source = entry.source;
  if (entry.definition) {
    const def = entry.definition;
    if (def.args.length === 1 && def.args[0] === 'x') return [sampleFunction(compile(`${def.name}(x)`), scope, bounds, color, name, false, doc.is3D)];
    if (def.args.join(',') === 'x,y' && doc.is3D) return [surface(compile(`${def.name}(x,y)`), scope, bounds, color, name)];
    return [];
  }
  if (entry.constant) return [];
  if (source.startsWith('(') && source.endsWith(')')) {
    const args = splitArguments(source.slice(1, -1));
    if (args.length === 2 || args.length === 3) {
      const nodes = args.map(s => parsed(s, names)), compiled = nodes.map(n => n.compile());
      const vars = new Set(nodes.flatMap(n => [...variables(n)]));
      if (doc.is3D && args.length === 3 && (vars.has('u') || vars.has('v'))) {
        const u = linspace(bounds.uMin, bounds.uMax, 41), v = linspace(bounds.vMin, bounds.vMax, 41);
        const grids = compiled.map(c => v.map(v => u.map(u => real(c, scope, { u, v }))));
        return [{ type: 'surface', x: grids[0], y: grids[1], z: grids[2], colorscale: [[0, color], [1, color]], showscale: false, name }];
      }
      if (vars.has('t') || vars.has('theta')) {
        const points = compiled.map(c => linspace(bounds.tMin, bounds.tMax, 1001).map(t => real(c, scope, { t, theta: t })));
        return [lineTrace(points[0], points[1], color, name, doc.is3D, points[2])];
      }
      const values = compiled.map(c => { try { const v = c.evaluate(scope); return v?.toArray ? v.toArray() : v; } catch { return null; } });
      const length = Math.max(...values.map(v => Array.isArray(v) ? v.length : 1));
      const arrays = values.map(v => Array.isArray(v) ? v : Array(length).fill(v));
      return [{ type: doc.is3D ? 'scatter3d' : 'scatter', mode: 'markers', x: arrays[0], y: arrays[1], ...(doc.is3D ? { z: arrays[2] || Array(length).fill(0) } : {}), marker: { color, size: doc.is3D ? 5 : 8 }, name }];
    }
  }
  const rel = relation(source);
  if (rel) {
    const rhs = parsed(rel.right, names), vars = variables(rhs);
    const compiledRight = compile(rel.right);
    if (rel.left === 'r' && rel.op === '=') {
      const c = compiledRight, points = linspace(bounds.tMin, bounds.tMax, 1001).map(t => {
        const radius = real(c, scope, { theta: t, t });
        const angle = t * (doc.degreeMode ? Math.PI / 180 : 1);
        return radius == null ? [null, null] : [radius * Math.cos(angle), radius * Math.sin(angle)];
      });
      return [lineTrace(points.map(p => p[0]), points.map(p => p[1]), color, name, doc.is3D)];
    }
    if (rel.op === '=' && rel.left === 'z' && !vars.has('z') && doc.is3D) return [surface(compiledRight, scope, bounds, color, name)];
    if (rel.op === '=' && rel.left === 'y' && !vars.has('y')) return [sampleFunction(compiledRight, scope, bounds, color, name, false, doc.is3D)];
    if (rel.op === '=' && rel.left === 'x' && !vars.has('x')) return [sampleFunction(compiledRight, scope, bounds, color, name, true, doc.is3D)];
    if (doc.is3D && rel.op !== '=') throw Error('3D inequalities are not supported in the web version.');
    return [implicit(compile(`(${rel.left})-(${rel.right})`), scope, bounds, color, name, rel.op, doc.is3D)];
  }
  const node = parsed(source, names), vars = [...variables(node)].filter(v => !scope.has(v));
  if (!vars.length || /^(solve|diff|integrate|factor|expand|simplify|limit|sum|defint)\(/.test(source)) return [];
  if (doc.is3D && vars.some(v => v === 'y' || v === 'z')) return [surface(node.compile(), scope, bounds, color, name)];
  if (vars.every(v => v === 'x')) return [sampleFunction(node.compile(), scope, bounds, color, name, false, doc.is3D)];
  return [];
}

self.onmessage = async ({ data: { id, document: doc, bounds } }) => {
  try {
    ({ splitArguments, tableData, noteText, colorHex } = await helpers);
    degrees = doc.degreeMode;
    const context = buildContext(doc), results = [], traces = [];
    for (const entry of context.rows) {
      const result = { id: entry.id };
      try {
        if (entry.error) throw Error(entry.error);
        const table = tableData(entry.text);
        if (table) {
          const fitted = fitTable(table, context.scope, context.names);
          result.summary = fitted.summary;
          if (fitted.fit) result.fit = fitted.fit.replaceAll('log(', 'ln(');
          if (!entry.hidden) {
            traces.push({ type: doc.is3D ? 'scatter3d' : 'scatter', mode: 'markers', x: fitted.points.map(p => p[0]), y: fitted.points.map(p => p[1]), ...(doc.is3D ? { z: fitted.points.map(() => 0) } : {}), marker: { color: colorHex(entry.color), size: 7 }, name: `Table ${results.length + 1}` });
            if (fitted.compiled && !table.options.regressionHidden) traces.push(sampleFunction(fitted.compiled, context.scope, bounds, '#6042a6', 'Regression', false, doc.is3D));
          }
        } else if (entry.source) {
          if (entry.constant) {
            Object.assign(result, calculate(Object.hasOwn(doc.parameters, entry.constant) ? String(entry.value) : relation(entry.source).right, context.scope, context.definitions, context.names, doc));
            if (typeof entry.value === 'number' && Number.isFinite(entry.value)) result.slider = { name: entry.constant, value: entry.value };
          } else if (entry.definition) {
            result.preview = `${entry.definition.name}(${entry.definition.args.join(',')}) = ${entry.definition.node.toTex()}`;
          } else {
            const rel = relation(entry.source);
            if (rel) result.preview = `${parsed(rel.left, context.names).toTex()} ${rel.op.replace('<=', '\\leq').replace('>=', '\\geq')} ${parsed(rel.right, context.names).toTex()}`;
            else if (!(entry.source.startsWith('(') && splitArguments(entry.source.slice(1, -1)).length > 1)) Object.assign(result, calculate(entry.source, context.scope, context.definitions, context.names, doc));
          }
          if (!entry.hidden) {
            const plotEntry = /^(diff|integrate|simplify|expand)\(/.test(entry.source) && result.exact ? { ...entry, source: result.exact.replace(/ \+ C$/, '') } : entry;
            traces.push(...tracesFor(plotEntry, context, doc, bounds));
          }
        }
      } catch (error) { result.error = error.message || 'Could not evaluate this expression.'; }
      results.push(result);
    }
    postMessage({ id, results, traces });
  } catch (error) { postMessage({ id, error: error.message }); }
};
