// Entrega da igreja: the media team comes in with the code, name and role, sees the event's photos as
// they are taken and downloads the versions the photographer allowed (original, pronta, RAW).
(function () {
  "use strict";
  var $ = JR.$, plural = JR.plural;
  var params = new URLSearchParams(location.search);
  var eventToken = params.get("e");
  var via = eventToken ? (params.get("via") === "qr" ? "qr" : "link") : "address";

  var member = null, me = null, photos = [], version = 0, timer = null, misses = 0, busy = false, firstLoad = true;
  var cells = {}, seen = {}, eventInfo = null, door = null, code = "";
  var VERSIONS = [
    { key: "original", label: "Original", long: "Original da câmera" },
    { key: "pronta", label: "Pronta", long: "Pronta, com o tratamento" },
    { key: "raw", label: "RAW", long: "RAW (CR2)" }
  ];

  // ---------- What this browser remembers ----------
  function read(key) { try { return localStorage.getItem(key); } catch (e) { return null; } }
  function write(key, value) { try { value == null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch (e) {} }
  function eventKey(name) { return "jr_c_" + (eventInfo ? eventInfo.date + "_" + eventInfo.name : "") + "_" + name; }
  function downloaded(kind) { try { return JSON.parse(read(eventKey("dl_" + kind)) || "{}"); } catch (e) { return {}; } }
  function markDownloaded(kind, ids) {
    var all = downloaded(kind);
    ids.forEach(function (id) { all[id] = 1; });
    write(eventKey("dl_" + kind), JSON.stringify(all));
  }

  // ---------- The event's colors ----------
  // The accent takes the place of mint: buttons, the code cells, the "Ao vivo" mark.
  function theme(ev) {
    if (!ev) return;
    var root = document.documentElement.style;
    root.setProperty("--mint", ev.accent);
    root.setProperty("--on-mint", light(ev.accent) ? "#0b0b0b" : "#ffffff");
    root.setProperty("--cyan", ev.secondary);
    if (ev.logo) { var l = $("eventLogo"); if (!l.src) l.src = "/evento-logo"; l.hidden = false; l.alt = ev.congregation || ev.name; $("jrLogo").hidden = true; }
  }
  function light(hex) {
    var n = parseInt(hex.slice(1), 16), r = n >> 16 & 255, g = n >> 8 & 255, b = n & 255;
    return (0.299 * r + 0.587 * g + 0.114 * b) > 150;
  }
  function day(ms) {
    try { return new Date(ms).toLocaleDateString("pt-BR", { day: "numeric", month: "long" }); } catch (e) { return ""; }
  }

  // ---------- The door ----------
  var codeBox = $("codeBox"), codeInput = $("code"), codeCells = codeBox.querySelectorAll(".cells i");

  function paintCode() {
    var v = codeInput.value;
    for (var i = 0; i < 4; i++) {
      var c = codeCells[i], d = v.charAt(i);
      if ((c.dataset.d || "") !== d) { c.dataset.d = d; c.innerHTML = d ? "<span>" + d + "</span>" : ""; }
      c.classList.toggle("next", i === v.length);
    }
  }
  function doorError(message) { $("doorError").textContent = message || ""; }

  function showDoor(message) {
    stopPolling();
    member = null; me = null; photos = []; version = 0; cells = {}; seen = {}; firstLoad = true;
    JR.dropLayers();
    document.body.classList.remove("protected", "veiled");
    $("grid").innerHTML = "";
    $("gallery").hidden = true; $("live").hidden = true; $("leave").hidden = true; $("door").hidden = false;
    $("foot").textContent = "";
    doorError(message);
    hello();
  }

  // The event and what the door asks for. Without an open event the door waits and asks again.
  function hello() {
    JR.get("/api/c/hello" + (eventToken ? "?e=" + encodeURIComponent(eventToken) : "")).then(function (r) {
      var d = r.body || {};
      if (!d.open) return closedDoor("Nenhum evento aberto agora. Esta página atualiza sozinha quando o fotógrafo abrir.");
      eventInfo = d; theme(d);
      $("doorChurch").textContent = [d.congregation, day(d.date)].filter(Boolean).join(" · ");
      $("doorTitle").textContent = d.name;
      $("doorMotto").textContent = d.motto || "";
      document.title = d.name;
      if (d.closed) return closedDoor("O fotógrafo desligou a entrada por link e QR. Peça o endereço ou o código a ele.");
      $("doorClosed").hidden = true;
      door = d;
      if (d.code && code.length !== 4) step("code"); else step("name");
    }).catch(function () { closedDoor("Sem conexão com o celular do fotógrafo. Confira se está no Wi-Fi dele."); });
  }
  function closedDoor(text) {
    $("codeStep").hidden = true; $("nameStep").hidden = true;
    $("doorClosedText").textContent = text; $("doorClosed").hidden = false;
    setTimeout(function () { if (!member && !$("door").hidden) hello(); }, 5000);
  }
  function step(which) {
    $("codeStep").hidden = which !== "code";
    $("nameStep").hidden = which !== "name";
    if (which === "code") { codeInput.value = ""; code = ""; paintCode(); focusSoon(codeInput); }
    else {
      if (!$("name").value) $("name").value = read("jr_c_name") || "";
      if (!$("role").value) $("role").value = read("jr_c_role") || "";
      focusSoon($("name").value ? $("role") : $("name"));
    }
  }
  function focusSoon(el) { if (!document.body.classList.contains("opening")) setTimeout(function () { el.focus(); }, 60); }

  codeInput.addEventListener("input", function () {
    codeInput.value = codeInput.value.replace(/\D/g, "").slice(0, 4);
    codeBox.classList.remove("wrong");
    doorError("");
    paintCode();
    // The code is checked with the name, in one step; four numbers move on to it.
    if (codeInput.value.length === 4) { code = codeInput.value; codeBox.classList.add("ok"); setTimeout(function () { codeBox.classList.remove("ok"); step("name"); }, JR.reduced() ? 0 : 260); }
  });
  codeInput.addEventListener("focus", function () { codeBox.classList.add("focus"); });
  codeInput.addEventListener("blur", function () { codeBox.classList.remove("focus"); });
  $("codeStep").addEventListener("submit", function (e) { e.preventDefault(); });

  $("nameStep").addEventListener("submit", function (e) { e.preventDefault(); join(); });
  $("name").addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); $("role").focus(); } });

  function join() {
    var name = $("name").value.trim(), role = $("role").value.trim();
    if (name.length < 2) { doorError("Escreva o seu nome."); $("name").focus(); return; }
    if (role.length < 2) { doorError("Escreva o seu cargo ou função."); $("role").focus(); return; }
    if (busy) return;
    busy = true; doorError("");
    var btn = $("enter"); btn.className = "btn wait wide"; btn.innerHTML = JR.loader() + "<span>Entrando</span>";
    var q = "via=" + via + "&name=" + encodeURIComponent(name) + "&role=" + encodeURIComponent(role);
    if (eventToken) q += "&e=" + encodeURIComponent(eventToken);
    if (code) q += "&code=" + code;
    var old = read("jr_c_member"); if (old) q += "&m=" + old;
    fetch("/api/c/join?" + q, { method: "POST", cache: "no-store" }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (b) { return { status: r.status, body: b }; });
    }).then(function (r) {
      busy = false; btn.className = "btn primary wide"; btn.textContent = "Entrar";
      if (r.status === 200 && r.body.token) {
        write("jr_c_member", r.body.token); write("jr_c_name", name); write("jr_c_role", role);
        member = r.body.token;
        showGallery();
        return;
      }
      var msg = r.body.error || "Não foi possível entrar.";
      // A wrong code sends the person back to the numbers; everything else stays on the name.
      if (/código/i.test(msg) && door && door.code) { step("code"); codeBox.classList.add("wrong"); }
      doorError(msg);
    }).catch(function () {
      busy = false; btn.className = "btn primary wide"; btn.textContent = "Entrar";
      doorError("Sem conexão com o celular do fotógrafo. Confira se está no Wi-Fi dele.");
    });
  }

  // ---------- Gallery ----------
  function showGallery() {
    $("door").hidden = true; $("gallery").hidden = false; $("leave").hidden = false; $("live").hidden = false;
    refresh(); stopPolling(); timer = setInterval(refresh, 2000);
  }
  function stopPolling() { if (timer) clearInterval(timer); timer = null; }

  function refresh() {
    if (!member) return;
    JR.get("/api/c/state?m=" + member + (version ? "&v=" + version : "")).then(function (r) {
      misses = 0; live(true);
      if (r.status === 401) { write("jr_c_member", null); return showDoor(r.body.gone || "Entre de novo para continuar."); }
      if (r.status !== 200 || r.body.same) return;
      render(r.body);
    }).catch(function () { if (++misses >= 3) live(false); });
  }
  function live(on) {
    var el = $("live");
    el.classList.toggle("lost", !on);
    el.lastChild.textContent = on ? "Ao vivo" : "Reconectando";
  }

  function allowed() { return VERSIONS.filter(function (v) { return me && me[v.key]; }); }
  function chosen() {
    var list = allowed(), saved = read("jr_c_version");
    for (var i = 0; i < list.length; i++) if (list[i].key === saved) return list[i];
    return list[0] || null;
  }

  function render(d) {
    version = d.v; me = d.me; eventInfo = d.event; theme(d.event);
    document.title = d.event.name;
    $("church").textContent = [d.event.congregation, day(d.event.date)].filter(Boolean).join(" · ");
    $("title").textContent = d.event.name;
    $("who").innerHTML = '<i class="mark on" aria-hidden="true"></i><span></span>';
    $("who").lastChild.textContent = me.name + " · " + me.role;
    $("foot").innerHTML = 'Fotos de <span class="brandname">Jr_Tech_OFC</span>, entregues pelo JR C50D Sync';

    var protectedView = allowed().length === 0;
    document.body.classList.toggle("protected", protectedView);
    $("viewOnly").hidden = !protectedView || !me.view;
    var fresh = [];
    d.photos.forEach(function (p) { if (!seen[p.id] && !firstLoad) fresh.push(p.id); seen[p.id] = 1; });
    photos = d.photos;
    paintVersions();
    paintGrid(fresh);
    firstLoad = false;

    var n = photos.length;
    $("count").textContent = n ? plural(n, "foto", "fotos") : "";
    $("empty").hidden = n > 0;
    if (!n) {
      $("emptyArt").innerHTML = JR.symbol("", true);
      $("emptyTitle").textContent = me.view ? "As fotos aparecem aqui" : "Aguardando liberação";
      $("emptyText").textContent = me.view
        ? "Assim que o fotógrafo fizer as fotos, elas chegam nesta página sozinhas. Pode deixar aberta."
        : "O fotógrafo ainda não liberou a visualização para você. Esta página atualiza sozinha.";
    }
    if (fresh.length) JR.toast(fresh.length === 1 ? "Chegou uma foto nova" : fresh.length + " fotos novas");
    viewerApi.refresh();
  }

  function paintVersions() {
    var list = allowed(), box = $("versions"), pick = chosen();
    box.innerHTML = "";
    list.forEach(function (v) {
      var b = document.createElement("button");
      b.type = "button"; b.setAttribute("role", "radio"); b.textContent = v.label; b.title = v.long;
      b.setAttribute("aria-checked", pick && pick.key === v.key ? "true" : "false");
      b.onclick = function () { write("jr_c_version", v.key); paintVersions(); paintGrid([]); viewerApi.refresh(); };
      box.appendChild(b);
    });
    box.hidden = list.length < 2;
    paintSaveNew();
  }

  function pending() {
    var v = chosen();
    if (!v) return [];
    var done = downloaded(v.key);
    return photos.filter(function (p) { return !done[p.id] && (v.key !== "raw" || p.raw); });
  }
  function paintSaveNew() {
    var v = chosen(), list = pending(), b = $("saveNew");
    $("tools").hidden = !v || photos.length === 0;
    b.hidden = !v || list.length === 0;
    if (!v) return;
    b.innerHTML = JR.icons.save + "<span></span>";
    b.lastChild.textContent = list.length === photos.length ? "Baixar todas (" + list.length + ")" : "Baixar " + plural(list.length, "nova", "novas");
  }
  $("saveNew").addEventListener("click", function () {
    var v = chosen(), list = pending();
    if (!v || !list.length) return;
    markDownloaded(v.key, list.map(function (p) { return p.id; }));
    JR.toast("Baixando " + plural(list.length, "foto", "fotos") + " (" + v.label + ")");
    // The name already carries the right ending; the phone sends the same one.
    var named = list.map(function (p) { return { id: p.id, name: p.name + (v.key === "raw" ? ".CR2" : v.key === "pronta" ? "_pronta.jpg" : ".JPG") }; });
    JR.saveMany(named, function (p) { return fileUrl(p, v.key); });
    paintSaveNew(); paintGrid([]);
  });

  function fileUrl(p, kind) { return "/f/" + member + "/" + p.id + "?v=" + kind + "&dl=1"; }

  function paintGrid(fresh) {
    var grid = $("grid"), keep = {}, v = chosen(), done = v ? downloaded(v.key) : {};
    var shielded = document.body.classList.contains("protected");
    photos.forEach(function (p, i) {
      keep[p.id] = 1;
      var c = cells[p.id];
      if (!c || c.shielded !== shielded) {
        if (c) c.el.remove();
        c = cells[p.id] = makeCell(p, shielded);
      }
      c.el.onclick = function () { viewerApi.open(photos.indexOf(p)); };
      c.el.setAttribute("aria-label", "Abrir " + p.name);
      c.tag.hidden = !p.raw; c.tag.textContent = "RAW";
      c.done.hidden = !done[p.id];
      if (fresh.indexOf(p.id) >= 0) {
        c.el.classList.add("new", "flash");
        setTimeout(function () { c.el.classList.remove("flash"); }, 600);
        setTimeout(function () { c.el.classList.remove("new"); }, 8000);
      }
      if (grid.children[i] !== c.el) grid.insertBefore(c.el, grid.children[i] || null);
    });
    Object.keys(cells).forEach(function (id) { if (!keep[id]) { cells[id].el.remove(); delete cells[id]; } });
  }

  // A cell shows the thumbnail; for who cannot download it is drawn on a canvas instead of an <img>.
  function makeCell(p, shielded) {
    var el = document.createElement("button");
    el.className = "cell";
    var pic;
    var src = "/t/" + member + "/" + p.id;
    if (shielded) {
      pic = document.createElement("canvas");
      var im = new Image();
      im.onload = function () {
        pic.width = im.naturalWidth; pic.height = im.naturalHeight;
        pic.getContext("2d").drawImage(im, 0, 0);
        el.style.setProperty("--ratio", (im.naturalWidth / im.naturalHeight).toFixed(3));
        pic.classList.add("loaded");
      };
      im.src = src;
    } else {
      pic = document.createElement("img");
      pic.alt = ""; pic.loading = "lazy"; pic.decoding = "async";
      pic.onload = function () { el.style.setProperty("--ratio", (pic.naturalWidth / pic.naturalHeight).toFixed(3)); pic.classList.add("loaded"); };
      pic.src = src;
    }
    var tag = document.createElement("span"); tag.className = "tag";
    var doneEl = document.createElement("i"); doneEl.className = "mark on done"; doneEl.title = "Já baixada";
    el.appendChild(pic); el.appendChild(tag); el.appendChild(doneEl);
    return { el: el, tag: tag, done: doneEl, shielded: shielded };
  }

  // ---------- Viewer (a canvas, so the big picture is never a saveable image) ----------
  var viewerApi = (function () {
    var root = $("viewer"), canvas = $("big"), stage = $("viewerStage"), current = 0, opener = null, sx = null, dx = 0, loading = null;
    $("close").innerHTML = JR.icons.close; $("prev").innerHTML = JR.icons.prev; $("next").innerHTML = JR.icons.next;
    $("bigWait").innerHTML = JR.loader();

    function draw(im) {
      var ratio = window.devicePixelRatio || 1, w = stage.clientWidth, h = stage.clientHeight;
      var scale = Math.min(w / im.naturalWidth, h / im.naturalHeight);
      var cw = Math.round(im.naturalWidth * scale), ch = Math.round(im.naturalHeight * scale);
      canvas.width = Math.round(cw * ratio); canvas.height = Math.round(ch * ratio);
      canvas.style.width = cw + "px"; canvas.style.height = ch + "px";
      canvas.getContext("2d").drawImage(im, 0, 0, canvas.width, canvas.height);
    }
    // Original or Pronta on the big picture, remembered for the next photos.
    var lookV = read("jr_c_look") === "pronta" ? "pronta" : "";
    function paintLook() {
      var buttons = $("look").querySelectorAll("button");
      for (var i = 0; i < buttons.length; i++) buttons[i].setAttribute("aria-checked", buttons[i].getAttribute("data-v") === lookV ? "true" : "false");
    }
    $("look").addEventListener("click", function (e) {
      var b = e.target.closest ? e.target.closest("button") : null;
      if (!b) return;
      lookV = b.getAttribute("data-v") || "";
      write("jr_c_look", lookV || null);
      paintLook(); paint();
    });
    paintLook();

    function paint() {
      var n = photos.length;
      if (!n) { if (!root.hidden) JR.closeLayer(); return; }
      current = Math.max(0, Math.min(current, n - 1));
      var p = photos[current], src = "/p/" + member + "/" + p.id + (lookV ? "?v=" + lookV : "");
      if (canvas.dataset.src !== src) {
        canvas.dataset.src = src;
        canvas.classList.add("wait"); $("bigWait").hidden = false;
        var im = new Image();
        loading = im;
        im.onload = function () { if (loading !== im) return; draw(im); canvas.classList.remove("wait"); $("bigWait").hidden = true; canvas._im = im; };
        im.onerror = function () { if (loading === im) { $("bigWait").hidden = true; JR.toast("Não foi possível abrir esta foto", "warn"); } };
        im.src = src;
      }
      $("pos").textContent = (current + 1) + " de " + n;
      $("prev").disabled = current === 0;
      $("next").disabled = current === n - 1;
      paintDownloads(p);
    }
    function paintDownloads(p) {
      var box = $("downloads"), list = allowed(), pick = chosen();
      box.innerHTML = "";
      if (!list.length) { box.innerHTML = '<span class="only">Só visualização</span>'; return; }
      list.forEach(function (v) {
        var missing = v.key === "raw" && !p.raw;
        var a = document.createElement("a");
        a.className = "btn " + (missing ? "wait" : pick && pick.key === v.key ? "primary" : "secondary");
        a.innerHTML = (missing ? "" : JR.icons.save) + "<span></span>";
        a.lastChild.textContent = missing ? "RAW a caminho" : v.label;
        if (!missing) {
          a.href = fileUrl(p, v.key); a.setAttribute("download", "");
          a.onclick = function () { markDownloaded(v.key, [p.id]); paintSaveNew(); paintGrid([]); };
        }
        box.appendChild(a);
      });
    }
    function go(step) {
      var to = Math.max(0, Math.min(current + step, photos.length - 1));
      if (to !== current) { current = to; paint(); }
    }
    function open(i) {
      opener = document.activeElement; current = i; canvas.dataset.src = "";
      JR.openLayer(root, function () { if (opener && opener.focus) opener.focus(); });
      paint(); $("close").focus();
    }
    $("close").onclick = JR.closeLayer;
    $("prev").onclick = function () { go(-1); };
    $("next").onclick = function () { go(1); };
    document.addEventListener("keydown", function (e) {
      if (root.hidden) return;
      if (e.key === "Escape") JR.closeLayer();
      if (e.key === "ArrowLeft") go(-1);
      if (e.key === "ArrowRight") go(1);
    });
    stage.addEventListener("touchstart", function (e) { sx = e.touches.length === 1 ? e.touches[0].clientX : null; dx = 0; }, { passive: true });
    stage.addEventListener("touchmove", function (e) { if (sx !== null) dx = e.touches[0].clientX - sx; }, { passive: true });
    stage.addEventListener("touchend", function () { if (sx !== null && Math.abs(dx) > 60) go(dx < 0 ? 1 : -1); sx = null; });
    window.addEventListener("resize", function () { if (!root.hidden && canvas._im) draw(canvas._im); });
    return { open: open, refresh: function () { if (!root.hidden) paint(); } };
  })();

  // ---------- Protections for who cannot download ----------
  // A page cannot stop a phone screenshot; it can keep the picture out of the save and share menus,
  // hide it when the person leaves the page, and every preview carries the person's name.
  function guarded() { return document.body.classList.contains("protected"); }
  ["contextmenu", "dragstart", "selectstart", "copy"].forEach(function (type) {
    document.addEventListener(type, function (e) {
      if (!guarded() || type === "selectstart" && e.target.tagName === "INPUT") return;
      e.preventDefault();
    });
  });
  function veil(on) { document.body.classList.toggle("veiled", on && guarded()); }
  window.addEventListener("blur", function () { veil(true); });
  window.addEventListener("focus", function () { veil(false); });
  document.addEventListener("visibilitychange", function () { veil(document.hidden); });
  document.addEventListener("keyup", function (e) {
    if (guarded() && e.key === "PrintScreen") { veil(true); JR.toast("Esta foto é só para visualização", "warn"); setTimeout(function () { veil(false); }, 1500); }
  });

  $("leave").addEventListener("click", function () {
    write("jr_c_member", null);
    showDoor("");
  });

  // ---------- Start ----------
  JR.topBar($("top"));
  JR.intro(function () {
    member = read("jr_c_member");
    if (member) {
      // This device came in before: straight to the photos, or back to the door when it can't anymore.
      hello(); $("door").hidden = true; showGallery();
    } else showDoor("");
  });
})();
