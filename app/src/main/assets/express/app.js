// Fotografia Express: one client at a time, no code, previews until the photographer releases them.
// Nothing about the client stays on their phone: every visit asks the photographer's phone who is the client now.
(function () {
  "use strict";
  var $ = JR.$, plural = JR.plural;
  var token = null, data = null, known = {}, first = true, asked = false, ended = false, cards = {};
  var pay = null, payKey = "";
  var ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

  function firstName(d) { return d && d.client && d.client !== "Express" ? JR.firstWord(d.client) : null; }
  // Locked photos ask for their marked version (a different address, so the browser never mixes them up).
  function view(it) { return "/p/" + token + "/" + it.id + (it.locked ? "?m=1" : ""); }
  function save(it) { return "/f/" + token + "/" + it.id + "?dl=1"; }

  function live(kind, text) {
    var el = $("live");
    el.className = "state" + (kind === "warn" ? " lost" : "");
    el.innerHTML = '<i class="mark ' + kind + '" aria-hidden="true"></i><span></span>';
    el.lastChild.textContent = text;
  }

  // ---------- Joining ----------
  function join() {
    JR.get("/api/express").then(function (r) {
      if (r.status === 200 && r.body.token) {
        if (token !== r.body.token) { token = r.body.token; known = {}; first = true; asked = false; clearFeed(); }
        $("closed").hidden = true;
        refresh();
      } else {
        token = null; data = null; clearFeed(); hideAll();
        closed("Nenhum atendimento aberto agora", "Fale com o fotógrafo: quando ele começar o seu, as fotos aparecem aqui.");
        live("off", "Sem atendimento");
      }
    }).catch(function () { lost(); });
  }

  function lost() {
    live("warn", "Reconectando");
    if (!data) $("sub").textContent = "Sem conexão com o celular do fotógrafo. Confira se está no Wi-Fi dele.";
  }

  function clearFeed() { $("feed").innerHTML = ""; cards = {}; }
  function hideAll() {
    $("hint").hidden = true; $("dock").hidden = true; $("empty").hidden = true; $("waiting").hidden = true;
    $("welcome").hidden = true; JR.dropLayers();
  }
  function closed(title, text) {
    if (!$("closedArt").firstChild) $("closedArt").innerHTML = JR.symbol("", false);
    $("closedTitle").textContent = title; $("closedText").textContent = text;
    $("closed").hidden = false; $("sub").textContent = "";
  }

  // The photographer finished this client: the page says goodbye and stops. It never moves on to the
  // next client's photos by itself; a new visit (the QR again) starts fresh.
  function end() {
    var name = firstName(data);
    ended = true; token = null; data = null;
    clearFeed(); hideAll();
    $("title").textContent = "Até a próxima" + (name ? ", " + name : "") + "!";
    $("eyebrow").textContent = "Fotografia Express";
    closed("Atendimento encerrado", "Obrigado pela visita. Para ver as fotos de novo, fale com o fotógrafo.");
    live("off", "Encerrado");
  }

  // The phone forgot this device (it restarted): ask again, but only stay if it is still the same client.
  function rekey(then) {
    JR.get("/api/express").then(function (r) {
      if (r.status === 200 && r.body.token === token) then(); else end();
    }).catch(function () {});
  }

  function refresh() {
    if (ended) return;
    if (!token) return join();
    JR.get("/api/s/" + token).then(function (r) {
      if (r.status === 404) return end();
      if (r.status === 200) render(r.body);
    }).catch(lost);
  }

  // ---------- Page ----------
  function render(d) {
    var fresh = [], released = [];
    d.items.forEach(function (it) {
      if (!(it.id in known)) { if (!first) fresh.push(it.id); }
      else if (known[it.id] && !it.locked) released.push(it.id);
      known[it.id] = !!it.locked;
    });
    data = d;
    live("on", "Ao vivo");
    var locked = d.items.filter(function (i) { return i.locked; });
    var free = d.items.filter(function (i) { return !i.locked; });
    var picked = d.items.filter(function (i) { return i.picked; });

    var name = firstName(d);
    $("eyebrow").textContent = name ? JR.greeting() + "," : "Fotografia Express";
    $("title").textContent = name || "Suas fotos de hoje";
    document.title = name ? "Fotos de " + name : "Fotografia Express";
    // A new client is asked their name once, kindly; they can skip it.
    if (!name && !asked) {
      asked = true;
      if (!$("welcomeArt").firstChild) $("welcomeArt").innerHTML = JR.symbol("", true);
      $("welcome").hidden = false;
      setTimeout(function () { $("nameInput").focus(); }, document.body.classList.contains("opening") ? 2300 : 80);
    }
    $("sub").textContent = (d.items.length ? plural(d.items.length, "foto", "fotos") + " · " : "") + "Fotografia de " + d.photographer;
    $("foot").innerHTML = 'Fotos de <span></span> · entregues pelo <span class="brandname">JR C50D Sync</span>';
    $("foot").firstElementChild.textContent = d.photographer || "seu fotógrafo";

    $("hint").hidden = !locked.length;
    if (!$("hint").firstElementChild.firstChild) $("hint").firstElementChild.innerHTML = JR.icons.heart;
    $("price").hidden = !d.price;
    $("price").textContent = d.price || "";

    var w = $("waiting");
    w.hidden = !d.waiting;
    if (d.waiting) {
      if (!w.firstChild) w.innerHTML = JR.loader() + "<span></span>";
      w.lastChild.textContent = plural(d.waiting, "foto chegando", "fotos chegando");
    }
    $("empty").hidden = d.items.length > 0 || d.waiting > 0;
    if (!$("empty").hidden && !$("emptyArt").firstChild) $("emptyArt").innerHTML = JR.loader();

    paintFeed(d, fresh, released);
    paintDock(d, locked, free, picked);

    if (released.length) JR.toast(released.length === 1 ? "Foto liberada: já pode salvar" : plural(released.length, "foto liberada", "fotos liberadas") + ": já pode salvar");
    else if (fresh.length) JR.toast(fresh.length === 1 ? "Chegou uma foto nova" : "Chegaram " + plural(fresh.length, "foto nova", "fotos novas"));
    first = false;
    gallery.refresh();
  }

  // Cards are kept by photo and only updated, so the feed never blinks on the 2-second refresh.
  function paintFeed(d, fresh, released) {
    var feed = $("feed"), keep = {};
    d.items.forEach(function (it, index) {
      var c = cards[it.id];
      if (!c) { c = makeCard(it); cards[it.id] = c; if (fresh.indexOf(it.id) >= 0) { c.classList.add("new"); flash(c.firstChild); } }
      updateCard(c, it, index, d, released.indexOf(it.id) >= 0);
      keep[it.id] = true;
      if (feed.children[index] !== c) feed.insertBefore(c, feed.children[index] || null);
    });
    Object.keys(cards).forEach(function (k) { if (!keep[k]) { cards[k].remove(); delete cards[k]; } });
  }

  function flash(el) { el.classList.remove("flash"); void el.offsetWidth; el.classList.add("flash"); }

  function makeCard(it) {
    var c = document.createElement("article");
    c.className = "card";
    c.innerHTML = '<button class="shot"><img alt="" decoding="async"></button>' +
      '<div class="card-foot"><span class="card-label"><b></b><span class="state"><i class="mark" aria-hidden="true"></i><span></span></span></span></div>';
    return c;
  }

  function updateCard(c, it, index, d, justReleased) {
    var number = d.items.length - index;
    var img = c.querySelector("img"), src = view(it);
    if (img.dataset.src !== src) { img.dataset.src = src; img.src = src; img.classList.toggle("locked-img", !!it.locked); }
    var shot = c.querySelector(".shot");
    shot.setAttribute("aria-label", "Abrir foto " + number);
    shot.onclick = function () { gallery.open(d.items.indexOf(it) >= 0 ? d.items.indexOf(it) : index); };
    c.querySelector("b").textContent = "Foto " + number + (it.version ? " · " + (it.label || "versão " + it.version) : "");
    c.classList.toggle("released", !it.locked);
    c.querySelector(".card-label .mark").className = "mark " + (it.locked ? "off" : "on");
    c.querySelector(".card-label .state span").textContent = it.locked ? (it.picked ? "Prévia · escolhida" : "Prévia") : "Liberada";
    if (justReleased) { c.classList.remove("just"); void c.offsetWidth; c.classList.add("just"); flash(shot); }

    var foot = c.querySelector(".card-foot");
    // The heart while choosing; once released, saving is the thing to do.
    var wantHeart = d.picks && (it.locked || !d.preview);
    var h = foot.querySelector(".heart");
    if (wantHeart && !h) { h = document.createElement("button"); h.className = "btn secondary icon heart"; foot.appendChild(h); }
    if (!wantHeart && h) { h.remove(); h = null; }
    if (h) heart(h, it);
    var a = foot.querySelector("a");
    if (!it.locked && !a) { a = document.createElement("a"); a.className = "btn primary small"; a.innerHTML = JR.icons.save + "<span>Salvar</span>"; foot.appendChild(a); }
    if (it.locked && a) { a.remove(); a = null; }
    if (a) { a.href = save(it); a.setAttribute("download", it.name); a.setAttribute("aria-label", "Salvar foto " + number); }
  }

  function paintDock(d, locked, free, picked) {
    var pickedLocked = picked.filter(function (i) { return i.locked; }).length;
    var text = "", icon = false;
    if (locked.length) { text = picked.length ? plural(picked.length, "favorita", "favoritas") : "Toque no coração das que quiser"; icon = picked.length > 0; }
    else if (free.length) text = plural(free.length, "foto liberada", "fotos liberadas");
    $("dockText").innerHTML = (icon ? JR.icons.heart : "") + "<span></span>";
    $("dockText").lastChild.textContent = text;
    // "Pagar" appears once something is hearted and the photographer shows payment.
    if (pickedLocked && d.price !== null && d.price !== undefined) loadPay(); else $("payBtn").hidden = true;
    $("saveAll").hidden = ios || free.length < 2 || pickedLocked > 0;
    $("dock").hidden = !d.items.length;
  }

  // ---------- Payment ----------
  function loadPay() {
    JR.get("/api/s/" + token + "/pay").then(function (r) {
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
    $("stepPix").hidden = !pay.pix;
    if (pay.pix && payKey !== pay.pix) { payKey = pay.pix; $("pixQr").src = "/api/s/" + token + "/pix.png?k=" + encodeURIComponent(pay.pix.slice(-8)); }
    $("payMethods").textContent = pay.methods && pay.methods.length ? "Aceito: " + pay.methods.join(", ") : "";
    $("payNote").textContent = pay.note || "";
    if ($("paySheet").hidden) JR.openLayer($("paySheet"));
  }

  // ---------- Hearts ----------
  function heart(btn, it) {
    JR.heart(btn, it.picked);
    btn.onclick = function (e) {
      e.stopPropagation();
      var on = !it.picked;
      JR.heart(btn, on); JR.popHeart(btn);
      var send = function (again) {
        fetch("/api/s/" + token + "/pick/" + it.id + "?on=" + (on ? 1 : 0), { method: "POST" }).then(function (r) {
          if (r.status === 403 && again) return rekey(function () { send(false); });
          if (r.ok) {
            it.picked = on;
            var n = firstName(data);
            JR.toast(on ? (n ? "Boa escolha, " + n + "! O fotógrafo já viu." : "Favorita marcada") : "Favorita desmarcada", on ? "on" : "off");
            refresh();
          } else { JR.heart(btn, it.picked); JR.toast("Não deu para marcar agora. Tente de novo.", "warn"); }
        }).catch(function () { JR.heart(btn, it.picked); JR.toast("Sem conexão com o celular do fotógrafo", "warn"); });
      };
      send(true);
    };
  }

  // ---------- Viewer ----------
  var gallery = JR.viewer({
    count: function () { return data ? data.items.length : 0; },
    src: function (i) { return view(data.items[i]); },
    paint: function (i) {
      var it = data.items[i], a = $("vSave");
      $("big").classList.toggle("locked-img", !!it.locked);
      if (it.locked) { a.removeAttribute("href"); a.removeAttribute("download"); a.className = "btn wait"; a.innerHTML = JR.icons.lock + "<span>Libera após o pagamento</span>"; }
      else { a.href = save(it); a.setAttribute("download", it.name); a.className = "btn primary"; a.innerHTML = JR.icons.save + "<span>Salvar foto</span>"; }
      var p = $("vPick");
      p.hidden = !(data.picks && (it.locked || !data.preview));
      if (!p.hidden) heart(p, it);
    }
  });

  // ---------- Controls ----------
  $("payBtn").onclick = function () { loadPay(); showPay(); };
  $("payClose").onclick = JR.closeLayer;
  $("paySheet").addEventListener("click", function (e) { if (e.target === $("paySheet")) JR.closeLayer(); });
  document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !$("paySheet").hidden) JR.closeLayer(); });
  $("copyPix").onclick = function () {
    if (!pay || !pay.pix) return;
    var code = pay.pix, btn = $("copyPix");
    function copied() { btn.textContent = "Código copiado"; JR.toast("Código Pix copiado. Agora cole no app do banco."); setTimeout(function () { btn.textContent = "Copiar Pix copia e cola"; }, 2600); }
    function fallback() {
      var t = document.createElement("textarea"); t.value = code; document.body.appendChild(t); t.select();
      try { document.execCommand("copy"); copied(); } catch (e) { JR.toast("Copie o código no app do banco", "warn"); }
      t.remove();
    }
    if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(code).then(copied, fallback);
    else fallback();
  };
  $("saveAll").onclick = function () {
    if (!data) return;
    var free = data.items.filter(function (i) { return !i.locked; }), btn = $("saveAll");
    btn.className = "btn wait"; btn.textContent = "Salvando " + free.length + "…";
    JR.toast("Salvando " + plural(free.length, "foto", "fotos") + ". Pode aceitar os downloads.");
    JR.saveMany(free, save, function () { btn.className = "btn primary"; btn.textContent = "Salvar liberadas"; });
  };

  $("nameForm").addEventListener("submit", function (e) {
    e.preventDefault();
    var n = $("nameInput").value.trim();
    if (n.length < 2) { $("nameInput").focus(); return; }
    var send = function (again) {
      fetch("/api/s/" + token + "/name?n=" + encodeURIComponent(n), { method: "POST" }).then(function (r) {
        if (r.status === 403 && again) return rekey(function () { send(false); });
        $("welcome").hidden = true; JR.toast("Prazer, " + JR.firstWord(n) + "!"); refresh();
      }).catch(function () { $("welcome").hidden = true; });
    };
    send(true);
  });
  $("skipName").onclick = function () { $("welcome").hidden = true; };
  JR.topBar($("top"));

  JR.intro();
  join();
  setInterval(refresh, 2000);
})();
