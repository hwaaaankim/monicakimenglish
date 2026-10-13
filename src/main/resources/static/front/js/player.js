(function () {
  "use strict";

  var shell = document.getElementById("secureLecturePlayer");
  var video = document.getElementById("lecturePlayer");
  var progressStatus = document.getElementById("playerProgress");

  if (!shell || !video || !progressStatus) {
    return;
  }

  var lectureId = shell.dataset.lectureId;
  var loading = document.getElementById("playerLoading");
  var errorBox = document.getElementById("playerError");
  var errorTitle = document.getElementById("playerErrorTitle");
  var errorMessage = document.getElementById("playerErrorMessage");
  var retryBtn = document.getElementById("playerRetryBtn");
  var centerPlay = document.getElementById("playerCenterPlay");
  var controls = document.getElementById("playerControls");
  var playBtn = document.getElementById("playerPlayBtn");
  var playIcon = document.getElementById("playerPlayIcon");
  var backBtn = document.getElementById("playerBackBtn");
  var forwardBtn = document.getElementById("playerForwardBtn");
  var currentTimeEl = document.getElementById("playerCurrentTime");
  var durationEl = document.getElementById("playerDuration");
  var seek = document.getElementById("playerSeek");
  var seekBuffer = document.getElementById("playerSeekBuffer");
  var muteBtn = document.getElementById("playerMuteBtn");
  var muteIcon = document.getElementById("playerMuteIcon");
  var volume = document.getElementById("playerVolume");
  var speedBtn = document.getElementById("playerSpeedBtn");
  var fullscreenBtn = document.getElementById("playerFullscreenBtn");
  var watermark = document.getElementById("playerWatermark");
  var bookmarkAddBtn = document.getElementById("playerBookmarkAddBtn");
  var bookmarkListBtn = document.getElementById("playerBookmarkListBtn");
  var bookmarkCount = document.getElementById("playerBookmarkCount");
  var bookmarkPanel = document.getElementById("playerBookmarkPanel");
  var bookmarkList = document.getElementById("playerBookmarkList");
  var bookmarkEmpty = document.getElementById("playerBookmarkEmpty");
  var bookmarkMarkers = document.getElementById("playerBookmarkMarkers");
  var seekTooltip = document.getElementById("playerSeekTooltip");
  var speedMenu = document.getElementById("playerSpeedMenu");

  var sessionKey = null;
  var playbackUrl = null;
  var resumePosition = 0;
  var playedDelta = 0;
  var lastTick = performance.now();
  var heartbeatTimer = null;
  var controlsTimer = null;
  var watermarkTimer = null;
  var watermarkPositionIndex = 0;
  var renewingTicket = false;
  var streamRetryCount = 0;
  var isScrubbing = false;
  var pendingRestore = null;
  var speedSteps = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2];
  var settingsStorageKey = "mk.player.settings.v2";
  var bookmarks = [];
  var heartbeatSequence = 0;
  var playbackSegments = [];
  var lastMediaPosition = null;
  var sendingHeartbeat = false;
  var retryHeartbeat = null;
  var pendingEnded = false;
  var bookmarkBusy = false;
  var bookmarkDialog = document.getElementById("playerBookmarkDialog");
  function historyPosition(value) {
    if (value === null || value === undefined || String(value).trim() === "") return null;
    var position = Number(value);
    return Number.isFinite(position) && position >= 0 && position <= 2147483647 ? Math.floor(position) : null;
  }
  var requestedPosition = historyPosition(new URLSearchParams(window.location.search).get("t"));
  window.addEventListener("pageshow", function (event) {
    if (!event.persisted) return;
    // A restored player must visit the description again instead of reusing its consumed entry.
    var position = historyPosition(video.currentTime);
    if (position === null) position = requestedPosition;
    video.pause();
    window.location.replace("/lectures/" + encodeURIComponent(lectureId) + (position === null ? "" : "?t=" + position));
  });
  var playerSettings = loadPlayerSettings();

  applyPlayerSettings();
  loadLearningSummary();
  installCasualProtection();
  installWatermark();
  bindControls();
  bindVideoEvents();
  renderBookmarks();
  startPlayerSession();

  async function startPlayerSession() {
    setLoading(true, "안전한 재생 연결을 준비하고 있습니다.");
    hideError();

    try {
      var response = await window.MK.fetch("/api/player/" + encodeURIComponent(lectureId) + "/session", {
        method: "POST"
      });

      if (!response.ok) {
        throw new Error("player session request failed: " + response.status);
      }

      var data = await response.json();
      sessionKey = data.sessionKey || null;
      heartbeatSequence = 0;
      retryHeartbeat = null;
      playbackSegments = [];
      lastMediaPosition = null;
      playbackUrl = data.playbackUrl || null;
      resumePosition = requestedPosition !== null ? requestedPosition : Number(data.lastPositionSeconds || 0);

      if (!sessionKey || !playbackUrl) {
        throw new Error("player session response is incomplete");
      }

      progressStatus.textContent = data.completed
        ? "시청완료 · 100%"
        : "학습 진행률 " + Number(data.progressPercent || 0) + "%";

      attachPlaybackSource(playbackUrl, resumePosition, false);
    } catch (error) {
      console.error("[player] session start failed", error);
      setLoading(false);
      showError(
        "재생 연결을 시작하지 못했습니다.",
        "로그인 상태와 수강 기간을 확인한 뒤 다시 시도해 주세요."
      );
      progressStatus.textContent = "학습 기록을 시작하지 못했습니다.";
    }
  }

  function attachPlaybackSource(url, restoreTime, autoplayAfterLoad) {
    if (!url) {
      return;
    }

    pendingRestore = {
      time: Math.max(0, Number(restoreTime || 0)),
      autoplay: Boolean(autoplayAfterLoad)
    };

    setLoading(true, "영상을 불러오고 있습니다.");
    hideError();

    video.pause();
    video.removeAttribute("src");
    video.load();
    video.src = url;
    video.load();
  }

  async function renewPlaybackTicket() {
    if (renewingTicket) {
      return;
    }

    renewingTicket = true;
    var restoreTime = Number.isFinite(video.currentTime) ? video.currentTime : 0;
    var shouldResume = !video.paused && !video.ended;

    setLoading(true, "재생 권한을 다시 확인하고 있습니다.");

    try {
      var response = await window.MK.fetch("/api/player/" + encodeURIComponent(lectureId) + "/ticket", {
        method: "POST"
      });

      if (!response.ok) {
        throw new Error("ticket refresh failed: " + response.status);
      }

      var data = await response.json();
      if (!data.playbackUrl) {
        throw new Error("ticket response has no playbackUrl");
      }

      playbackUrl = data.playbackUrl;
      attachPlaybackSource(playbackUrl, restoreTime, shouldResume);
    } catch (error) {
      console.error("[player] ticket refresh failed", error);
      setLoading(false);
      showError(
        "재생 권한을 갱신하지 못했습니다.",
        "페이지를 새로고침하거나 다시 로그인한 뒤 시도해 주세요."
      );
    } finally {
      renewingTicket = false;
    }
  }

  function bindVideoEvents() {
    video.addEventListener("loadedmetadata", function () {
      durationEl.textContent = formatTime(video.duration);
      updateSeekUI();
      renderBookmarkMarkers();

      if (pendingRestore) {
        var restore = pendingRestore;
        pendingRestore = null;

        var maxPosition = Math.max(0, (video.duration || 0) - 0.1);
        var target = Math.min(Math.max(0, restore.time), maxPosition);

        if (target > 0) {
          try {
            video.currentTime = target;
          } catch (ignored) {
          }
        }

        if (restore.autoplay) {
          safePlay();
        }
      }
    });

    video.addEventListener("loadeddata", function () {
      setLoading(false);
    });

    video.addEventListener("canplay", function () {
      setLoading(false);
      streamRetryCount = 0;
    });

    video.addEventListener("playing", function () {
      setLoading(false);
      updatePlayState();
      lastTick = performance.now();
      lastMediaPosition = Number(video.currentTime || 0);
      ensureHeartbeatTimer();
      scheduleControlsHide();
    });

    video.addEventListener("play", function () {
      lastTick = performance.now();
      updatePlayState();
      ensureHeartbeatTimer();
      scheduleControlsHide();
    });

    video.addEventListener("pause", function () {
      updatePlayState();
      showControls();
      clearControlsTimer();
      sendHeartbeat(false);
      lastTick = performance.now();
    });

    video.addEventListener("ended", function () {
      updatePlayState();
      showControls();
      sendHeartbeat(true);
      clearHeartbeatTimer();
    });

    video.addEventListener("waiting", function () {
      if (!video.paused && !video.ended) {
        setLoading(true, "네트워크 상태를 확인하며 영상을 불러오고 있습니다.");
      }
    });

    video.addEventListener("stalled", function () {
      if (!video.paused && !video.ended) {
        setLoading(true, "영상 데이터를 다시 연결하고 있습니다.");
      }
    });

    video.addEventListener("seeking", function () {
      lastMediaPosition = null;
      setLoading(true, "이동한 위치를 불러오고 있습니다.");
    });

    video.addEventListener("seeked", function () {
      setLoading(false);
      lastTick = performance.now();
      lastMediaPosition = Number(video.currentTime || 0);
    });

    video.addEventListener("timeupdate", function () {
      var now = performance.now();
      var current = Number(video.currentTime || 0);
      var wallDelta = (now - lastTick) / 1000;
      if (!video.paused && !video.seeking && video.readyState >= 2 && !document.hidden && lastMediaPosition !== null) {
        var mediaDelta = current - lastMediaPosition;
        var expectedDelta = Math.max(0, wallDelta) * Number(video.playbackRate || 1);
        // Only contiguous media actually played is counted. Seeking never bridges an unseen interval.
        if (wallDelta > 0 && wallDelta < 2.5 && mediaDelta > 0 && mediaDelta <= expectedDelta + 0.6) {
          playedDelta += wallDelta;
          appendPlaybackSegment(lastMediaPosition, current, wallDelta);
        }
      }
      lastTick = now;
      lastMediaPosition = !video.seeking ? current : null;
      if (!isScrubbing) updateSeekUI();
      currentTimeEl.textContent = formatTime(current);
    });

    function appendPlaybackSegment(start, end, wallSeconds) {
      var previous = playbackSegments[playbackSegments.length - 1];
      if (previous && Math.abs(previous.endSeconds - start) < 0.25) {
        previous.endSeconds = end;
        previous.wallSeconds += wallSeconds;
        previous.maxRate = Math.max(previous.maxRate, Number(video.playbackRate || 1));
      } else if (playbackSegments.length < 64) {
        playbackSegments.push({ startSeconds: start, endSeconds: end, wallSeconds: wallSeconds, maxRate: Number(video.playbackRate || 1) });
      }
    }

    video.addEventListener("progress", updateBufferedUI);
    video.addEventListener("durationchange", function () {
      durationEl.textContent = formatTime(video.duration);
      renderBookmarkMarkers();
    });

    video.addEventListener("ratechange", function () {
      lastTick = performance.now();
      lastMediaPosition = Number(video.currentTime || 0);
      var currentRate = Number(video.playbackRate || 1);
      if (speedSteps.indexOf(currentRate) >= 0) {
        playerSettings.rate = currentRate;
        savePlayerSettings();
        if (speedBtn) {
          speedBtn.textContent = formatRate(currentRate);
        }
      }
    });

    video.addEventListener("error", async function () {
      setLoading(false);
      var mediaError = video.error;
      var code = mediaError ? mediaError.code : 0;

      console.error("[player] media error", {
        code: code,
        message: mediaError && mediaError.message ? mediaError.message : "",
        networkState: video.networkState,
        readyState: video.readyState
      });

      // 네트워크/소스 접근 오류는 만료된 재생 티켓일 수 있으므로 한 번만 자동 갱신합니다.
      if ((code === 2 || code === 4) && streamRetryCount < 1 && sessionKey) {
        streamRetryCount += 1;
        await renewPlaybackTicket();
        return;
      }

      if (code === 3) {
        showError(
          "영상 형식을 브라우저에서 해석하지 못했습니다.",
          "영상 파일이 손상되었거나 코덱이 호환되지 않을 수 있습니다. MP4(H.264/AAC) 형식으로 다시 등록해 주세요."
        );
      } else if (code === 4) {
        showError(
          "재생 가능한 영상 소스를 확인하지 못했습니다.",
          "재생 권한이 만료되었거나 영상 형식이 브라우저와 호환되지 않을 수 있습니다."
        );
      } else {
        showError(
          "영상을 불러오지 못했습니다.",
          "네트워크 연결을 확인한 뒤 다시 연결해 주세요."
        );
      }
    });

    video.addEventListener("click", function () {
      togglePlay();
    });

    video.addEventListener("dblclick", function (event) {
      event.preventDefault();
      toggleFullscreen();
    });

    window.addEventListener("pagehide", function () {
      savePlayerSettings();
      sendFinalHeartbeat();
    });
    document.addEventListener("visibilitychange", function () {
      if (document.hidden && !video.paused) {
        // 백그라운드에서 시간 누적이 과하게 발생하지 않도록 즉시 현재까지를 기록합니다.
        sendHeartbeat(false);
        lastTick = performance.now();
        lastMediaPosition = null;
      } else {
        lastTick = performance.now();
        lastMediaPosition = Number(video.currentTime || 0);
      }
    });
  }

  function bindControls() {
    playBtn.addEventListener("click", togglePlay);
    centerPlay.addEventListener("click", togglePlay);

    backBtn.addEventListener("click", function () {
      seekBy(-10);
    });

    forwardBtn.addEventListener("click", function () {
      seekBy(10);
    });

    seek.addEventListener("pointerdown", function () {
      isScrubbing = true;
      showControls();
    });

    seek.addEventListener("input", function () {
      var duration = Number(video.duration || 0);
      if (!duration) {
        return;
      }
      var target = duration * (Number(seek.value) / 1000);
      currentTimeEl.textContent = formatTime(target);
      seek.style.setProperty("--played", (Number(seek.value) / 10) + "%");
    });

    seek.addEventListener("change", function () {
      var duration = Number(video.duration || 0);
      if (duration) {
        video.currentTime = duration * (Number(seek.value) / 1000);
      }
      isScrubbing = false;
    });

    seek.addEventListener("pointerup", function () {
      isScrubbing = false;
    });

    volume.addEventListener("input", function () {
      video.volume = clamp(Number(volume.value), 0, 1);
      video.muted = video.volume <= 0;
      updateVolumeUI();
      savePlayerSettings();
    });

    muteBtn.addEventListener("click", function () {
      video.muted = !video.muted;
      if (!video.muted && video.volume <= 0) {
        video.volume = 0.6;
        volume.value = "0.6";
      }
      updateVolumeUI();
      savePlayerSettings();
    });

    speedBtn.addEventListener("click", function (event) {
      event.stopPropagation();
      togglePopover(speedMenu, speedBtn);
    });

    if (speedMenu) {
      speedMenu.addEventListener("click", function (event) {
        var button = event.target.closest("[data-player-speed]");
        if (!button) {
          return;
        }
        var rate = Number(button.getAttribute("data-player-speed"));
        setPlaybackRate(rate, true);
        closeAllPopovers();
      });
    }

    if (bookmarkDialog) {
      document.getElementById("bookmarkEditorForm").addEventListener("submit", saveBookmark);
      bookmarkDialog.querySelectorAll("[data-close-bookmark-editor]").forEach(function (button) { button.addEventListener("click", function () { if (!bookmarkBusy) bookmarkDialog.close(); }); });
    }

    if (bookmarkAddBtn) {
      bookmarkAddBtn.addEventListener("click", function () {
        addBookmark();
      });
    }

    if (bookmarkListBtn) {
      bookmarkListBtn.addEventListener("click", function (event) {
        event.stopPropagation();
        togglePopover(bookmarkPanel, bookmarkListBtn);
      });
    }

    if (bookmarkList) {
      bookmarkList.addEventListener("click", function (event) {
        var seekButton = event.target.closest("[data-bookmark-seek]");
        var deleteButton = event.target.closest("[data-bookmark-delete]");
        var editButton = event.target.closest("[data-bookmark-edit]");
        if (editButton) { event.stopPropagation(); openBookmarkEditor(editButton.dataset.bookmarkEdit); return; }

        if (deleteButton) {
          event.stopPropagation();
          removeBookmark(deleteButton.getAttribute("data-bookmark-delete"));
          return;
        }

        if (seekButton) {
          var time = Number(seekButton.getAttribute("data-bookmark-seek"));
          if (Number.isFinite(time)) {
            video.currentTime = clamp(time, 0, Math.max(0, Number(video.duration || time)));
            updateSeekUI();
            closeAllPopovers();
            showSecurityToast("책갈피 " + formatTime(time) + " 위치로 이동했습니다.");
          }
        }
      });
    }

    if (seek) {
      seek.addEventListener("pointermove", updateSeekTooltip);
      seek.addEventListener("pointerenter", function (event) {
        updateSeekTooltip(event);
        if (seekTooltip) {
          seekTooltip.classList.add("is-visible");
        }
      });
      seek.addEventListener("pointerleave", function () {
        if (seekTooltip) {
          seekTooltip.classList.remove("is-visible");
        }
      });
    }

    document.addEventListener("click", function (event) {
      if (!event.target.closest(".mk-player-popover-wrap")) {
        closeAllPopovers();
      }
    });

    fullscreenBtn.addEventListener("click", toggleFullscreen);

    retryBtn.addEventListener("click", function () {
      hideError();
      streamRetryCount = 0;
      if (sessionKey) {
        renewPlaybackTicket();
      } else {
        startPlayerSession();
      }
    });

    shell.addEventListener("mousemove", function () {
      showControls();
      scheduleControlsHide();
    });

    shell.addEventListener("mouseenter", showControls);
    shell.addEventListener("mouseleave", function () {
      if (!video.paused) {
        scheduleControlsHide(450);
      }
    });

    shell.addEventListener("touchstart", function () {
      showControls();
      scheduleControlsHide(3000);
    }, { passive: true });

    document.addEventListener("fullscreenchange", function () {
      shell.classList.toggle("is-fullscreen", document.fullscreenElement === shell);
      showControls();
    });

    updatePlayState();
    updateVolumeUI();
  }

  function installCasualProtection() {
    // 브라우저 UI/개발자도구 자체를 기술적으로 완전히 차단할 수는 없습니다.
    // 아래 처리는 우클릭/대표 단축키/드래그 같은 '일반적인 저장 시도'를 억제하는 UX 방어입니다.
    document.addEventListener("contextmenu", function (event) {
      if (event.target.closest(".mk-watch-page")) {
        event.preventDefault();
        showSecurityToast("수강 콘텐츠 보호를 위해 우클릭 메뉴를 사용할 수 없습니다.");
      }
    }, true);

    video.addEventListener("dragstart", function (event) {
      event.preventDefault();
    });

    document.addEventListener("keydown", function (event) {
      var key = String(event.key || "").toLowerCase();
      var modifier = event.ctrlKey || event.metaKey;
      var devtoolsShortcut = event.key === "F12"
        || (event.ctrlKey && event.shiftKey && ["i", "j", "c"].indexOf(key) >= 0)
        || (event.metaKey && event.altKey && ["i", "j", "c"].indexOf(key) >= 0)
        || (modifier && key === "u")
        || (modifier && key === "s")
        || (modifier && key === "p")
        || key === "printscreen";

      if (bookmarkDialog && bookmarkDialog.open) return;

      if (devtoolsShortcut) {
        event.preventDefault();
        event.stopPropagation();
        showSecurityToast("수강 콘텐츠 보호 기능이 적용되어 있습니다.");
        return;
      }

      var activeElement = document.activeElement;
      var activeTag = activeElement ? activeElement.tagName : "";
      if (["INPUT", "TEXTAREA", "SELECT", "BUTTON", "A"].indexOf(activeTag) >= 0
          || (activeElement && activeElement.isContentEditable)) {
        return;
      }

      if (event.key === "Escape") {
        closeAllPopovers();
        return;
      }

      if (event.code === "Space" || key === "k") {
        event.preventDefault();
        togglePlay();
      } else if (event.key === "ArrowLeft" || key === "j") {
        event.preventDefault();
        seekBy(-10);
      } else if (event.key === "ArrowRight" || key === "l") {
        event.preventDefault();
        seekBy(10);
      } else if (key === "b") {
        event.preventDefault();
        addBookmark();
      } else if (key === "m") {
        event.preventDefault();
        video.muted = !video.muted;
        updateVolumeUI();
        savePlayerSettings();
      } else if (key === "f") {
        event.preventDefault();
        toggleFullscreen();
      } else if (event.key === "ArrowUp") {
        event.preventDefault();
        video.volume = clamp(video.volume + 0.1, 0, 1);
        video.muted = false;
        volume.value = String(video.volume);
        updateVolumeUI();
        savePlayerSettings();
      } else if (event.key === "ArrowDown") {
        event.preventDefault();
        video.volume = clamp(video.volume - 0.1, 0, 1);
        volume.value = String(video.volume);
        updateVolumeUI();
        savePlayerSettings();
      }
    }, true);
  }

  function installWatermark() {
    if (!watermark) {
      return;
    }

    updateWatermarkText();
    positionWatermark();
    moveWatermark();

    window.addEventListener("resize", positionWatermark);
    video.addEventListener("loadedmetadata", positionWatermark);
    document.addEventListener("fullscreenchange", function () { requestAnimationFrame(positionWatermark); });
    if (typeof ResizeObserver !== "undefined") {
      var watermarkResizeObserver = new ResizeObserver(positionWatermark);
      watermarkResizeObserver.observe(video);
      window.addEventListener("pagehide", function () { watermarkResizeObserver.disconnect(); });
    }

    watermarkTimer = window.setInterval(function () {
      updateWatermarkText();
      moveWatermark();
    }, 17000);

    window.addEventListener("pagehide", function () {
      if (watermarkTimer) {
        clearInterval(watermarkTimer);
        watermarkTimer = null;
      }
    });
  }

  function updateWatermarkText() {
    var viewer = (shell.dataset.watermark || "수강 회원").trim();
    var now = new Date();
    var stamp = now.getFullYear() + "-"
      + pad2(now.getMonth() + 1) + "-"
      + pad2(now.getDate()) + " "
      + pad2(now.getHours()) + ":"
      + pad2(now.getMinutes());

    watermark.textContent = viewer + " · " + stamp;
  }

  function moveWatermark() {
    if (!watermark) {
      return;
    }

    watermarkPositionIndex = (watermarkPositionIndex + 1) % 5;
    watermark.classList.add("is-moving");

    window.setTimeout(function () {
      positionWatermark();
      watermark.classList.remove("is-moving");
    }, 220);
  }

  function positionWatermark() {
    if (!watermark || !video.clientWidth || !video.clientHeight) return;
    // Use the displayed video picture, excluding the mobile controls below it
    // and any object-fit letterboxing. Recompute after rotation/fullscreen.
    var width = video.clientWidth;
    var height = video.clientHeight;
    var left = video.offsetLeft;
    var top = video.offsetTop;
    if (video.videoWidth > 0 && video.videoHeight > 0) {
      var scale = Math.min(width / video.videoWidth, height / video.videoHeight);
      var pictureWidth = video.videoWidth * scale;
      var pictureHeight = video.videoHeight * scale;
      left += (width - pictureWidth) / 2;
      top += (height - pictureHeight) / 2;
      width = pictureWidth;
      height = pictureHeight;
    }
    var positions = [[.04, .07], [.62, .10], [.07, .66], [.58, .68], [.34, .38]];
    var position = positions[watermarkPositionIndex];
    var padding = Math.min(8, width / 10, height / 10);
    watermark.style.maxWidth = Math.max(20, Math.min(width * .4, width - padding * 2)) + "px";
    watermark.style.left = (left + clamp(width * position[0], padding, Math.max(padding, width - watermark.offsetWidth - padding))) + "px";
    watermark.style.top = (top + clamp(height * position[1], padding, Math.max(padding, height - watermark.offsetHeight - padding))) + "px";
  }

  function safeLocalStorageGet(key) {
    try {
      return window.localStorage ? window.localStorage.getItem(key) : null;
    } catch (error) {
      return null;
    }
  }

  function safeLocalStorageSet(key, value) {
    try {
      if (window.localStorage) {
        window.localStorage.setItem(key, value);
        return true;
      }
    } catch (error) {
    }
    return false;
  }

  function loadPlayerSettings() {
    var defaults = { rate: 1, volume: 1, muted: false };
    var raw = safeLocalStorageGet(settingsStorageKey);
    if (!raw) {
      return defaults;
    }

    try {
      var parsed = JSON.parse(raw);
      var rate = speedSteps.indexOf(Number(parsed.rate)) >= 0 ? Number(parsed.rate) : 1;
      var volumeValue = clamp(Number(parsed.volume), 0, 1);
      if (!Number.isFinite(volumeValue)) {
        volumeValue = 1;
      }
      return {
        rate: rate,
        volume: volumeValue,
        muted: Boolean(parsed.muted)
      };
    } catch (error) {
      return defaults;
    }
  }

  function applyPlayerSettings() {
    video.volume = clamp(Number(playerSettings.volume), 0, 1);
    video.muted = Boolean(playerSettings.muted);
    setPlaybackRate(playerSettings.rate, false);
    updateVolumeUI();
  }

  function savePlayerSettings() {
    playerSettings = {
      rate: Number(video.playbackRate || 1),
      volume: clamp(Number(video.volume || 0), 0, 1),
      muted: Boolean(video.muted)
    };
    safeLocalStorageSet(settingsStorageKey, JSON.stringify(playerSettings));
  }

  function setPlaybackRate(rate, announce) {
    var safeRate = speedSteps.indexOf(Number(rate)) >= 0 ? Number(rate) : 1;
    video.defaultPlaybackRate = safeRate;
    video.playbackRate = safeRate;
    if (speedBtn) {
      speedBtn.textContent = formatRate(safeRate);
    }
    if (speedMenu) {
      speedMenu.querySelectorAll("[data-player-speed]").forEach(function (button) {
        button.classList.toggle("is-active", Number(button.getAttribute("data-player-speed")) === safeRate);
      });
    }
    playerSettings.rate = safeRate;
    savePlayerSettings();
    if (announce) {
      showSecurityToast("재생 속도 " + formatRate(safeRate));
    }
  }

  function formatRate(rate) {
    return String(Number(rate)) + "×";
  }

  async function loadLearningSummary() {
    try {
      var response = await window.MK.fetch("/api/player/" + encodeURIComponent(lectureId) + "/learning-summary");
      if (!response.ok) throw new Error("learning summary " + response.status);
      var data = await response.json();
      bookmarks = (Array.isArray(data.bookmarks) ? data.bookmarks : []).map(function (item) {
        return { id: String(item.id), time: Number(item.positionSeconds || 0), label: String(item.label || "책갈피"), thumbnailUrl: item.thumbnailUrl || null };
      }).slice(0, 5);
      renderBookmarks();
      renderSavedLearning(data);
    } catch (error) {
      var status = document.getElementById("playerLearningNotice");
      if (status) status.textContent = "학습 기록을 불러오지 못했습니다. 새로고침 후 다시 확인해 주세요.";
    }
  }

  function renderSavedLearning(data) {
    var content = document.getElementById("playerLearningContent");
    if (!content) return;
    var records = [];
    if (data.lastWatchedAt) records.push({ position: Number(data.lastPositionSeconds), label: "마지막 시청 위치", image: data.lastThumbnailUrl, last: true });
    bookmarks.forEach(function (item) { records.push({ position: item.time, label: item.label, image: item.thumbnailUrl, id: item.id }); });
    content.replaceChildren();
    if (!records.length) {
      var empty = document.createElement("p");
      empty.className = "mk-learning-empty";
      empty.textContent = "시청한 위치는 자동으로 저장됩니다. 기억하고 싶은 장면에는 책갈피를 추가해 보세요.";
      content.appendChild(empty);
      return;
    }
    records.forEach(function (record) {
      var card = document.createElement("div"); card.className = "mk-learning-point";
      var button = document.createElement("button"); button.type = "button"; button.className = "mk-learning-point-link";
      var media = document.createElement("span"); media.className = "mk-learning-point-media";
      var placeholder = document.createElement("span"); placeholder.textContent = record.last ? "이어보기" : "책갈피"; media.appendChild(placeholder);
      if (record.image) { var img = document.createElement("img"); img.src = record.image; img.alt = ""; img.loading = "lazy"; img.addEventListener("error", function () { img.remove(); }); media.appendChild(img); }
      var time = document.createElement("b"); time.textContent = formatTime(record.position); media.appendChild(time);
      var title = document.createElement("strong"); title.textContent = record.label; button.append(media, title);
      button.addEventListener("click", function () { video.currentTime = clamp(record.position, 0, Math.max(0, Number(video.duration || record.position))); safePlay(); shell.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "center" }); });
      card.appendChild(button);
      if (record.id) { var actions = document.createElement("div"); actions.className = "mk-learning-point-actions";
        var rename = document.createElement("button"); rename.type = "button"; rename.textContent = "이름 수정"; rename.addEventListener("click", function () { openBookmarkEditor(record.id); });
        var remove = document.createElement("button"); remove.type = "button"; remove.textContent = "삭제"; remove.addEventListener("click", function () { removeBookmark(record.id); }); actions.append(rename, remove); card.appendChild(actions); }
      content.appendChild(card);
    });
  }

  function addBookmark() { openBookmarkEditor(null); }

  function openBookmarkEditor(id) {
    if (bookmarkBusy || !bookmarkDialog) return;
    var item = id ? bookmarks.find(function (entry) { return entry.id === String(id); }) : null;
    if (!id && (!Number.isFinite(video.duration) || video.duration <= 0)) { showSecurityToast("영상이 준비된 뒤 책갈피를 추가할 수 있습니다."); return; }
    if (!id && bookmarks.length >= 5) { showSecurityToast("책갈피는 강의당 최대 5개입니다. 기존 책갈피를 삭제한 후 추가해 주세요."); return; }
    if (!id && bookmarks.some(function (entry) { return Math.abs(entry.time - video.currentTime) < 2; })) { showSecurityToast("현재 위치 근처에 이미 책갈피가 있습니다."); return; }
    bookmarkDialog.dataset.bookmarkId = item ? item.id : "";
    bookmarkDialog.dataset.position = String(item ? item.time : Math.floor(video.currentTime || 0));
    document.getElementById("bookmarkEditorTitle").textContent = item ? "책갈피 이름 수정" : "현재 장면 책갈피 추가";
    document.getElementById("bookmarkEditorTime").textContent = formatTime(Number(bookmarkDialog.dataset.position));
    var input = document.getElementById("bookmarkLabelInput"); input.value = item ? item.label : "책갈피 " + (bookmarks.length + 1);
    document.getElementById("bookmarkEditorError").textContent = "";
    if (document.fullscreenElement === shell) shell.appendChild(bookmarkDialog);
    bookmarkDialog.showModal(); input.focus(); input.select();
  }

  async function saveBookmark(event) {
    event.preventDefault();
    if (bookmarkBusy) return;
    var id = bookmarkDialog.dataset.bookmarkId;
    var label = document.getElementById("bookmarkLabelInput").value.trim();
    if (!label) { document.getElementById("bookmarkEditorError").textContent = "책갈피 이름을 입력해 주세요."; return; }
    bookmarkBusy = true;
    var button = document.getElementById("bookmarkSaveButton"); button.disabled = true;
    try {
      var url = "/api/player/" + encodeURIComponent(lectureId) + "/bookmarks" + (id ? "/" + encodeURIComponent(id) : "");
      var response = await window.MK.fetch(url, { method: id ? "PUT" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ positionSeconds: Number(bookmarkDialog.dataset.position), label: label }) });
      if (!response.ok) { var errorData = await response.json().catch(function () { return {}; }); throw new Error(errorData.message || "책갈피를 저장하지 못했습니다."); }
      bookmarkDialog.close(); await loadLearningSummary(); showSecurityToast(id ? "책갈피 이름을 수정했습니다." : "현재 장면을 책갈피에 저장했습니다.");
    } catch (error) { document.getElementById("bookmarkEditorError").textContent = error.message; }
    finally { bookmarkBusy = false; button.disabled = false; }
  }

  async function removeBookmark(id) {
    if (bookmarkBusy) return;
    bookmarkBusy = true;
    try {
      var response = await window.MK.fetch("/api/player/" + encodeURIComponent(lectureId) + "/bookmarks/" + encodeURIComponent(id), { method: "DELETE" });
      if (!response.ok) throw new Error("책갈피를 삭제하지 못했습니다.");
      await loadLearningSummary(); showSecurityToast("책갈피를 삭제했습니다.");
    } catch (error) { showSecurityToast(error.message); }
    finally { bookmarkBusy = false; }
  }

  function renderBookmarks() {
    if (bookmarkCount) bookmarkCount.textContent = String(bookmarks.length) + "/5";
    if (bookmarkEmpty) bookmarkEmpty.hidden = bookmarks.length > 0;
    if (bookmarkList) {
      bookmarkList.innerHTML = bookmarks.map(function (item) {
        return '<div class="mk-player-bookmark-row"><button type="button" class="mk-player-bookmark-seek" data-bookmark-seek="' + item.time + '">'
          + '<span class="mk-bookmark-point-media"><span aria-hidden="true">▶</span>'
          + (item.thumbnailUrl ? '<img class="mk-bookmark-mini-thumbnail" src="' + escapeHtml(item.thumbnailUrl) + '" alt="" loading="lazy">' : '') + '</span>'
          + '<span class="mk-bookmark-point-copy"><span class="mk-player-bookmark-time">' + escapeHtml(formatTime(item.time)) + '</span><span class="mk-player-bookmark-label">' + escapeHtml(item.label) + '</span></span></button>'
          + '<button type="button" class="mk-player-bookmark-delete" data-bookmark-edit="' + escapeHtml(item.id) + '" aria-label="책갈피 이름 수정">✎</button>'
          + '<button type="button" class="mk-player-bookmark-delete" data-bookmark-delete="' + escapeHtml(item.id) + '" aria-label="책갈피 삭제">×</button></div>';
      }).join("");
      bookmarkList.querySelectorAll("img").forEach(function (img) { img.addEventListener("error", function () { img.remove(); }); });
    }
    renderBookmarkMarkers();
  }

  function renderBookmarkMarkers() {
    if (!bookmarkMarkers) {
      return;
    }
    var duration = Number(video.duration || 0);
    if (!duration || bookmarks.length === 0) {
      bookmarkMarkers.innerHTML = "";
      return;
    }
    bookmarkMarkers.innerHTML = bookmarks.map(function (item) {
      var percent = clamp((item.time / duration) * 100, 0, 100);
      return '<span class="mk-player-bookmark-marker" style="left:' + percent.toFixed(4) + '%"></span>';
    }).join("");
  }

  function updateSeekTooltip(event) {
    if (!seekTooltip || !seek || !video.duration) {
      return;
    }
    var rect = seek.getBoundingClientRect();
    var ratio = clamp((event.clientX - rect.left) / Math.max(1, rect.width), 0, 1);
    seekTooltip.textContent = formatTime(ratio * video.duration);
    seekTooltip.style.left = (ratio * 100) + "%";
  }

  function togglePopover(panel, trigger) {
    if (!panel) {
      return;
    }
    var opening = !panel.classList.contains("is-open");
    closeAllPopovers();
    if (opening) {
      panel.hidden = false;
      requestAnimationFrame(function () {
        panel.classList.add("is-open");
      });
      if (trigger) {
        trigger.setAttribute("aria-expanded", "true");
      }
      showControls();
      clearControlsTimer();
    }
  }

  function closeAllPopovers() {
    [
      { panel: bookmarkPanel, trigger: bookmarkListBtn },
      { panel: speedMenu, trigger: speedBtn }
    ].forEach(function (entry) {
      if (!entry.panel) {
        return;
      }
      entry.panel.classList.remove("is-open");
      if (entry.trigger) {
        entry.trigger.setAttribute("aria-expanded", "false");
      }
      window.setTimeout(function () {
        if (!entry.panel.classList.contains("is-open")) {
          entry.panel.hidden = true;
        }
      }, 190);
    });
    scheduleControlsHide();
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function togglePlay() {
    if (!video.src) {
      return;
    }
    if (video.paused || video.ended) {
      safePlay();
    } else {
      video.pause();
    }
  }

  async function safePlay() {
    try {
      await video.play();
    } catch (error) {
      if (error && error.name !== "AbortError") {
        console.warn("[player] play request rejected", error);
      }
    }
  }

  function seekBy(seconds) {
    var duration = Number(video.duration || 0);
    if (!duration) {
      return;
    }
    video.currentTime = clamp(video.currentTime + seconds, 0, duration);
    showControls();
    showSecurityToast((seconds > 0 ? "+" : "") + seconds + "초");
  }

  function toggleFullscreen() {
    if (document.fullscreenElement === shell) {
      if (document.exitFullscreen) {
        document.exitFullscreen();
      }
      return;
    }

    if (shell.requestFullscreen) {
      shell.requestFullscreen().catch(function () {
        showSecurityToast("이 브라우저에서는 전체 화면을 시작할 수 없습니다.");
      });
    }
  }

  function updatePlayState() {
    var playing = !video.paused && !video.ended;
    playIcon.textContent = playing ? "Ⅱ" : "▶";
    playBtn.setAttribute("aria-label", playing ? "일시정지" : "재생");
    centerPlay.setAttribute("aria-label", playing ? "일시정지" : "재생");
    centerPlay.classList.toggle("is-hidden", playing);
    shell.classList.toggle("is-playing", playing);
  }

  function updateVolumeUI() {
    var effectiveVolume = video.muted ? 0 : video.volume;
    volume.value = String(effectiveVolume);
    volume.style.setProperty("--volume", Math.round(effectiveVolume * 100) + "%");
    muteIcon.textContent = effectiveVolume <= 0 ? "MUTE" : (effectiveVolume < 0.5 ? "VOL-" : "VOL");
    muteBtn.setAttribute("aria-label", effectiveVolume <= 0 ? "음소거 해제" : "음소거");
  }

  function updateSeekUI() {
    var duration = Number(video.duration || 0);
    var current = Number(video.currentTime || 0);
    var ratio = duration > 0 ? clamp(current / duration, 0, 1) : 0;
    var value = Math.round(ratio * 1000);

    seek.value = String(value);
    seek.style.setProperty("--played", (ratio * 100) + "%");
    currentTimeEl.textContent = formatTime(current);
    durationEl.textContent = formatTime(duration);
    updateBufferedUI();
  }

  function updateBufferedUI() {
    if (!video.duration || !video.buffered || video.buffered.length === 0) {
      seek.style.setProperty("--buffered", "0%");
      if (seekBuffer) {
        seekBuffer.style.width = "0%";
      }
      return;
    }

    var end = 0;
    for (var i = 0; i < video.buffered.length; i += 1) {
      if (video.buffered.start(i) <= video.currentTime + 0.5) {
        end = Math.max(end, video.buffered.end(i));
      }
    }
    if (!end) {
      end = video.buffered.end(video.buffered.length - 1);
    }

    var ratio = clamp(end / video.duration, 0, 1);
    seek.style.setProperty("--buffered", (ratio * 100) + "%");
    if (seekBuffer) {
      seekBuffer.style.width = (ratio * 100) + "%";
    }
  }

  function showControls() {
    shell.classList.remove("controls-hidden");
  }

  function scheduleControlsHide(delay) {
    clearControlsTimer();
    if (video.paused || video.ended
        || (bookmarkPanel && bookmarkPanel.classList.contains("is-open"))
        || (speedMenu && speedMenu.classList.contains("is-open"))) {
      return;
    }
    controlsTimer = window.setTimeout(function () {
      if (!video.paused && !isScrubbing) {
        shell.classList.add("controls-hidden");
      }
    }, Number(delay || 2200));
  }

  function clearControlsTimer() {
    if (controlsTimer) {
      clearTimeout(controlsTimer);
      controlsTimer = null;
    }
  }

  function setLoading(visible, message) {
    if (!loading) {
      return;
    }
    loading.classList.toggle("is-visible", Boolean(visible));
    var strong = loading.querySelector("strong");
    if (strong && message) {
      strong.textContent = message;
    }
  }

  function showError(title, message) {
    setLoading(false);
    errorTitle.textContent = title;
    errorMessage.textContent = message;
    errorBox.hidden = false;
    shell.classList.add("has-error");
    centerPlay.classList.add("is-hidden");
  }

  function hideError() {
    errorBox.hidden = true;
    shell.classList.remove("has-error");
    updatePlayState();
  }

  function ensureHeartbeatTimer() {
    if (!heartbeatTimer) {
      heartbeatTimer = window.setInterval(function () {
        sendHeartbeat(false);
      }, 10000);
    }
  }

  function clearHeartbeatTimer() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  function heartbeatPayload(ended) {
    var allowance = Math.min(playedDelta, 20);
    var consumed = 0;
    var segments = [];
    var maximumRate = 0.5;
    // Keep unsent intervals after a failed connection; send only a bounded wall-time batch.
    while (playbackSegments.length && consumed < allowance && segments.length < 64) {
      var segment = playbackSegments[0];
      var wallSeconds = Math.min(segment.wallSeconds, allowance - consumed);
      if (wallSeconds <= 0) { playbackSegments.shift(); continue; }
      var end = segment.startSeconds + (segment.endSeconds - segment.startSeconds) * (wallSeconds / segment.wallSeconds);
      segments.push({ startSeconds: segment.startSeconds, endSeconds: end });
      maximumRate = Math.max(maximumRate, segment.maxRate);
      consumed += wallSeconds;
      if (wallSeconds >= segment.wallSeconds - 0.00001) playbackSegments.shift();
      else { segment.startSeconds = end; segment.wallSeconds -= wallSeconds; }
    }
    playedDelta = Math.max(0, playedDelta - consumed);
    return {
      sessionKey: sessionKey,
      currentTime: Number(video.currentTime || 0),
      duration: Number.isFinite(video.duration) ? Number(video.duration) : 0,
      playedDelta: consumed,
      ended: Boolean(ended && playbackSegments.length === 0),
      playbackSegments: segments,
      playbackRate: segments.length ? maximumRate : Number(video.playbackRate || 1),
      sequence: ++heartbeatSequence
    };
  }

  async function sendHeartbeat(ended) {
    if (ended) pendingEnded = true;
    if (!sessionKey || sendingHeartbeat || (!ended && video.paused && playedDelta < 0.05 && !retryHeartbeat && !pendingEnded)) return;
    sendingHeartbeat = true;
    var successful = false;
    var payload = retryHeartbeat || heartbeatPayload(ended || pendingEnded);
    retryHeartbeat = payload;
    try {
      var response = await window.MK.fetch("/api/player/" + encodeURIComponent(lectureId) + "/heartbeat", {
        method: "POST", keepalive: true, headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload)
      });
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) { video.pause(); retryHeartbeat = null; showError("수강 권한을 다시 확인해 주세요.", "로그인 상태 또는 수강 기간이 변경되었습니다."); }
        else throw new Error("heartbeat " + response.status);
        return;
      }
      retryHeartbeat = null;
      successful = true;
      if (payload.ended) { pendingEnded = false; clearHeartbeatTimer(); }
      var data = await response.json();
      progressStatus.textContent = data.completed ? "시청완료 · 100%" : "학습 진행률 " + Number(data.progressPercent || 0) + "%";
      if (video.paused || ended) loadLearningSummary();
    } catch (error) {
      progressStatus.textContent = "연결 후 학습 기록을 다시 저장합니다.";
      ensureHeartbeatTimer();
    } finally {
      sendingHeartbeat = false;
      if (successful && pendingEnded) ensureHeartbeatTimer();
    }
  }

  function sendFinalHeartbeat() {
    if (!sessionKey || sendingHeartbeat) return;
    var csrf = window.MK.csrf();
    var headers = { "Content-Type": "application/json" };
    if (csrf) headers[csrf.header] = csrf.token;
    var payload = retryHeartbeat || heartbeatPayload(Boolean(video.ended));
    fetch("/api/player/" + encodeURIComponent(lectureId) + "/heartbeat", {
      method: "POST", keepalive: true, headers: headers, body: JSON.stringify(payload)
    }).catch(function () {});
  }

  function showSecurityToast(message) {
    var toast = shell.querySelector(".mk-player-security-toast");
    if (!toast) {
      toast = document.createElement("div");
      toast.className = "mk-player-security-toast";
      shell.appendChild(toast);
    }

    toast.textContent = message;
    toast.classList.remove("is-visible");
    void toast.offsetWidth;
    toast.classList.add("is-visible");

    clearTimeout(toast._hideTimer);
    toast._hideTimer = setTimeout(function () {
      toast.classList.remove("is-visible");
    }, 1800);
  }

  function formatTime(value) {
    var seconds = Number(value);
    if (!Number.isFinite(seconds) || seconds < 0) {
      seconds = 0;
    }
    seconds = Math.floor(seconds);
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    var remain = seconds % 60;

    if (hours > 0) {
      return hours + ":" + pad2(minutes) + ":" + pad2(remain);
    }
    return minutes + ":" + pad2(remain);
  }

  function pad2(value) {
    return String(value).padStart(2, "0");
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, Number(value) || 0));
  }
})();
