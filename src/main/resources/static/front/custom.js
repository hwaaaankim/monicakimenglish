(function () {
  "use strict";

  /**
   * 기존 스크립트는 .menu-link와 .sub-menu-container의 개수가 항상 같다고 가정하여
   * menuBottom[i].style에 바로 접근했습니다.
   * 하위 메뉴가 없는 신규 메뉴가 추가되면 undefined.style 오류가 발생하므로
   * 각 menu-item 내부에서 실제 하위 메뉴가 존재할 때만 폭을 계산합니다.
   */
  function syncSubMenuWidths() {
    document.querySelectorAll(".menu-item").forEach(function (menuItem) {
      var menuLink = menuItem.querySelector(".menu-link");
      var subMenu = menuItem.querySelector(".sub-menu-container");

      if (!menuLink || !subMenu) {
        return;
      }

      subMenu.style.width = Math.max(0, menuLink.clientWidth - 20) + "px";
    });
  }

  var resizeRaf = null;
  function scheduleSubMenuSync() {
    if (resizeRaf !== null) {
      cancelAnimationFrame(resizeRaf);
    }
    resizeRaf = requestAnimationFrame(function () {
      syncSubMenuWidths();
      resizeRaf = null;
    });
  }

  function initIndexScrollAnimation() {
    var targetContainer = document.getElementById("target-container");

    // target-container는 index 일부 화면에만 존재합니다.
    // 없는 페이지에서는 스크롤 애니메이션을 아예 등록하지 않습니다.
    if (!targetContainer || typeof window.gsap === "undefined") {
      return;
    }

    var currentPhase = null;

    function animateWrap(selector, visible) {
      var element = document.querySelector(selector);
      if (!element) {
        return;
      }

      window.gsap.to(element, {
        opacity: visible ? 1 : 0,
        zIndex: visible ? 2 : 0,
        duration: 0.75,
        overwrite: "auto"
      });
    }

    function resolvePhase(y) {
      if (y > 420) return "before";
      if (y > -343) return "first";
      if (y > -1343) return "second";
      if (y > -2143) return "third";
      return "after";
    }

    function updateIndexAnimation() {
      var y = targetContainer.getBoundingClientRect().y;
      var phase = resolvePhase(y);

      if (phase === currentPhase) {
        return;
      }
      currentPhase = phase;

      animateWrap(".wrap01", phase === "first");
      animateWrap(".wrap02", phase === "second");
      animateWrap(".wrap03", phase === "third");
    }

    window.addEventListener("scroll", updateIndexAnimation, { passive: true });
    updateIndexAnimation();
  }

  function init() {
    syncSubMenuWidths();
    initIndexScrollAnimation();

    window.addEventListener("resize", scheduleSubMenuSync, { passive: true });
    window.addEventListener("load", scheduleSubMenuSync, { once: true });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  } else {
    init();
  }
})();
