(function () {
  "use strict";
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var transitionDelay = reduceMotion ? 0 : 140;

  function mediaState(image, available) {
    var frame = image.parentElement;
    if (!frame) return;
    image.hidden = !available;
    frame.classList.toggle("has-media-image", available);
    frame.classList.toggle("is-image-unavailable", !available);
    var fallback = frame.querySelector(".mk-media-fallback");
    if (fallback) {
      // Thumbnail/link controls already have their own accessible labels.
      var labelledControl = frame.matches("a,button");
      fallback.setAttribute("aria-hidden", String(available || labelledControl));
      if (!available && !labelledControl) { fallback.setAttribute("role", "img"); fallback.setAttribute("aria-label", image.alt || "이미지 준비 중"); }
    }
  }
  function watchMedia(root) {
    root.querySelectorAll("[data-media-image]").forEach(function (image) {
      if (image.dataset.mediaObserved) return;
      image.dataset.mediaObserved = "true";
      image.addEventListener("load", function () { mediaState(image, image.naturalWidth > 0); });
      image.addEventListener("error", function () { mediaState(image, false); });
      if (image.complete) mediaState(image, image.naturalWidth > 0);
    });
  }
  watchMedia(document);

  function imageTransition(image, source) {
    if (!image || !source || (image.getAttribute("src") === source && !image.hidden)) return;
    var token = String(Date.now()) + Math.random();
    image.dataset.transitionToken = token;
    image.classList.add("is-changing");
    var next = new Image();
    function display(available) {
      if (image.dataset.transitionToken !== token) return;
      setTimeout(function () {
        if (image.dataset.transitionToken !== token || !image.isConnected) return;
        image.src = source;
        mediaState(image, available);
        requestAnimationFrame(function () { if (image.dataset.transitionToken === token) image.classList.remove("is-changing"); });
      }, transitionDelay);
    }
    next.onload = function () { display(next.naturalWidth > 0); };
    next.onerror = function () { display(false); };
    next.src = source;
  }

  function revealThumbnail(button) {
    var strip = button.parentElement;
    if (!strip || strip.scrollWidth <= strip.clientWidth) return;
    var bounds = strip.getBoundingClientRect(); var selected = button.getBoundingClientRect();
    var left = strip.scrollLeft;
    if (selected.left < bounds.left + 10) left -= bounds.left + 10 - selected.left;
    else if (selected.right > bounds.right - 10) left += selected.right - bounds.right + 10;
    strip.scrollTo({ left: Math.max(0, left), behavior: reduceMotion ? "auto" : "smooth" });
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
      revealThumbnail(thumbs[index]);
    }
    thumbs.forEach(function (button, position) {
      button.addEventListener("click", function () { select(position); });
      button.addEventListener("keydown", function (event) {
        var nextPosition;
        if (event.key === "ArrowRight") nextPosition = position + 1;
        else if (event.key === "ArrowLeft") nextPosition = position - 1;
        else if (event.key === "Home") nextPosition = 0;
        else if (event.key === "End") nextPosition = thumbs.length - 1;
        if (nextPosition !== undefined) { event.preventDefault(); select(nextPosition); thumbs[index].focus({ preventScroll: true }); }
      });
    });
    var prev = gallery.querySelector("[data-gallery-prev]"); var next = gallery.querySelector("[data-gallery-next]");
    if (prev) prev.addEventListener("click", function () { select(index - 1); });
    if (next) next.addEventListener("click", function () { select(index + 1); });
    var touchStart = null;
    var touchSurface = main.parentElement;
    touchSurface.addEventListener("touchstart", function (event) { touchStart = event.changedTouches[0].clientX; }, { passive: true });
    touchSurface.addEventListener("touchend", function (event) { if (touchStart === null) return; var movement = event.changedTouches[0].clientX - touchStart; touchStart = null; if (Math.abs(movement) > 55) select(index + (movement < 0 ? 1 : -1)); }, { passive: true });
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
    var guideCloseTimer = null;
    document.querySelectorAll("[data-open-course-guide]").forEach(function (button) {
      button.addEventListener("click", function () {
        guideTrigger = button;
        if (guideCloseTimer !== null) { clearTimeout(guideCloseTimer); guideCloseTimer = null; }
        if (!guide.open) guide.showModal();
        document.body.classList.add("mk-modal-open");
        requestAnimationFrame(function () { guide.classList.add("is-open"); });
      });
    });
    function closeGuide() {
      if (!guide.open || guideCloseTimer !== null) return;
      guide.classList.remove("is-open");
      guideCloseTimer = setTimeout(function () { guideCloseTimer = null; if (guide.open) guide.close(); }, reduceMotion ? 0 : 180);
    }
    guide.querySelectorAll("[data-close-course-guide]").forEach(function (button) { button.addEventListener("click", closeGuide); });
    guide.addEventListener("cancel", function (event) { event.preventDefault(); closeGuide(); });
    guide.addEventListener("click", function (event) { if (event.target !== guide) return; var bounds = guide.getBoundingClientRect(); if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) closeGuide(); });
    guide.addEventListener("close", function () { document.body.classList.remove("mk-modal-open"); if (guideTrigger) guideTrigger.focus(); });
  }

  var descriptionToggle = document.querySelector("[data-toggle-description]");
  var paragraph = document.getElementById("courseDescription");
  if (descriptionToggle && paragraph) {
    function updateDescriptionToggle() {
      var lineHeight = parseFloat(window.getComputedStyle(paragraph).lineHeight) || 24;
      var expanded = paragraph.classList.contains("expanded");
      var overflow = paragraph.scrollHeight > Math.ceil(lineHeight * 3) + 1;
      descriptionToggle.hidden = !expanded && !overflow;
      if (expanded && paragraph.scrollHeight > paragraph.clientHeight + 1) {
        paragraph.tabIndex = 0; paragraph.setAttribute("aria-label", "강의 설명 전체 보기");
      } else { paragraph.removeAttribute("tabindex"); paragraph.removeAttribute("aria-label"); }
    }
    descriptionToggle.addEventListener("click", function () {
      var expanded = paragraph.classList.toggle("expanded");
      descriptionToggle.textContent = expanded ? "설명 접기" : "설명 더보기";
      descriptionToggle.setAttribute("aria-expanded", String(expanded));
      if (!expanded) paragraph.scrollTop = 0;
      updateDescriptionToggle();
    });
    updateDescriptionToggle();
    if ("ResizeObserver" in window) new ResizeObserver(updateDescriptionToggle).observe(paragraph);
    else window.addEventListener("resize", updateDescriptionToggle);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(updateDescriptionToggle);
  }

  document.querySelectorAll(".mk-lecture-card .mk-tags, .mk-purchase-heading .mk-tags").forEach(function (tags) {
    function updateTags() {
      if (tags.scrollHeight > tags.clientHeight + 1) { tags.tabIndex = 0; tags.setAttribute("aria-label", "강의 주제 목록"); }
      else { tags.removeAttribute("tabindex"); tags.removeAttribute("aria-label"); }
    }
    updateTags();
    if ("ResizeObserver" in window) new ResizeObserver(updateTags).observe(tags);
    else window.addEventListener("resize", updateTags);
  });

  document.querySelectorAll("[data-wish-id]").forEach(function (button) {
    button.addEventListener("click", async function (event) {
      event.preventDefault(); event.stopPropagation(); button.disabled = true;
      try {
        var response = await window.MK.fetch("/wishlist/" + encodeURIComponent(button.dataset.wishId) + "/toggle", { method: "POST" });
        if (response.status === 401 || response.status === 403) { window.location.href = "/auth/login"; return; }
        var data = await response.json(); if (!response.ok) throw new Error(data.message || "처리할 수 없습니다.");
        button.classList.toggle("active", data.wished); button.setAttribute("aria-pressed", String(data.wished));
        if (button.classList.contains("mk-wish-btn")) button.setAttribute("aria-label", data.wished ? "찜 해제" : "찜하기");
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
  function normalizedPosition(value) {
    if (value === null || value === undefined || String(value).trim() === "") return null;
    var position = Number(value);
    return Number.isFinite(position) && position >= 0 && position <= 2147483647 ? Math.floor(position) : null;
  }
  // Only the description page receives a server-issued classroom entry token.
  // Classroom cards must keep the requested position while returning through that page.
  function learningPointUrl(lectureId, position, entryToken) {
    var encodedId = encodeURIComponent(lectureId);
    var seekPosition = normalizedPosition(position);
    var parameters = new URLSearchParams();
    if (entryToken) parameters.set("entry", entryToken);
    if (seekPosition !== null) parameters.set("t", String(seekPosition));
    var query = parameters.toString();
    return (entryToken ? "/watch/" : "/lectures/") + encodedId + (query ? "?" + query : "") + (entryToken ? "" : "#course-learning");
  }
  var requestedPosition = normalizedPosition(new URLSearchParams(window.location.search).get("t"));
  window.addEventListener("pageshow", function (event) {
    // Back/forward cache can restore a description whose one-use entry token was consumed.
    if (event.persisted && document.querySelector("[data-classroom-entry]")) window.location.reload();
  });
  if (requestedPosition !== null) {
    document.querySelectorAll("[data-classroom-entry]").forEach(function (link) {
      var target = new URL(link.href, window.location.href);
      target.searchParams.set("t", String(requestedPosition));
      link.href = target.pathname + target.search + target.hash;
    });
  }
  function learningPoint(lectureId, record, available, entryToken) {
    var link = document.createElement(available ? "a" : "div"); link.className = "mk-learning-point-link";
    if (available) link.href = learningPointUrl(lectureId, record.position, entryToken);
    else { link.classList.add("is-locked"); link.setAttribute("aria-disabled", "true"); }
    var media = document.createElement("span"); media.className = "mk-learning-point-media";
    var empty = document.createElement("span"); empty.textContent = record.last ? "이어보기" : "책갈피"; media.appendChild(empty);
    if (record.image) { var image = document.createElement("img"); image.src = record.image; image.alt = ""; image.loading = "lazy"; image.addEventListener("error", function () { image.remove(); }); media.appendChild(image); }
    var time = document.createElement("b"); time.textContent = formatTime(record.position); media.appendChild(time);
    var title = document.createElement("strong"); title.textContent = record.label; title.title = record.label; link.append(media, title);
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
      var entryToken = section.dataset.courseDetail === "true" ? section.dataset.classroomEntryToken : null;
      if (points.length) { content.classList.add("mk-learning-point-grid"); points.forEach(function (point) { content.appendChild(learningPoint(id, point, section.dataset.learningAvailable !== "false", entryToken)); }); }
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
    var previewFallback = '<span class="mk-media-fallback" aria-hidden="true"><i class="icon-film" aria-hidden="true"></i><small>ONLINE CLASS</small><span>강의 이미지 준비 중</span></span>';
    var imageMarkup = imageIds.length ? '<div class="mk-preview-media">' + previewFallback + '<img data-media-image class="mk-preview-main" src="/media/lectures/' + id + '/images/' + encodeURIComponent(imageIds[0]) + '" alt="강의 소개"></div><div class="mk-preview-thumbs">' + imageIds.map(function (imageId, index) { var source = "/media/lectures/" + id + "/images/" + encodeURIComponent(imageId); return '<button type="button" data-img="' + source + '" aria-label="소개 이미지 ' + (index + 1) + '" class="' + (index === 0 ? "active" : "") + '"><span class="mk-media-fallback" aria-hidden="true"><i class="icon-image" aria-hidden="true"></i></span><img data-media-image src="' + source + '" alt=""></button>'; }).join("") + '</div>' : '<div class="mk-preview-media is-image-unavailable">' + previewFallback + '</div>';
    modal.innerHTML = '<div class="mk-preview-dialog"><button class="mk-preview-close" type="button" aria-label="닫기">×</button>' + imageMarkup + '<div class="mk-preview-content"><div class="mk-preview-meta"><strong></strong><span></span></div><h2 id="quickPreviewTitle"></h2><div class="mk-preview-tags"></div><p></p><a class="mk-primary-btn" href="/lectures/' + id + '">강의 상세보기</a></div></div>';
    modal.querySelector("h2").textContent = data.title || "강의"; modal.querySelector("p").textContent = data.description || "";
    modal.querySelector(".mk-preview-meta strong").textContent = data.pricing === "FREE" ? "무료" : Number(data.price || 0).toLocaleString("ko-KR") + "원";
    modal.querySelector(".mk-preview-meta span").textContent = formatTime(data.durationSeconds);
    (data.tags || []).forEach(function (tag) { var span = document.createElement("span"); span.textContent = "#" + tag; span.title = "#" + tag; modal.querySelector(".mk-preview-tags").appendChild(span); });
    modal.querySelectorAll("[data-img]").forEach(function (button) { button.addEventListener("click", function () { modal.querySelectorAll("[data-img]").forEach(function (item) { item.classList.toggle("active", item === button); }); imageTransition(modal.querySelector(".mk-preview-main"), button.dataset.img); revealThumbnail(button); }); });
    var closing = false;
    function close() { if (closing) return; closing = true; modal.classList.remove("open"); setTimeout(function () { modal.close(); modal.remove(); document.body.classList.remove("mk-modal-open"); trigger.focus(); }, reduceMotion ? 0 : 200); }
    modal.querySelector(".mk-preview-close").addEventListener("click", close);
    modal.addEventListener("cancel", function (event) { event.preventDefault(); close(); });
    modal.addEventListener("click", function (event) { if (event.target === modal) close(); });
    document.body.appendChild(modal); watchMedia(modal); modal.showModal(); document.body.classList.add("mk-modal-open"); requestAnimationFrame(function () { modal.classList.add("open"); });
  }
})();
