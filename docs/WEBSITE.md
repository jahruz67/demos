# Demos website on GitHub Pages

The `web/` directory is a separate, static browser application. It does not use or change the Android Gradle project, APK signing, bundled native libraries, or Android release workflow. No server or API key is required. The deployed site includes its own JavaScript libraries and fonts; calculations run in the browser.

## Publish

1. In your GitHub repository, open **Settings → Pages** and set **Build and deployment → Source** to **GitHub Actions**. This is a one-time repository setting, required by [GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages).
2. Commit and push the new website files and `.github/workflows/website.yml` to your repository's **default branch**.
3. Open **Actions → Website · GitHub Pages**. After its build and deploy jobs finish, open the URL in the deployment or **Settings → Pages**. Normally it is `https://<owner>.github.io/<repository>/`; a repository named `<owner>.github.io` uses the domain root.

The website workflow assembles changes under `web/` and deploys only from the default branch. Pushes to other branches produce a website artifact without replacing the live site. You can also run the workflow manually from the Actions tab. If you enabled Pages after the initial push, manually rerun the workflow on the default branch.

Actions and Pages must be allowed by your repository/account settings. Organization restrictions or environment approvals may require an administrator. The existing Android workflow retains its own behavior on pushes.

## Browser features

- Editable expression worksheet, exact and decimal results, math formatting, custom functions, degree/radian modes, complex results, precision settings, and touch keypad.
- Interactive 2D functions, vertical lines, implicit relations, inequalities, polar and parametric curves, points, and lists of points.
- Interactive 3D surfaces, implicit surfaces, parametric curves and surfaces, with orbit/pan/zoom controls.
- Variable sliders with configurable bounds and animation.
- Tables with editable columns, descriptive statistics, linear/quadratic/cubic/quartic/exponential/logarithmic/power regression, and CSV export. CSV, TSV, and JSON table import.
- Notes, undo/redo, graph settings, split/graph/calculator layouts, PNG export, and browser printing to PDF.
- Automatic worksheet recovery and a named graph library in browser storage. JSON import/export uses the Android `GraphDocument` version 1 envelope, preserving unknown fields and unsupported worksheet sources.

Examples are available through **Help & examples**. For example, enter `a=2` and `y=a*sin(x)`, or switch to 3D and enter `z=sin(x)*cos(y)`. Use `diff(x^3,x)`, `integrate(x^2,x)`, `integrate(x^2,x,0,1)`, and `solve(x^2=4,x)` for symbolic calculations.

## Compatibility limits

This is a browser port of the main calculator workflow, not an Android emulator. [math.js](https://mathjs.org/docs/expressions/parsing.html) supplies numerical evaluation, [Nerdamer 1.x](https://github.com/jiggzson/nerdamer) supplies symbolic algebra, [Plotly](https://plotly.com/javascript/) supplies graphs, and KaTeX formats mathematics. The Android app continues to use native Giac. Their supported commands, exact forms, precision, and behavior at singularities can differ. Browser calculations have a 12-second timeout so an expensive expression does not freeze the UI.

Images, folder controls, statistical inference editors, logistic/sinusoidal regression, 3D inequalities, and the full Giac command set are not implemented in the browser. Those imported worksheet sources are retained in JSON exports, with unsupported items called out in the UI. Android image URIs refer to device-local files and cannot be rendered by a website. This port uses text input with formatted previews rather than Android's structured math cursor. Graphs use finite sampling, so small features and discontinuities can require zooming or changing bounds.

Storage is local to this browser and website origin; it does not sync with Android or GitHub. Export JSON for backups and transfers. Private browsing, cleared site data, or exhausted browser storage can prevent saving; the UI reports storage errors. A publicly hosted Pages site contains only application assets: the workflow never includes graph documents, keystores, or Android outputs.

## Local development (optional)

With Node.js 22 installed, run the following commands yourself, or explicitly ask for local execution:

```powershell
cd web
npm install --ignore-scripts --no-audit --no-fund --package-lock=false
npm run build
```

Serve **`web/_site/`** through a local HTTP server. Opening `index.html` with `file://` will not work because browser modules and workers require HTTP. The same relative paths work at both GitHub Pages repository URLs and custom domains. No Android SDK is involved. The build only replaces `web/_site/`, copies the web source, and includes the four pinned browser bundles, their fonts, and license files. No runtime CDN requests are needed.
