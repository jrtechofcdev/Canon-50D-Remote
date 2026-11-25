// The full editor on the media page, for admins with the Editor switch. Panels and ranges come from
// the app (EditorControls, /api/c/editor); the phone turns each look into the shader's numbers
// (/api/c/params), and look.js draws it with the app's own shader. Nothing here computes colour.
(function () {
  "use strict";

  var ICONS = {
    undo: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 010 11H11" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>',
    redo: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 14l5-5-5-5M20 9H9.5a5.5 5.5 0 000 11H13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>'
  };
  var CURVE_ID = "0,0;255,255";
  var spec = null, shader = null, identity = null;

  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }

  // ---- The look as the app writes it: "key=value" lines and "curve:Key=x,y;x,y" ----
  function parse(text) {
    var look = { values: {}, curves: {} };
    (text || "").split("\n").forEach(function (line) {
      var i = line.indexOf("=");
      if (i < 1) return;
      var k = line.slice(0, i), v = line.slice(i + 1);
      if (k.indexOf("curve:") === 0) {
        look.curves[k.slice(6)] = v.split(";").map(function (p) { var xy = p.split(","); return [parseInt(xy[0], 10), parseInt(xy[1], 10)]; })
          .filter(function (p) { return !isNaN(p[0]) && !isNaN(p[1]); });
      } else {
        var n = parseFloat(v);
        look.values[k] = isNaN(n) || /[A-Za-z&]/.test(v) ? v : n;
      }
    });
    return look;
  }
  function encode(look) {
    var out = [];
    Object.keys(look.values).sort().forEach(function (k) { out.push(k + "=" + look.values[k]); });
    Object.keys(look.curves).sort().forEach(function (k) {
      var pts = look.curves[k];
      var text = pts.map(function (p) { return p[0] + "," + p[1]; }).join(";");
      if (text !== CURVE_ID) out.push("curve:" + k + "=" + text);
    });
    return out.join("\n") + "\n";
  }

  function call(path, method, body) {
    return fetch(path, { method: method || "GET", cache: "no-store", body: body, headers: body ? { "Content-Type": "text/plain; charset=utf-8" } : undefined })
      .then(function (r) {
        var type = r.headers.get("Content-Type") || "";
        return (type.indexOf("json") >= 0 ? r.json() : r.text()).then(function (b) { return { status: r.status, body: b }; });
      });
  }

  /**
   * Opens the editor on photo [o.photoId], starting from [o.from] ("preset:ID", "own" or "event").
   * o: { member, photoId, from, rank, admins, toast, onSaved }
   */
  function open(o) {
    var root = $("editor"), canvas = $("edCanvas"), wait = $("edWait");
    var look = null, start = "", history = [], future = [], renderer = null, tab = 0;
    var inflight = false, dirtyRender = false, before = false, current = null;
    var mixerBand = 0, gradeZone = 0, curveChannel = 0;

    $("edUndo").innerHTML = ICONS.undo; $("edRedo").innerHTML = ICONS.redo; $("edClose").innerHTML = JR.icons.close;
    wait.innerHTML = JR.loader(); wait.hidden = false;
    JR.openLayer(root);

    var m = o.member;
    var from = o.from.indexOf("preset:") === 0 ? "from=preset&id=" + o.from.slice(7) : "from=" + o.from;
    var specReady = spec ? Promise.resolve(spec) : call("/api/c/editor?m=" + m).then(function (r) { if (r.status !== 200) throw new Error(r.body.error || "editor"); spec = r.body; return spec; });
    var shaderReady = shader ? Promise.resolve(shader) : call("/api/c/shader?m=" + m).then(function (r) { if (r.status !== 200) throw new Error("shader"); shader = r.body; return shader; });
    var lookReady = call("/api/c/settings?m=" + m + "&" + from).then(function (r) { if (r.status !== 200) throw new Error("look"); return r.body; });
    var baseReady = new Promise(function (ok, no) { var im = new Image(); im.onload = function () { ok(im); }; im.onerror = no; im.src = "/b/" + m + "/" + o.photoId; });

    Promise.all([specReady, shaderReady, lookReady, baseReady]).then(function (all) {
      start = all[2];
      look = parse(start);
      renderer = new JRLook.Renderer(canvas, all[1]);
      renderer.setImage(all[3]);
      fit();
      paintTabs(); paintPanel(); paintHistory();
      render();
    }).catch(function (e) {
      wait.hidden = true;
      o.toast(e && e.message === "editor" ? "Editor indisponível" : "Não deu para abrir o editor (" + (e && e.message || "erro") + ")", "warn");
      JR.closeLayer();
    });

    // ---- Preview: one request at a time, the latest look wins ----
    function render() {
      if (!look) return;
      if (inflight) { dirtyRender = true; return; }
      inflight = true;
      call("/api/c/params?m=" + m, "POST", encode(look)).then(function (r) {
        inflight = false;
        if (r.status === 200) { current = r.body; if (!before) draw(current); }
        wait.hidden = true;
        if (dirtyRender) { dirtyRender = false; render(); }
      }).catch(function () { inflight = false; wait.hidden = true; });
    }
    function draw(p) { if (renderer && p) renderer.draw(p); }
    function fit() {
      if (!renderer) return;
      var stage = $("edStage"), w = stage.clientWidth, h = stage.clientHeight;
      var s = Math.min(w / renderer.width, h / renderer.height);
      canvas.style.width = Math.round(renderer.width * s) + "px"; canvas.style.height = Math.round(renderer.height * s) + "px";
    }
    // One resize listener for the editor, whichever photo it is on.
    if (root._fit) window.removeEventListener("resize", root._fit);
    root._fit = fit;
    window.addEventListener("resize", fit);

    // ---- Before / after: hold the button ----
    function showBefore(on) {
      before = on;
      $("edBefore").classList.toggle("on", on);
      if (!on) { draw(current); return; }
      if (identity) { draw(identity); return; }
      call("/api/c/params?m=" + m, "POST", "\n").then(function (r) { if (r.status === 200) { identity = r.body; if (before) draw(identity); } });
    }
    var b4 = $("edBefore");
    b4.onpointerdown = function (e) { e.preventDefault(); showBefore(true); };
    b4.onpointerup = b4.onpointerleave = b4.onpointercancel = function () { if (before) showBefore(false); };

    // ---- Undo / redo: a step per finished gesture ----
    var lastText = null;
    function commit() {
      var text = encode(look);
      if (text === lastText) return;
      if (lastText !== null) history.push(lastText);
      lastText = text; future = [];
      paintHistory();
    }
    function paintHistory() {
      if (lastText === null && look) lastText = encode(look);
      $("edUndo").disabled = !history.length; $("edRedo").disabled = !future.length;
    }
    $("edUndo").onclick = function () { if (!history.length) return; future.push(lastText); lastText = history.pop(); look = parse(lastText); paintPanel(); paintHistory(); render(); };
    $("edRedo").onclick = function () { if (!future.length) return; history.push(lastText); lastText = future.pop(); look = parse(lastText); paintPanel(); paintHistory(); render(); };
    $("edClose").onclick = function () {
      if (look && encode(look) !== encode(parse(start)) && !window.confirm("Sair sem guardar a edição?")) return;
      JR.closeLayer();
    };

    // ---- Panels ----
    function num(key, def) { var v = look.values[key]; return typeof v === "number" ? v : def; }
    function kelvin() { return look.values.WhiteBalance === "Custom" && typeof look.values.Temperature === "number"; }
    function mono() { return look.values.ConvertToGrayscale === "True" || look.values.Treatment === "Black & White"; }

    function paintTabs() {
      var box = $("edTabs");
      box.innerHTML = "";
      spec.tabs.forEach(function (t, i) {
        var b = el("button", "", t.label);
        b.type = "button"; b.setAttribute("role", "tab"); b.setAttribute("aria-selected", i === tab ? "true" : "false");
        b.onclick = function () { tab = i; paintTabs(); paintPanel(); };
        box.appendChild(b);
      });
    }

    function slider(s, box) {
      var row = el("div", "ed-slider"), head = el("div", "ed-head"), name = el("span", "", s.label), value = el("b");
      var input = el("input");
      var step = s.dec > 0 ? Math.pow(10, -s.dec) : 1;
      input.type = "range"; input.min = s.min; input.max = s.max; input.step = step;
      input.setAttribute("aria-label", s.label);
      if (s.track && s.track !== "plain") input.className = "track-" + s.track;
      function show(v) { value.textContent = (v > 0 && s.min < 0 ? "+" : "") + v.toFixed(s.dec); }
      var v0 = num(s.key, s.def);
      input.value = v0; show(v0);
      input.oninput = function () { var v = parseFloat(input.value); look.values[s.key] = v; show(v); render(); };
      input.onchange = commit;
      // Double tap on the name puts the slider back, as in the app.
      name.ondblclick = function () { look.values[s.key] = s.def; input.value = s.def; show(s.def); render(); commit(); };
      head.appendChild(name); head.appendChild(value);
      row.appendChild(head); row.appendChild(input);
      box.appendChild(row);
    }
    function group(title, sliders, box) {
      box.appendChild(el("p", "ed-group", title));
      sliders.forEach(function (s) { slider(s, box); });
    }
    function chips(list, pick, onPick, box) {
      var row = el("div", "ed-chips");
      list.forEach(function (c, i) {
        var b = el("button", "", c.label);
        b.type = "button"; b.setAttribute("aria-pressed", i === pick ? "true" : "false");
        if (c.color) b.style.setProperty("--swatch", c.color);
        b.onclick = function () { onPick(i); };
        row.appendChild(b);
      });
      box.appendChild(row);
    }

    function paintPanel() {
      var box = $("edPanel"), t = spec.tabs[tab];
      box.innerHTML = "";
      if (t.id === "color") colorPanel(box);
      if (t.id === "grading") gradingPanel(box);
      if (t.id === "curve") curvePanel(box);
      t.groups.forEach(function (g) {
        // A raw preset with a Kelvin white balance shows the Kelvin sliders, as the app does.
        var list = g.title === "Balanço de branco" && kelvin() ? spec.kelvin : g.sliders;
        group(g.title, list, box);
      });
      box.scrollTop = 0;
    }

    function colorPanel(box) {
      var row = el("label", "ed-switch");
      var cb = el("input"); cb.type = "checkbox"; cb.checked = mono();
      cb.onchange = function () {
        look.values.ConvertToGrayscale = cb.checked ? "True" : "False";
        look.values.Treatment = cb.checked ? "Black & White" : "Color";
        paintPanel(); render(); commit();
      };
      row.appendChild(cb); row.appendChild(el("span", "", "Preto e branco"));
      box.appendChild(row);
      box.appendChild(el("p", "ed-group", mono() ? "Mistura P&B" : "Mistura de cores"));
      chips(spec.tabs[tab].mixer, mixerBand, function (i) { mixerBand = i; paintPanel(); }, box);
      var band = spec.tabs[tab].mixer[mixerBand];
      var list = mono() ? [{ key: "GrayMixer" + band.band, label: band.label, min: -100, max: 100, def: 0, dec: 0 }] : [
        { key: "HueAdjustment" + band.band, label: "Matiz · " + band.label, min: -100, max: 100, def: 0, dec: 0 },
        { key: "SaturationAdjustment" + band.band, label: "Saturação · " + band.label, min: -100, max: 100, def: 0, dec: 0 },
        { key: "LuminanceAdjustment" + band.band, label: "Luminância · " + band.label, min: -100, max: 100, def: 0, dec: 0 }
      ];
      list.forEach(function (s) { slider(s, box); });
    }

    function gradingPanel(box) {
      var zones = spec.tabs[tab].zones, z = zones[gradeZone].key;
      chips(zones, gradeZone, function (i) { gradeZone = i; paintPanel(); }, box);
      [{ key: "ColorGrade" + z + "Hue", label: "Matiz", min: 0, max: 360, def: 0, dec: 0, track: "hue" },
        { key: "ColorGrade" + z + "Sat", label: "Saturação", min: 0, max: 100, def: 0, dec: 0 },
        { key: "ColorGrade" + z + "Lum", label: "Luminância", min: -100, max: 100, def: 0, dec: 0 }].forEach(function (s) { slider(s, box); });
    }

    // ---- Point curve: tap to add, drag to move, double tap to remove ----
    function curvePanel(box) {
      var channels = spec.tabs[tab].curves, key = channels[curveChannel].key;
      chips(channels, curveChannel, function (i) { curveChannel = i; paintPanel(); }, box);
      var c = el("canvas", "ed-curve"); c.width = 512; c.height = 512;
      box.appendChild(c);
      var reset = el("button", "btn ghost small", "Restaurar curva"); reset.type = "button";
      reset.onclick = function () { delete look.curves[key]; drawCurve(); render(); commit(); };
      box.appendChild(el("p", "ed-hint", "Toque para criar um ponto, arraste para mover, toque duas vezes para apagar."));
      box.appendChild(reset);
      var colors = ["#f2f2f2", "#ff6b6b", "#6be38a", "#6ba8ff"];
      function pts() { return look.curves[key] || [[0, 0], [255, 255]]; }
      function drawCurve() {
        var g = c.getContext("2d"), s = c.width / 255, p = pts();
        g.clearRect(0, 0, c.width, c.height);
        g.strokeStyle = "rgba(255,255,255,.12)"; g.lineWidth = 2;
        for (var i = 1; i < 4; i++) { g.beginPath(); g.moveTo(i * c.width / 4, 0); g.lineTo(i * c.width / 4, c.height); g.moveTo(0, i * c.height / 4); g.lineTo(c.width, i * c.height / 4); g.stroke(); }
        g.beginPath(); g.moveTo(0, c.height); g.lineTo(c.width, 0); g.stroke();
        g.strokeStyle = colors[curveChannel]; g.lineWidth = 4; g.beginPath();
        p.forEach(function (q, i) { var x = q[0] * s, y = c.height - q[1] * s; if (i) g.lineTo(x, y); else g.moveTo(x, y); });
        g.stroke();
        g.fillStyle = colors[curveChannel];
        p.forEach(function (q) { g.beginPath(); g.arc(q[0] * s, c.height - q[1] * s, 12, 0, Math.PI * 2); g.fill(); });
      }
      function at(e) {
        var r = c.getBoundingClientRect();
        return [Math.max(0, Math.min(255, Math.round((e.clientX - r.left) / r.width * 255))), Math.max(0, Math.min(255, Math.round((1 - (e.clientY - r.top) / r.height) * 255)))];
      }
      function near(p, q) {
        var r = c.getBoundingClientRect(), k = r.width / 255;
        for (var i = 0; i < p.length; i++) if (Math.abs(p[i][0] - q[0]) * k < 18 && Math.abs(p[i][1] - q[1]) * k < 18) return i;
        return -1;
      }
      var dragging = -1, lastTap = 0;
      c.onpointerdown = function (e) {
        e.preventDefault();
        c.setPointerCapture(e.pointerId);
        var p = pts().slice(), q = at(e), i = near(p, q), now = Date.now();
        if (i >= 0 && now - lastTap < 350 && i > 0 && i < p.length - 1) {
          p.splice(i, 1); look.curves[key] = p; dragging = -1; drawCurve(); render(); commit(); lastTap = 0; return;
        }
        lastTap = now;
        if (i < 0) {
          if (p.length >= 16) return;
          p.push(q); p.sort(function (a, b) { return a[0] - b[0]; });
          i = p.indexOf(q);
        }
        look.curves[key] = p; dragging = i; drawCurve(); render();
      };
      c.onpointermove = function (e) {
        if (dragging < 0) return;
        var p = pts().slice(), q = at(e);
        // The ends stay at the edges; the points in between stay in order.
        if (dragging === 0) q[0] = 0;
        else if (dragging === p.length - 1) q[0] = 255;
        else q[0] = Math.max(p[dragging - 1][0] + 1, Math.min(p[dragging + 1][0] - 1, q[0]));
        p[dragging] = q; look.curves[key] = p; drawCurve(); render();
      };
      c.onpointerup = c.onpointercancel = function () { if (dragging >= 0) { dragging = -1; commit(); } };
      drawCurve();
    }

    // ---- Saving ----
    function paintActions() {
      var box = $("edActions");
      box.innerHTML = "";
      function action(label, cls, fn) { var b = el("button", "btn " + cls, label); b.type = "button"; b.onclick = fn; box.appendChild(b); return b; }
      function save(path, done) {
        call(path, "POST", encode(look)).then(function (r) {
          if (r.status !== 200) { o.toast(r.body && r.body.error || "Não deu para guardar", "warn"); return; }
          o.toast(done); start = encode(look); if (o.onSaved) o.onSaved();
        }).catch(function () { o.toast("Sem conexão com o celular do fotógrafo", "warn"); });
      }
      if (o.rank === 2) action("Usar no evento, para todos", "primary", function () { save("/api/c/look?m=" + m, "Edição aplicada ao evento"); });
      if (o.rank === 1) action("Guardar como meu look", "primary", function () { save("/api/c/mylook?m=" + m, "Edição guardada como seu look"); });
      action("Mandar ao fotógrafo", "secondary", function () { save("/api/c/share?m=" + m + "&to=fotografo", "Edição mandada ao fotógrafo"); });
      // The other admins in one button: the list opens under it only when wanted.
      if (o.admins && o.admins.length) {
        var list = el("div", "ed-targets"); list.hidden = true;
        o.admins.forEach(function (a) {
          var t = el("button", "btn secondary small", a.label); t.type = "button";
          t.onclick = function () { list.hidden = true; save("/api/c/share?m=" + m + "&to=" + a.id, "Edição mandada a " + a.label); };
          list.appendChild(t);
        });
        action("Mandar a um ADM", "secondary", function () { list.hidden = !list.hidden; });
        box.appendChild(list);
      }
      action("Desfazer tudo", "ghost", function () { look = parse(start); paintPanel(); render(); commit(); });
    }
    paintActions();
  }

  window.JREditor = { open: open, parse: parse, encode: encode };
})();
