// Portal of the JR C50D Sync local delivery. Runs offline, served by the photographer's phone.
(function () {
  "use strict";
  var $ = function (id) { return document.getElementById(id); };
  var token = null, data = null, known = {}, firstLoad = true, current = 0, timer = null;
  var ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

  function store(t) { try { t ? localStorage.setItem("jr_token", t) : localStorage.removeItem("jr_token"); } catch (e) {} }
  function stored() { try { return localStorage.getItem("jr_token"); } catch (e) { return null; } }

  function toast(text) {
    var el = $("toast"); el.textContent = text; el.classList.add("show");
    clearTimeout(el._t); el._t = setTimeout(function () { el.classList.remove("show"); }, 2600);
  }

  function get(url) {
    return fetch(url, { cache: "no-store" }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (body) { return { status: r.status, body: body }; });
    });
  }

  function plural(n, one, many) { return n + " " + (n === 1 ? one : many); }
  function file(item, dl) { return "/f/" + token + "/" + item.id + (dl ? "?dl=1" : ""); }
  // The big picture: the clean photo, or only its marked preview while it is locked.
  function view(item) { return "/p/" + token + "/" + item.id + (item.locked ? "?m=1" : ""); }
  function saveButton(a, item) {
    if (item.locked) {
      a.removeAttribute("href"); a.removeAttribute("download");
      a.classList.add("wait"); a.textContent = "Prévia: libera após pagar";
    } else {
      a.href = file(item, true); a.setAttribute("download", item.name);
      a.classList.remove("wait"); a.textContent = "Salvar foto";
    }
  }
  function thumb(item) { return "/t/" + token + "/" + item.id; }

  function showLogin(message) {
    stop(); token = null; store(null);
    $("gallery").hidden = true; $("login").hidden = false; $("leave").hidden = true;
    $("loginError").textContent = message || "";
    setTimeout(function () { $("code").focus(); }, 50);
  }

  function showGallery() {
    $("login").hidden = true; $("gallery").hidden = false; $("leave").hidden = false;
    $("iosHint").hidden = !ios;
    refresh(); stop(); timer = setInterval(refresh, 3000);
  }

  function stop() { if (timer) clearInterval(timer); timer = null; }

  function refresh() {
    if (!token) return;
    get("/api/s/" + token).then(function (r) {
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
    var name = d.event ? d.client : "Olá, " + d.client.split(" ")[0];
    $("hello").textContent = name;
    $("count").textContent = d.items.length ? plural(d.items.length, "foto pronta", "fotos prontas") + " · " + d.photographer : d.photographer;
    $("foot").textContent = "Fotos de " + d.photographer + " · entregues pelo JR C50D Sync";
    document.title = d.event ? d.client : "Fotos de " + d.client;

    var w = $("waiting");
    w.hidden = !d.waiting;
    w.textContent = d.waiting ? plural(d.waiting, "foto sendo preparada", "fotos sendo preparadas") + "…" : "";

    var hero = d.items[0];
    $("hero").hidden = !hero;
    if (hero) {
      $("heroTag").textContent = d.items.length === 1 ? "Sua foto está pronta!" : "Foto mais recente";
      var heroKey = hero.id + (hero.locked ? "l" : "");
      if ($("heroImg").dataset.id !== heroKey) { $("heroImg").src = view(hero); $("heroImg").dataset.id = heroKey; }
      $("heroImg").classList.toggle("locked-img", !!hero.locked);
      saveButton($("heroSave"), hero);
      setPick($("heroPick"), hero, d.picks);
    }
    $("saveAll").hidden = ios || d.items.filter(function (i) { return !i.locked; }).length < 2;
    $("empty").hidden = d.items.length > 0;

    var grid = $("grid");
    var ids = d.items.map(function (i) { return i.id + (i.locked ? "l" : "") + (i.picked ? "p" : ""); }).join(",");
    if (grid.dataset.ids !== ids) {
      grid.dataset.ids = ids; grid.innerHTML = "";
      d.items.forEach(function (it, index) {
        var b = document.createElement("button");
        b.className = "cell" + (fresh.indexOf(it.id) >= 0 ? " new" : "");
        b.setAttribute("aria-label", it.name);
        var img = document.createElement("img");
        img.loading = "lazy"; img.alt = ""; img.onload = function () { img.classList.add("loaded"); };
        img.src = thumb(it) + (it.locked ? "?m=1" : "");
        if (it.locked) img.classList.add("locked-img");
        b.appendChild(img);
        if (it.locked) { var l = document.createElement("span"); l.className = "lock"; l.textContent = "Prévia"; b.appendChild(l); }
        if (it.picked) { var s = document.createElement("span"); s.className = "badge"; s.textContent = "♥"; b.appendChild(s); }
        b.onclick = function () { open(index); };
        grid.appendChild(b);
      });
    }
    if (released) toast(released === 1 ? "Foto liberada: já pode salvar" : plural(released, "foto liberada", "fotos liberadas") + ": já pode salvar");
    else if (fresh.length) toast(fresh.length === 1 ? "Foto nova chegou" : plural(fresh.length, "foto nova", "fotos novas") + " chegaram");
    firstLoad = false;
    if (!$("viewer").hidden) show();
  }

  function setPick(btn, item, enabled) {
    btn.hidden = !enabled;
    btn.textContent = item.picked ? "♥" : "♡";
    btn.classList.toggle("on", item.picked);
    btn.setAttribute("aria-pressed", item.picked ? "true" : "false");
    btn.onclick = function () {
      var on = !item.picked;
      fetch("/api/s/" + token + "/pick/" + item.id + "?on=" + (on ? 1 : 0), { method: "POST" }).then(function (r) {
        if (r.ok) { item.picked = on; setPick(btn, item, enabled); toast(on ? "Marcada como favorita" : "Desmarcada"); refresh(); }
      });
    };
  }

  function open(index) { current = index; $("viewer").hidden = false; show(); }

  function show() {
    if (!data || !data.items.length) { $("viewer").hidden = true; return; }
    current = Math.max(0, Math.min(current, data.items.length - 1));
    var it = data.items[current];
    var key = it.id + (it.locked ? "l" : "");
    if ($("big").dataset.id !== key) { $("big").src = view(it); $("big").dataset.id = key; }
    $("big").classList.toggle("locked-img", !!it.locked);
    saveButton($("save"), it);
    $("position").textContent = (current + 1) + " de " + data.items.length;
    $("prev").disabled = current === 0; $("next").disabled = current === data.items.length - 1;
    setPick($("pick"), it, data.picks);
  }

  $("codeForm").addEventListener("submit", function (e) {
    e.preventDefault();
    var code = $("code").value.replace(/\D/g, "");
    if (code.length !== 4 && code.length !== 6) { $("loginError").textContent = "O código tem 4 números."; return; }
    get("/api/login?code=" + code).then(function (r) {
      if (r.status === 200 && r.body.token) { token = r.body.token; store(token); known = {}; firstLoad = true; showGallery(); }
      else $("loginError").textContent = r.body.error || "Código não encontrado.";
    }).catch(function () { $("loginError").textContent = "Sem conexão com o celular do fotógrafo. Confira o Wi-Fi."; });
  });
  $("code").addEventListener("input", function () {
    this.value = this.value.replace(/\D/g, "").slice(0, 6);
    // Four digits typed: no need to look for the button.
    if (this.value.length === 4) $("codeForm").requestSubmit();
  });
  $("leave").onclick = function () { showLogin(""); };
  $("heroOpen").onclick = function () { open(0); };
  $("close").onclick = function () { $("viewer").hidden = true; };
  $("prev").onclick = function () { current--; show(); };
  $("next").onclick = function () { current++; show(); };
  document.addEventListener("keydown", function (e) {
    if ($("viewer").hidden) return;
    if (e.key === "Escape") $("viewer").hidden = true;
    if (e.key === "ArrowLeft") { current--; show(); }
    if (e.key === "ArrowRight") { current++; show(); }
  });
  var startX = null;
  $("big").addEventListener("touchstart", function (e) { startX = e.touches[0].clientX; }, { passive: true });
  $("big").addEventListener("touchend", function (e) {
    if (startX === null) return;
    var dx = e.changedTouches[0].clientX - startX; startX = null;
    if (Math.abs(dx) > 60) { current += dx < 0 ? 1 : -1; show(); }
  });
  $("saveAll").onclick = function () {
    if (!data) return;
    var free = data.items.filter(function (i) { return !i.locked; });
    toast("Baixando " + plural(free.length, "foto", "fotos"));
    free.forEach(function (it, i) {
      setTimeout(function () {
        var a = document.createElement("a"); a.href = file(it, true); a.download = it.name;
        document.body.appendChild(a); a.click(); a.remove();
      }, i * 900);
    });
  };

  var fromLink = new URLSearchParams(location.search).get("s");
  if (fromLink && /^[0-9a-f]{32}$/.test(fromLink)) {
    token = fromLink; store(token);
    history.replaceState(null, "", "/");
    showGallery();
  } else if (stored()) {
    token = stored(); showGallery();
  } else showLogin("");
})();
