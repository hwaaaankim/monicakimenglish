(function () {
  "use strict";

  function lockBody(lock) {
    document.body.classList.toggle("mk-modal-open", lock);
  }

  document.querySelectorAll("[data-wish-id]").forEach(function (button) {
    button.addEventListener("click", async function (event) {
      event.preventDefault();
      event.stopPropagation();

      if (!window.MK) {
        alert("페이지 공통 스크립트를 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.");
        return;
      }

      button.disabled = true;

      try {
        var response = await window.MK.fetch("/wishlist/" + button.dataset.wishId + "/toggle", {
          method: "POST"
        });

        if (response.status === 401 || response.status === 403) {
          window.location.href = "/auth/login";
          return;
        }

        var data = await response.json();
        if (!response.ok) {
          alert(data.message || "처리할 수 없습니다.");
          return;
        }

        button.classList.toggle("active", data.wished);
        button.setAttribute("aria-pressed", data.wished ? "true" : "false");

        if (button.classList.contains("mk-wish-detail")) {
          button.textContent = data.wished ? "찜 해제" : "찜 목록에 담기";
        }

        if (window.location.pathname === "/my/wishlist" && !data.wished) {
          var column = button.closest(".col-12");
          if (column) {
            column.classList.add("mk-card-removing");
            window.setTimeout(function () {
              column.remove();
            }, 220);
          }
        }
      } catch (error) {
        alert("찜 처리 중 오류가 발생했습니다.");
      } finally {
        button.disabled = false;
      }
    });
  });

  document.querySelectorAll(".js-gallery-thumb").forEach(function (button) {
    button.addEventListener("click", function () {
      var mainImage = document.getElementById("mainIntroImage");
      if (!mainImage || !button.dataset.src) {
        return;
      }

      document.querySelectorAll(".js-gallery-thumb").forEach(function (thumb) {
        thumb.classList.remove("active");
      });
      button.classList.add("active");

      mainImage.classList.add("is-changing");
      window.setTimeout(function () {
        mainImage.src = button.dataset.src;
        mainImage.onload = function () {
          mainImage.classList.remove("is-changing");
        };
      }, 120);
    });
  });

  var descriptionToggle = document.querySelector("[data-toggle-description]");
  if (descriptionToggle) {
    descriptionToggle.addEventListener("click", function (event) {
      var paragraph = document.querySelector(".mk-detail-intro");
      if (!paragraph) {
        return;
      }

      paragraph.classList.toggle("expanded");
      event.currentTarget.textContent = paragraph.classList.contains("expanded")
        ? "설명 접기"
        : "설명 더보기";
    });
  }

  document.querySelectorAll("[data-preview-id]").forEach(function (button) {
    button.addEventListener("click", async function () {
      button.disabled = true;

      try {
        var response = await fetch("/lectures/" + encodeURIComponent(button.dataset.previewId) + "/preview");
        if (!response.ok) {
          throw new Error("preview request failed");
        }

        var data = await response.json();
        showPreview(data);
      } catch (error) {
        alert("강의 미리보기를 불러오지 못했습니다.");
      } finally {
        button.disabled = false;
      }
    });
  });

  function showPreview(data) {
    var previous = document.querySelector(".mk-preview-modal");
    if (previous) {
      previous.remove();
    }

    var modal = document.createElement("div");
    modal.className = "mk-preview-modal";
    modal.setAttribute("role", "dialog");
    modal.setAttribute("aria-modal", "true");
    modal.setAttribute("aria-label", "강의 빠른보기");

    var imageIds = Array.isArray(data.imageIds) ? data.imageIds : [];
    var lectureId = encodeURIComponent(data.id);
    var imageHtml;

    if (imageIds.length > 0) {
      var firstImage = encodeURIComponent(imageIds[0]);
      imageHtml =
        '<div class="mk-preview-media">' +
          '<img class="mk-preview-main" src="/media/lectures/' + lectureId + '/images/' + firstImage + '" alt="강의 소개">' +
        '</div>' +
        '<div class="mk-preview-thumbs">' +
          imageIds.map(function (imageId) {
            var safeImageId = encodeURIComponent(imageId);
            var source = "/media/lectures/" + lectureId + "/images/" + safeImageId;
            return '<button type="button" data-img="' + source + '"><img src="' + source + '" alt="소개 이미지"></button>';
          }).join("") +
        '</div>';
    } else {
      imageHtml = '<div class="mk-preview-media mk-thumb-placeholder"><span>MONICA KIM ONLINE CLASS</span></div>';
    }

    var price = data.pricing === "FREE"
      ? "무료"
      : Number(data.price || 0).toLocaleString("ko-KR") + "원";

    var seconds = Number(data.durationSeconds || 0);
    var duration = Math.floor(seconds / 60) + "분 " + String(seconds % 60).padStart(2, "0") + "초";

    modal.innerHTML =
      '<div class="mk-preview-backdrop"></div>' +
      '<div class="mk-preview-dialog">' +
        '<button class="mk-preview-close" type="button" aria-label="닫기">×</button>' +
        imageHtml +
        '<div class="mk-preview-content">' +
          '<div class="mk-preview-meta"><strong>' + price + '</strong><span>' + duration + '</span></div>' +
          '<h2></h2>' +
          '<div class="mk-preview-tags"></div>' +
          '<p></p>' +
          '<a class="mk-primary-btn" href="/lectures/' + lectureId + '">강의 상세보기</a>' +
        '</div>' +
      '</div>';

    modal.querySelector("h2").textContent = data.title || "강의";
    modal.querySelector("p").textContent = data.description || "";

    var tags = modal.querySelector(".mk-preview-tags");
    (data.tags || []).forEach(function (tag) {
      var element = document.createElement("span");
      element.textContent = "#" + tag;
      tags.appendChild(element);
    });

    modal.querySelectorAll("[data-img]").forEach(function (thumbnailButton) {
      thumbnailButton.addEventListener("click", function () {
        var main = modal.querySelector(".mk-preview-main");
        if (!main) return;

        modal.querySelectorAll("[data-img]").forEach(function (item) {
          item.classList.remove("active");
        });
        thumbnailButton.classList.add("active");

        main.classList.add("is-changing");
        window.setTimeout(function () {
          main.src = thumbnailButton.dataset.img;
          main.onload = function () {
            main.classList.remove("is-changing");
          };
        }, 100);
      });
    });

    var closed = false;
    function closeModal() {
      if (closed) return;
      closed = true;
      modal.classList.remove("open");
      lockBody(false);
      document.removeEventListener("keydown", onKeydown);
      window.setTimeout(function () {
        modal.remove();
      }, 240);
    }

    function onKeydown(event) {
      if (event.key === "Escape") {
        closeModal();
      }
    }

    modal.querySelector(".mk-preview-close").addEventListener("click", closeModal);
    modal.querySelector(".mk-preview-backdrop").addEventListener("click", closeModal);
    document.addEventListener("keydown", onKeydown);

    document.body.appendChild(modal);
    lockBody(true);

    requestAnimationFrame(function () {
      modal.classList.add("open");
      modal.querySelector(".mk-preview-close").focus();
    });
  }
})();
