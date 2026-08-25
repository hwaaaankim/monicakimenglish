(function () {
  'use strict';

  const overlay = document.getElementById('mkIndexPopupOverlay');
  if (!overlay) return;

  const dialog = overlay.querySelector('.mk-index-popup-dialog');
  const track = overlay.querySelector('.mk-index-popup-track');
  const slides = Array.from(overlay.querySelectorAll('.mk-index-popup-slide'));
  const dots = Array.from(overlay.querySelectorAll('.mk-index-popup-dot'));
  const closeButtons = Array.from(overlay.querySelectorAll('[data-popup-close]'));
  const viewport = overlay.querySelector('.mk-index-popup-viewport');
  const hideWeekButton = overlay.querySelector('[data-popup-hide-week]');
  const prevButton = overlay.querySelector('[data-popup-prev]');
  const nextButton = overlay.querySelector('[data-popup-next]');

  const STORAGE_KEY = 'monicakim:index-popup:hidden-until';
  const HIDE_DAYS_MS = 7 * 24 * 60 * 60 * 1000;
  const AUTO_DELAY = 5000;
  let index = 0;
  let timer = null;
  let lastFocused = null;
  let touchStartX = null;
  let didSwipe = false;

  function getHiddenUntil() {
    try {
      const value = Number(window.localStorage.getItem(STORAGE_KEY));
      return Number.isFinite(value) ? value : 0;
    } catch (ignored) {
      return 0;
    }
  }

  function setHiddenUntil(value) {
    try {
      window.localStorage.setItem(STORAGE_KEY, String(value));
    } catch (ignored) {
      // localStorage가 차단된 환경에서는 현재 세션에서 닫기만 수행합니다.
    }
  }

  function render() {
    if (!track || slides.length === 0) return;
    track.style.transform = 'translate3d(' + (-index * 100) + '%,0,0)';
    dots.forEach(function (dot, dotIndex) {
      const active = dotIndex === index;
      dot.classList.toggle('is-active', active);
      dot.setAttribute('aria-current', active ? 'true' : 'false');
    });
  }

  function stopAuto() {
    if (timer) {
      window.clearInterval(timer);
      timer = null;
    }
  }

  function startAuto() {
    stopAuto();
    if (slides.length <= 1) return;
    timer = window.setInterval(function () {
      index = (index + 1) % slides.length;
      render();
    }, AUTO_DELAY);
  }

  function move(delta) {
    if (slides.length <= 1) return;
    index = (index + delta + slides.length) % slides.length;
    render();
    startAuto();
  }

  function open() {
    if (getHiddenUntil() > Date.now()) return;
    lastFocused = document.activeElement;
    overlay.hidden = false;
    overlay.setAttribute('aria-hidden', 'false');
    document.body.classList.add('mk-index-popup-open');
    window.requestAnimationFrame(function () {
      overlay.classList.add('is-visible');
      const close = overlay.querySelector('[data-popup-close]');
      if (close) close.focus({ preventScroll: true });
    });
    render();
    startAuto();
  }

  function close() {
    stopAuto();
    overlay.classList.remove('is-visible');
    document.body.classList.remove('mk-index-popup-open');
    window.setTimeout(function () {
      overlay.hidden = true;
      overlay.setAttribute('aria-hidden', 'true');
      if (lastFocused && typeof lastFocused.focus === 'function') {
        try { lastFocused.focus({ preventScroll: true }); } catch (ignored) { }
      }
    }, 220);
  }

  closeButtons.forEach(function (button) {
    button.addEventListener('click', close);
  });

  if (hideWeekButton) {
    hideWeekButton.addEventListener('click', function () {
      setHiddenUntil(Date.now() + HIDE_DAYS_MS);
      close();
    });
  }

  if (prevButton) prevButton.addEventListener('click', function () { move(-1); });
  if (nextButton) nextButton.addEventListener('click', function () { move(1); });

  dots.forEach(function (dot, dotIndex) {
    dot.addEventListener('click', function () {
      index = dotIndex;
      render();
      startAuto();
    });
  });

  overlay.addEventListener('click', function (event) {
    if (event.target === overlay) close();
  });

  if (dialog) {
    dialog.addEventListener('click', function (event) {
      event.stopPropagation();
    });
    dialog.addEventListener('mouseenter', stopAuto);
    dialog.addEventListener('mouseleave', startAuto);
  }

  if (viewport && slides.length > 1) {
    viewport.addEventListener('touchstart', function (event) {
      if (!event.touches || event.touches.length !== 1) return;
      touchStartX = event.touches[0].clientX;
      didSwipe = false;
      stopAuto();
    }, { passive: true });

    viewport.addEventListener('touchend', function (event) {
      if (touchStartX == null || !event.changedTouches || event.changedTouches.length !== 1) {
        touchStartX = null;
        startAuto();
        return;
      }
      const delta = event.changedTouches[0].clientX - touchStartX;
      touchStartX = null;
      if (Math.abs(delta) >= 42) {
        didSwipe = true;
        move(delta > 0 ? -1 : 1);
      } else {
        startAuto();
      }
    }, { passive: true });

    viewport.addEventListener('click', function (event) {
      if (!didSwipe) return;
      event.preventDefault();
      event.stopPropagation();
      didSwipe = false;
    }, true);
  }

  document.addEventListener('keydown', function (event) {
    if (overlay.hidden) return;
    if (event.key === 'Escape') close();
    else if (event.key === 'ArrowLeft') move(-1);
    else if (event.key === 'ArrowRight') move(1);
  });

  // 스크린리더와 키보드 사용자가 팝업 밖으로 빠지지 않도록 최소한의 focus loop를 제공합니다.
  overlay.addEventListener('keydown', function (event) {
    if (event.key !== 'Tab') return;
    const focusable = Array.from(overlay.querySelectorAll('button:not([disabled]),a[href]'))
      .filter(function (node) { return node.offsetParent !== null; });
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', open, { once: true });
  } else {
    open();
  }
})();
