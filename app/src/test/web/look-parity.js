// Does the media page's live preview (assets/church/look.js, WebGL2) match the app's render?
// 1. gradle :app:testDebugUnitTest --tests com.example.core.render.LookParityFixtureTest
// 2. node app/src/test/web/look-parity.js   (needs Playwright with Chromium)
// Compares app/build/look-parity/app.png (CpuRenderer) with the page's render of source.png + params.json.
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../../..');
const dir = path.join(root, 'build/look-parity');
const shaderKt = fs.readFileSync(path.join(root, 'src/main/java/com/example/core/render/PresetShader.kt'), 'utf8');
const pick = (name) => shaderKt.split(`const val ${name} = """`)[1].split('"""')[0];
const dataUrl = (f) => 'data:image/png;base64,' + fs.readFileSync(path.join(dir, f)).toString('base64');

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage();
  page.on('pageerror', (e) => console.log('PAGEERR', e.message));
  await page.setContent('<canvas id="gl"></canvas>');
  await page.addScriptTag({ content: fs.readFileSync(path.join(root, 'src/main/assets/church/look.js'), 'utf8') });
  const result = await page.evaluate(async ({ vertex, fragment, params, source, app }) => {
    const load = (src) => new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = no; i.src = src; });
    const [img, ref] = await Promise.all([load(source), load(app)]);
    if (!JRLook.supported()) return { error: 'no webgl2' };
    const r = new JRLook.Renderer(document.getElementById('gl'), { vertex, fragment });
    r.setImage(img);
    r.draw(params);
    const pixels = (drawable, w, h) => {
      const c = document.createElement('canvas'); c.width = w; c.height = h;
      const x = c.getContext('2d'); x.drawImage(drawable, 0, 0); return x.getImageData(0, 0, w, h).data;
    };
    const w = img.naturalWidth, h = img.naturalHeight;
    const a = pixels(document.getElementById('gl'), w, h), b = pixels(ref, w, h);
    let sum = 0, max = 0, far = 0, n = 0;
    for (let i = 0; i < a.length; i += 4) {
      for (let c = 0; c < 3; c++) {
        const d = Math.abs(a[i + c] - b[i + c]); sum += d; n++; if (d > max) max = d; if (d > 12) far++;
      }
    }
    return { mean: sum / n, max, farShare: far / n };
  }, { vertex: pick('VERTEX'), fragment: pick('FRAGMENT'), params: JSON.parse(fs.readFileSync(path.join(dir, 'params.json'), 'utf8')), source: dataUrl('source.png'), app: dataUrl('app.png') });
  console.log(JSON.stringify(result));
  await browser.close();
  // Mipmaps (GPU) and box blurs (CPU) differ a little in clarity and texture; the rest is the same maths.
  const ok = result.mean !== undefined && result.mean <= 3 && result.farShare < 0.01;
  console.log(ok ? 'OK: a prévia da página bate com o app' : 'DIFERENTE');
  process.exit(ok ? 0 : 1);
})();
