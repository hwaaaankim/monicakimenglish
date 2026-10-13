(function () {
  "use strict";
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var transitionDelay = reduceMotion ? 0 : 140;

  function imageTransition(image, source) {
    if (!image || !source || image.getAttribute("src") === source) return;
    var token = String(Date.now()) + Math.random();
    image.dataset.transitionToken = token;
    image.classList.add("is-changing");
    var next = new Image();
    var display = function () {
      if (image.dataset.transitionToken !== token) return;
      setTimeout(function () {
        if (image.dataset.transitionToken !== token) return;
        image.src = source;
        requestAnimationFrame(function () { image.classList.remove("is-changing"); });
      }, transitionDelay);
    };
    next.onload = display;
    next.onerror = function () { if (image.dataset.transitionToken === token) image.classList.remove("is-changing"); };
    next.src = source;
  }

  document.querySelectorAll("[data-course-gallery]").forEach(function (gallery) {
    var main = gallery.querySelector("[data-gallery-main]");
    var thumbs = Array.from(gallery.querySelectorAll("[data-gallery-src]"));
    var index = 0;
    if (!main || !thumbs.length) return;
    function select(position) {
      index = (position + thumbs.length) % thumbs.length;
      thumbs.forEach(function (button, position) {
        button.classList.toggle("active", position === index);
        button.setAttribute("aria-pressed", String(position === index));
      });
      imageTransition(main, thumbs[index].dataset.gallerySrc);
      var count = gallery.querySelector("[data-gallery-count]");
      if (count) count.textContent = (index + 1) + " / " + thumbs.length;
      thumbs[index].scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "nearest", inline: "nearest" });
    }
    thumbs.forEach(function (button, position) { button.addEventListener("click", function () { select(position); }); });
    var prev = gallery.querySelector("[data-gallery-prev]"); var next = gallery.querySelector("[data-gallery-next]");
    if (prev) prev.addEventListener("click", function () { select(index - 1); });
    if (next) next.addEventListener("click", function () { select(index + 1); });
    var touchStart = null;
    main.addEventListener("touchstart", function (event) { touchStart = event.changedTouches[0].clientX; }, { passive: true });
    main.addEventListener("touchend", function (event) { if (touchStart === null) return; var movement = event.changedTouches[0].clientX - touchStart; touchStart = null; if (Math.abs(movement) > 55) select(index + (movement < 0 ? 1 : -1)); }, { passive: true });
  });

  var tabs = Array.from(document.querySelectorAll("[data-course-tab]"));
  function activateTab(button, focus) {
    tabs.forEach(function (tab) {
      var selected = tab === button;
      tab.setAttribute("aria-selected", String(selected)); tab.tabIndex = selected ? 0 : -1;
      var panel = document.getElementById(tab.dataset.courseTab);
      if (panel) { panel.hidden = !selected; if (selected) { panel.classList.remove("is-active"); requestAnimationFrame(function () { panel.classList.add("is-active"); }); } }
    });
    if (focus) button.focus();
  }
  tabs.forEach(function (tab, index) {
    tab.addEventListener("click", function () { activateTab(tab, false); });
    tab.addEventListener("keydown", function (event) {
      var target;
      if (event.key === "ArrowRight") target = tabs[(index + 1) % tabs.length];
      else if (event.key === "ArrowLeft") target = tabs[(index + tabs.length - 1) % tabs.length];
      else if (event.key === "Home") target = tabs[0];
      else if (event.key === "End") target = tabs[tabs.length - 1];
      if (target) { event.preventDefault(); activateTab(target, true); }
    });
  });

  var guide = document.getElementById("courseGuideDialog");
  var guideTrigger = null;
  if (guide) {
    document.querySelectorAll("[data-open-course-guide]").forEach(function (button) { button.addEventListener("click", function () { guideTrigger = button; guide.showModal(); document.body.classList.add("mk-modal-open"); requestAnimationFrame(function () { guide.classList.add("is-open"); }); }); });
    function closeGuide() { guide.classList.remove("is-open"); setTimeout(function () { guide.close(); }, reduceMotion ? 0 : 180); }
    guide.querySelectorAll("[data-close-course-guide]").forEach(function (button) { button.addEventListener("click", closeGuide); });
    guide.addEventListener("cancel", function (event) { event.preventDefault(); closeGuide(); });
    guide.addEventListener("click", function (event) { if (event.target !== guide) return; var bounds = guide.getBoundingClientRect(); if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) closeGuide(); });
    guide.addEventListener("close", function () { document.body.classList.remove("mk-modal-open"); if (guideTrigger) guideTrigger.focus(); });
  }

  var descriptionToggle = document.querySelector("[data-toggle-description]");
  if (descriptionToggle) descriptionToggle.addEventListener("click", function () {
    var paragraph = document.querySelector(".mk-detail-intro"); if (!paragraph) return;
    var expanded = paragraph.classList.toggle("expanded"); descriptionToggle.textContent = expanded ? "설명 접기" : "설명 더보기"; descriptionToggle.setAttribute("aria-expanded", String(expanded));
  });

  document.querySelectorAll("[data-wish-id]").forEach(function (button) {
    button.addEventListener("click", async function (event) {
      event.preventDefault(); event.stopPropagation(); button.disabled = true;
      try {
        var response = await window.MK.fetch("/wishlist/" + encodeURIComponent(button.dataset.wishId) + "/toggle", { method: "POST" });
        if (response.status === 401 || response.status === 403) { window.location.href = "/auth/login"; return; }
        var data = await response.json(); if (!response.ok) throw new Error(data.message || "처리할 수 없습니다.");
        button.classList.toggle("active", data.wished); button.setAttribute("aria-pressed", String(data.wished));
        if (button.classList.contains("mk-wish-detail")) button.textContent = data.wished ? "찜 해제" : "찜 목록에 담기";
        if (window.location.pathname === "/my/wishlist" && !data.wished) { var column = button.closest(".col-12"); if (column) { column.classList.add("mk-card-removing"); setTimeout(function () { column.remove(); }, reduceMotion ? 0 : 220); } }
      } catch (error) { alert(error.message || "찜 처리 중 오류가 발생했습니다."); }
      finally { button.disabled = false; }
    });
  });

  function formatTime(position) {
    var seconds = Math.floor(Math.max(0, Number(position) || 0));
    return seconds >= 3600 ? Math.floor(seconds / 3600) + ":" + String(Math.floor((seconds % 3600) / 60)).padStart(2, "0") + ":" + String(seconds % 60).padStart(2, "0") : Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
  }
  function learningPoint(lectureId, record, available) {
    var link = document.createElement(available ? "a" : "div"); link.className = "mk-learning-point-link";
    if (available) link.href = "/watch/" + encodeURIComponent(lectureId) + "?t=" + Math.floor(record.position);
    else { link.classList.add("is-locked"); link.setAttribute("aria-disabled", "true"); }
    var media = document.createElement("span"); media.className = "mk-learning-point-media";
    var empty = document.createElement("span"); empty.textContent = record.last ? "이어보기" : "책갈피"; media.appendChild(empty);
    if (record.image) { var image = document.createElement("img"); image.src = record.image; image.alt = ""; image.loading = "lazy"; image.addEventListener("error", function () { image.remove(); }); media.appendChild(image); }
    var time = document.createElement("b"); time.textContent = formatTime(record.position); media.appendChild(time);
    var title = document.createElement("strong"); title.textContent = record.label; link.append(media, title);
    var point = document.createElement("div"); point.className = "mk-learning-point"; point.appendChild(link); if (!available) { var note = document.createElement("small"); note.className = "mk-learning-locked-note"; note.textContent = "수강 가능 기간에 시청할 수 있어요."; point.appendChild(note); } return point;
  }
  async function loadSummary(section) {
    var content = section.querySelector("[data-learning-content]"); if (!content) return;
    try {
      var id = section.dataset.learningSummary;
      var response = await window.MK.fetch("/api/player/" + encodeURIComponent(id) + "/learning-summary");
      if (!response.ok) throw new Error("learning unavailable");
      var data = await response.json(); var points = [];
      if (data.lastWatchedAt) points.push({ position: Number(data.lastPositionSeconds), label: "마지막 시청 위치", image: data.lastThumbnailUrl, last: true });
      (data.bookmarks || []).slice(0, 5).forEach(function (item) { points.push({ position: Number(item.positionSeconds), label: item.label || "책갈피", image: item.thumbnailUrl }); });
      content.replaceChildren();
      if (points.length) { content.classList.add("mk-learning-point-grid"); points.forEach(function (point) { content.appendChild(learningPoint(id, point, section.dataset.learningAvailable !== "false")); }); }
      else { var paragraph = document.createElement("p"); paragraph.className = "mk-learning-empty"; paragraph.textContent = "아직 학습 기록이 없어요. 강의를 시작하면 마지막 위치가 자동으로 저장됩니다."; content.appendChild(paragraph); }
    } catch (error) { content.replaceChildren(); var warning = document.createElement("p"); warning.className = "mk-learning-empty"; warning.textContent = "학습 기록을 불러오지 못했어요. 강의 시청 페이지에서 다시 확인해 주세요."; content.appendChild(warning); }
  }
  var summarySections = Array.from(document.querySelectorAll("[data-learning-summary]"));
  // Avoid firing a request for every off-screen classroom card at once.
  if ("IntersectionObserver" in window) {
    var observer = new IntersectionObserver(function (entries) { entries.forEach(function (entry) { if (entry.isIntersecting) { observer.unobserve(entry.target); loadSummary(entry.target); } }); }, { rootMargin: "200px" });
    summarySections.forEach(function (section) { observer.observe(section); });
  } else summarySections.forEach(loadSummary);

  document.querySelectorAll("[data-preview-id]").forEach(function (button) {
    button.addEventListener("click", async function () {
      button.disabled = true;
      try { var response = await fetch("/lectures/" + encodeURIComponent(button.dataset.previewId) + "/preview"); if (!response.ok) throw new Error("강의 미리보기를 불러오지 못했습니다."); showPreview(await response.json(), button); }
      catch (error) { alert(error.message); }
      finally { button.disabled = false; }
    });
  });

  function showPreview(data, trigger) {
    var prior = document.querySelector(".mk-preview-modal"); if (prior) prior.remove();
    var modal = document.createElement("dialog"); modal.className = "mk-preview-modal mk-native-preview"; modal.setAttribute("aria-labelledby", "quickPreviewTitle");
    var id = encodeURIComponent(data.id); var imageIds = Array.isArray(data.imageIds) ? data.imageIds : [];
    var imageMarkup = imageIds.length ? '<div class="mk-preview-media"><img class="mk-preview-main" src="/media/lectures/' + id + '/images/' + encodeURIComponent(imageIds[0]) + '" alt="강의 소개"></div><div class="mk-preview-thumbs">' + imageIds.map(function (imageId, index) { var source = "/media/lectures/" + id + "/images/" + encodeURIComponent(imageId); return '<button type="button" data-img="' + source + '" aria-label="소개 이미지 ' + (index + 1) + '" class="' + (index === 0 ? "active" : "") + '"><img src="' + source + '" alt=""></button>'; }).join("") + '</div>' : '<div class="mk-preview-media mk-thumb-placeholder"><span>MONICA KIM ONLINE CLASS</span></div>';
    modal.innerHTML = '<div class="mk-preview-dialog"><button class="mk-preview-close" type="button" aria-label="닫기">×</button>' + imageMarkup + '<div class="mk-preview-content"><div class="mk-preview-meta"><strong></strong><span></span></div><h2 id="quickPreviewTitle"></h2><div class="mk-preview-tags"></div><p></p><a class="mk-primary-btn" href="/lectures/' + id + '">강의 상세보기</a></div></div>';
    modal.querySelector("h2").textContent = data.title || "강의"; modal.querySelector("p").textContent = data.description || "";
    modal.querySelector(".mk-preview-meta strong").textContent = data.pricing === "FREE" ? "무료" : Number(data.price || 0).toLocaleString("ko-KR") + "원";
    modal.querySelector(".mk-preview-meta span").textContent = formatTime(data.durationSeconds);
    (data.tags || []).forEach(function (tag) { var span = document.createElement("span"); span.textContent = "#" + tag; modal.querySelector(".mk-preview-tags").appendChild(span); });
    modal.querySelectorAll("[data-img]").forEach(function (button) { button.addEventListener("click", function () { modal.querySelectorAll("[data-img]").forEach(function (item) { item.classList.toggle("active", item === button); }); imageTransition(modal.querySelector(".mk-preview-main"), button.dataset.img); }); });
    var closing = false;
    function close() { if (closing) return; closing = true; modal.classList.remove("open"); setTimeout(function () { modal.close(); modal.remove(); document.body.classList.remove("mk-modal-open"); trigger.focus(); }, reduceMotion ? 0 : 200); }
    modal.querySelector(".mk-preview-close").addEventListener("click", close);
    modal.addEventListener("cancel", function (event) { event.preventDefault(); close(); });
    modal.addEventListener("click", function (event) { if (event.target === modal) close(); });
    document.body.appendChild(modal); modal.showModal(); document.body.classList.add("mk-modal-open"); requestAnimationFrame(function () { modal.classList.add("open"); });
  }
})();
