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
  var viewerScope = String(shell.dataset.watermark || "viewer").trim().toLowerCase();
  var settingsStorageKey = "mk.player.settings.v2";
  var bookmarkStorageKey = "mk.player.bookmarks.v2." + encodeURIComponent(viewerScope) + "." + encodeURIComponent(lectureId);
  var bookmarks = [];
  var playerSettings = loadPlayerSettings();

  applyPlayerSettings();
  loadBookmarks();
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
      playbackUrl = data.playbackUrl || null;
      resumePosition = Number(data.lastPositionSeconds || 0);

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

        var maxPosition = Math.max(0, (video.duration || 0) - 3);
        var target = Math.min(Math.max(0, restore.time), maxPosition);

        if (target >= 5) {
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
      setLoading(true, "이동한 위치를 불러오고 있습니다.");
    });

    video.addEventListener("seeked", function () {
      setLoading(false);
      lastTick = performance.now();
    });

    video.addEventListener("timeupdate", function () {
      if (!video.paused && !video.seeking) {
        var now = performance.now();
        var delta = (now - lastTick) / 1000;
        if (delta > 0 && delta < 2.5) {
          playedDelta += delta;
        }
        lastTick = now;
      }

      if (!isScrubbing) {
        updateSeekUI();
      }
      currentTimeEl.textContent = formatTime(video.currentTime);
    });

    video.addEventListener("progress", updateBufferedUI);
    video.addEventListener("durationchange", function () {
      durationEl.textContent = formatTime(video.duration);
      renderBookmarkMarkers();
    });

    video.addEventListener("ratechange", function () {
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
    moveWatermark();

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

    var positions = [
      { left: "4%", top: "7%" },
      { left: "62%", top: "10%" },
      { left: "7%", top: "66%" },
      { left: "58%", top: "68%" },
      { left: "34%", top: "38%" }
    ];

    watermarkPositionIndex = (watermarkPositionIndex + 1) % positions.length;
    watermark.classList.add("is-moving");

    window.setTimeout(function () {
      var position = positions[watermarkPositionIndex];
      watermark.style.left = position.left;
      watermark.style.top = position.top;
      watermark.classList.remove("is-moving");
    }, 220);
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
    return Number(rate).toFixed(Number(rate) % 1 === 0 ? 0 : 2).replace(/0$/, "") + "×";
  }

  function loadBookmarks() {
    bookmarks = [];
    var raw = safeLocalStorageGet(bookmarkStorageKey);
    if (!raw) {
      return;
    }
    try {
      var parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        return;
      }
      bookmarks = parsed
        .filter(function (item) {
          return item && typeof item.id === "string" && Number.isFinite(Number(item.time)) && Number(item.time) >= 0;
        })
        .map(function (item) {
          return {
            id: item.id,
            time: Number(item.time),
            label: typeof item.label === "string" && item.label.trim() ? item.label.trim().slice(0, 60) : "책갈피",
            createdAt: Number(item.createdAt || Date.now())
          };
        })
        .sort(function (a, b) { return a.time - b.time; })
        .slice(0, 50);
    } catch (error) {
      bookmarks = [];
    }
  }

  function saveBookmarks() {
    safeLocalStorageSet(bookmarkStorageKey, JSON.stringify(bookmarks.slice(0, 50)));
  }

  function addBookmark() {
    var time = Number(video.currentTime || 0);
    var duration = Number(video.duration || 0);
    if (!Number.isFinite(time) || duration <= 0) {
      showSecurityToast("영상이 준비된 뒤 책갈피를 추가할 수 있습니다.");
      return;
    }

    var duplicate = bookmarks.some(function (item) {
      return Math.abs(item.time - time) < 2;
    });
    if (duplicate) {
      showSecurityToast("현재 위치 근처에 이미 책갈피가 있습니다.");
      return;
    }

    if (bookmarks.length >= 50) {
      showSecurityToast("책갈피는 강의당 최대 50개까지 저장할 수 있습니다.");
      return;
    }

    var rounded = Math.max(0, Math.round(time));
    bookmarks.push({
      id: Date.now().toString(36) + Math.random().toString(36).slice(2, 8),
      time: rounded,
      label: "책갈피 " + (bookmarks.length + 1),
      createdAt: Date.now()
    });
    bookmarks.sort(function (a, b) { return a.time - b.time; });
    saveBookmarks();
    renderBookmarks();
    showSecurityToast(formatTime(rounded) + " 위치를 책갈피에 저장했습니다.");
  }

  function removeBookmark(id) {
    var before = bookmarks.length;
    bookmarks = bookmarks.filter(function (item) { return item.id !== id; });
    if (bookmarks.length !== before) {
      saveBookmarks();
      renderBookmarks();
      showSecurityToast("책갈피를 삭제했습니다.");
    }
  }

  function renderBookmarks() {
    if (bookmarkCount) {
      bookmarkCount.textContent = String(bookmarks.length);
    }
    if (bookmarkEmpty) {
      bookmarkEmpty.hidden = bookmarks.length > 0;
    }
    if (bookmarkList) {
      bookmarkList.innerHTML = bookmarks.map(function (item) {
        return '<div class="mk-player-bookmark-row">'
          + '<button type="button" class="mk-player-bookmark-seek" data-bookmark-seek="' + item.time + '">'
          + '<span class="mk-player-bookmark-time">' + escapeHtml(formatTime(item.time)) + '</span>'
          + '<span class="mk-player-bookmark-label">' + escapeHtml(item.label) + '</span>'
          + '</button>'
          + '<button type="button" class="mk-player-bookmark-delete" data-bookmark-delete="' + escapeHtml(item.id) + '" aria-label="책갈피 삭제">×</button>'
          + '</div>';
      }).join("");
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

  async function sendHeartbeat(ended) {
    if (!sessionKey) {
      return;
    }
    if (!ended && video.paused && playedDelta < 0.5) {
      return;
    }

    var delta = Math.min(playedDelta, 20);
    playedDelta = 0;

    try {
      var response = await window.MK.fetch("/api/player/" + encodeURIComponent(lectureId) + "/heartbeat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sessionKey: sessionKey,
          currentTime: Number(video.currentTime || 0),
          duration: Number(video.duration || 0),
          playedDelta: delta,
          ended: Boolean(ended)
        })
      });

      if (response.ok) {
        var data = await response.json();
        progressStatus.textContent = data.completed
          ? "시청완료 · 100%"
          : "학습 진행률 " + data.progressPercent + "%";
      } else {
        playedDelta += delta;
      }
    } catch (error) {
      playedDelta += delta;
      progressStatus.textContent = "네트워크 연결 후 학습 기록을 다시 전송합니다.";
    }
  }

  function sendFinalHeartbeat() {
    if (!sessionKey) {
      return;
    }

    var csrf = window.MK.csrf();
    var headers = { "Content-Type": "application/json" };
    if (csrf) {
      headers[csrf.header] = csrf.token;
    }

    fetch("/api/player/" + encodeURIComponent(lectureId) + "/heartbeat", {
      method: "POST",
      keepalive: true,
      headers: headers,
      body: JSON.stringify({
        sessionKey: sessionKey,
        currentTime: Number(video.currentTime || 0),
        duration: Number(video.duration || 0),
        playedDelta: Math.min(playedDelta, 20),
        ended: Boolean(video.ended)
      })
    }).catch(function () {
    });
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
