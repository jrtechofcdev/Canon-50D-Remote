// Entrega local: the client types their code and gets their photos, served by the photographer's phone.
(function () {
  "use strict";
  var $ = JR.$, plural = JR.plural;
  var token = null, data = null, known = {}, firstLoad = true, timer = null, busy = false;
  var ratios = {}, cells = {};
  var ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

  function store(t) { try { t ? localStorage.setItem("jr_token", t) : localStorage.removeItem("jr_token"); } catch (e) {} }
  function stored() { try { return localStorage.getItem("jr_token"); } catch (e) { return null; } }

  function file(item) { return "/f/" + token + "/" + item.id + "?dl=1"; }
  // The big picture: the clean photo, or only its marked preview while it is locked.
  function view(item) { return "/p/" + token + "/" + item.id + (item.locked ? "?m=1" : ""); }
  function thumb(item) { return "/t/" + token + "/" + item.id + (item.locked ? "?m=1" : ""); }

  function saveButton(a, item) {
    if (item.locked) {
      a.removeAttribute("href"); a.removeAttribute("download");
      a.className = "btn wait"; a.innerHTML = JR.icons.lock + "<span>Libera após o pagamento</span>";
    } else {
      a.href = file(item); a.setAttribute("download", item.name);
      a.className = "btn primary"; a.innerHTML = JR.icons.save + "<span>Salvar foto</span>";
    }
  }

  // ---------- Code ----------
  var codeBox = $("codeBox"), input = $("code"), gateSymbol = $("gateSymbol");
  gateSymbol.innerHTML = JR.symbol("", true);
  var blocks = gateSymbol.querySelectorAll("path"), cellEls = codeBox.querySelectorAll(".cells i");

  function paintCode() {
    var v = input.value;
    for (var i = 0; i < 4; i++) {
      var c = cellEls[i], d = v.charAt(i);
      if ((c.dataset.d || "") !== d) { c.dataset.d = d; c.innerHTML = d ? "<span>" + d + "</span>" : ""; }
      c.classList.toggle("next", i === v.length);
      blocks[i].classList.toggle("on", i < v.length);
    }
  }
  function wrong(message) {
    $("loginError").textContent = message;
    codeBox.classList.remove("wrong"); void codeBox.offsetWidth; codeBox.classList.add("wrong");
    gateSymbol.classList.remove("busy");
    gateSymbol.classList.add("wrong");
    setTimeout(function () { gateSymbol.classList.remove("wrong"); }, 900);
    input.value = ""; paintCode(); input.focus();
  }

  function showLogin(message) {
    stop(); token = null; data = null; store(null); known = {}; cells = {};
    JR.dropLayers();
    $("grid").innerHTML = "";
    $("gallery").hidden = true; $("login").hidden = false; $("leave").hidden = true;
    $("foot").textContent = "";
    document.title = "Suas fotos";
    codeBox.classList.remove("ok", "wrong"); gateSymbol.classList.remove("busy");
    input.value = ""; paintCode();
    $("loginError").textContent = message || "";
    // During the opening the keyboard would cover it; the opening focuses the code when it ends.
    if (!document.body.classList.contains("opening")) setTimeout(function () { input.focus(); }, 60);
  }

  function login() {
    var code = input.value.replace(/\D/g, "");
    if (code.length !== 4) { wrong("O código tem 4 números."); return; }
    if (busy) return;
    busy = true;
    $("loginError").textContent = "";
    gateSymbol.classList.add("busy");
    JR.get("/api/login?code=" + code).then(function (r) {
      busy = false;
      if (r.status === 200 && r.body.token) {
        // All four blocks lit, the cells turn mint, then the photos.
        gateSymbol.classList.remove("busy");
        codeBox.classList.add("ok");
        token = r.body.token; store(token); known = {}; firstLoad = true;
        setTimeout(showGallery, JR.reduced() ? 0 : 420);
      } else wrong(r.body.error || "Código não encontrado. Confira os números com o fotógrafo.");
    }).catch(function () { busy = false; wrong("Sem conexão com o celular do fotógrafo. Confira se está no Wi-Fi dele."); });
  }

  $("codeForm").addEventListener("submit", function (e) { e.preventDefault(); login(); });
  input.addEventListener("input", function () {
    input.value = input.value.replace(/\D/g, "").slice(0, 4);
    codeBox.classList.remove("wrong");
    if ($("loginError").textContent) $("loginError").textContent = "";
    paintCode();
    // Four numbers typed: no need to look for the button.
    if (input.value.length === 4) login();
  });
  input.addEventListener("focus", function () { codeBox.classList.add("focus"); });
  input.addEventListener("blur", function () { codeBox.classList.remove("focus"); });

  // ---------- Gallery ----------
  function showGallery() {
    $("login").hidden = true; $("gallery").hidden = false; $("leave").hidden = false;
    $("iosHint").hidden = !ios;
    refresh(); stop(); timer = setInterval(refresh, 2000);
  }
  function stop() { if (timer) clearInterval(timer); timer = null; }

  function refresh() {
    if (!token) return;
    JR.get("/api/s/" + token).then(function (r) {
      if (r.status === 404) return showLogin("Este acesso foi encerrado. Peça um código novo ao fotógrafo.");
      if (r.status !== 200) return;
      render(r.body);
    }).catch(function () { /* the Wi-Fi blinked; the next round tries again */ });
  }

  function render(d) {
    var fresh = [], released = 0;
    d.items.forEach(function (it) {
      if (!(it.id in known)) { if (!firstLoad) fresh.push(it.id); }
      else if (known[it.id] === true && !it.locked) released++;
      known[it.id] = !!it.locked;
    });
    data = d;

    var name = JR.firstWord(d.client);
    $("eyebrow").textContent = d.event ? "Fotos do evento" : JR.greeting() + ",";
    $("hello").textContent = d.event ? d.client : name;
    $("count").textContent = d.items.length ? plural(d.items.length, "foto pronta", "fotos prontas") : "Fotos a caminho";
    $("by").textContent = d.photographer ? "por " + d.photographer : "";
    $("foot").innerHTML = 'Fotos de <span></span> · entregues pelo <span class="brandname">JR C50D Sync</span>';
    $("foot").firstElementChild.textContent = d.photographer || "seu fotógrafo";
    document.title = d.event ? d.client : "Fotos de " + d.client;

    var w = $("waiting");
    w.hidden = !d.waiting;
    if (d.waiting) {
      if (!w.firstChild) w.innerHTML = JR.loader() + "<span></span>";
      w.lastChild.textContent = plural(d.waiting, "foto sendo preparada", "fotos sendo preparadas");
    }

    paintHero(d, fresh);

    var free = d.items.filter(function (i) { return !i.locked; });
    $("saveAll").hidden = ios || free.length < 2;
    $("all").hidden = d.items.length < 2;
    $("allCount").textContent = d.items.length;
    $("empty").hidden = d.items.length > 0 || d.waiting > 0;
    if (!$("empty").hidden && !$("emptyArt").firstChild) $("emptyArt").innerHTML = JR.loader();

    paintGrid(d, fresh);

    if (released) JR.toast(released === 1 ? "Foto liberada: já pode salvar" : plural(released, "foto liberada", "fotos liberadas") + ": já pode salvar");
    else if (fresh.length) JR.toast(fresh.length === 1 ? "Chegou uma foto nova" : "Chegaram " + plural(fresh.length, "foto nova", "fotos novas"));
    firstLoad = false;
    gallery.refresh();
  }

  function paintHero(d, fresh) {
    var hero = d.items[0];
    $("hero").hidden = !hero;
    if (!hero) return;
    var img = $("heroImg"), key = hero.id + (hero.locked ? "l" : "");
    if (img.dataset.id !== key) {
      var replacing = !!img.dataset.id;
      img.dataset.id = key;
      img.src = view(hero);
      img.classList.toggle("locked-img", !!hero.locked);
      // A new photo arriving while the page is open: the short shutter flash.
      if (replacing && fresh.indexOf(hero.id) >= 0) flash($("heroOpen"));
    }
    $("heroTag").innerHTML = '<i class="mark on" aria-hidden="true"></i><span></span>';
    $("heroTag").lastChild.textContent = d.items.length === 1 ? "Sua foto está pronta" : "Mais recente";
    $("heroTitle").textContent = d.items.length === 1 ? "Sua foto está pronta" : "A mais recente de " + d.items.length;
    saveButton($("heroSave"), hero);
    pickButton($("heroPick"), hero, d.picks);
  }

  function flash(el) { el.classList.remove("flash"); void el.offsetWidth; el.classList.add("flash"); }

  // Cells are kept by photo and only moved, so the grid never blinks on the 2-second refresh.
  function paintGrid(d, fresh) {
    var grid = $("grid"), keep = {};
    d.items.forEach(function (it, index) {
      var key = it.id + (it.locked ? "l" : "");
      var c = cells[key];
      if (!c) {
        c = document.createElement("button");
        c.className = "cell";
        var img = document.createElement("img");
        img.alt = ""; img.loading = index < 12 ? "eager" : "lazy"; img.decoding = "async";
        img.onload = function () {
          // Each photo keeps its own shape in the row.
          var r = img.naturalWidth / img.naturalHeight;
          if (r > 0) { ratios[it.id] = r; c.style.setProperty("--ratio", r.toFixed(3)); }
          img.classList.add("loaded");
        };
        img.src = thumb(it);
        if (it.locked) img.classList.add("locked-img");
        c.appendChild(img);
        if (ratios[it.id]) c.style.setProperty("--ratio", ratios[it.id].toFixed(3));
        if (fresh.indexOf(it.id) >= 0) { c.classList.add("new"); flash(c); }
        cells[key] = c;
      }
      c.setAttribute("aria-label", "Abrir foto " + (d.items.length - index) + (it.picked ? ", favorita" : ""));
      c.onclick = function () { gallery.open(index); };
      setBadge(c, ".badge", it.picked, function (b) { b.className = "badge"; b.innerHTML = JR.icons.heart; });
      setBadge(c, ".tag", it.locked || it.version, function (b) { b.className = "tag"; });
      var t = c.querySelector(".tag");
      if (t) t.textContent = it.locked ? "Prévia" : (it.label || "Versão " + it.version);
      keep[key] = true;
      // Moved only when out of place: moving a cell restarts its animations.
      if (grid.children[index] !== c) grid.insertBefore(c, grid.children[index] || null);
    });
    Object.keys(cells).forEach(function (k) { if (!keep[k]) { cells[k].remove(); delete cells[k]; } });
  }

  function setBadge(cell, sel, want, make) {
    var b = cell.querySelector(sel);
    if (want && !b) { b = document.createElement("span"); make(b); b.setAttribute("aria-hidden", "true"); cell.appendChild(b); }
    else if (!want && b) b.remove();
  }

  function pickButton(btn, item, enabled) {
    btn.hidden = !enabled;
    if (!enabled) return;
    JR.heart(btn, item.picked);
    btn.onclick = function () {
      var on = !item.picked;
      JR.heart(btn, on); JR.popHeart(btn);
      fetch("/api/s/" + token + "/pick/" + item.id + "?on=" + (on ? 1 : 0), { method: "POST" }).then(function (r) {
        if (r.ok) {
          item.picked = on;
          var name = data && !data.event ? JR.firstWord(data.client) : null;
          JR.toast(on ? (name ? "Obrigado, " + name + "! O fotógrafo já viu sua escolha." : "Marcada como favorita") : "Favorita desmarcada", on ? "on" : "off");
          refresh();
        } else { JR.heart(btn, item.picked); JR.toast("Não deu para marcar agora. Tente de novo.", "warn"); }
      }).catch(function () { JR.heart(btn, item.picked); JR.toast("Sem conexão com o celular do fotógrafo", "warn"); });
    };
  }

  var gallery = JR.viewer({
    count: function () { return data ? data.items.length : 0; },
    src: function (i) { return view(data.items[i]); },
    paint: function (i) {
      var it = data.items[i];
      $("big").classList.toggle("locked-img", !!it.locked);
      saveButton($("save"), it);
      pickButton($("pick"), it, data.picks);
    }
  });

  $("heroOpen").onclick = function () { gallery.open(0); };
  $("leave").onclick = function () { showLogin(""); };
  $("saveAll").onclick = function () {
    if (!data) return;
    var free = data.items.filter(function (i) { return !i.locked; });
    var btn = $("saveAll");
    btn.className = "btn wait small"; btn.textContent = "Salvando " + free.length + "…";
    JR.toast("Salvando " + plural(free.length, "foto", "fotos") + ". Pode aceitar os downloads.");
    JR.saveMany(free, file, function () { btn.className = "btn secondary small"; btn.textContent = "Salvar todas"; });
  };
  JR.topBar($("top"));

  // ---------- Start ----------
  var fromLink = new URLSearchParams(location.search).get("s");
  JR.intro(function () { if (!$("login").hidden) input.focus(); });
  if (fromLink && /^[0-9a-f]{32}$/.test(fromLink)) {
    token = fromLink; store(token);
    history.replaceState(null, "", "/");
    showGallery();
  } else if (stored()) {
    token = stored(); showGallery();
  } else showLogin("");
})();
