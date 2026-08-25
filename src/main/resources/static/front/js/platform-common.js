(function () {
  "use strict";

  function csrf() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    return token && header ? { token: token.content, header: header.content } : null;
  }

  window.MK = {
    csrf: csrf,
    fetch: function (url, options) {
      var opts = options || {};
      opts.headers = opts.headers || {};
      var csrfValue = csrf();

      if (csrfValue) {
        opts.headers[csrfValue.header] = csrfValue.token;
      }

      return fetch(url, opts);
    },
    formatPhone: function (value) {
      var digits = (value || "").replace(/\D/g, "").slice(0, 11);

      if (digits.length < 4) return digits;
      if (digits.length < 8) return digits.replace(/(\d{3})(\d+)/, "$1-$2");
      if (digits.length === 10) return digits.replace(/(\d{3})(\d{3})(\d{4})/, "$1-$2-$3");
      return digits.replace(/(\d{3})(\d{4})(\d{4})/, "$1-$2-$3");
    }
  };

  document.addEventListener("input", function (event) {
    if (event.target.matches(".js-phone")) {
      event.target.value = window.MK.formatPhone(event.target.value);
    }
  });

  document.querySelectorAll(".js-phone").forEach(function (element) {
    element.value = window.MK.formatPhone(element.value);
  });

  function initDesktopUserMenu() {
    var userMenu = document.querySelector("[data-user-menu]");
    var userToggle = userMenu ? userMenu.querySelector(".mk-user-toggle") : null;
    var dropdown = userMenu ? userMenu.querySelector(".mk-user-dropdown") : null;

    if (!userMenu || !userToggle || !dropdown) {
      return;
    }

    var hoverQuery = window.matchMedia
      ? window.matchMedia("(hover: hover) and (pointer: fine)")
      : null;
    var closeTimer = null;

    function clearCloseTimer() {
      if (closeTimer !== null) {
        window.clearTimeout(closeTimer);
        closeTimer = null;
      }
    }

    function isMenuOpen() {
      return userMenu.classList.contains("open");
    }

    function setMenuOpen(open) {
      clearCloseTimer();
      userMenu.classList.toggle("open", open);
      userToggle.setAttribute("aria-expanded", open ? "true" : "false");
      dropdown.setAttribute("aria-hidden", open ? "false" : "true");
    }

    function scheduleMenuClose(delay) {
      clearCloseTimer();
      closeTimer = window.setTimeout(function () {
        setMenuOpen(false);
      }, typeof delay === "number" ? delay : 140);
    }

    function menuItems() {
      return Array.prototype.slice.call(dropdown.querySelectorAll('[role="menuitem"]'));
    }

    userToggle.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      setMenuOpen(!isMenuOpen());
    });

    userToggle.addEventListener("keydown", function (event) {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setMenuOpen(true);
        var items = menuItems();
        if (items.length) {
          items[0].focus();
        }
      }
    });

    userMenu.addEventListener("mouseenter", function () {
      if (hoverQuery && hoverQuery.matches) {
        setMenuOpen(true);
      }
    });

    userMenu.addEventListener("mouseleave", function () {
      if (hoverQuery && hoverQuery.matches) {
        scheduleMenuClose(160);
      }
    });

    userMenu.addEventListener("focusin", function () {
      clearCloseTimer();
    });

    userMenu.addEventListener("focusout", function () {
      window.setTimeout(function () {
        if (!userMenu.contains(document.activeElement)) {
          setMenuOpen(false);
        }
      }, 0);
    });

    dropdown.addEventListener("keydown", function (event) {
      var items = menuItems();
      var currentIndex = items.indexOf(document.activeElement);

      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        if (!items.length) {
          return;
        }

        var delta = event.key === "ArrowDown" ? 1 : -1;
        var nextIndex = currentIndex < 0 ? 0 : (currentIndex + delta + items.length) % items.length;
        items[nextIndex].focus();
        return;
      }

      if (event.key === "Escape") {
        event.preventDefault();
        setMenuOpen(false);
        userToggle.focus();
      }
    });

    document.addEventListener("click", function (event) {
      if (!userMenu.contains(event.target)) {
        setMenuOpen(false);
      }
    });

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && isMenuOpen()) {
        setMenuOpen(false);
        userToggle.focus();
      }
    });
  }

  function initMobileUserMenu() {
    var mobileMenu = document.querySelector("[data-mobile-user-menu]");
    var mobileToggle = mobileMenu ? mobileMenu.querySelector(".mk-mobile-user-toggle") : null;
    var mobileSubmenu = mobileMenu ? mobileMenu.querySelector(".mk-mobile-user-submenu") : null;

    if (!mobileMenu || !mobileToggle || !mobileSubmenu) {
      return;
    }

    function isOpen() {
      return mobileMenu.classList.contains("open");
    }

    function setOpen(open) {
      mobileMenu.classList.toggle("open", open);
      mobileToggle.setAttribute("aria-expanded", open ? "true" : "false");
      mobileSubmenu.setAttribute("aria-hidden", open ? "false" : "true");
    }

    mobileToggle.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      setOpen(!isOpen());
    });

    mobileToggle.addEventListener("keydown", function (event) {
      if (event.key === "ArrowDown" || event.key === "Enter" || event.key === " ") {
        if (event.key !== "Enter" && event.key !== " ") {
          event.preventDefault();
          setOpen(true);
        }
      }
    });

    var primaryTrigger = document.getElementById("primary-menu-trigger");
    if (primaryTrigger) {
      primaryTrigger.addEventListener("click", function () {
        // functions.js toggles body.primary-menu-open first. When the hamburger
        // closes, collapse the personal submenu as well so the next open starts cleanly.
        window.setTimeout(function () {
          if (!document.body.classList.contains("primary-menu-open")) {
            setOpen(false);
          }
        }, 0);
      });
    }

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && isOpen()) {
        setOpen(false);
        mobileToggle.focus();
      }
    });
  }

  initDesktopUserMenu();
  initMobileUserMenu();
})();
