// Fotografia Express page: one client, no code, previews until the photographer releases them.
// Nothing is stored on the client's phone: every visit asks the photographer's phone who is the client now.
(function () {
  "use strict";
  var $ = function (id) { return document.getElementById(id); };
  var token = null, data = null, known = {}, first = true, current = 0, asked = false;
  var ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

  function toast(t) {
    var el = $("toast"); el.textContent = t; el.classList.add("show");
    clearTimeout(el._t); el._t = setTimeout(function () { el.classList.remove("show"); }, 2600);
  }
  function get(url) {
    return fetch(url, { cache: "no-store" }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (b) { return { status: r.status, body: b }; });
    });
  }
  function plural(n, one, many) { return n + " " + (n === 1 ? one : many); }
  function greeting() { var h = new Date().getHours(); return h < 12 ? "Bom dia" : h < 18 ? "Boa tarde" : "Boa noite"; }
  function firstName(d) { return d && d.client && d.client !== "Express" ? d.client.split(" ")[0] : null; }
  // Locked photos ask for their marked version (a different address, so the browser never mixes them up).
  function view(it) { return "/p/" + token + "/" + it.id + (it.locked ? "?m=1" : ""); }
  function save(it) { return "/f/" + token + "/" + it.id + "?dl=1"; }

  function join() {
    get("/api/express").then(function (r) {
      if (r.status === 200 && r.body.token) {
        if (token !== r.body.token) { token = r.body.token; known = {}; first = true; asked = false; $("feed").innerHTML = ""; }
        $("closed").hidden = true;
        refresh();
      } else {
        token = null; data = null; $("feed").innerHTML = ""; $("hint").hidden = true; $("dock").hidden = true; $("paySheet").hidden = true; $("empty").hidden = true;
        $("closed").hidden = false; $("sub").textContent = "";
      }
    }).catch(function () { $("sub").textContent = "Sem conexão com o celular do fotógrafo. Confira o Wi-Fi."; });
  }

  function refresh() {
    if (!token) return join();
    get("/api/s/" + token).then(function (r) {
      if (r.status === 404) return join(); // finished: the next client starts clean
      if (r.status === 200) render(r.body);
    }).catch(function () {});
  }

  function render(d) {
    var fresh = [], released = 0;
    d.items.forEach(function (it) {
      if (!(it.id in known)) { if (!first) fresh.push(it.id); }
      else if (known[it.id] && !it.locked) released++;
      known[it.id] = !!it.locked;
    });
    data = d;
    var locked = d.items.filter(function (i) { return i.locked; });
    var free = d.items.filter(function (i) { return !i.locked; });
    var picked = d.items.filter(function (i) { return i.picked; });

    var name = firstName(d);
    $("title").textContent = name ? greeting() + ", " + name + "!" : "Suas fotos de hoje";
    // A new client is asked their name once, kindly; they can skip it.
    if (!name && !asked) { asked = true; $("welcome").hidden = false; setTimeout(function () { $("nameInput").focus(); }, 80); }
    $("sub").textContent = d.items.length ? plural(d.items.length, "foto", "fotos") + " · " + d.photographer : "Fotografia de " + d.photographer;
    // Discreet: one line while there are previews; prices only when the photographer set them.
    $("hint").hidden = !locked.length;
    $("hintText").textContent = "Prévia com marca. Toque no coração das que quiser" + (d.price ? " · " + d.price : "");
    $("waiting").hidden = !d.waiting;
    $("waiting").textContent = d.waiting ? plural(d.waiting, "foto chegando", "fotos chegando") + "…" : "";
    $("empty").hidden = d.items.length > 0 || d.waiting > 0;

    // The feed: newest first. Cards are rebuilt only when something about them changed.
    var feed = $("feed");
    var key = d.items.map(function (i) { return i.id + (i.locked ? "l" : "") + (i.picked ? "p" : ""); }).join(",");
    if (feed.dataset.key !== key) {
      feed.dataset.key = key; feed.innerHTML = "";
      d.items.forEach(function (it, index) { feed.appendChild(card(it, index, d, fresh.indexOf(it.id) >= 0)); });
    }

    // Dock: what the client chose and, once released, saving everything at once.
    var dockText = "";
    if (locked.length) dockText = picked.length ? plural(picked.length, "favorita", "favoritas") : "Marque suas favoritas";
    else if (free.length) dockText = plural(free.length, "foto liberada", "fotos liberadas");
    // "Pagar" appears once something is hearted and the photographer shows payment.
    var pickedLocked = picked.filter(function (i) { return i.locked; }).length;
    if (pickedLocked && d.price !== null && d.price !== undefined) loadPay(); else $("payBtn").hidden = true;
    $("dockText").textContent = dockText;
    $("dock").hidden = !d.items.length;
    $("saveAll").hidden = ios || free.length < 2;

    if (released) toast(released === 1 ? "Foto liberada: já pode salvar" : plural(released, "foto liberada", "fotos liberadas"));
    else if (fresh.length) toast(fresh.length === 1 ? "Foto nova" : plural(fresh.length, "foto nova", "fotos novas"));
    first = false;
    if (!$("viewer").hidden) show();
  }

  var pay = null, payKey = "";
  function loadPay() {
    get("/api/s/" + token + "/pay").then(function (r) {
      if (r.status !== 200) return;
      pay = r.body;
      $("payBtn").hidden = !(pay.total || pay.pix || (pay.methods && pay.methods.length));
      $("payBtn").textContent = pay.total ? "Pagar " + pay.total : "Pagar";
      if (!$("paySheet").hidden) showPay();
    }).catch(function () {});
  }
  function showPay() {
    if (!pay) return;
    $("payTotal").textContent = pay.total || "";
    $("payDetail").textContent = pay.detail || pay.table || "";
    $("pixBox").hidden = !pay.pix;
    if (pay.pix && payKey !== pay.pix) { payKey = pay.pix; $("pixQr").src = "/api/s/" + token + "/pix.png?k=" + encodeURIComponent(pay.pix.slice(-8)); }
    $("payMethods").textContent = pay.methods && pay.methods.length ? "Aceito: " + pay.methods.join(", ") : "";
    $("payNote").textContent = pay.note || "";
    $("paySheet").hidden = false;
  }

  function card(it, index, d, isNew) {
    var c = document.createElement("article");
    c.className = "card" + (it.locked ? " locked" : "") + (isNew ? " new" : "");
    var shot = document.createElement("button");
    shot.className = "shot"; shot.setAttribute("aria-label", "Abrir foto " + (d.items.length - index));
    var img = document.createElement("img"); img.loading = "lazy"; img.alt = ""; img.src = view(it);
    shot.appendChild(img); shot.onclick = function () { open(index); };
    c.appendChild(shot);
    var foot = document.createElement("div"); foot.className = "card-foot";
    var num = document.createElement("span"); num.className = "num"; num.textContent = "Foto " + (d.items.length - index) + (it.version ? " · " + (it.label || "versão " + it.version) : "");
    var pill = document.createElement("span"); pill.className = "pill" + (it.locked ? "" : " free"); pill.textContent = it.locked ? "Prévia" : "Liberada";
    var grow = document.createElement("span"); grow.className = "grow";
    foot.appendChild(num); foot.appendChild(pill); foot.appendChild(grow);
    if (d.picks) { var h = document.createElement("button"); h.className = "ghost heart"; foot.appendChild(h); heart(h, it); }
    if (!it.locked) {
      var a = document.createElement("a"); a.className = "primary"; a.textContent = "Salvar"; a.href = save(it); a.setAttribute("download", it.name);
      foot.appendChild(a);
    }
    c.appendChild(foot);
    return c;
  }

  function heart(btn, it) {
    btn.textContent = it.picked ? "♥" : "♡";
    btn.classList.toggle("on", it.picked);
    btn.setAttribute("aria-pressed", it.picked ? "true" : "false");
    btn.setAttribute("aria-label", it.picked ? "Desmarcar favorita" : "Marcar como favorita");
    btn.onclick = function (e) {
      e.stopPropagation();
      var on = !it.picked;
      fetch("/api/s/" + token + "/pick/" + it.id + "?on=" + (on ? 1 : 0), { method: "POST" }).then(function (r) {
        if (r.ok) {
          it.picked = on; heart(btn, it);
          var n = firstName(data);
          toast(on ? (n ? "Obrigado, " + n + "! O fotógrafo já recebeu." : "Favorita marcada") : "Desmarcada");
          refresh();
        }
      });
    };
  }

  function open(i) { current = i; $("viewer").hidden = false; show(); }
  function show() {
    if (!data || !data.items.length) { $("viewer").hidden = true; return; }
    current = Math.max(0, Math.min(current, data.items.length - 1));
    var it = data.items[current];
    var key = it.id + (it.locked ? "l" : "");
    if ($("big").dataset.key !== key) { $("big").src = view(it); $("big").dataset.key = key; }
    $("big").classList.toggle("locked", !!it.locked);
    var a = $("vSave");
    if (it.locked) { a.removeAttribute("href"); a.classList.add("wait"); a.textContent = "Prévia: libera após o pagamento"; }
    else { a.href = save(it); a.setAttribute("download", it.name); a.classList.remove("wait"); a.textContent = "Salvar foto"; }
    $("vPick").hidden = !data.picks; heart($("vPick"), it);
    $("pos").textContent = (current + 1) + " de " + data.items.length;
    $("prev").disabled = current === 0; $("next").disabled = current === data.items.length - 1;
  }

  $("close").onclick = function () { $("viewer").hidden = true; };
  $("prev").onclick = function () { current--; show(); };
  $("next").onclick = function () { current++; show(); };
  var sx = null;
  $("big").addEventListener("touchstart", function (e) { sx = e.touches[0].clientX; }, { passive: true });
  $("big").addEventListener("touchend", function (e) {
    if (sx === null) return; var dx = e.changedTouches[0].clientX - sx; sx = null;
    if (Math.abs(dx) > 60) { current += dx < 0 ? 1 : -1; show(); }
  });
  document.addEventListener("keydown", function (e) {
    if ($("viewer").hidden) return;
    if (e.key === "Escape") $("viewer").hidden = true;
    if (e.key === "ArrowLeft") { current--; show(); }
    if (e.key === "ArrowRight") { current++; show(); }
  });
  $("payBtn").onclick = function () { loadPay(); showPay(); };
  $("payClose").onclick = function () { $("paySheet").hidden = true; };
  $("paySheet").addEventListener("click", function (e) { if (e.target === $("paySheet")) $("paySheet").hidden = true; });
  $("copyPix").onclick = function () {
    if (!pay || !pay.pix) return;
    var code = pay.pix;
    function fallback() {
      var t = document.createElement("textarea"); t.value = code; document.body.appendChild(t); t.select();
      try { document.execCommand("copy"); toast("Código Pix copiado"); } catch (e) { toast("Copie o código no app do banco"); }
      t.remove();
    }
    if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(code).then(function () { toast("Código Pix copiado"); }, fallback);
    else fallback();
  };
  $("saveAll").onclick = function () {
    if (!data) return;
    var free = data.items.filter(function (i) { return !i.locked; });
    toast("Baixando " + plural(free.length, "foto", "fotos"));
    free.forEach(function (it, i) {
      setTimeout(function () { var a = document.createElement("a"); a.href = save(it); a.download = it.name; document.body.appendChild(a); a.click(); a.remove(); }, i * 900);
    });
  };

  $("nameForm").addEventListener("submit", function (e) {
    e.preventDefault();
    var n = $("nameInput").value.trim();
    if (n.length < 2) { $("nameInput").focus(); return; }
    fetch("/api/s/" + token + "/name?n=" + encodeURIComponent(n), { method: "POST" }).then(function () {
      $("welcome").hidden = true; toast("Prazer, " + n.split(" ")[0] + "!"); refresh();
    }).catch(function () { $("welcome").hidden = true; });
  });
  $("skipName").onclick = function () { $("welcome").hidden = true; };

  join();
  setInterval(refresh, 2000);
})();
