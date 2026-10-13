(function () {
  'use strict';

  const page = document.querySelector('.mk-lecture-editor-page');
  const form = document.getElementById('lectureForm');
  if (!page || !form) {
    return;
  }

  let TARGET_WIDTH = 1280;
  let TARGET_HEIGHT = 720;
  const OUTPUT_TYPE = 'image/jpeg';
  const OUTPUT_QUALITY = 0.94;

  const pricing = document.getElementById('pricingType');
  const price = document.getElementById('priceInput');
  const video = document.getElementById('videoFile');
  const videoMeta = document.getElementById('videoMeta');
  const videoFileName = document.getElementById('videoFileName');
  const attachmentFiles = document.getElementById('attachmentFiles');
  const attachmentFileInfo = document.getElementById('attachmentFileInfo');

  const picker = document.getElementById('introImagePicker');
  const pickerInfo = document.getElementById('introImagePickerInfo');
  const processedInput = document.getElementById('processedIntroImages');
  const editedImageList = document.getElementById('editedImageList');
  const editedImageEmpty = document.getElementById('editedImageEmpty');
  const thumbnailSelectionInput = document.getElementById('thumbnailSelection');
  const introImageCountBadge = document.getElementById('introImageCountBadge');
  const thumbnailStatusBadge = document.getElementById('thumbnailStatusBadge');
  const introImageSection = document.getElementById('introImageSection');
  const instructorPicker = document.getElementById('instructorImagePicker');
  const instructorPickerInfo = document.getElementById('instructorImagePickerInfo');
  const instructorProcessedInput = document.getElementById('processedInstructorImages');
  const instructorImageList = document.getElementById('editedInstructorImageList');
  const instructorImageEmpty = document.getElementById('editedInstructorImageEmpty');
  const instructorImageOrderInput = document.getElementById('instructorImageOrder');
  const introImageOrderInput = document.getElementById('introImageOrder');
  const sourceZoomRange = document.getElementById('sourceZoomRange');
  const sourceZoomValue = document.getElementById('sourceZoomValue');

  const editorModal = document.getElementById('lectureImageEditorModal');
  const editorTitle = document.getElementById('lectureImageEditorTitle');
  const editorClose = document.getElementById('lectureImageEditorClose');
  const cropGuide = document.getElementById('cropGuide');
  const cropStepOneTab = document.getElementById('cropStepOneTab');
  const cropStepTwoTab = document.getElementById('cropStepTwoTab');
  const cropStepOnePanel = document.getElementById('cropStepOnePanel');
  const cropStepTwoPanel = document.getElementById('cropStepTwoPanel');

  const cropSourceStageWrap = document.getElementById('cropSourceStageWrap');
  const cropSourceStage = document.getElementById('cropSourceStage');
  const cropSourceImage = document.getElementById('cropSourceImage');
  const cropSelection = document.getElementById('cropSelection');
  const cropXInput = document.getElementById('cropX');
  const cropYInput = document.getElementById('cropY');
  const cropWidthInput = document.getElementById('cropWidth');
  const cropHeightInput = document.getElementById('cropHeight');
  const cropFullImageBtn = document.getElementById('cropFullImageBtn');
  const cropCenterAreaBtn = document.getElementById('cropCenterAreaBtn');
  const cropDrawAreaBtn = document.getElementById('cropDrawAreaBtn');
  const cropNextBtn = document.getElementById('cropNextBtn');

  const placementStage = document.getElementById('placementStage');
  const placementImage = document.getElementById('placementImage');
  const placementZoomOutBtn = document.getElementById('placementZoomOutBtn');
  const placementZoomInBtn = document.getElementById('placementZoomInBtn');
  const placementZoomRange = document.getElementById('placementZoomRange');
  const placementZoomValue = document.getElementById('placementZoomValue');
  const placementXInput = document.getElementById('placementX');
  const placementYInput = document.getElementById('placementY');
  const placementWidthInput = document.getElementById('placementWidth');
  const placementHeightInput = document.getElementById('placementHeight');
  const placementBackgroundColor = document.getElementById('placementBackgroundColor');
  const placementBackgroundHex = document.getElementById('placementBackgroundHex');
  const placementFitContainBtn = document.getElementById('placementFitContainBtn');
  const placementFitCoverBtn = document.getElementById('placementFitCoverBtn');
  const placementOriginalBtn = document.getElementById('placementOriginalBtn');
  const cropBackBtn = document.getElementById('cropBackBtn');
  const cropSelectBtn = document.getElementById('cropSelectBtn');
  const cropCancelBtn = document.getElementById('cropCancelBtn');

  let processedImages = [];
  let instructorProcessedImages = [];
  let activeImageKind = 'intro';
  let editingSnapshot = null;
  let editorReturnFocus = null;
  let editorReading = false;
  let nextProcessedUid = 1;
  let thumbnailKey = null;
  let thumbnailAutomatic = false;
  let lastActiveImageCount = 0;

  let pendingQueue = [];
  let pendingQueueIndex = 0;
  let editingProcessedUid = null;
  let cropState = null;

  function syncPrice() {
    if (!pricing || !price) {
      return;
    }
    const free = pricing.value === 'FREE';
    price.min = free ? '0' : '100';
    price.disabled = free;
    price.required = !free;
    price.min = free ? '0' : '1';
    if (free) {
      price.value = '0';
    }
  }

  if (pricing) {
    pricing.addEventListener('change', syncPrice);
  }
  syncPrice();

  if (video) {
    video.addEventListener('change', function () {
      const file = video.files && video.files[0];
      if (!file) {
        return;
      }

      if (videoFileName) {
        videoFileName.textContent = file.name;
      }

      const url = URL.createObjectURL(file);
      const previewVideo = document.createElement('video');
      previewVideo.preload = 'metadata';

      previewVideo.onloadedmetadata = function () {
        URL.revokeObjectURL(url);
        const duration = Math.round(previewVideo.duration || 0);
        const resolution = previewVideo.videoWidth > 0 && previewVideo.videoHeight > 0
          ? ' · ' + previewVideo.videoWidth + '×' + previewVideo.videoHeight
          : '';
        const durationText = duration > 0
          ? ' · ' + Math.floor(duration / 60) + '분 ' + String(duration % 60).padStart(2, '0') + '초'
          : '';
        if (videoMeta) {
          videoMeta.textContent = file.name + ' · ' + (file.size / 1048576).toFixed(1) + ' MB' + resolution + durationText
            + ' · 서버에서 호환성 검사 후 자동 최적화';
        }
      };

      previewVideo.onerror = function () {
        URL.revokeObjectURL(url);
        if (videoMeta) {
          videoMeta.textContent = file.name + ' · ' + (file.size / 1048576).toFixed(1)
            + ' MB · 브라우저 미리보기는 지원하지 않지만 서버 FFmpeg에서 실제 영상 여부를 검사합니다.';
        }
      };

      previewVideo.src = url;
    });
  }

  if (attachmentFiles) {
    attachmentFiles.addEventListener('change', function () {
      const files = Array.from(attachmentFiles.files || []);
      if (!attachmentFileInfo) {
        return;
      }
      if (files.length === 0) {
        attachmentFileInfo.textContent = '선택된 새 첨부파일 없음';
        return;
      }
      const names = files.slice(0, 3).map(function (file) { return file.name; }).join(', ');
      const suffix = files.length > 3 ? ' 외 ' + (files.length - 3) + '개' : '';
      attachmentFileInfo.textContent = files.length + '개 선택 · ' + names + suffix;
    });
  }

  function clampNumber(value, min, max) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return min;
    }
    return Math.min(max, Math.max(min, numeric));
  }

  function roundEditorNumber(value) {
    return Math.round(Number(value) || 0);
  }

  function normalizeHexColor(value) {
    const normalized = String(value || '').trim().toLowerCase();
    if (/^#[0-9a-f]{6}$/.test(normalized)) {
      return normalized;
    }
    if (/^[0-9a-f]{6}$/.test(normalized)) {
      return '#' + normalized;
    }
    return null;
  }

  function safeBaseName(name) {
    const source = String(name || 'image');
    const dot = source.lastIndexOf('.');
    const base = dot > 0 ? source.substring(0, dot) : source;
    return base.replace(/[^0-9a-zA-Z가-힣._-]+/g, '_').substring(0, 120) || 'image';
  }

  function buildOutputFileName(originalName) {
    return activeImageKind + '-' + safeBaseName(originalName) + '-' + TARGET_WIDTH + 'x' + TARGET_HEIGHT + '-' + Date.now() + '.jpg';
  }

  function getExistingCards() {
    return Array.from(document.querySelectorAll('[data-existing-image]'));
  }

  function existingKey(card) {
    return 'existing:' + card.getAttribute('data-image-id');
  }

  function getActiveExistingCards() {
    return getExistingCards().filter(function (card) {
      const deleteInput = card.querySelector('[data-delete-existing]');
      return !deleteInput || !deleteInput.checked;
    });
  }

  function getProcessedKey(item) {
    return 'new:' + item.uid;
  }

  function getActiveImageKeys() {
    const keys = getActiveExistingCards().map(existingKey);
    processedImages.forEach(function (item) {
      if (item.upload !== false) keys.push(getProcessedKey(item));
    });
    return keys;
  }

  function toServerThumbnailSelection() {
    if (!thumbnailKey) {
      return '';
    }
    if (thumbnailKey.indexOf('existing:') === 0) {
      return thumbnailKey;
    }
    if (thumbnailKey.indexOf('new:') === 0) {
      const uid = Number(thumbnailKey.substring(4));
      const index = processedImages.filter(function (item) { return item.upload !== false; }).findIndex(function (item) { return item.uid === uid; });
      return index >= 0 ? 'new:' + index : '';
    }
    return '';
  }

  function updateThumbnailControlState(activeKeys) {
    const count = activeKeys.length;
    document.querySelectorAll('[data-thumbnail-choice]').forEach(function (input) {
      const key = input.getAttribute('data-thumbnail-key');
      const card = input.closest('.mk-lecture-image-card');
      const active = activeKeys.indexOf(key) >= 0;
      input.checked = active && key === thumbnailKey;
      input.disabled = !active || count <= 1;

      const label = input.closest('.mk-thumbnail-check');
      if (label) {
        label.classList.toggle('is-disabled', input.disabled);
        label.classList.toggle('is-selected', input.checked);
      }

      if (card) {
        const badge = card.querySelector('.mk-image-thumbnail-badge');
        if (badge) {
          badge.classList.toggle('is-visible', input.checked);
        }
      }
    });
  }

  function updateImageSummary(activeKeys) {
    const count = activeKeys.length;
    if (introImageCountBadge) {
      introImageCountBadge.textContent = '총 ' + count + '장';
    }

    if (!thumbnailStatusBadge) {
      return;
    }

    thumbnailStatusBadge.classList.remove('is-warning', 'is-success', 'is-auto');
    if (count === 0) {
      thumbnailStatusBadge.textContent = '이미지 1장 이상 필수';
      thumbnailStatusBadge.classList.add('is-warning');
    } else if (count === 1) {
      thumbnailStatusBadge.textContent = '자동 썸네일';
      thumbnailStatusBadge.classList.add('is-auto');
    } else if (thumbnailKey && activeKeys.indexOf(thumbnailKey) >= 0) {
      thumbnailStatusBadge.textContent = '썸네일 선택 완료';
      thumbnailStatusBadge.classList.add('is-success');
    } else {
      thumbnailStatusBadge.textContent = '썸네일 1장 선택 필요';
      thumbnailStatusBadge.classList.add('is-warning');
    }
  }

  function syncImageState(options) {
    const settings = options || {};
    const activeKeys = getActiveImageKeys();
    const count = activeKeys.length;
    const previousCount = lastActiveImageCount;
    const wasAutomatic = thumbnailAutomatic;

    if (count === 0) {
      thumbnailKey = null;
      thumbnailAutomatic = false;
    } else if (count === 1) {
      thumbnailKey = activeKeys[0];
      thumbnailAutomatic = true;
    } else {
      if (activeKeys.indexOf(thumbnailKey) < 0) {
        thumbnailKey = null;
        thumbnailAutomatic = false;
      }

      if (!settings.initial && previousCount === 1 && count > 1 && wasAutomatic) {
        thumbnailKey = null;
        thumbnailAutomatic = false;
      }
    }

    lastActiveImageCount = count;
    updateThumbnailControlState(activeKeys);
    updateImageSummary(activeKeys);

    if (thumbnailSelectionInput) {
      thumbnailSelectionInput.value = toServerThumbnailSelection();
    }
    syncGalleryOrder();
  }

  function chooseThumbnail(key, checked) {
    const activeKeys = getActiveImageKeys();
    if (activeKeys.length <= 1) {
      syncImageState();
      return;
    }

    if (checked) {
      thumbnailKey = key;
      thumbnailAutomatic = false;
    } else if (thumbnailKey === key) {
      thumbnailKey = null;
      thumbnailAutomatic = false;
    }
    syncImageState();
  }

  function bindExistingImageControls() {
    getExistingCards().forEach(function (card) {
      const deleteInput = card.querySelector('[data-delete-existing]');
      const thumbnailInput = card.querySelector('[data-thumbnail-choice]');

      if (deleteInput) {
        deleteInput.addEventListener('change', function () {
          card.classList.toggle('is-marked-delete', deleteInput.checked);
          if (deleteInput.checked && thumbnailKey === existingKey(card)) {
            thumbnailKey = null;
            thumbnailAutomatic = false;
          }
          syncImageState();
        });
      }

      if (thumbnailInput) {
        thumbnailInput.addEventListener('change', function () {
          chooseThumbnail(thumbnailInput.getAttribute('data-thumbnail-key'), thumbnailInput.checked);
        });
      }
    });
  }

  function syncProcessedInput() {
    if (!processedInput) {
      return;
    }
    const transfer = new DataTransfer();
    processedImages.forEach(function (item) {
      if (item.upload !== false) transfer.items.add(item.file);
    });
    processedInput.files = transfer.files;
  }

  function createIconButton(iconClass, text, action, uid) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'mk-image-card-button';
    button.setAttribute('data-action', action);
    button.setAttribute('data-uid', String(uid));

    const icon = document.createElement('i');
    icon.className = iconClass;
    const label = document.createElement('span');
    label.textContent = text;
    button.append(icon, label);
    return button;
  }

  function renderProcessedImages() {
    if (!editedImageList) {
      return;
    }

    editedImageList.innerHTML = '';
    processedImages.forEach(function (item, index) {
      const key = getProcessedKey(item);
      const card = document.createElement('article');
      card.className = 'mk-lecture-image-card';
      card.setAttribute('data-new-image', 'true');
      card.setAttribute('data-new-uid', String(item.uid));
      card.classList.toggle('is-upload-excluded', item.upload === false);

      const preview = document.createElement('div');
      preview.className = 'mk-lecture-image-preview';

      const image = document.createElement('img');
      image.src = item.previewUrl;
      image.alt = '새 소개 이미지';

      const originBadge = document.createElement('span');
      originBadge.className = 'mk-image-origin-badge is-new';
      originBadge.textContent = '신규';

      const thumbnailBadge = document.createElement('span');
      thumbnailBadge.className = 'mk-image-thumbnail-badge';
      thumbnailBadge.textContent = 'THUMBNAIL';
      if (thumbnailKey === key) {
        thumbnailBadge.classList.add('is-visible');
      }

      preview.append(image, originBadge, thumbnailBadge);

      const body = document.createElement('div');
      body.className = 'mk-lecture-image-card-body';

      const name = document.createElement('strong');
      name.className = 'mk-lecture-image-name';
      name.textContent = item.originalName;

      const tools = document.createElement('div');
      tools.className = 'mk-new-image-tools';
      tools.append(
        createIconButton('fa fa-pencil', '다시 편집', 'edit', item.uid),
        createIconButton('fa fa-trash-o', '제거', 'remove', item.uid)
      );
      const orderTools = document.createElement('div');
      orderTools.className = 'mk-new-image-tools';
      const up = createIconButton('fa fa-arrow-up', '앞 순서', 'up', item.uid);
      const down = createIconButton('fa fa-arrow-down', '뒤 순서', 'down', item.uid);
      up.disabled = index === 0;
      down.disabled = index === processedImages.length - 1;
      orderTools.append(up, down);

      const actions = document.createElement('div');
      actions.className = 'mk-lecture-image-card-actions';

      const thumbnailLabel = document.createElement('label');
      thumbnailLabel.className = 'mk-image-check mk-thumbnail-check';
      const thumbnailInput = document.createElement('input');
      thumbnailInput.type = 'checkbox';
      thumbnailInput.setAttribute('data-thumbnail-choice', 'true');
      thumbnailInput.setAttribute('data-thumbnail-key', key);
      thumbnailInput.checked = thumbnailKey === key;
      const thumbnailText = document.createElement('span');
      thumbnailText.textContent = '썸네일';
      thumbnailLabel.append(thumbnailInput, thumbnailText);
      actions.append(thumbnailLabel, createUploadChoice(item));

      body.append(name, tools, orderTools, actions);
      card.append(preview, body);
      editedImageList.append(card);
    });

    if (editedImageEmpty) {
      editedImageEmpty.classList.toggle('is-hidden', processedImages.length > 0);
    }

    syncImageState();
  }

  if (editedImageList) {
    editedImageList.addEventListener('change', function (event) {
      const uploadChoice = event.target.closest('[data-upload-uid]');
      if (uploadChoice) {
        const item = processedImages.find(function (entry) { return entry.uid === Number(uploadChoice.dataset.uploadUid); });
        if (item) item.upload = uploadChoice.checked;
        syncProcessedInput();
        renderProcessedImages();
        return;
      }
      const choice = event.target.closest('[data-thumbnail-choice]');
      if (!choice) {
        return;
      }
      chooseThumbnail(choice.getAttribute('data-thumbnail-key'), choice.checked);
    });

    editedImageList.addEventListener('click', function (event) {
      const button = event.target.closest('[data-action][data-uid]');
      if (!button) {
        return;
      }

      const uid = Number(button.getAttribute('data-uid'));
      const item = processedImages.find(function (image) { return image.uid === uid; });
      if (!item) {
        return;
      }

      if (button.getAttribute('data-action') === 'remove') {
        const removingKey = getProcessedKey(item);
        if (item.previewUrl) {
          URL.revokeObjectURL(item.previewUrl);
        }
        processedImages = processedImages.filter(function (image) { return image.uid !== uid; });
        if (thumbnailKey === removingKey) {
          thumbnailKey = null;
          thumbnailAutomatic = false;
        }
        syncProcessedInput();
        renderProcessedImages();
        if (pickerInfo) {
          pickerInfo.textContent = processedImages.length > 0
            ? '새 이미지 ' + processedImages.length + '장 편집 완료 · 사진 선택으로 더 추가할 수 있습니다.'
            : '이미지 1장 이상 · 여러 장을 한 번에 선택할 수 있습니다.';
        }
      } else if (button.getAttribute('data-action') === 'edit') {
        setActiveImageKind('intro');
        pendingQueue = [];
        pendingQueueIndex = 0;
        editingProcessedUid = uid;
        editingSnapshot = item.snapshot || null;
        openCropEditor(item.sourceFile, '다시 편집');
      } else if (button.getAttribute('data-action') === 'up' || button.getAttribute('data-action') === 'down') {
        moveProcessedImage(processedImages, uid, button.getAttribute('data-action'));
        syncProcessedInput();
        renderProcessedImages();
      }
    });
  }

  function showEditorModal() {
    if (!editorModal) {
      return;
    }
    if (!editorModal.classList.contains('is-open')) editorReturnFocus = document.activeElement;
    editorModal.classList.add('is-open');
    editorModal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('mk-image-editor-open');
    const background = document.querySelector('main');
    if (background) background.inert = true;
    requestAnimationFrame(function () { editorModal.classList.add('is-visible'); editorClose.focus(); });
  }

  function hideEditorModal() {
    if (!editorModal) {
      return;
    }
    editorModal.classList.remove('is-visible');
    setTimeout(function () { if (editorModal.getAttribute('aria-hidden') === 'true') editorModal.classList.remove('is-open'); }, 220);
    editorModal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('mk-image-editor-open');
    const background = document.querySelector('main');
    if (background) background.inert = false;
    if (editorReturnFocus && document.contains(editorReturnFocus)) editorReturnFocus.focus();
  }

  function setCropEditorStep(step) {
    if (!cropState) {
      return;
    }

    cropState.step = step === 2 ? 2 : 1;
    const isStepOne = cropState.step === 1;

    cropStepOnePanel.classList.toggle('is-hidden', !isStepOne);
    cropStepTwoPanel.classList.toggle('is-hidden', isStepOne);
    cropStepOneTab.classList.toggle('is-active', isStepOne);
    cropStepOneTab.classList.toggle('is-complete', !isStepOne);
    cropStepTwoTab.classList.toggle('is-active', !isStepOne);
    cropStepTwoTab.classList.toggle('is-disabled', isStepOne);
    cropBackBtn.classList.toggle('is-hidden', isStepOne);
    cropSelectBtn.classList.toggle('is-hidden', isStepOne);

    cropGuide.textContent = isStepOne
      ? '1단계: 원본 ' + cropState.naturalWidth + '×' + cropState.naturalHeight + 'px에서 사용할 영역을 자릅니다. 테두리·모서리를 드래그하거나 X·Y·너비·높이를 픽셀 단위로 입력할 수 있습니다.'
      : '2단계: 잘라낸 이미지를 ' + TARGET_WIDTH + '×' + TARGET_HEIGHT + 'px(' + (activeImageKind === 'instructor' ? '4:5' : '16:9') + ') 안에 배치합니다. 드래그, 정렬, 확대/축소, 수치 입력과 배경색을 이용해 최종 화면을 정확히 맞춰주세요.';

    if (isStepOne) {
      requestAnimationFrame(function () {
        refreshCropSourceStageMetrics();
        renderCropSelection();
      });
    } else {
      requestAnimationFrame(renderPlacement);
    }
  }

  function openCropEditor(file, customLabel) {
    if (!file) {
      return;
    }

    if (!file.type || file.type.indexOf('image/') !== 0) {
      alert('이미지 파일만 등록할 수 있습니다.');
      return;
    }

    editorReading = true;
    const kindWhenReading = activeImageKind;
    const reader = new FileReader();
    reader.onload = function (event) {
      const image = new Image();
      image.onload = function () {
        editorReading = false;
        if (activeImageKind !== kindWhenReading) return;
        if (image.naturalWidth * image.naturalHeight > 40000000) {
          alert('원본 이미지가 너무 큽니다. 4,000만 화소 이하 이미지로 줄인 뒤 선택해주세요.');
          skipFailedImage();
          return;
        }
        cropState = {
          sourceFile: file,
          originalImage: image,
          image: image,
          rotation: 0,
          flipX: false,
          flipY: false,
          sourceZoom: 1,
          naturalWidth: image.naturalWidth,
          naturalHeight: image.naturalHeight,
          step: 1,
          crop: {
            x: 0,
            y: 0,
            width: image.naturalWidth,
            height: image.naturalHeight
          },
          sourceDisplayScale: 1,
          sourcePointer: null,
          forceCreateNext: false,
          placementPointer: null,
          placement: {
            x: 0,
            y: 0,
            width: 0,
            height: 0,
            containWidth: 0,
            containHeight: 0,
            zoomPercent: 100,
            backgroundColor: '#ffffff',
            previewUrl: ''
          }
        };

        let progressLabel = customLabel || (activeImageKind === 'instructor' ? '강사 사진 편집' : '소개 이미지 편집');
        if (pendingQueue.length > 0 && editingProcessedUid === null) {
          progressLabel += ' ' + (pendingQueueIndex + 1) + '/' + pendingQueue.length;
        }
        editorTitle.textContent = progressLabel + ' · ' + file.name;
        cropSourceImage.src = image.src;
        placementBackgroundColor.value = '#ffffff';
        placementBackgroundHex.value = '#ffffff';
        cropDrawAreaBtn.classList.remove('is-active');
        cropSourceStage.classList.remove('is-draw-mode');
        cropSelectBtn.disabled = false;
        sourceZoomRange.value = '100';
        sourceZoomValue.textContent = '100%';
        if (editingSnapshot) {
          cropState.rotation = editingSnapshot.rotation || 0;
          cropState.flipX = Boolean(editingSnapshot.flipX);
          cropState.flipY = Boolean(editingSnapshot.flipY);
          transformOriginalImage(false);
          cropState.crop = Object.assign({}, editingSnapshot.crop);
        }

        showEditorModal();
        setTimeout(function () {
          setCropEditorStep(1);
        }, 80);
      };

      image.onerror = function () {
        editorReading = false;
        alert('이미지를 불러오지 못했습니다. 다른 이미지 파일을 선택해주세요.');
        skipFailedImage();
      };
      image.src = event.target.result;
    };

    reader.onerror = function () {
      editorReading = false;
      alert('이미지 파일을 읽지 못했습니다.');
      skipFailedImage();
    };
    reader.readAsDataURL(file);
  }

  function closeCropEditor(clearQueue) {
    if (cropState && cropState.placement.previewUrl) {
      cropState.placement.previewUrl = '';
    }
    cropState = null;
    cropSourceImage.removeAttribute('src');
    placementImage.removeAttribute('src');
    cropSourceStage.classList.remove('is-draw-mode');
    cropDrawAreaBtn.classList.remove('is-active');
    cropSelectBtn.disabled = false;
    hideEditorModal();

    if (clearQueue) {
      pendingQueue = [];
      pendingQueueIndex = 0;
      editingProcessedUid = null;
      editingSnapshot = null;
    }
  }

  function refreshCropSourceStageMetrics() {
    if (!cropState || !cropSourceStageWrap) {
      return;
    }

    const availableWidth = Math.max(280, Math.min(900, cropSourceStageWrap.clientWidth - 8));
    const availableHeight = Math.max(280, Math.min(590, window.innerHeight * 0.54));
    const scale = Math.min(
      availableWidth / cropState.naturalWidth,
      availableHeight / cropState.naturalHeight
    );

    cropState.sourceDisplayScale = Math.max(scale * cropState.sourceZoom, 0.0001);
    cropSourceStage.style.width = (cropState.naturalWidth * cropState.sourceDisplayScale) + 'px';
    cropSourceStage.style.height = (cropState.naturalHeight * cropState.sourceDisplayScale) + 'px';
  }

  function normalizeCropRect() {
    if (!cropState) {
      return;
    }

    const minCropSize = Math.min(10, cropState.naturalWidth, cropState.naturalHeight);
    const crop = cropState.crop;
    crop.width = clampNumber(crop.width, minCropSize, cropState.naturalWidth);
    crop.height = clampNumber(crop.height, minCropSize, cropState.naturalHeight);
    crop.x = clampNumber(crop.x, 0, cropState.naturalWidth - crop.width);
    crop.y = clampNumber(crop.y, 0, cropState.naturalHeight - crop.height);
  }

  function syncCropNumericInputs() {
    if (!cropState) {
      return;
    }
    cropXInput.value = roundEditorNumber(cropState.crop.x);
    cropYInput.value = roundEditorNumber(cropState.crop.y);
    cropWidthInput.value = roundEditorNumber(cropState.crop.width);
    cropHeightInput.value = roundEditorNumber(cropState.crop.height);
  }

  function renderCropSelection() {
    if (!cropState) {
      return;
    }
    normalizeCropRect();
    const scale = cropState.sourceDisplayScale;
    cropSelection.style.left = (cropState.crop.x * scale) + 'px';
    cropSelection.style.top = (cropState.crop.y * scale) + 'px';
    cropSelection.style.width = (cropState.crop.width * scale) + 'px';
    cropSelection.style.height = (cropState.crop.height * scale) + 'px';
    syncCropNumericInputs();
  }

  function getSourceNaturalPoint(event) {
    const rect = cropSourceStage.getBoundingClientRect();
    return {
      x: clampNumber((event.clientX - rect.left) / cropState.sourceDisplayScale, 0, cropState.naturalWidth),
      y: clampNumber((event.clientY - rect.top) / cropState.sourceDisplayScale, 0, cropState.naturalHeight)
    };
  }

  function beginCropPointer(event) {
    if (!cropState || cropState.step !== 1) {
      return;
    }

    const point = getSourceNaturalPoint(event);
    const handle = event.target.closest('[data-crop-handle]');
    const insideSelection = event.target.closest('#cropSelection');
    let mode = 'create';

    if (cropState.forceCreateNext) {
      cropState.forceCreateNext = false;
      cropSourceStage.classList.remove('is-draw-mode');
      cropDrawAreaBtn.classList.remove('is-active');
    } else if (handle) {
      mode = 'resize-' + handle.getAttribute('data-crop-handle');
    } else if (insideSelection) {
      mode = 'move';
    }

    cropState.sourcePointer = {
      pointerId: event.pointerId,
      mode: mode,
      startPoint: point,
      startCrop: Object.assign({}, cropState.crop)
    };

    if (mode === 'create') {
      cropState.crop = {
        x: point.x,
        y: point.y,
        width: 1,
        height: 1
      };
      renderCropSelection();
    }

    cropSourceStage.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  function moveCropPointer(event) {
    if (!cropState || !cropState.sourcePointer || cropState.sourcePointer.pointerId !== event.pointerId) {
      return;
    }

    const pointer = cropState.sourcePointer;
    const point = getSourceNaturalPoint(event);
    const start = pointer.startCrop;
    const dx = point.x - pointer.startPoint.x;
    const dy = point.y - pointer.startPoint.y;
    const minSize = Math.min(10, cropState.naturalWidth, cropState.naturalHeight);

    if (pointer.mode === 'move') {
      cropState.crop.x = clampNumber(start.x + dx, 0, cropState.naturalWidth - start.width);
      cropState.crop.y = clampNumber(start.y + dy, 0, cropState.naturalHeight - start.height);
    } else if (pointer.mode === 'create') {
      cropState.crop.x = Math.min(pointer.startPoint.x, point.x);
      cropState.crop.y = Math.min(pointer.startPoint.y, point.y);
      cropState.crop.width = Math.max(1, Math.abs(point.x - pointer.startPoint.x));
      cropState.crop.height = Math.max(1, Math.abs(point.y - pointer.startPoint.y));
    } else if (pointer.mode.indexOf('resize-') === 0) {
      const handle = pointer.mode.replace('resize-', '');
      let left = start.x;
      let top = start.y;
      let right = start.x + start.width;
      let bottom = start.y + start.height;

      if (handle.indexOf('w') >= 0) {
        left = clampNumber(start.x + dx, 0, right - minSize);
      }
      if (handle.indexOf('e') >= 0) {
        right = clampNumber(start.x + start.width + dx, left + minSize, cropState.naturalWidth);
      }
      if (handle.indexOf('n') >= 0) {
        top = clampNumber(start.y + dy, 0, bottom - minSize);
      }
      if (handle.indexOf('s') >= 0) {
        bottom = clampNumber(start.y + start.height + dy, top + minSize, cropState.naturalHeight);
      }

      cropState.crop = {
        x: left,
        y: top,
        width: right - left,
        height: bottom - top
      };
    }

    renderCropSelection();
    event.preventDefault();
  }

  function endCropPointer(event) {
    if (!cropState || !cropState.sourcePointer || cropState.sourcePointer.pointerId !== event.pointerId) {
      return;
    }

    cropState.sourcePointer = null;
    normalizeCropRect();
    renderCropSelection();

    try {
      cropSourceStage.releasePointerCapture(event.pointerId);
    } catch (ignored) {
      // 이미 포인터 캡처가 해제된 경우 무시합니다.
    }
  }

  function applyCropNumericInputs() {
    if (!cropState) {
      return;
    }
    cropState.crop.x = Number(cropXInput.value);
    cropState.crop.y = Number(cropYInput.value);
    cropState.crop.width = Number(cropWidthInput.value);
    cropState.crop.height = Number(cropHeightInput.value);
    normalizeCropRect();
    renderCropSelection();
  }

  function setFullImageCrop() {
    if (!cropState) {
      return;
    }
    cropState.forceCreateNext = false;
    cropSourceStage.classList.remove('is-draw-mode');
    cropDrawAreaBtn.classList.remove('is-active');
    cropState.crop = {
      x: 0,
      y: 0,
      width: cropState.naturalWidth,
      height: cropState.naturalHeight
    };
    renderCropSelection();
  }

  function setCenteredCropArea() {
    if (!cropState) {
      return;
    }
    cropState.forceCreateNext = false;
    cropSourceStage.classList.remove('is-draw-mode');
    cropDrawAreaBtn.classList.remove('is-active');
    const width = Math.max(10, cropState.naturalWidth * 0.6);
    const height = Math.max(10, cropState.naturalHeight * 0.6);
    cropState.crop = {
      x: (cropState.naturalWidth - width) / 2,
      y: (cropState.naturalHeight - height) / 2,
      width: width,
      height: height
    };
    renderCropSelection();
  }

  function buildCroppedPreview() {
    if (!cropState) {
      return;
    }

    normalizeCropRect();
    const crop = cropState.crop;
    const previewCanvas = document.createElement('canvas');
    previewCanvas.width = Math.max(1, roundEditorNumber(crop.width));
    previewCanvas.height = Math.max(1, roundEditorNumber(crop.height));
    const previewContext = previewCanvas.getContext('2d');

    previewContext.imageSmoothingEnabled = true;
    previewContext.imageSmoothingQuality = 'high';
    previewContext.drawImage(
      cropState.image,
      crop.x,
      crop.y,
      crop.width,
      crop.height,
      0,
      0,
      previewCanvas.width,
      previewCanvas.height
    );

    cropState.placement.previewUrl = previewCanvas.toDataURL('image/png');
    placementImage.src = cropState.placement.previewUrl;
    setPlacementSizeMode('contain');
    if (editingSnapshot && JSON.stringify(editingSnapshot.crop) === JSON.stringify(cropState.crop)) {
      Object.assign(cropState.placement, editingSnapshot.placement);
      placementBackgroundColor.value = cropState.placement.backgroundColor;
      placementBackgroundHex.value = cropState.placement.backgroundColor;
      renderPlacement();
    }
    editingSnapshot = null;
  }

  function normalizePlacement() {
    if (!cropState) {
      return;
    }

    const placement = cropState.placement;
    const minVisible = 16;
    placement.width = Math.max(1, Number(placement.width) || 1);
    placement.height = Math.max(1, Number(placement.height) || 1);
    placement.x = clampNumber(placement.x, -placement.width + minVisible, TARGET_WIDTH - minVisible);
    placement.y = clampNumber(placement.y, -placement.height + minVisible, TARGET_HEIGHT - minVisible);
  }

  function syncPlacementNumericInputs() {
    if (!cropState) {
      return;
    }

    const placement = cropState.placement;
    placementXInput.value = roundEditorNumber(placement.x);
    placementYInput.value = roundEditorNumber(placement.y);
    placementWidthInput.value = roundEditorNumber(placement.width);
    placementHeightInput.value = roundEditorNumber(placement.height);
    placementZoomRange.value = clampNumber(placement.zoomPercent, 10, 1000);
    placementZoomValue.textContent = roundEditorNumber(placement.zoomPercent) + '%';
  }

  function renderPlacement() {
    if (!cropState) {
      return;
    }

    normalizePlacement();
    const stageWidth = placementStage.clientWidth || TARGET_WIDTH;
    const stageHeight = placementStage.clientHeight || TARGET_HEIGHT;
    const ratioX = stageWidth / TARGET_WIDTH;
    const ratioY = stageHeight / TARGET_HEIGHT;
    const placement = cropState.placement;

    placementStage.style.backgroundColor = placement.backgroundColor;
    placementImage.style.left = (placement.x * ratioX) + 'px';
    placementImage.style.top = (placement.y * ratioY) + 'px';
    placementImage.style.width = (placement.width * ratioX) + 'px';
    placementImage.style.height = (placement.height * ratioY) + 'px';
    syncPlacementNumericInputs();
  }

  function updatePlacementZoomFromSize() {
    if (!cropState || cropState.placement.containWidth <= 0) {
      return;
    }
    cropState.placement.zoomPercent = (cropState.placement.width / cropState.placement.containWidth) * 100;
  }

  function setPlacementSize(width, height, align) {
    if (!cropState) {
      return;
    }
    cropState.placement.width = Math.max(1, width);
    cropState.placement.height = Math.max(1, height);
    updatePlacementZoomFromSize();
    alignPlacement(align || 'center');
  }

  function setPlacementSizeMode(mode) {
    if (!cropState) {
      return;
    }

    const crop = cropState.crop;
    const containScale = Math.min(TARGET_WIDTH / crop.width, TARGET_HEIGHT / crop.height);
    const coverScale = Math.max(TARGET_WIDTH / crop.width, TARGET_HEIGHT / crop.height);
    cropState.placement.containWidth = crop.width * containScale;
    cropState.placement.containHeight = crop.height * containScale;

    if (mode === 'cover') {
      setPlacementSize(crop.width * coverScale, crop.height * coverScale, 'center');
    } else if (mode === 'original') {
      setPlacementSize(crop.width, crop.height, 'center');
    } else {
      setPlacementSize(cropState.placement.containWidth, cropState.placement.containHeight, 'center');
    }
  }

  function alignPlacement(position) {
    if (!cropState) {
      return;
    }

    const placement = cropState.placement;
    const centeredX = (TARGET_WIDTH - placement.width) / 2;
    const centeredY = (TARGET_HEIGHT - placement.height) / 2;

    switch (position) {
      case 'top':
        placement.x = centeredX;
        placement.y = 0;
        break;
      case 'left':
        placement.x = 0;
        placement.y = centeredY;
        break;
      case 'right':
        placement.x = TARGET_WIDTH - placement.width;
        placement.y = centeredY;
        break;
      case 'bottom':
        placement.x = centeredX;
        placement.y = TARGET_HEIGHT - placement.height;
        break;
      default:
        placement.x = centeredX;
        placement.y = centeredY;
        break;
    }
    renderPlacement();
  }

  function setPlacementZoom(percent) {
    if (!cropState) {
      return;
    }

    const placement = cropState.placement;
    const previousCenterX = placement.x + placement.width / 2;
    const previousCenterY = placement.y + placement.height / 2;
    const safePercent = clampNumber(percent, 10, 1000);
    const aspectRatio = cropState.crop.width / cropState.crop.height;

    placement.zoomPercent = safePercent;
    placement.width = placement.containWidth * safePercent / 100;
    placement.height = placement.width / aspectRatio;
    placement.x = previousCenterX - placement.width / 2;
    placement.y = previousCenterY - placement.height / 2;
    renderPlacement();
  }

  function applyPlacementNumericInputs(changedField) {
    if (!cropState) {
      return;
    }

    const placement = cropState.placement;
    const aspectRatio = cropState.crop.width / cropState.crop.height;
    placement.x = Number(placementXInput.value);
    placement.y = Number(placementYInput.value);

    if (changedField === 'height') {
      placement.height = Math.max(1, Number(placementHeightInput.value) || 1);
      placement.width = placement.height * aspectRatio;
    } else if (changedField === 'width') {
      placement.width = Math.max(1, Number(placementWidthInput.value) || 1);
      placement.height = placement.width / aspectRatio;
    }

    updatePlacementZoomFromSize();
    renderPlacement();
  }

  function beginPlacementPointer(event) {
    if (!cropState || cropState.step !== 2 || event.target !== placementImage) {
      return;
    }

    cropState.placementPointer = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      startPlacementX: cropState.placement.x,
      startPlacementY: cropState.placement.y
    };
    placementStage.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  function movePlacementPointer(event) {
    if (!cropState || !cropState.placementPointer || cropState.placementPointer.pointerId !== event.pointerId) {
      return;
    }

    const pointer = cropState.placementPointer;
    const ratioX = TARGET_WIDTH / placementStage.clientWidth;
    const ratioY = TARGET_HEIGHT / placementStage.clientHeight;
    cropState.placement.x = pointer.startPlacementX + (event.clientX - pointer.startX) * ratioX;
    cropState.placement.y = pointer.startPlacementY + (event.clientY - pointer.startY) * ratioY;
    renderPlacement();
    event.preventDefault();
  }

  function endPlacementPointer(event) {
    if (!cropState || !cropState.placementPointer || cropState.placementPointer.pointerId !== event.pointerId) {
      return;
    }

    cropState.placementPointer = null;
    try {
      placementStage.releasePointerCapture(event.pointerId);
    } catch (ignored) {
      // 이미 포인터 캡처가 해제된 경우 무시합니다.
    }
  }

  function storeEditedCanvas() {
    if (!cropState || cropState.step !== 2) {
      return;
    }

    const currentState = cropState;
    const kindBeingSaved = activeImageKind;
    const savedWidth = TARGET_WIDTH;
    const savedHeight = TARGET_HEIGHT;
    const savedUid = editingProcessedUid;
    cropSelectBtn.disabled = true;
    editorModal.setAttribute('aria-busy', 'true');
    cropCancelBtn.disabled = true;
    editorClose.disabled = true;

    const canvas = document.createElement('canvas');
    canvas.width = TARGET_WIDTH;
    canvas.height = TARGET_HEIGHT;
    const context = canvas.getContext('2d');
    const crop = currentState.crop;
    const placement = currentState.placement;

    context.fillStyle = placement.backgroundColor;
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = 'high';
    context.drawImage(
      currentState.image,
      crop.x,
      crop.y,
      crop.width,
      crop.height,
      placement.x,
      placement.y,
      placement.width,
      placement.height
    );

    canvas.toBlob(function (blob) {
      editorModal.removeAttribute('aria-busy');
      cropCancelBtn.disabled = false;
      editorClose.disabled = false;
      if (!blob) {
        cropSelectBtn.disabled = false;
        alert('이미지 처리에 실패했습니다. 다시 시도해주세요.');
        return;
      }

      const outputFile = new File([blob], buildOutputFileName(currentState.sourceFile.name), {
        type: OUTPUT_TYPE,
        lastModified: Date.now()
      });
      const previewUrl = URL.createObjectURL(blob);
      const gallery = kindBeingSaved === 'instructor' ? instructorProcessedImages : processedImages;
      const snapshot = {
        rotation: currentState.rotation,
        flipX: currentState.flipX,
        flipY: currentState.flipY,
        crop: Object.assign({}, currentState.crop),
        placement: {
          x: placement.x, y: placement.y, width: placement.width, height: placement.height,
          containWidth: placement.containWidth, containHeight: placement.containHeight,
          zoomPercent: placement.zoomPercent, backgroundColor: placement.backgroundColor
        }
      };

      if (savedUid !== null) {
        const item = gallery.find(function (image) { return image.uid === savedUid; });
        if (item) {
          if (item.previewUrl) {
            URL.revokeObjectURL(item.previewUrl);
          }
          item.file = outputFile;
          item.previewUrl = previewUrl;
          item.sourceFile = currentState.sourceFile;
          item.originalName = currentState.sourceFile.name;
          item.snapshot = snapshot;
        }
        editingProcessedUid = null;
      } else {
        gallery.push({
          uid: nextProcessedUid++,
          file: outputFile,
          previewUrl: previewUrl,
          sourceFile: currentState.sourceFile,
          originalName: currentState.sourceFile.name,
          upload: true,
          snapshot: snapshot,
          width: savedWidth,
          height: savedHeight
        });
      }

      syncProcessedInput();
      renderProcessedImages();
      syncInstructorImages();
      renderInstructorImages();

      if (pendingQueue.length > 0 && pendingQueueIndex + 1 < pendingQueue.length) {
        pendingQueueIndex += 1;
        cropState = null;
        openCropEditor(pendingQueue[pendingQueueIndex]);
        return;
      }

      const activeInfo = kindBeingSaved === 'instructor' ? instructorPickerInfo : pickerInfo;
      if (activeInfo) {
        activeInfo.textContent = '새 이미지 ' + gallery.length + '장 편집 완료 · 업로드 포함 항목만 저장됩니다.';
      }
      pendingQueue = [];
      pendingQueueIndex = 0;
      closeCropEditor(false);
    }, OUTPUT_TYPE, OUTPUT_QUALITY);
  }

  if (cropSourceStage) {
    cropSourceStage.addEventListener('pointerdown', beginCropPointer);
    cropSourceStage.addEventListener('pointermove', moveCropPointer);
    cropSourceStage.addEventListener('pointerup', endCropPointer);
    cropSourceStage.addEventListener('pointercancel', endCropPointer);
  }

  [cropXInput, cropYInput, cropWidthInput, cropHeightInput].forEach(function (input) {
    if (!input) {
      return;
    }
    input.addEventListener('change', applyCropNumericInputs);
    input.addEventListener('blur', applyCropNumericInputs);
  });

  cropFullImageBtn.addEventListener('click', setFullImageCrop);
  cropCenterAreaBtn.addEventListener('click', setCenteredCropArea);
  cropDrawAreaBtn.addEventListener('click', function () {
    if (!cropState) {
      return;
    }
    cropState.forceCreateNext = true;
    cropSourceStage.classList.add('is-draw-mode');
    cropDrawAreaBtn.classList.add('is-active');
  });

  cropNextBtn.addEventListener('click', function () {
    if (!cropState) {
      return;
    }
    cropState.forceCreateNext = false;
    cropSourceStage.classList.remove('is-draw-mode');
    cropDrawAreaBtn.classList.remove('is-active');
    buildCroppedPreview();
    setCropEditorStep(2);
  });

  cropBackBtn.addEventListener('click', function () {
    setCropEditorStep(1);
  });

  if (placementStage) {
    placementStage.addEventListener('pointerdown', beginPlacementPointer);
    placementStage.addEventListener('pointermove', movePlacementPointer);
    placementStage.addEventListener('pointerup', endPlacementPointer);
    placementStage.addEventListener('pointercancel', endPlacementPointer);
  }

  document.querySelectorAll('[data-placement-align]').forEach(function (button) {
    button.addEventListener('click', function () {
      alignPlacement(button.getAttribute('data-placement-align'));
    });
  });

  placementFitContainBtn.addEventListener('click', function () { setPlacementSizeMode('contain'); });
  placementFitCoverBtn.addEventListener('click', function () { setPlacementSizeMode('cover'); });
  placementOriginalBtn.addEventListener('click', function () { setPlacementSizeMode('original'); });
  placementZoomOutBtn.addEventListener('click', function () {
    setPlacementZoom(cropState ? cropState.placement.zoomPercent - 10 : 100);
  });
  placementZoomInBtn.addEventListener('click', function () {
    setPlacementZoom(cropState ? cropState.placement.zoomPercent + 10 : 100);
  });
  placementZoomRange.addEventListener('input', function () {
    setPlacementZoom(Number(placementZoomRange.value));
  });

  placementXInput.addEventListener('change', function () { applyPlacementNumericInputs('position'); });
  placementYInput.addEventListener('change', function () { applyPlacementNumericInputs('position'); });
  placementWidthInput.addEventListener('change', function () { applyPlacementNumericInputs('width'); });
  placementHeightInput.addEventListener('change', function () { applyPlacementNumericInputs('height'); });

  placementBackgroundColor.addEventListener('input', function () {
    if (!cropState) {
      return;
    }
    cropState.placement.backgroundColor = placementBackgroundColor.value;
    placementBackgroundHex.value = placementBackgroundColor.value;
    renderPlacement();
  });

  placementBackgroundHex.addEventListener('change', function () {
    if (!cropState) {
      return;
    }
    const color = normalizeHexColor(placementBackgroundHex.value);
    if (!color) {
      alert('배경색은 #RRGGBB 형식으로 입력해주세요.');
      placementBackgroundHex.value = cropState.placement.backgroundColor;
      return;
    }
    cropState.placement.backgroundColor = color;
    placementBackgroundColor.value = color;
    placementBackgroundHex.value = color;
    renderPlacement();
  });

  cropSelectBtn.addEventListener('click', storeEditedCanvas);
  cropCancelBtn.addEventListener('click', function () { closeCropEditor(true); });
  editorClose.addEventListener('click', function () { closeCropEditor(true); });

  window.addEventListener('resize', function () {
    if (!cropState) {
      return;
    }
    if (cropState.step === 1) {
      refreshCropSourceStageMetrics();
      renderCropSelection();
    } else {
      renderPlacement();
    }
  });

  if (picker) {
    picker.addEventListener('change', function () {
      startImageQueue(picker, 'intro');
    });
  }

  form.addEventListener('submit', function (event) {
    if (editorReading || cropState || pendingQueue.length > 0) {
      event.preventDefault();
      alert('진행 중인 이미지 편집을 완료하거나 취소한 뒤 저장해주세요.');
      return;
    }
    const start = document.getElementById('enrollmentStartsAt');
    const end = document.getElementById('enrollmentEndsAt');
    if (Boolean(start.value) !== Boolean(end.value) || (start.value && end.value && start.value >= end.value)) {
      event.preventDefault();
      end.setCustomValidity('기간 한정 모집은 시작과 마감을 모두 입력하고, 마감을 시작 이후로 지정해주세요.');
      end.reportValidity();
      return;
    }
    const careerInputs = Array.from(document.querySelectorAll('#instructorCareerList input'));
    if (document.getElementById('lectureStatus').value === 'PUBLISHED' && !careerInputs.some(function (input) { return input.value.trim().length > 0; })) {
      event.preventDefault();
      careerInputs[0].setCustomValidity('강사의 주요 이력을 1개 이상 입력해주세요.');
      careerInputs[0].reportValidity();
      return;
    }
    const mode = form.getAttribute('data-mode') || 'edit';
    if (mode === 'create' && (!video || !video.files || video.files.length === 0)) {
      event.preventDefault();
      alert('영상 파일을 선택해주세요.');
      if (videoFileName) {
        videoFileName.closest('.mk-file-picker-row').classList.add('is-error');
        videoFileName.closest('.mk-file-picker-row').scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
      return;
    }

    const activeKeys = getActiveImageKeys();
    if (activeKeys.length < 1) {
      event.preventDefault();
      alert('소개 이미지는 1장 이상 반드시 등록해야 합니다.');
      if (introImageSection) {
        introImageSection.classList.add('has-validation-error');
        introImageSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
      return;
    }

    if (activeKeys.length > 1 && (!thumbnailKey || activeKeys.indexOf(thumbnailKey) < 0)) {
      event.preventDefault();
      alert('소개 이미지가 2장 이상이면 썸네일 이미지를 체크박스로 1장 선택해주세요.');
      if (introImageSection) {
        introImageSection.classList.add('has-validation-error');
        introImageSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
      return;
    }

    if (introImageSection) {
      introImageSection.classList.remove('has-validation-error');
    }
    syncProcessedInput();
    syncImageState();
    syncInstructorImages();
    syncGalleryOrder();

    const processingOverlay = document.getElementById('videoProcessingOverlay');
    const processingMessage = document.getElementById('videoProcessingMessage');
    const submitButton = form.querySelector('button[type="submit"]');
    const hasNewVideo = Boolean(video && video.files && video.files.length > 0);

    if (processingMessage) {
      processingMessage.textContent = hasNewVideo
        ? '영상 업로드 → 실제 코덱 검사 → MP4(H.264/AAC) 자동 최적화 → 강의 저장 순서로 처리합니다.'
        : '강의 정보와 이미지·첨부자료 변경사항을 저장하고 있습니다.';
    }
    if (processingOverlay) {
      processingOverlay.hidden = false;
      requestAnimationFrame(function () {
        processingOverlay.classList.add('is-visible');
      });
    }
    if (submitButton) {
      submitButton.disabled = true;
      submitButton.setAttribute('aria-disabled', 'true');
    }
  });

  function setActiveImageKind(kind) {
    activeImageKind = kind === 'instructor' ? 'instructor' : 'intro';
    TARGET_WIDTH = activeImageKind === 'instructor' ? 800 : 1280;
    TARGET_HEIGHT = activeImageKind === 'instructor' ? 1000 : 720;
    const ratio = activeImageKind === 'instructor' ? '4:5' : '16:9';
    placementStage.style.aspectRatio = TARGET_WIDTH + '/' + TARGET_HEIGHT;
    placementStage.classList.toggle('is-portrait', activeImageKind === 'instructor');
    document.getElementById('placementStepLabel').textContent = ratio + ' 배치';
    document.getElementById('placementSizeLabel').textContent = TARGET_WIDTH + ' × ' + TARGET_HEIGHT + ' · ' + ratio + ' · 고객 화면 미리보기';
  }

  function createUploadChoice(item) {
    const label = document.createElement('label');
    label.className = 'mk-image-check';
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.checked = item.upload !== false;
    input.dataset.uploadUid = String(item.uid);
    const span = document.createElement('span');
    span.textContent = '업로드 포함';
    label.append(input, span);
    return label;
  }

  function moveProcessedImage(gallery, uid, direction) {
    const index = gallery.findIndex(function (item) { return item.uid === uid; });
    const target = index + (direction === 'up' ? -1 : 1);
    if (index < 0 || target < 0 || target >= gallery.length) return;
    const item = gallery.splice(index, 1)[0];
    gallery.splice(target, 0, item);
  }

  function activeInstructorCards() {
    return Array.from(document.querySelectorAll('[data-existing-instructor-image]')).filter(function (card) {
      return !card.querySelector('[data-delete-existing-instructor]').checked;
    });
  }

  function syncGalleryOrder() {
    if (introImageOrderInput) {
      const introOrder = getActiveExistingCards().map(existingKey);
      processedImages.filter(function (item) { return item.upload !== false; }).forEach(function (_, index) { introOrder.push('new:' + index); });
      introImageOrderInput.value = introOrder.join(',');
    }
    if (instructorImageOrderInput) {
      const instructorOrder = activeInstructorCards().map(existingKey);
      instructorProcessedImages.filter(function (item) { return item.upload !== false; }).forEach(function (_, index) { instructorOrder.push('new:' + index); });
      instructorImageOrderInput.value = instructorOrder.join(',');
    }
  }

  function syncInstructorImages() {
    if (!instructorProcessedInput) return;
    const transfer = new DataTransfer();
    instructorProcessedImages.forEach(function (item) { if (item.upload !== false) transfer.items.add(item.file); });
    instructorProcessedInput.files = transfer.files;
    const count = activeInstructorCards().length + transfer.files.length;
    document.getElementById('instructorImageCountBadge').textContent = '총 ' + count + '장 · 첫 사진이 대표';
    syncGalleryOrder();
  }

  function renderInstructorImages() {
    if (!instructorImageList) return;
    instructorImageList.replaceChildren();
    instructorProcessedImages.forEach(function (item, index) {
      const card = document.createElement('article');
      card.className = 'mk-lecture-image-card';
      card.classList.toggle('is-upload-excluded', item.upload === false);
      const preview = document.createElement('div');
      preview.className = 'mk-lecture-image-preview';
      const image = document.createElement('img');
      image.src = item.previewUrl;
      image.alt = '편집한 강사 사진 ' + (index + 1);
      const origin = document.createElement('span');
      origin.className = 'mk-image-origin-badge is-new';
      origin.textContent = '신규 ' + (index + 1);
      preview.append(image, origin);
      const body = document.createElement('div');
      body.className = 'mk-lecture-image-card-body';
      const name = document.createElement('strong');
      name.className = 'mk-lecture-image-name';
      name.textContent = item.originalName;
      const tools = document.createElement('div');
      tools.className = 'mk-new-image-tools';
      tools.append(createIconButton('fa fa-pencil', '다시 편집', 'edit', item.uid), createIconButton('fa fa-trash-o', '제외', 'remove', item.uid));
      const order = document.createElement('div');
      order.className = 'mk-new-image-tools';
      const up = createIconButton('fa fa-arrow-up', '앞 순서', 'up', item.uid);
      const down = createIconButton('fa fa-arrow-down', '뒤 순서', 'down', item.uid);
      up.disabled = index === 0;
      down.disabled = index === instructorProcessedImages.length - 1;
      order.append(up, down);
      body.append(name, tools, order, createUploadChoice(item));
      card.append(preview, body);
      instructorImageList.append(card);
    });
    instructorImageEmpty.classList.toggle('is-hidden', instructorProcessedImages.length > 0);
    syncInstructorImages();
  }

  if (instructorImageList) {
    instructorImageList.addEventListener('change', function (event) {
      const input = event.target.closest('[data-upload-uid]');
      if (!input) return;
      const item = instructorProcessedImages.find(function (entry) { return entry.uid === Number(input.dataset.uploadUid); });
      if (item) item.upload = input.checked;
      renderInstructorImages();
    });
    instructorImageList.addEventListener('click', function (event) {
      const button = event.target.closest('[data-action][data-uid]');
      if (!button) return;
      const uid = Number(button.dataset.uid);
      const item = instructorProcessedImages.find(function (entry) { return entry.uid === uid; });
      if (!item) return;
      if (button.dataset.action === 'remove') {
        URL.revokeObjectURL(item.previewUrl);
        instructorProcessedImages = instructorProcessedImages.filter(function (entry) { return entry.uid !== uid; });
        renderInstructorImages();
      } else if (button.dataset.action === 'edit') {
        setActiveImageKind('instructor');
        editingProcessedUid = uid;
        editingSnapshot = item.snapshot || null;
        pendingQueue = [];
        pendingQueueIndex = 0;
        openCropEditor(item.sourceFile, '강사 사진 다시 편집');
      } else {
        moveProcessedImage(instructorProcessedImages, uid, button.dataset.action);
        renderInstructorImages();
      }
    });
  }

  document.querySelectorAll('[data-delete-existing-instructor]').forEach(function (input) {
    input.addEventListener('change', function () {
      input.closest('.mk-lecture-image-card').classList.toggle('is-marked-delete', input.checked);
      syncInstructorImages();
    });
  });

  document.querySelectorAll('[data-existing-intro-move], [data-existing-instructor-move]').forEach(function (button) {
    button.addEventListener('click', function () {
      const card = button.closest('.mk-lecture-image-card');
      const direction = button.dataset.existingIntroMove || button.dataset.existingInstructorMove;
      if (direction === 'up' && card.previousElementSibling) card.parentNode.insertBefore(card, card.previousElementSibling);
      if (direction === 'down' && card.nextElementSibling) card.parentNode.insertBefore(card.nextElementSibling, card);
      syncImageState();
      syncInstructorImages();
    });
  });

  function startImageQueue(input, kind) {
    const files = Array.from(input.files || []);
    input.value = '';
    if (!files.length || editorReading || cropState) return;
    const limitMB = kind === 'instructor' ? 15 : 30;
    const maxImages = kind === 'instructor' ? 20 : 30;
    const invalid = files.filter(function (file) { return !/^image\/(jpeg|png|webp|gif|bmp)$/i.test(file.type) || file.size > limitMB * 1024 * 1024; });
    if (invalid.length) {
      alert('JPG, PNG, WebP, GIF, BMP 이미지(파일당 최대 ' + limitMB + 'MB)를 선택해주세요.\n확인 필요: ' + invalid.map(function (file) { return file.name; }).join(', '));
      return;
    }
    const gallery = kind === 'instructor' ? instructorProcessedImages : processedImages;
    const existingCount = kind === 'instructor' ? activeInstructorCards().length : getActiveExistingCards().length;
    if (gallery.length + existingCount + files.length > maxImages) { alert('이 이미지 목록은 최대 ' + maxImages + '장까지 등록할 수 있습니다.'); return; }
    setActiveImageKind(kind);
    pendingQueue = files;
    pendingQueueIndex = 0;
    editingProcessedUid = null;
    editingSnapshot = null;
    openCropEditor(files[0]);
  }

  function skipFailedImage() {
    if (pendingQueue.length && pendingQueueIndex + 1 < pendingQueue.length) {
      pendingQueueIndex += 1;
      openCropEditor(pendingQueue[pendingQueueIndex]);
    } else {
      closeCropEditor(true);
    }
  }

  function transformOriginalImage(resetCrop) {
    if (!cropState) return;
    const original = cropState.originalImage;
    const canvas = document.createElement('canvas');
    const swapped = cropState.rotation % 180 !== 0;
    canvas.width = swapped ? original.naturalHeight : original.naturalWidth;
    canvas.height = swapped ? original.naturalWidth : original.naturalHeight;
    const context = canvas.getContext('2d');
    context.translate(canvas.width / 2, canvas.height / 2);
    context.rotate(cropState.rotation * Math.PI / 180);
    context.scale(cropState.flipX ? -1 : 1, cropState.flipY ? -1 : 1);
    context.drawImage(original, -original.naturalWidth / 2, -original.naturalHeight / 2);
    cropState.image = canvas;
    cropState.naturalWidth = canvas.width;
    cropState.naturalHeight = canvas.height;
    cropSourceImage.src = canvas.toDataURL('image/png');
    if (resetCrop !== false) setFullImageCrop();
    refreshCropSourceStageMetrics();
    renderCropSelection();
  }

  function setSourceZoom(percent) {
    if (!cropState) return;
    const value = clampNumber(percent, 50, 300);
    cropState.sourceZoom = value / 100;
    sourceZoomRange.value = value;
    sourceZoomValue.textContent = Math.round(value) + '%';
    refreshCropSourceStageMetrics();
    renderCropSelection();
  }

  sourceZoomRange.addEventListener('input', function () { setSourceZoom(Number(sourceZoomRange.value)); });
  document.getElementById('sourceZoomOut').addEventListener('click', function () { setSourceZoom(Number(sourceZoomRange.value) - 10); });
  document.getElementById('sourceZoomIn').addEventListener('click', function () { setSourceZoom(Number(sourceZoomRange.value) + 10); });
  document.getElementById('sourceRotateLeft').addEventListener('click', function () { if (cropState) { cropState.rotation = (cropState.rotation + 270) % 360; transformOriginalImage(); } });
  document.getElementById('sourceRotateRight').addEventListener('click', function () { if (cropState) { cropState.rotation = (cropState.rotation + 90) % 360; transformOriginalImage(); } });
  document.getElementById('sourceFlipHorizontal').addEventListener('click', function () { if (cropState) { cropState.flipX = !cropState.flipX; transformOriginalImage(); } });
  document.getElementById('sourceFlipVertical').addEventListener('click', function () { if (cropState) { cropState.flipY = !cropState.flipY; transformOriginalImage(); } });
  document.getElementById('sourceReset').addEventListener('click', function () {
    if (!cropState) return;
    cropState.rotation = 0; cropState.flipX = false; cropState.flipY = false;
    transformOriginalImage(); setSourceZoom(100);
  });

  if (instructorPicker) instructorPicker.addEventListener('change', function () { startImageQueue(instructorPicker, 'instructor'); });

  editorModal.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && editorModal.getAttribute('aria-busy') !== 'true') { event.preventDefault(); closeCropEditor(true); }
    if (event.key !== 'Tab') return;
    const candidates = Array.from(editorModal.querySelectorAll('button:not(:disabled), input:not(:disabled), [tabindex="0"]')).filter(function (element) { return element.getClientRects().length > 0; });
    const first = candidates[0];
    const last = candidates[candidates.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });

  const careerList = document.getElementById('instructorCareerList');
  const addCareerButton = document.getElementById('addInstructorCareer');
  function numberCareers() {
    const rows = Array.from(careerList.children);
    rows.forEach(function (row, index) {
      row.querySelector('.mk-career-number').textContent = String(index + 1).padStart(2, '0');
      row.querySelector('[data-career-action="up"]').disabled = index === 0;
      row.querySelector('[data-career-action="down"]').disabled = index === rows.length - 1;
      row.querySelector('[data-career-action="remove"]').disabled = rows.length === 1;
    });
    addCareerButton.disabled = rows.length >= 30;
  }
  function addCareer(focus) {
    if (careerList.children.length >= 30) return;
    const row = document.createElement('div');
    row.className = 'mk-career-row';
    row.innerHTML = '<span class="mk-career-number"></span><input name="instructorCareers" maxlength="500" aria-label="강사 이력" placeholder="실제 학력, 자격 또는 강의 경력"><div class="mk-career-actions"><button type="button" data-career-action="up" aria-label="이력 위로 이동">↑</button><button type="button" data-career-action="down" aria-label="이력 아래로 이동">↓</button><button type="button" data-career-action="remove" aria-label="이력 삭제">×</button></div>';
    careerList.append(row);
    numberCareers();
    if (focus) row.querySelector('input').focus();
  }
  addCareerButton.addEventListener('click', function () { addCareer(true); });
  careerList.addEventListener('input', function (event) {
    if (event.target.matches('input')) careerList.querySelectorAll('input').forEach(function (input) { input.setCustomValidity(''); });
  });
  careerList.addEventListener('click', function (event) {
    const button = event.target.closest('[data-career-action]');
    if (!button) return;
    const row = button.closest('.mk-career-row');
    if (button.dataset.careerAction === 'remove' && careerList.children.length > 1) row.remove();
    if (button.dataset.careerAction === 'up' && row.previousElementSibling) careerList.insertBefore(row, row.previousElementSibling);
    if (button.dataset.careerAction === 'down' && row.nextElementSibling) careerList.insertBefore(row.nextElementSibling, row);
    numberCareers();
  });
  if (!careerList.children.length) for (let index = 0; index < 5; index += 1) addCareer(false);
  numberCareers();

  function syncPublicationRequirements() {
    document.getElementById('instructorName').required = document.getElementById('lectureStatus').value === 'PUBLISHED';
    document.querySelectorAll('#instructorCareerList input').forEach(function (input) { input.setCustomValidity(''); });
  }
  document.getElementById('lectureStatus').addEventListener('change', syncPublicationRequirements);
  syncPublicationRequirements();

  function renderSchedule() {
    const start = document.getElementById('enrollmentStartsAt');
    const end = document.getElementById('enrollmentEndsAt');
    const lectureStart = document.getElementById('lectureStartsAt').value;
    const days = document.getElementById('viewingDays').value;
    end.setCustomValidity(Boolean(start.value) !== Boolean(end.value) || (start.value && end.value && start.value >= end.value) ? '모집 시작과 마감을 모두 입력하고, 마감을 시작 이후로 지정해주세요.' : '');
    const format = function (value) { return value.replace('T', ' ').replace(/-/g, '.'); };
    const enrollment = !start.value && !end.value ? '상시 모집' : (start.value ? format(start.value) : '상시') + ' ~ ' + (end.value ? format(end.value) : '마감 제한 없음');
    document.getElementById('schedulePreview').textContent = '모집: ' + enrollment + ' · 강의 시작: ' + (lectureStart ? format(lectureStart) : '신청 후 바로') + ' · 수강기간: ' + (days ? days + '일 (신청일과 강의 시작 중 늦은 날부터)' : '무제한');
  }
  ['enrollmentStartsAt', 'enrollmentEndsAt', 'lectureStartsAt', 'viewingDays'].forEach(function (id) { document.getElementById(id).addEventListener('input', renderSchedule); });
  renderSchedule();
  renderInstructorImages();

  const initiallyCheckedThumbnail = document.querySelector('[data-existing-image] [data-thumbnail-choice]:checked');
  if (initiallyCheckedThumbnail) {
    thumbnailKey = initiallyCheckedThumbnail.getAttribute('data-thumbnail-key');
  }
  bindExistingImageControls();
  lastActiveImageCount = getActiveImageKeys().length;
  thumbnailAutomatic = lastActiveImageCount === 1;
  renderProcessedImages();
  syncImageState({ initial: true });
})();
