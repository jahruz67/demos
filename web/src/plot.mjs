const defaults = { xMin: -10, xMax: 10, yMin: -7, yMax: 7, zMin: -7, zMax: 7, tMin: -10, tMax: 10, uMin: -5, uMax: 5, vMin: -5, vMax: 5 };

export class Graph {
  constructor(element, onBoundsChange, onError) {
    this.element = element;
    this.onBoundsChange = onBoundsChange;
    this.onError = onError;
    this.bounds = { ...defaults };
    this.viewRevision = 0;
    this.rendering = false;
    this.queue = null;
    this.observer = new ResizeObserver(() => {
      if (element.data && element.clientWidth && element.clientHeight) Plotly.Plots.resize(element).catch(onError);
    });
    this.observer.observe(element);
  }

  reset(doc) {
    this.bounds = { ...defaults, ...(doc.is3D ? doc.viewBounds3D : doc.viewBounds) };
    for (const axis of ['x', 'y', 'z', 't', 'u', 'v']) {
      if (!Number.isFinite(this.bounds[`${axis}Min`]) || !Number.isFinite(this.bounds[`${axis}Max`]) || this.bounds[`${axis}Min`] >= this.bounds[`${axis}Max`]) {
        this.bounds[`${axis}Min`] = defaults[`${axis}Min`]; this.bounds[`${axis}Max`] = defaults[`${axis}Max`];
      }
    }
    this.viewRevision++;
  }

  async render(traces, doc) {
    // Plotly.react calls must finish in order; keep only the newest waiting render.
    if (this.rendering) { this.queue = [traces, doc]; return; }
    this.rendering = true;
    try {
      const grid = doc.is3D ? doc.showGrid3D : doc.showGrid, axes = doc.is3D ? doc.showAxes3D : doc.showAxes;
      const axis = name => ({ range: [this.bounds[`${name}Min`], this.bounds[`${name}Max`]], title: { text: doc.is3D ? name : '' }, showgrid: grid, zeroline: axes, showline: false, showticklabels: axes, gridcolor: '#e4e9ef', zerolinecolor: '#8c98a8', zerolinewidth: 1.5, tickfont: { size: 11, color: '#7b8795' }, fixedrange: false, ...(doc.is3D ? { showbackground: true, backgroundcolor: '#fcfdff' } : {}) });
      const revision = `${doc.id}-${doc.is3D}-${this.viewRevision}`;
      const layout = {
        autosize: true, margin: doc.is3D ? { l: 0, r: 0, t: 50, b: 25 } : { l: 42, r: 20, t: 60, b: 40 },
        paper_bgcolor: '#ffffff', plot_bgcolor: '#ffffff', showlegend: false, uirevision: revision,
        hovermode: 'closest', dragmode: doc.is3D ? 'orbit' : 'pan',
        xaxis: { ...axis('x'), constrain: 'domain' }, yaxis: axis('y'),
        scene: { xaxis: axis('x'), yaxis: axis('y'), zaxis: axis('z'), aspectmode: 'cube', uirevision: revision, camera: { projection: { type: doc.appearance3D?.perspective === false ? 'orthographic' : 'perspective' } } },
        annotations: traces.length ? [] : [{ text: doc.is3D ? 'Try z = sin(x) · cos(y)' : 'Enter an expression to start exploring', xref: 'paper', yref: 'paper', x: .5, y: .5, showarrow: false, font: { size: 13, color: '#a1adbc' } }],
      };
      // A blank 3D scene still needs a 3D trace for orbit controls and axes.
      const data = traces.length ? traces : [{ type: doc.is3D ? 'scatter3d' : 'scatter', x: [], y: [], ...(doc.is3D ? { z: [] } : {}), hoverinfo: 'skip' }];
      await Plotly.react(this.element, data, layout, { responsive: true, scrollZoom: true, displayModeBar: false, displaylogo: false, showTips: false });
      if (!this.listening) {
        this.listening = true;
        this.element.on('plotly_relayout', event => {
          if (this.rendering) return;
          let changed = false;
          for (const axis of ['x', 'y']) {
            const range = event[`${axis}axis.range`] || [event[`${axis}axis.range[0]`], event[`${axis}axis.range[1]`]];
            if (range.every(Number.isFinite) && range[1] > range[0]) {
              this.bounds[`${axis}Min`] = range[0]; this.bounds[`${axis}Max`] = range[1]; changed = true;
            }
          }
          if (changed) this.onBoundsChange({ ...this.bounds });
        });
      }
    } catch (error) { this.onError(error); }
    finally {
      this.rendering = false;
      if (this.queue) { const next = this.queue; this.queue = null; this.render(...next); }
    }
  }

  async exportPNG(title) {
    await Plotly.downloadImage(this.element, { format: 'png', width: 1600, height: 1100, filename: title.replace(/[^a-zA-Z0-9_-]/g, '_') || 'Demos' });
  }
}
