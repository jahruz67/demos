import { cp, mkdir, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const destination = path.join(root, '_site');
// Only this web project's generated output is replaced.
await rm(destination, { recursive: true, force: true });
await mkdir(path.join(destination, 'vendor'), { recursive: true });
for (const file of ['index.html', 'styles.css', 'src']) {
  await cp(path.join(root, file), path.join(destination, file), { recursive: true });
}
const bundles = [
  ['mathjs/lib/browser/math.js', 'math.js'],
  ['nerdamer/all.min.js', 'nerdamer.js'],
  ['plotly.js-dist-min/plotly.min.js', 'plotly.js'],
  ['katex/dist/katex.min.js', 'katex.js'],
  ['katex/dist/katex.min.css', 'katex.css'],
  ['katex/dist/fonts', 'fonts'],
  ['mathjs/LICENSE', 'mathjs-LICENSE.txt'],
  ['nerdamer/LICENSE', 'nerdamer-LICENSE.txt'],
  ['plotly.js-dist-min/LICENSE', 'plotly-LICENSE.txt'],
  ['katex/LICENSE', 'katex-LICENSE.txt'],
];
for (const [source, name] of bundles) {
  await cp(path.join(root, 'node_modules', source), path.join(destination, 'vendor', name), { recursive: true });
}
await writeFile(path.join(destination, '.nojekyll'), '');
console.log('Website assembled in web/_site. Android files were not used or modified.');
