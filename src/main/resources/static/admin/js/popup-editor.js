(function () {
  'use strict';

  const form = document.getElementById('popupForm');
  if (!form) return;

  const picker = document.getElementById('popupImagePicker');
  const processedInput = document.getElementById('popupProcessedImage');
  const fileInfo = document.getElementById('popupFileInfo');
  const previewBox = document.getElementById('popupCurrentPreview');
  let previewImage = document.getElementById('popupPreviewImage');
  let previewEmpty = document.getElementById('popupPreviewEmpty');
  const editExistingButton = document.getElementById('editExistingPopupImage');
  const unlimited = document.getElementById('popupUnlimited');
  const startDate = document.getElementById('popupStartDate');
  const endDate = document.getElementById('popupEndDate');

  const editor = document.getElementById('popupImageEditor');
  const cropPanel = document.getElementById('popupCropPanel');
  const placementPanel = document.getElementById('popupPlacementPanel');
  const stepButtons = Array.from(editor.querySelectorAll('[data-editor-step]'));
  const cropCanvas = document.getElementById('popupCropCanvas');
  const cropCtx = cropCanvas.getContext('2d');
  const cropNewButton = document.getElementById('popupCropNew');
  const cropFullButton = document.getElementById('popupCropFull');
  const cropCenterButton = document.getElementById('popupCropCenter');
  const cropInfo = document.getElementById('popupCropInfo');
  const goPlacement = document.getElementById('popupGoPlacement');
  const backCrop = document.getElementById('popupBackCrop');
  const placementCanvas = document.getElementById('popupPlacementCanvas');
  const placementCtx = placementCanvas.getContext('2d');
  const zoomInput = document.getElementById('popupZoom');
  const zoomValue = document.getElementById('popupZoomValue');
  const placementReset = document.getElementById('popupPlacementReset');
  const applyImage = document.getElementById('popupApplyImage');

  const OUTPUT_WIDTH = 800;
  const OUTPUT_HEIGHT = 1200;
  const HANDLE_RADIUS = 9;
  const MIN_CROP_SCREEN = 32;

  let sourceImage = null;
  let sourceFile = null;
  let crop = null;
  let cropScale = 1;
  let cropPointer = null;
  let forceNewCrop = false;
  let placement = { zoom: 1, x: 0, y: 0, baseScale: 1, pointer: null };
  let previewObjectUrl = null;

  function setUnlimitedState() {
    const disabled = unlimited.checked;
    startDate.disabled = disabled;
    endDate.disabled = disabled;
    if (disabled) {
      startDate.value = '';
      endDate.value = '';
    }
  }

  unlimited.addEventListener('change', setUnlimitedState);
  [startDate, endDate].forEach(function (input) {
    input.addEventListener('input', function () {
      if (input.value) {
        unlimited.checked = false;
        setUnlimitedState();
      }
    });
  });
  setUnlimitedState();

  function openEditor() {
    editor.classList.add('is-open');
    editor.setAttribute('aria-hidden', 'false');
    document.body.classList.add('mk-popup-editor-open');
    showStep(1);
    requestAnimationFrame(resizeCropCanvas);
  }

  function closeEditor() {
    editor.classList.remove('is-open');
    editor.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('mk-popup-editor-open');
    cropPointer = null;
    placement.pointer = null;
  }

  editor.querySelectorAll('[data-popup-editor-close]').forEach(function (button) {
    button.addEventListener('click', closeEditor);
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && editor.classList.contains('is-open')) closeEditor();
  });

  function showStep(step) {
    const first = step === 1;
    cropPanel.hidden = !first;
    placementPanel.hidden = first;
    stepButtons.forEach(function (button) {
      button.classList.toggle('is-active', Number(button.getAttribute('data-editor-step')) === step);
    });
    if (first) requestAnimationFrame(resizeCropCanvas);
    else requestAnimationFrame(renderPlacement);
  }

  function safeFileName(name) {
    return String(name || 'popup').replace(/\.[^.]+$/, '').replace(/[^0-9a-zA-Z가-힣._-]+/g, '_').slice(0, 80) || 'popup';
  }

  function loadFile(file) {
    if (!file || !file.type || file.type.indexOf('image/') !== 0) {
      alert('이미지 파일만 선택할 수 있습니다.');
      return;
    }
    if (file.size > 20 * 1024 * 1024) {
      alert('원본 이미지는 20MB 이하로 선택해주세요. 편집 결과는 자동으로 압축됩니다.');
      return;
    }

    const objectUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = function () {
      URL.revokeObjectURL(objectUrl);
      if (image.naturalWidth < 400 || image.naturalHeight < 600) {
        alert('팝업 원본 이미지는 최소 400×600px 이상을 권장합니다. 더 큰 이미지를 선택해주세요.');
      }
      sourceImage = image;
      sourceFile = file;
      crop = { x: 0, y: 0, width: image.naturalWidth, height: image.naturalHeight };
      openEditor();
    };
    image.onerror = function () {
      URL.revokeObjectURL(objectUrl);
      alert('이미지 파일을 읽지 못했습니다. 다른 파일을 선택해주세요.');
    };
    image.src = objectUrl;
  }

  picker.addEventListener('change', function () {
    const file = picker.files && picker.files[0];
    if (file) loadFile(file);
    picker.value = '';
  });

  if (editExistingButton) {
    editExistingButton.addEventListener('click', async function () {
      const src = editExistingButton.getAttribute('data-existing-src');
      if (!src) return;
      editExistingButton.disabled = true;
      try {
        const response = await fetch(src, { credentials: 'same-origin', cache: 'no-store' });
        if (!response.ok) throw new Error('image fetch failed');
        const blob = await response.blob();
        loadFile(new File([blob], 'popup-current.jpg', { type: blob.type || 'image/jpeg' }));
      } catch (error) {
        alert('현재 팝업 이미지를 불러오지 못했습니다. 새 이미지를 선택해주세요.');
      } finally {
        editExistingButton.disabled = false;
      }
    });
  }

  function resizeCropCanvas() {
    if (!sourceImage || cropPanel.hidden) return;
    const wrap = cropCanvas.parentElement;
    const maxWidth = Math.max(260, Math.min(760, wrap.clientWidth - 20));
    const maxHeight = Math.max(260, Math.min(500, window.innerHeight * 0.52));
    cropScale = Math.min(maxWidth / sourceImage.naturalWidth, maxHeight / sourceImage.naturalHeight);
    cropScale = Math.max(cropScale, 0.0001);
    cropCanvas.width = Math.max(1, Math.round(sourceImage.naturalWidth * cropScale));
    cropCanvas.height = Math.max(1, Math.round(sourceImage.naturalHeight * cropScale));
    renderCrop();
  }

  window.addEventListener('resize', function () {
    if (editor.classList.contains('is-open') && !cropPanel.hidden) resizeCropCanvas();
  });

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function normalizeCrop() {
    if (!crop || !sourceImage) return;
    const minNatural = Math.max(10, MIN_CROP_SCREEN / cropScale);
    crop.width = clamp(crop.width, minNatural, sourceImage.naturalWidth);
    crop.height = clamp(crop.height, minNatural, sourceImage.naturalHeight);
    crop.x = clamp(crop.x, 0, sourceImage.naturalWidth - crop.width);
    crop.y = clamp(crop.y, 0, sourceImage.naturalHeight - crop.height);
  }

  function cropScreenRect() {
    normalizeCrop();
    return {
      x: crop.x * cropScale,
      y: crop.y * cropScale,
      width: crop.width * cropScale,
      height: crop.height * cropScale
    };
  }

  function renderCrop() {
    if (!sourceImage || !crop) return;
    normalizeCrop();
    const r = cropScreenRect();

    cropCtx.clearRect(0, 0, cropCanvas.width, cropCanvas.height);
    cropCtx.drawImage(sourceImage, 0, 0, cropCanvas.width, cropCanvas.height);
    cropCtx.fillStyle = 'rgba(0,0,0,.58)';
    cropCtx.fillRect(0, 0, cropCanvas.width, cropCanvas.height);
    cropCtx.drawImage(sourceImage, crop.x, crop.y, crop.width, crop.height, r.x, r.y, r.width, r.height);
    cropCtx.strokeStyle = '#d4aa63';
    cropCtx.lineWidth = 2;
    cropCtx.strokeRect(r.x + 1, r.y + 1, Math.max(0, r.width - 2), Math.max(0, r.height - 2));

    const handles = handlePoints(r);
    cropCtx.fillStyle = '#fff';
    cropCtx.strokeStyle = '#a47a3e';
    Object.keys(handles).forEach(function (key) {
      const p = handles[key];
      cropCtx.beginPath();
      cropCtx.arc(p.x, p.y, 5, 0, Math.PI * 2);
      cropCtx.fill();
      cropCtx.stroke();
    });

    cropInfo.textContent = Math.round(crop.width) + '×' + Math.round(crop.height) + 'px 선택';
  }

  function handlePoints(r) {
    return {
      nw: { x: r.x, y: r.y },
      ne: { x: r.x + r.width, y: r.y },
      se: { x: r.x + r.width, y: r.y + r.height },
      sw: { x: r.x, y: r.y + r.height }
    };
  }

  function canvasPoint(event, canvas) {
    const rect = canvas.getBoundingClientRect();
    return {
      x: (event.clientX - rect.left) * canvas.width / rect.width,
      y: (event.clientY - rect.top) * canvas.height / rect.height
    };
  }

  function naturalPoint(event) {
    const p = canvasPoint(event, cropCanvas);
    return { x: p.x / cropScale, y: p.y / cropScale };
  }

  function detectCropMode(event) {
    const p = canvasPoint(event, cropCanvas);
    const r = cropScreenRect();
    const handles = handlePoints(r);
    for (const key of Object.keys(handles)) {
      const h = handles[key];
      if (Math.hypot(p.x - h.x, p.y - h.y) <= HANDLE_RADIUS + 4) return 'resize-' + key;
    }
    if (p.x >= r.x && p.x <= r.x + r.width && p.y >= r.y && p.y <= r.y + r.height) return 'move';
    return 'create';
  }

  cropCanvas.addEventListener('pointerdown', function (event) {
    if (!sourceImage) return;
    const point = naturalPoint(event);
    const mode = forceNewCrop ? 'create' : detectCropMode(event);
    forceNewCrop = false;
    cropNewButton.classList.remove('is-active');
    cropPointer = {
      id: event.pointerId,
      mode: mode,
      startPoint: point,
      startCrop: Object.assign({}, crop)
    };
    if (mode === 'create') crop = { x: point.x, y: point.y, width: 1, height: 1 };
    cropCanvas.setPointerCapture(event.pointerId);
    event.preventDefault();
  });

  cropCanvas.addEventListener('pointermove', function (event) {
    if (!cropPointer || cropPointer.id !== event.pointerId) return;
    const point = naturalPoint(event);
    const start = cropPointer.startCrop;
    const dx = point.x - cropPointer.startPoint.x;
    const dy = point.y - cropPointer.startPoint.y;
    const minSize = Math.max(10, MIN_CROP_SCREEN / cropScale);

    if (cropPointer.mode === 'move') {
      crop.x = clamp(start.x + dx, 0, sourceImage.naturalWidth - start.width);
      crop.y = clamp(start.y + dy, 0, sourceImage.naturalHeight - start.height);
    } else if (cropPointer.mode === 'create') {
      crop.x = Math.min(cropPointer.startPoint.x, point.x);
      crop.y = Math.min(cropPointer.startPoint.y, point.y);
      crop.width = Math.max(1, Math.abs(point.x - cropPointer.startPoint.x));
      crop.height = Math.max(1, Math.abs(point.y - cropPointer.startPoint.y));
    } else if (cropPointer.mode.indexOf('resize-') === 0) {
      const handle = cropPointer.mode.substring('resize-'.length);
      let left = start.x;
      let top = start.y;
      let right = start.x + start.width;
      let bottom = start.y + start.height;
      if (handle.indexOf('w') >= 0) left = clamp(start.x + dx, 0, right - minSize);
      if (handle.indexOf('e') >= 0) right = clamp(start.x + start.width + dx, left + minSize, sourceImage.naturalWidth);
      if (handle.indexOf('n') >= 0) top = clamp(start.y + dy, 0, bottom - minSize);
      if (handle.indexOf('s') >= 0) bottom = clamp(start.y + start.height + dy, top + minSize, sourceImage.naturalHeight);
      crop = { x: left, y: top, width: right - left, height: bottom - top };
    }
    renderCrop();
    event.preventDefault();
  });

  function endCropPointer(event) {
    if (!cropPointer || cropPointer.id !== event.pointerId) return;
    cropPointer = null;
    normalizeCrop();
    renderCrop();
    try { cropCanvas.releasePointerCapture(event.pointerId); } catch (ignored) { }
  }
  cropCanvas.addEventListener('pointerup', endCropPointer);
  cropCanvas.addEventListener('pointercancel', endCropPointer);

  cropNewButton.addEventListener('click', function () {
    if (!sourceImage) return;
    forceNewCrop = true;
    cropNewButton.classList.add('is-active');
    cropInfo.textContent = '이미지 위에서 새 자르기 영역을 드래그하세요.';
  });

  cropFullButton.addEventListener('click', function () {
    if (!sourceImage) return;
    forceNewCrop = false;
    cropNewButton.classList.remove('is-active');
    crop = { x: 0, y: 0, width: sourceImage.naturalWidth, height: sourceImage.naturalHeight };
    renderCrop();
  });

  cropCenterButton.addEventListener('click', function () {
    if (!sourceImage) return;
    forceNewCrop = false;
    cropNewButton.classList.remove('is-active');
    const width = sourceImage.naturalWidth * 0.8;
    const height = sourceImage.naturalHeight * 0.8;
    crop = {
      x: (sourceImage.naturalWidth - width) / 2,
      y: (sourceImage.naturalHeight - height) / 2,
      width: width,
      height: height
    };
    renderCrop();
  });

  function resetPlacement() {
    normalizeCrop();
    const baseScale = Math.max(placementCanvas.width / crop.width, placementCanvas.height / crop.height);
    placement.baseScale = baseScale;
    placement.zoom = 1;
    zoomInput.value = '100';
    zoomValue.textContent = '100%';
    const drawWidth = crop.width * baseScale;
    const drawHeight = crop.height * baseScale;
    placement.x = (placementCanvas.width - drawWidth) / 2;
    placement.y = (placementCanvas.height - drawHeight) / 2;
    renderPlacement();
  }

  function clampPlacement() {
    const scale = placement.baseScale * placement.zoom;
    const width = crop.width * scale;
    const height = crop.height * scale;
    placement.x = clamp(placement.x, placementCanvas.width - width, 0);
    placement.y = clamp(placement.y, placementCanvas.height - height, 0);
  }

  function renderPlacement() {
    if (!sourceImage || !crop) return;
    clampPlacement();
    const scale = placement.baseScale * placement.zoom;
    const width = crop.width * scale;
    const height = crop.height * scale;
    placementCtx.clearRect(0, 0, placementCanvas.width, placementCanvas.height);
    placementCtx.fillStyle = '#fff';
    placementCtx.fillRect(0, 0, placementCanvas.width, placementCanvas.height);
    placementCtx.imageSmoothingEnabled = true;
    placementCtx.imageSmoothingQuality = 'high';
    placementCtx.drawImage(sourceImage, crop.x, crop.y, crop.width, crop.height, placement.x, placement.y, width, height);
  }

  goPlacement.addEventListener('click', function () {
    if (!sourceImage || !crop || crop.width < 2 || crop.height < 2) {
      alert('자르기 영역을 먼저 선택해주세요.');
      return;
    }
    showStep(2);
    resetPlacement();
  });

  backCrop.addEventListener('click', function () { showStep(1); });
  stepButtons[0].addEventListener('click', function () { showStep(1); });
  stepButtons[1].addEventListener('click', function () {
    if (sourceImage && crop) {
      showStep(2);
      resetPlacement();
    }
  });

  zoomInput.addEventListener('input', function () {
    if (!sourceImage || !crop) return;
    const oldScale = placement.baseScale * placement.zoom;
    const oldWidth = crop.width * oldScale;
    const oldHeight = crop.height * oldScale;
    const centerSourceX = (placementCanvas.width / 2 - placement.x) / oldWidth;
    const centerSourceY = (placementCanvas.height / 2 - placement.y) / oldHeight;

    placement.zoom = Number(zoomInput.value) / 100;
    const newScale = placement.baseScale * placement.zoom;
    const newWidth = crop.width * newScale;
    const newHeight = crop.height * newScale;
    placement.x = placementCanvas.width / 2 - centerSourceX * newWidth;
    placement.y = placementCanvas.height / 2 - centerSourceY * newHeight;
    zoomValue.textContent = Math.round(placement.zoom * 100) + '%';
    renderPlacement();
  });

  placementReset.addEventListener('click', resetPlacement);

  placementCanvas.addEventListener('pointerdown', function (event) {
    if (!sourceImage) return;
    const p = canvasPoint(event, placementCanvas);
    placement.pointer = { id: event.pointerId, x: p.x, y: p.y, startX: placement.x, startY: placement.y };
    placementCanvas.setPointerCapture(event.pointerId);
    event.preventDefault();
  });

  placementCanvas.addEventListener('pointermove', function (event) {
    if (!placement.pointer || placement.pointer.id !== event.pointerId) return;
    const p = canvasPoint(event, placementCanvas);
    placement.x = placement.pointer.startX + (p.x - placement.pointer.x);
    placement.y = placement.pointer.startY + (p.y - placement.pointer.y);
    renderPlacement();
    event.preventDefault();
  });

  function endPlacementPointer(event) {
    if (!placement.pointer || placement.pointer.id !== event.pointerId) return;
    placement.pointer = null;
    try { placementCanvas.releasePointerCapture(event.pointerId); } catch (ignored) { }
  }
  placementCanvas.addEventListener('pointerup', endPlacementPointer);
  placementCanvas.addEventListener('pointercancel', endPlacementPointer);

  function outputFile() {
    return new Promise(function (resolve, reject) {
      const canvas = document.createElement('canvas');
      canvas.width = OUTPUT_WIDTH;
      canvas.height = OUTPUT_HEIGHT;
      const context = canvas.getContext('2d');
      const factorX = OUTPUT_WIDTH / placementCanvas.width;
      const factorY = OUTPUT_HEIGHT / placementCanvas.height;
      const scale = placement.baseScale * placement.zoom;

      context.fillStyle = '#fff';
      context.fillRect(0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT);
      context.imageSmoothingEnabled = true;
      context.imageSmoothingQuality = 'high';
      context.drawImage(
        sourceImage,
        crop.x, crop.y, crop.width, crop.height,
        placement.x * factorX,
        placement.y * factorY,
        crop.width * scale * factorX,
        crop.height * scale * factorY
      );
      canvas.toBlob(function (blob) {
        if (!blob) {
          reject(new Error('image export failed'));
          return;
        }
        const name = 'popup-' + safeFileName(sourceFile && sourceFile.name) + '-' + Date.now() + '.jpg';
        resolve(new File([blob], name, { type: 'image/jpeg', lastModified: Date.now() }));
      }, 'image/jpeg', 0.93);
    });
  }

  applyImage.addEventListener('click', async function () {
    applyImage.disabled = true;
    try {
      const file = await outputFile();
      const transfer = new DataTransfer();
      transfer.items.add(file);
      processedInput.files = transfer.files;

      if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
      previewObjectUrl = URL.createObjectURL(file);
      if (!previewImage) {
        previewImage = document.createElement('img');
        previewImage.id = 'popupPreviewImage';
        previewImage.alt = '편집된 팝업 이미지';
        previewBox.prepend(previewImage);
      }
      previewImage.src = previewObjectUrl;
      if (previewEmpty) {
        previewEmpty.remove();
        previewEmpty = null;
      }
      previewBox.classList.remove('is-empty');
      fileInfo.textContent = file.name + ' · 800×1200px · ' + (file.size / 1024).toFixed(0) + 'KB · 편집 완료';
      closeEditor();
    } catch (error) {
      alert('편집 이미지를 생성하지 못했습니다. 다시 시도해주세요.');
    } finally {
      applyImage.disabled = false;
    }
  });

  form.addEventListener('submit', function (event) {
    if (startDate.value && endDate.value && startDate.value > endDate.value) {
      event.preventDefault();
      alert('게시 시작일은 종료일보다 늦을 수 없습니다.');
      startDate.focus();
      return;
    }

    const isCreate = !editExistingButton;
    if (isCreate && (!processedInput.files || processedInput.files.length === 0)) {
      event.preventDefault();
      alert('팝업 이미지를 선택하고 편집을 완료해주세요.');
      picker.click();
    }
  });

  document.querySelectorAll('[data-popup-delete-form]').forEach(function (deleteForm) {
    deleteForm.addEventListener('submit', function (event) {
      const id = deleteForm.getAttribute('data-popup-id') || '';
      if (!window.confirm('팝업 #' + id + '을(를) 삭제하시겠습니까?\n삭제한 팝업 이미지는 복구할 수 없습니다.')) {
        event.preventDefault();
      }
    });
  });
})();
