// JR C50D Sync client pages: what the Entrega local and the Fotografia Express pages share.
// Plain JavaScript, no libraries: the page comes from the photographer's phone, without internet.
(function () {
  "use strict";

  // The four blocks of the Jr_Tech_OFC symbol (res/drawable/ic_brand_symbol.xml), clockwise from top left.
  var BLOCKS = [
    "M151.4,40L332.4,40Q341.4,40 339,48.7L289,226.3Q286.6,235 277.6,235L96.6,235Q87.6,235 90,226.3L140,48.7Q142.4,40 151.4,40Z",
    "M347.4,125L441.4,125Q450.4,125 448,133.7L422,226.3Q419.6,235 410.6,235L316.6,235Q307.6,235 310,226.3L336,133.7Q338.4,125 347.4,125Z",
    "M221.4,264L402.4,264Q411.4,264 409,272.7L359,450.3Q356.6,459 347.6,459L166.6,459Q157.6,459 160,450.3L210,272.7Q212.4,264 221.4,264Z",
    "M88.4,264L182.4,264Q191.4,264 189,272.7L163,365.3Q160.6,374 151.6,374L57.6,374Q48.6,374 51,365.3L77,272.7Q79.4,264 88.4,264Z"
  ];
  var gradients = 0;

  /** The symbol as inline SVG. With [gradient], blocks are filled mint to cyan (identity only). */
  function symbol(cls, gradient) {
    var id = "jrg" + (++gradients);
    var defs = gradient
      ? '<defs><linearGradient id="' + id + '" gradientUnits="userSpaceOnUse" x1="150" y1="40" x2="330" y2="460">' +
        '<stop offset="0" stop-color="#00FCA8"/><stop offset="1" stop-color="#1CDAFF"/></linearGradient></defs>'
      : "";
    var fill = gradient ? ' fill="url(#' + id + ')"' : "";
    return '<svg class="symbol ' + (cls || "") + '" viewBox="44 36 410 428" aria-hidden="true" focusable="false">' + defs +
      BLOCKS.map(function (d) { return '<path d="' + d + '"' + fill + "/>"; }).join("") + "</svg>";
  }

  function loader() { return symbol("loader", false); }

  // Small line icons, drawn for these pages (24 px grid).
  var ICONS = {
    heart: '<svg viewBox="0 0 24 24" aria-hidden="true"><path class="fill" d="M12 20.3l-1.3-1.2C5.9 14.8 3 12.2 3 8.9 3 6.3 5 4.3 7.6 4.3c1.7 0 3.3.8 4.4 2.1 1.1-1.3 2.7-2.1 4.4-2.1C19 4.3 21 6.3 21 8.9c0 3.3-2.9 5.9-7.7 10.2L12 20.3z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>',
    close: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none"/></svg>',
    prev: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>',
    next: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>',
    save: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11m0 0l-4.5-4.5M12 15l4.5-4.5M5 19.5h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>',
    lock: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7.5 10.5V8a4.5 4.5 0 019 0v2.5M6 10.5h12v9H6z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" fill="none"/></svg>'
  };

  function $(id) { return document.getElementById(id); }
  function plural(n, one, many) { return n + " " + (n === 1 ? one : many); }
  function greeting() { var h = new Date().getHours(); return h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite"; }
  function firstWord(s) { return (s || "").trim().split(/\s+/)[0]; }
  function get(url) {
    return fetch(url, { cache: "no-store" }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (body) { return { status: r.status, body: body }; });
    });
  }
  function reduced() { return window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches; }
  function remember(key, value) { try { value === undefined ? sessionStorage.setItem(key, "1") : localStorage.setItem(key, value); } catch (e) {} }
  function seen(key) { try { return sessionStorage.getItem(key) === "1"; } catch (e) { return false; } }

  // ---------- Toast ----------
  function toast(text, kind) {
    var el = $("toast");
    if (!el) return;
    el.innerHTML = '<i class="mark ' + (kind || "on") + '" aria-hidden="true"></i><span></span>';
    el.lastChild.textContent = text;
    el.classList.add("show");
    clearTimeout(el._t);
    el._t = setTimeout(function () { el.classList.remove("show"); }, 2800);
  }

  // ---------- Opening ----------
  // Under 2.5 s, once per visit, skipped with a tap, and not at all with reduced motion.
  function intro(done) {
    var body = document.body;
    if (reduced() || seen("jr_intro")) { body.classList.remove("opening"); if (done) done(); return; }
    remember("jr_intro");
    var el = document.createElement("div");
    el.className = "intro";
    el.setAttribute("role", "presentation");
    el.innerHTML = symbol("", true) +
      '<div class="wordmark"><b>Jr_Tech_OFC</b><span>Nossa Arte, Seu Sucesso</span></div>' +
      '<span class="skip">Toque para pular</span>';
    body.appendChild(el);
    var timers = [], finished = false;
    function at(ms, fn) { timers.push(setTimeout(fn, ms)); }
    function finish() {
      if (finished) return;
      finished = true;
      timers.forEach(clearTimeout);
      el.classList.add("in", "lit", "name", "out");
      body.classList.remove("opening");
      setTimeout(function () { el.remove(); }, 450);
      if (done) done();
    }
    // Two frames so the starting positions are painted before the blocks move.
    requestAnimationFrame(function () { requestAnimationFrame(function () { el.classList.add("in"); }); });
    at(520, function () { el.classList.add("lit"); });
    at(1000, function () { el.classList.add("name"); });
    at(2200, finish);
    el.addEventListener("click", finish);
  }

  // ---------- Layers that the phone's Back button closes ----------
  // Opening a layer adds a history entry; Back (or the close button) takes it away.
  var layers = [];
  function openLayer(el, onClose) {
    el.hidden = false;
    layers.push({ el: el, onClose: onClose });
    try { history.pushState({ layer: layers.length }, ""); } catch (e) {}
  }
  function closeLayer() {
    if (!layers.length) return;
    try { history.back(); } catch (e) { pop(); }
  }
  function pop() {
    var top = layers.pop();
    if (!top) return;
    top.el.hidden = true;
    if (top.onClose) top.onClose();
  }
  window.addEventListener("popstate", pop);
  /** Closes every open layer at once, without touching history (used when the page resets). */
  function dropLayers() { while (layers.length) { var l = layers.pop(); l.el.hidden = true; } }

  // ---------- Viewer ----------
  // The page tells it how many photos there are and paints one; the viewer does the rest:
  // arrows, keys, swipe with the finger, Back to close, neighbours loaded ahead.
  function viewer(opts) {
    var root = $("viewer"), img = $("big"), stage = root.querySelector(".viewer-stage");
    var current = 0, opener = null, sx = null, dx = 0;
    $("close").innerHTML = ICONS.close;
    $("prev").innerHTML = ICONS.prev;
    $("next").innerHTML = ICONS.next;

    function count() { return opts.count(); }
    function paint(direction) {
      var n = count();
      if (!n) { if (!root.hidden) closeLayer(); return; }
      current = Math.max(0, Math.min(current, n - 1));
      var src = opts.src(current);
      if (img.dataset.src !== src) {
        img.dataset.src = src;
        if (direction && !reduced()) {
          // Slide in from the side the finger came from.
          img.classList.add("dragging");
          img.style.transform = "translateX(" + (direction * 48) + "px)";
          img.style.opacity = "0";
          img.src = src;
          requestAnimationFrame(function () { requestAnimationFrame(function () {
            img.classList.remove("dragging"); img.style.transform = ""; img.style.opacity = "";
          }); });
        } else img.src = src;
      }
      $("pos").textContent = (current + 1) + " de " + n;
      $("prev").disabled = current === 0;
      $("next").disabled = current === n - 1;
      opts.paint(current);
      [current - 1, current + 1].forEach(function (i) { if (i >= 0 && i < n) { var p = new Image(); p.src = opts.src(i); } });
    }
    function go(step) {
      var to = Math.max(0, Math.min(current + step, count() - 1));
      if (to === current) { img.style.transform = ""; return; }
      current = to; paint(step);
    }
    function open(index) {
      opener = document.activeElement;
      current = index;
      img.dataset.src = "";
      openLayer(root, function () { if (opener && opener.focus) opener.focus(); });
      paint(0);
      $("close").focus();
    }

    $("close").onclick = closeLayer;
    $("prev").onclick = function () { go(-1); };
    $("next").onclick = function () { go(1); };
    document.addEventListener("keydown", function (e) {
      if (root.hidden) return;
      if (e.key === "Escape") closeLayer();
      if (e.key === "ArrowLeft") go(-1);
      if (e.key === "ArrowRight") go(1);
    });
    stage.addEventListener("touchstart", function (e) {
      if (e.touches.length !== 1) { sx = null; return; }
      sx = e.touches[0].clientX; dx = 0; img.classList.add("dragging");
    }, { passive: true });
    stage.addEventListener("touchmove", function (e) {
      if (sx === null || e.touches.length !== 1) return;
      dx = e.touches[0].clientX - sx;
      img.style.transform = "translateX(" + dx + "px)";
    }, { passive: true });
    stage.addEventListener("touchend", function () {
      if (sx === null) return;
      sx = null; img.classList.remove("dragging");
      if (Math.abs(dx) > 60) go(dx < 0 ? 1 : -1); else img.style.transform = "";
    });
    return { open: open, refresh: function () { if (!root.hidden) paint(0); }, index: function () { return current; }, isOpen: function () { return !root.hidden; } };
  }

  // A top bar that shows its line only once the page scrolls under it.
  function topBar(el) {
    if (!el) return;
    var on = false;
    window.addEventListener("scroll", function () {
      var now = window.scrollY > 4;
      if (now !== on) { on = now; el.classList.toggle("scrolled", on); }
    }, { passive: true });
  }

  /** Saves several photos one after the other (browsers block a burst of downloads). */
  function saveMany(list, url, done) {
    list.forEach(function (it, i) {
      setTimeout(function () {
        var a = document.createElement("a");
        a.href = url(it); a.download = it.name;
        document.body.appendChild(a); a.click(); a.remove();
        if (i === list.length - 1 && done) done();
      }, i * 900);
    });
  }

  function heart(btn, on) {
    if (!btn.firstChild) btn.innerHTML = ICONS.heart;
    btn.classList.toggle("on", !!on);
    btn.setAttribute("aria-pressed", on ? "true" : "false");
    btn.setAttribute("aria-label", on ? "Desmarcar favorita" : "Marcar como favorita");
  }
  function popHeart(btn) {
    btn.classList.remove("pop"); void btn.offsetWidth; btn.classList.add("pop");
  }

  window.JR = {
    $: $, symbol: symbol, loader: loader, icons: ICONS, plural: plural, greeting: greeting, firstWord: firstWord,
    get: get, toast: toast, intro: intro, viewer: viewer, openLayer: openLayer, closeLayer: closeLayer, dropLayers: dropLayers,
    topBar: topBar, saveMany: saveMany, heart: heart, popHeart: popHeart, reduced: reduced
  };
})();
