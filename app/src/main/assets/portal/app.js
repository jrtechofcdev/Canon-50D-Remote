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
    var fresh = [];
    d.items.forEach(function (it) { if (!known[it.id]) { if (!firstLoad) fresh.push(it.id); known[it.id] = true; } });
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
      if ($("heroImg").dataset.id !== String(hero.id)) { $("heroImg").src = file(hero, false); $("heroImg").dataset.id = hero.id; }
      $("heroSave").href = file(hero, true); $("heroSave").setAttribute("download", hero.name);
      setPick($("heroPick"), hero, d.picks);
    }
    $("saveAll").hidden = ios || d.items.length < 2;
    $("empty").hidden = d.items.length > 0;

    var grid = $("grid");
    var ids = d.items.map(function (i) { return String(i.id); }).join(",");
    if (grid.dataset.ids !== ids) {
      grid.dataset.ids = ids; grid.innerHTML = "";
      d.items.forEach(function (it, index) {
        var b = document.createElement("button");
        b.className = "cell" + (fresh.indexOf(it.id) >= 0 ? " new" : "");
        b.setAttribute("aria-label", it.name);
        var img = document.createElement("img");
        img.loading = "lazy"; img.alt = ""; img.onload = function () { img.classList.add("loaded"); };
        img.src = thumb(it);
        b.appendChild(img);
        if (it.picked) { var s = document.createElement("span"); s.className = "badge"; s.textContent = "♥"; b.appendChild(s); }
        b.onclick = function () { open(index); };
        grid.appendChild(b);
      });
    }
    if (fresh.length) toast(fresh.length === 1 ? "Foto nova chegou" : plural(fresh.length, "foto nova", "fotos novas") + " chegaram");
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
    if ($("big").dataset.id !== String(it.id)) { $("big").src = file(it, false); $("big").dataset.id = it.id; }
    $("save").href = file(it, true); $("save").setAttribute("download", it.name);
    $("position").textContent = (current + 1) + " de " + data.items.length;
    $("prev").disabled = current === 0; $("next").disabled = current === data.items.length - 1;
    setPick($("pick"), it, data.picks);
  }

  $("codeForm").addEventListener("submit", function (e) {
    e.preventDefault();
    var code = $("code").value.replace(/\D/g, "");
    if (code.length !== 6) { $("loginError").textContent = "O código tem 6 números."; return; }
    get("/api/login?code=" + code).then(function (r) {
      if (r.status === 200 && r.body.token) { token = r.body.token; store(token); known = {}; firstLoad = true; showGallery(); }
      else $("loginError").textContent = r.body.error || "Código não encontrado.";
    }).catch(function () { $("loginError").textContent = "Sem conexão com o celular do fotógrafo. Confira o Wi-Fi."; });
  });
  $("code").addEventListener("input", function () { this.value = this.value.replace(/\D/g, "").slice(0, 6); });
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
    toast("Baixando " + plural(data.items.length, "foto", "fotos"));
    data.items.forEach(function (it, i) {
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
  } else token = stored();
  token ? showGallery() : showLogin("");
})();
