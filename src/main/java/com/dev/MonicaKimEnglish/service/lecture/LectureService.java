package com.dev.MonicaKimEnglish.service.lecture;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.MonicaKimEnglish.dto.lecture.LectureCardDto;
import com.dev.MonicaKimEnglish.model.enums.LectureStatus;
import com.dev.MonicaKimEnglish.model.enums.PaymentStatus;
import com.dev.MonicaKimEnglish.model.enums.PricingType;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;
import com.dev.MonicaKimEnglish.model.lecture.Lecture;
import com.dev.MonicaKimEnglish.model.lecture.LectureAttachment;
import com.dev.MonicaKimEnglish.model.lecture.LectureIntroImage;
import com.dev.MonicaKimEnglish.model.lecture.LectureTag;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.repository.learning.LectureProgressRepository;
import com.dev.MonicaKimEnglish.repository.learning.WishlistRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureAttachmentRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureIntroImageRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureTagRepository;
import com.dev.MonicaKimEnglish.repository.payment.PaymentRepository;
import com.dev.MonicaKimEnglish.service.storage.FfmpegService;
import com.dev.MonicaKimEnglish.service.storage.FileStorageService;
import com.dev.MonicaKimEnglish.service.storage.TransactionFileCleanup;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureService {
    private final LectureRepository lectures;
    private final LectureTagRepository tags;
    private final LectureIntroImageRepository images;
    private final LectureAttachmentRepository attachments;
    private final EnrollmentRepository enrollments;
    private final WishlistRepository wishlists;
    private final LectureProgressRepository progresses;
    private final PaymentRepository payments;
    private final FileStorageService storage;
    private final FfmpegService ffmpeg;
    private final TransactionFileCleanup fileCleanup;

    public Lecture get(Long id) {
        return lectures.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다."));
    }

    public Lecture getPublic(Long id) {
        Lecture lecture = get(id);
        if (lecture.getStatus() != LectureStatus.PUBLISHED) {
            throw new IllegalArgumentException("현재 공개되지 않은 강의입니다.");
        }
        return lecture;
    }

    public List<LectureCardDto> searchPublic(String q, String tag, String pricing, String ownership, Long clientId) {
        Specification<Lecture> spec = (root, query, cb) -> cb.equal(root.get("status"), LectureStatus.PUBLISHED);

        if (q != null && !q.isBlank()) {
            String keyword = q.trim();
            Set<Long> keywordIds = new HashSet<>(lectures.findPublishedIdsByKeyword(keyword));
            spec = spec.and((root, query, cb) ->
                    keywordIds.isEmpty() ? cb.disjunction() : root.get("id").in(keywordIds));
        }

        if (pricing != null && !pricing.equalsIgnoreCase("ALL")) {
            try {
                PricingType pricingType = PricingType.valueOf(pricing.toUpperCase());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("pricingType"), pricingType));
            } catch (Exception ignored) {
                // 잘못된 검색 파라미터는 전체 가격 조건으로 처리합니다.
            }
        }

        // LectureTag는 별도 엔티티이므로 태그명으로 강의 ID를 먼저 제한합니다.
        if (tag != null && !tag.isBlank()) {
            Set<Long> ids = new HashSet<>(tags.findLectureIdsByName(tag.trim()));
            spec = spec.and((root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids));
        }

        List<LectureCardDto> rows = lectures
                .findAll(spec, Sort.by(Sort.Direction.DESC, "publishedAt", "createdAt"))
                .stream()
                .map(lecture -> toCard(lecture, clientId))
                .toList();

        if (clientId != null && ownership != null && !ownership.equalsIgnoreCase("ALL")) {
            boolean purchased = ownership.equalsIgnoreCase("PURCHASED");
            rows = rows.stream().filter(row -> purchased == row.owned()).toList();
        }
        return rows;
    }

    public List<String> publicTagNames() {
        return tags.findPublishedTagNames();
    }

    public LectureCardDto toCard(Lecture lecture, Long clientId) {
        List<String> tagNames = tags.findByLectureIdOrderByNameAsc(lecture.getId())
                .stream()
                .map(LectureTag::getName)
                .toList();

        // display_order=0인 첫 번째 이미지를 썸네일로 사용합니다.
        // 관리자에서 선택한 썸네일은 applyThumbnailSelection()이 항상 첫 순서로 재정렬합니다.
        Long thumbnail = images.findFirstByLectureIdOrderByDisplayOrderAsc(lecture.getId())
                .map(LectureIntroImage::getId)
                .orElse(null);

        boolean owned = false;
        boolean wished = false;
        boolean completed = false;
        boolean available = false;
        int percent = 0;
        LocalDateTime expires = null;

        if (clientId != null) {
            List<Enrollment> history = enrollments.findHistory(clientId, lecture.getId());
            List<Enrollment> usable = enrollments.findUsable(clientId, lecture.getId(), LocalDateTime.now());
            owned = !history.isEmpty();
            available = !usable.isEmpty();

            if (available) {
                expires = usable.get(0).getExpiresAt();
            } else if (owned) {
                expires = history.get(0).getExpiresAt();
            }

            wished = wishlists.existsByClientIdAndLectureId(clientId, lecture.getId());
            var progress = progresses.findByClientIdAndLectureId(clientId, lecture.getId()).orElse(null);
            if (progress != null) {
                percent = progress.getProgressPercent();
                completed = progress.isCompleted();
            }
        }

        return new LectureCardDto(
                lecture.getId(),
                lecture.getTitle(),
                lecture.getDescription(),
                lecture.getPricingType(),
                lecture.getPrice(),
                lecture.getCreatedAt(),
                lecture.getDurationSeconds(),
                lecture.getFileSizeBytes(),
                tagNames,
                thumbnail,
                owned,
                wished,
                percent,
                completed,
                expires,
                available);
    }

    public List<LectureIntroImage> introImages(Long lectureId) {
        return images.findByLectureIdOrderByDisplayOrderAsc(lectureId);
    }

    public List<LectureAttachment> attachmentList(Long lectureId) {
        return attachments.findByLectureIdOrderByIdAsc(lectureId);
    }

    public List<String> tagList(Long lectureId) {
        return tags.findByLectureIdOrderByNameAsc(lectureId)
                .stream()
                .map(LectureTag::getName)
                .toList();
    }

    @Transactional
    public Lecture create(
            String title,
            String description,
            PricingType pricingType,
            BigDecimal price,
            Integer viewingDays,
            LectureStatus status,
            String tagText,
            MultipartFile video,
            MultipartFile[] introFiles,
            String thumbnailSelection,
            MultipartFile[] attachmentFiles) {

        if (video == null || video.isEmpty()) {
            throw new IllegalArgumentException("영상 파일은 필수입니다.");
        }
        if (countNonEmptyFiles(introFiles) < 1) {
            throw new IllegalArgumentException("소개 이미지는 1장 이상 반드시 등록해야 합니다.");
        }

        validatePricing(pricingType, price);

        Lecture lecture = new Lecture();
        applyMeta(lecture, title, description, pricingType, price, viewingDays, status);
        lectures.save(lecture);

        applyNormalizedVideo(lecture, video, null);

        saveTags(lecture, tagText);
        List<LectureIntroImage> savedIntroImages = saveIntroFiles(lecture, introFiles, 0);
        applyThumbnailSelection(lecture.getId(), savedIntroImages, thumbnailSelection);
        saveAttachments(lecture, attachmentFiles);
        return lecture;
    }

    @Transactional
    public Lecture update(
            Long id,
            String title,
            String description,
            PricingType pricingType,
            BigDecimal price,
            Integer viewingDays,
            LectureStatus status,
            String tagText,
            MultipartFile replacementVideo,
            MultipartFile[] newIntroFiles,
            String thumbnailSelection,
            MultipartFile[] newAttachmentFiles,
            List<Long> deleteImageIds,
            List<Long> deleteAttachmentIds) {

        Lecture lecture = get(id);
        validatePricing(pricingType, price);
        applyMeta(lecture, title, description, pricingType, price, viewingDays, status);

        // 기존 태그 삭제와 신규 태그 INSERT가 같은 트랜잭션에서 실행될 때
        // IDENTITY 기반 신규 INSERT가 먼저 실행되면 uk_lecture_tag(lecture_id, name)에
        // 기존 행이 아직 남아 있어 Duplicate entry가 발생할 수 있습니다.
        // 삭제를 즉시 flush한 뒤 신규 태그를 저장하여 실행 순서를 확정합니다.
        tags.deleteByLectureId(id);
        tags.flush();
        saveTags(lecture, tagText);

        if (replacementVideo != null && !replacementVideo.isEmpty()) {
            String oldPath = lecture.getVideoPath();
            applyNormalizedVideo(lecture, replacementVideo, oldPath);
        }

        if (deleteImageIds != null) {
            for (Long imageId : deleteImageIds) {
                images.findById(imageId)
                        .filter(image -> image.getLecture().getId().equals(id))
                        .ifPresent(image -> {
                            String oldPath = image.getFilePath();
                            images.delete(image);
                            fileCleanup.deleteAfterCommit(oldPath);
                        });
            }
            // 이후 남은 이미지 수와 display_order를 정확히 계산하기 위해 삭제를 먼저 반영합니다.
            images.flush();
        }

        if (deleteAttachmentIds != null) {
            for (Long attachmentId : deleteAttachmentIds) {
                attachments.findById(attachmentId)
                        .filter(attachment -> attachment.getLecture().getId().equals(id))
                        .ifPresent(attachment -> {
                            String oldPath = attachment.getFilePath();
                            attachments.delete(attachment);
                            fileCleanup.deleteAfterCommit(oldPath);
                        });
            }
        }

        int startOrder = images.findByLectureIdOrderByDisplayOrderAsc(id).size();
        List<LectureIntroImage> savedNewImages = saveIntroFiles(lecture, newIntroFiles, startOrder);
        applyThumbnailSelection(id, savedNewImages, thumbnailSelection);
        saveAttachments(lecture, newAttachmentFiles);
        return lecture;
    }

    @Transactional
    public void archive(Long id) {
        Lecture lecture = get(id);
        lecture.setStatus(LectureStatus.ARCHIVED);
        lecture.setArchivedAt(LocalDateTime.now());
    }

    public long purchaseCount(Long lectureId) {
        return payments.countByLectureIdAndStatus(lectureId, PaymentStatus.PAID);
    }

    /**
     * 업로드 원본은 저장 직후 rollback 정리 대상으로 등록하고,
     * FFmpeg/FFprobe로 실제 영상 스트림을 검증한 뒤 웹 표준 MP4로 자동 정규화합니다.
     * 변환 성공 전에는 기존 강의 영상을 삭제하지 않습니다.
     */
    private void applyNormalizedVideo(Lecture lecture, MultipartFile sourceFile, String oldVideoPath) {
        if (sourceFile == null || sourceFile.isEmpty()) {
            throw new IllegalArgumentException("영상 파일을 선택해주세요.");
        }

        var stored = storage.store(sourceFile, "lectures/" + lecture.getId() + "/video");
        fileCleanup.deleteOnRollback(stored.relativePath());

        FfmpegService.NormalizedVideo normalized = ffmpeg.normalizeForWeb(stored.relativePath());
        fileCleanup.deleteOnRollback(normalized.relativePath());

        lecture.setVideoPath(normalized.relativePath());
        lecture.setVideoOriginalName(stored.originalName());
        lecture.setVideoMimeType("video/mp4");
        lecture.setFileSizeBytes(normalized.fileSizeBytes());
        lecture.setDurationSeconds(normalized.durationSeconds());

        if (lecture.getDurationSeconds() <= 0) {
            throw new IllegalStateException("최적화된 영상의 재생시간을 확인하지 못했습니다.");
        }

        if (oldVideoPath != null
                && !oldVideoPath.isBlank()
                && !oldVideoPath.equals(normalized.relativePath())) {
            fileCleanup.deleteAfterCommit(oldVideoPath);
        }
    }

    private void applyMeta(
            Lecture lecture,
            String title,
            String description,
            PricingType pricingType,
            BigDecimal price,
            Integer viewingDays,
            LectureStatus status) {

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("영상 제목은 필수입니다.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("영상 설명은 필수입니다.");
        }
        if (viewingDays != null && viewingDays <= 0) {
            throw new IllegalArgumentException("시청 가능일은 1일 이상이거나 비워주세요.");
        }

        lecture.setTitle(title.trim());
        lecture.setDescription(description.trim());
        lecture.setPricingType(pricingType);
        lecture.setPrice(pricingType == PricingType.FREE ? BigDecimal.ZERO : price);
        lecture.setViewingDays(viewingDays);

        if (status == LectureStatus.PUBLISHED && lecture.getPublishedAt() == null) {
            lecture.setPublishedAt(LocalDateTime.now());
        }
        if (status != LectureStatus.ARCHIVED) {
            lecture.setArchivedAt(null);
        }
        lecture.setStatus(status);
    }

    private void validatePricing(PricingType type, BigDecimal price) {
        if (type == null) {
            throw new IllegalArgumentException("무료/유료 구분을 선택해주세요.");
        }
        if (type == PricingType.PAID && (price == null || price.signum() <= 0)) {
            throw new IllegalArgumentException("유료 영상은 0원보다 큰 가격이 필요합니다.");
        }
    }

    private void saveTags(Lecture lecture, String tagText) {
        if (tagText == null) {
            return;
        }

        Set<String> normalizedNames = new HashSet<>();

        Arrays.stream(tagText.split("[,#\\n]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.length() > 80 ? value.substring(0, 80) : value)
                // MariaDB의 일반적인 *_ci collation은 대소문자를 구분하지 않으므로
                // Java의 단순 distinct()보다 DB unique 제약과 동일한 방향으로 중복을 제거합니다.
                .filter(value -> normalizedNames.add(value.toLowerCase(java.util.Locale.ROOT)))
                .limit(20)
                .forEach(name -> {
                    LectureTag tag = new LectureTag();
                    tag.setLecture(lecture);
                    tag.setName(name);
                    tags.save(tag);
                });
    }

    private List<LectureIntroImage> saveIntroFiles(Lecture lecture, MultipartFile[] files, int startOrder) {
        List<LectureIntroImage> saved = new ArrayList<>();
        if (files == null) {
            return saved;
        }

        int order = startOrder;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String type = file.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw new IllegalArgumentException("소개 이미지는 이미지 파일만 등록할 수 있습니다.");
            }

            var stored = storage.store(file, "lectures/" + lecture.getId() + "/intro");
            fileCleanup.deleteOnRollback(stored.relativePath());

            LectureIntroImage image = new LectureIntroImage();
            image.setLecture(lecture);
            image.setFilePath(stored.relativePath());
            image.setOriginalName(stored.originalName());
            image.setDisplayOrder(order++);
            saved.add(images.save(image));
        }
        return saved;
    }

    /**
     * 기존 화면/카드가 display_order가 가장 작은 이미지를 썸네일로 사용하고 있으므로
     * 선택된 이미지를 0번 순서로 재배치하여 별도의 DB 컬럼 없이 썸네일을 영속화합니다.
     *
     * thumbnailSelection 형식:
     * - existing:{imageId} : 기존 이미지
     * - new:{zeroBasedIndex} : 이번 요청에서 새로 업로드한 이미지
     */
    private void applyThumbnailSelection(
            Long lectureId,
            List<LectureIntroImage> newlySavedImages,
            String thumbnailSelection) {

        List<LectureIntroImage> allImages = new ArrayList<>(images.findByLectureIdOrderByDisplayOrderAsc(lectureId));
        if (allImages.isEmpty()) {
            throw new IllegalArgumentException("소개 이미지는 1장 이상 반드시 유지해야 합니다.");
        }

        LectureIntroImage selected;
        if (allImages.size() == 1) {
            selected = allImages.get(0);
        } else {
            if (thumbnailSelection == null || thumbnailSelection.isBlank()) {
                throw new IllegalArgumentException("소개 이미지가 2장 이상이면 썸네일 이미지를 1장 선택해야 합니다.");
            }
            selected = resolveThumbnailSelection(allImages, newlySavedImages, thumbnailSelection.trim());
        }

        allImages.sort(Comparator
                .comparingInt(LectureIntroImage::getDisplayOrder)
                .thenComparing(LectureIntroImage::getId, Comparator.nullsLast(Long::compareTo)));
        allImages.removeIf(image -> sameImage(image, selected));
        allImages.add(0, selected);

        for (int index = 0; index < allImages.size(); index++) {
            allImages.get(index).setDisplayOrder(index);
        }
        images.saveAll(allImages);
    }

    private LectureIntroImage resolveThumbnailSelection(
            List<LectureIntroImage> allImages,
            List<LectureIntroImage> newlySavedImages,
            String selection) {

        if (selection.startsWith("existing:")) {
            Long imageId = parseLong(selection.substring("existing:".length()), "썸네일 이미지 선택값이 올바르지 않습니다.");
            return allImages.stream()
                    .filter(image -> Objects.equals(image.getId(), imageId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("선택한 썸네일 이미지가 현재 강의 이미지에 존재하지 않습니다."));
        }

        if (selection.startsWith("new:")) {
            int newIndex = parseInt(selection.substring("new:".length()), "썸네일 이미지 선택값이 올바르지 않습니다.");
            if (newIndex < 0 || newIndex >= newlySavedImages.size()) {
                throw new IllegalArgumentException("선택한 새 썸네일 이미지 순서를 확인해주세요.");
            }
            LectureIntroImage selected = newlySavedImages.get(newIndex);
            boolean belongsToLecture = allImages.stream().anyMatch(image -> sameImage(image, selected));
            if (!belongsToLecture) {
                throw new IllegalArgumentException("선택한 썸네일 이미지가 현재 강의 이미지에 존재하지 않습니다.");
            }
            return selected;
        }

        throw new IllegalArgumentException("썸네일 이미지 선택 형식을 확인해주세요.");
    }

    private boolean sameImage(LectureIntroImage left, LectureIntroImage right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.getId() != null && Objects.equals(left.getId(), right.getId());
    }

    private Long parseLong(String value, String message) {
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }

    private int parseInt(String value, String message) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
    }

    private int countNonEmptyFiles(MultipartFile[] files) {
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private void saveAttachments(Lecture lecture, MultipartFile[] files) {
        if (files == null) {
            return;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            var stored = storage.store(file, "lectures/" + lecture.getId() + "/attachments");
            fileCleanup.deleteOnRollback(stored.relativePath());

            LectureAttachment attachment = new LectureAttachment();
            attachment.setLecture(lecture);
            attachment.setFilePath(stored.relativePath());
            attachment.setOriginalName(stored.originalName());
            attachment.setContentType(stored.contentType());
            attachment.setFileSizeBytes(stored.size());
            attachments.save(attachment);
        }
    }
}
