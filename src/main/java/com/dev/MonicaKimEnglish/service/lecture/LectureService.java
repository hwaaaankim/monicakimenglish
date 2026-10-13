package com.dev.MonicaKimEnglish.service.lecture;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

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
import com.dev.MonicaKimEnglish.model.lecture.LectureInstructorImage;
import com.dev.MonicaKimEnglish.model.lecture.LectureTag;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.repository.learning.LectureProgressRepository;
import com.dev.MonicaKimEnglish.repository.learning.WishlistRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureAttachmentRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureIntroImageRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureInstructorImageRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureTagRepository;
import com.dev.MonicaKimEnglish.repository.payment.PaymentRepository;
import com.dev.MonicaKimEnglish.service.storage.FfmpegService;
import com.dev.MonicaKimEnglish.service.storage.FileStorageService;
import com.dev.MonicaKimEnglish.service.storage.TransactionFileCleanup;
import com.dev.MonicaKimEnglish.service.learning.EnrollmentFundingPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureService {
    private final LectureRepository lectures;
    private final LectureTagRepository tags;
    private final LectureIntroImageRepository images;
    private final LectureInstructorImageRepository instructorImageRepository;
    private final LectureAttachmentRepository attachments;
    private final EnrollmentRepository enrollments;
    private final WishlistRepository wishlists;
    private final LectureProgressRepository progresses;
    private final PaymentRepository payments;
    private final FileStorageService storage;
    private final FfmpegService ffmpeg;
    private final TransactionFileCleanup fileCleanup;
    private final EnrollmentFundingPolicy fundingPolicy;

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
            List<Enrollment> usable = enrollments.findUsable(clientId, lecture.getId(), LocalDateTime.now()).stream()
                    .filter(fundingPolicy::isEligible).toList();
            owned = !history.isEmpty();
            available = lecture.getStatus() != LectureStatus.DRAFT && !usable.isEmpty();

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

    public List<LectureInstructorImage> instructorImages(Long lectureId) {
        return instructorImageRepository.findByLectureIdOrderByDisplayOrderAsc(lectureId);
    }

    public void requireEnrollmentOpen(Lecture lecture) {
        LocalDateTime now = LocalDateTime.now();
        if (lecture.getStatus() != LectureStatus.PUBLISHED) {
            throw new IllegalArgumentException("현재 신청할 수 없는 강의입니다.");
        }
        if (lecture.getInstructorName() == null || lecture.getInstructorName().isBlank() || lecture.getInstructorCareers().isEmpty()) {
            throw new IllegalArgumentException("강사 정보 보완 중입니다. 관리자에게 문의해주세요.");
        }
        if (lecture.getVideoPath() == null || lecture.getVideoPath().isBlank() || lecture.getDurationSeconds() <= 0) {
            throw new IllegalArgumentException("강의 영상 준비 중입니다. 관리자에게 문의해주세요.");
        }
        validatePricing(lecture.getPricingType(), lecture.getPrice());
        if (lecture.getEnrollmentStartsAt() != null && now.isBefore(lecture.getEnrollmentStartsAt())) {
            throw new IllegalArgumentException("아직 강의 모집이 시작되지 않았습니다.");
        }
        if (lecture.getEnrollmentEndsAt() != null && now.isAfter(lecture.getEnrollmentEndsAt())) {
            throw new IllegalArgumentException("강의 모집기간이 종료되었습니다.");
        }
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
            MultipartFile[] attachmentFiles,
            String introImageOrder,
            String instructorName,
            String instructorBiography,
            List<String> instructorCareers,
            MultipartFile[] instructorFiles,
            String instructorImageOrder,
            LocalDateTime enrollmentStartsAt,
            LocalDateTime enrollmentEndsAt,
            LocalDateTime lectureStartsAt) {

        if (video == null || video.isEmpty()) {
            throw new IllegalArgumentException("영상 파일은 필수입니다.");
        }
        if (countNonEmptyFiles(introFiles) < 1) {
            throw new IllegalArgumentException("소개 이미지는 1장 이상 반드시 등록해야 합니다.");
        }

        validatePricing(pricingType, price);

        Lecture lecture = new Lecture();
        applyMeta(lecture, title, description, pricingType, price, viewingDays, status);
        applyInstructorAndSchedule(lecture, instructorName, instructorBiography, instructorCareers,
                enrollmentStartsAt, enrollmentEndsAt, lectureStartsAt);
        lectures.save(lecture);

        applyNormalizedVideo(lecture, video, null);

        saveTags(lecture, tagText);
        List<LectureIntroImage> savedIntroImages = saveIntroFiles(lecture, introFiles, 0);
        applyIntroImageOrder(lecture.getId(), savedIntroImages, introImageOrder);
        applyThumbnailSelection(lecture.getId(), savedIntroImages, thumbnailSelection);
        List<LectureInstructorImage> savedInstructorImages = saveInstructorFiles(lecture, instructorFiles, 0);
        applyInstructorImageOrder(lecture.getId(), savedInstructorImages, instructorImageOrder);
        validatePublishableAssets(lecture);
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
            List<Long> deleteAttachmentIds,
            String introImageOrder,
            String instructorName,
            String instructorBiography,
            List<String> instructorCareers,
            MultipartFile[] instructorFiles,
            List<Long> deleteInstructorImageIds,
            String instructorImageOrder,
            LocalDateTime enrollmentStartsAt,
            LocalDateTime enrollmentEndsAt,
            LocalDateTime lectureStartsAt) {

        Lecture lecture = get(id);
        validatePricing(pricingType, price);
        applyMeta(lecture, title, description, pricingType, price, viewingDays, status);
        applyInstructorAndSchedule(lecture, instructorName, instructorBiography, instructorCareers,
                enrollmentStartsAt, enrollmentEndsAt, lectureStartsAt);

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

        if (deleteInstructorImageIds != null) {
            for (Long imageId : new LinkedHashSet<>(deleteInstructorImageIds)) {
                instructorImageRepository.findByIdAndLectureId(imageId, id).ifPresent(image -> {
                    instructorImageRepository.delete(image);
                    fileCleanup.deleteAfterCommit(image.getFilePath());
                });
            }
            instructorImageRepository.flush();
        }

        int startOrder = images.findByLectureIdOrderByDisplayOrderAsc(id).size();
        List<LectureIntroImage> savedNewImages = saveIntroFiles(lecture, newIntroFiles, startOrder);
        applyIntroImageOrder(id, savedNewImages, introImageOrder);
        applyThumbnailSelection(id, savedNewImages, thumbnailSelection);
        int instructorStartOrder = instructorImageRepository.findByLectureIdOrderByDisplayOrderAsc(id).size();
        List<LectureInstructorImage> savedInstructorImages = saveInstructorFiles(lecture, instructorFiles, instructorStartOrder);
        applyInstructorImageOrder(id, savedInstructorImages, instructorImageOrder);
        validatePublishableAssets(lecture);
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
        return payments.countByLectureIdAndStatusIn(lectureId, List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED));
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
        if (title.trim().length() > 200) {
            throw new IllegalArgumentException("영상 제목은 200자까지 입력할 수 있습니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("강의 공개 상태를 선택해주세요.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("영상 설명은 필수입니다.");
        }
        if (viewingDays != null && (viewingDays <= 0 || viewingDays > 36500)) {
            throw new IllegalArgumentException("시청 가능일은 1~36,500일이거나 무제한으로 설정해주세요.");
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
        if (type == PricingType.PAID && (price == null || price.compareTo(BigDecimal.valueOf(100)) < 0)) {
            throw new IllegalArgumentException("카드 결제가 가능한 유료 강의의 최소 가격은 100원입니다.");
        }
        if (type == PricingType.PAID && (price.stripTrailingZeros().scale() > 0 || price.precision() - price.scale() > 14)) {
            throw new IllegalArgumentException("가격은 소수점 없는 원 단위로 입력해주세요.");
        }
    }

    private void applyInstructorAndSchedule(
            Lecture lecture, String instructorName, String biography, List<String> careers,
            LocalDateTime enrollmentStartsAt, LocalDateTime enrollmentEndsAt, LocalDateTime lectureStartsAt) {
        String name = instructorName == null ? "" : instructorName.trim();
        String normalizedBiography = biography == null ? "" : biography.trim();
        if (name.length() > 100) throw new IllegalArgumentException("강사 실명은 100자까지 입력할 수 있습니다.");
        if (normalizedBiography.length() > 10000) throw new IllegalArgumentException("강사 약력은 10,000자까지 입력할 수 있습니다.");
        List<String> normalizedCareers = new ArrayList<>();
        if (careers != null) {
            for (String career : careers) {
                if (career == null || career.isBlank()) continue;
                String value = career.trim();
                if (value.length() > 500) throw new IllegalArgumentException("강사 이력은 항목당 500자까지 입력할 수 있습니다.");
                normalizedCareers.add(value);
            }
        }
        if (normalizedCareers.size() > 30) throw new IllegalArgumentException("강사 이력은 최대 30개까지 등록할 수 있습니다.");
        if (lecture.getStatus() == LectureStatus.PUBLISHED && (name.isEmpty() || normalizedCareers.isEmpty())) {
            throw new IllegalArgumentException("강의를 공개하려면 실제 강사 실명과 이력을 1개 이상 입력해주세요.");
        }
        if ((enrollmentStartsAt == null) != (enrollmentEndsAt == null)) {
            throw new IllegalArgumentException("기간 한정 모집은 시작일과 종료일을 모두 입력해주세요. 상시 모집은 둘 다 비워주세요.");
        }
        if (enrollmentStartsAt != null && !enrollmentStartsAt.isBefore(enrollmentEndsAt)) {
            throw new IllegalArgumentException("모집 종료일은 모집 시작일보다 이후여야 합니다.");
        }
        lecture.setInstructorName(name.isEmpty() ? null : name);
        lecture.setInstructorBiography(normalizedBiography.isEmpty() ? null : normalizedBiography);
        lecture.getInstructorCareers().clear();
        lecture.getInstructorCareers().addAll(normalizedCareers);
        lecture.setEnrollmentStartsAt(enrollmentStartsAt);
        lecture.setEnrollmentEndsAt(enrollmentEndsAt);
        lecture.setLectureStartsAt(lectureStartsAt);
    }

    private void validatePublishableAssets(Lecture lecture) {
        if (lecture.getStatus() == LectureStatus.PUBLISHED
                && (lecture.getVideoPath() == null || lecture.getVideoPath().isBlank() || lecture.getDurationSeconds() <= 0)) {
            throw new IllegalArgumentException("강의를 공개하려면 재생 가능한 영상을 등록해주세요.");
        }
        if (instructorImageRepository.findByLectureIdOrderByDisplayOrderAsc(lecture.getId()).size() > 20) {
            throw new IllegalArgumentException("강사 사진은 최대 20장까지 등록할 수 있습니다.");
        }
    }

    private void validateImageFile(MultipartFile file, String label) {
        if (file.getSize() > 15L * 1024 * 1024) {
            throw new IllegalArgumentException(label + "는 장당 15MB 이하로 등록해주세요.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(jpe?g|png|gif)$")) {
            throw new IllegalArgumentException(label + "는 편집 완료된 JPG, PNG, GIF 파일로 등록해주세요.");
        }
        try (var source = file.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(source)) {
            if (input == null) throw new IllegalArgumentException(label + " 파일을 읽을 수 없습니다.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException(label + "의 실제 이미지 형식을 확인해주세요.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 40_000_000L) {
                    throw new IllegalArgumentException(label + " 해상도는 4,000만 화소 이내로 등록해주세요.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(label + "가 손상되었거나 읽을 수 없는 파일입니다.", e);
        }
    }

    private List<LectureInstructorImage> saveInstructorFiles(Lecture lecture, MultipartFile[] files, int startOrder) {
        List<LectureInstructorImage> saved = new ArrayList<>();
        if (files == null) return saved;
        int order = startOrder;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            validateImageFile(file, "강사 사진");
            var stored = storage.store(file, "lectures/" + lecture.getId() + "/instructors");
            fileCleanup.deleteOnRollback(stored.relativePath());
            LectureInstructorImage image = new LectureInstructorImage();
            image.setLecture(lecture);
            image.setFilePath(stored.relativePath());
            image.setOriginalName(stored.originalName());
            image.setDisplayOrder(order++);
            saved.add(instructorImageRepository.save(image));
        }
        return saved;
    }

    private void applyIntroImageOrder(Long lectureId, List<LectureIntroImage> newlySaved, String orderText) {
        List<LectureIntroImage> all = new ArrayList<>(images.findByLectureIdOrderByDisplayOrderAsc(lectureId));
        if (orderText == null || orderText.isBlank()) return;
        List<LectureIntroImage> ordered = new ArrayList<>();
        for (String token : orderText.split(",")) {
            LectureIntroImage image = resolveThumbnailSelection(all, newlySaved, token.trim());
            if (ordered.stream().anyMatch(row -> sameImage(row, image))) {
                throw new IllegalArgumentException("소개 이미지 순서에 같은 이미지가 중복되었습니다.");
            }
            ordered.add(image);
        }
        all.stream().filter(image -> ordered.stream().noneMatch(row -> sameImage(row, image))).forEach(ordered::add);
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setDisplayOrder(index);
        images.saveAll(ordered);
    }

    private void applyInstructorImageOrder(Long lectureId, List<LectureInstructorImage> newlySaved, String orderText) {
        List<LectureInstructorImage> all = new ArrayList<>(instructorImageRepository.findByLectureIdOrderByDisplayOrderAsc(lectureId));
        List<LectureInstructorImage> ordered = new ArrayList<>();
        if (orderText != null && !orderText.isBlank()) {
            for (String value : orderText.split(",")) {
                String token = value.trim();
                LectureInstructorImage selected;
                if (token.startsWith("existing:")) {
                    Long id = parseLong(token.substring("existing:".length()), "강사 사진 순서 형식을 확인해주세요.");
                    selected = all.stream().filter(row -> Objects.equals(row.getId(), id)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("강사 사진 순서에 삭제되었거나 다른 강의의 사진이 있습니다."));
                } else if (token.startsWith("new:")) {
                    int index = parseInt(token.substring("new:".length()), "강사 사진 순서 형식을 확인해주세요.");
                    if (index < 0 || index >= newlySaved.size()) throw new IllegalArgumentException("새 강사 사진 순서를 확인해주세요.");
                    selected = newlySaved.get(index);
                } else {
                    throw new IllegalArgumentException("강사 사진 순서 형식을 확인해주세요.");
                }
                if (ordered.stream().anyMatch(row -> Objects.equals(row.getId(), selected.getId()))) {
                    throw new IllegalArgumentException("강사 사진 순서에 같은 이미지가 중복되었습니다.");
                }
                ordered.add(selected);
            }
        }
        all.stream().filter(image -> ordered.stream().noneMatch(row -> Objects.equals(row.getId(), image.getId()))).forEach(ordered::add);
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setDisplayOrder(index);
        instructorImageRepository.saveAll(ordered);
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

            validateImageFile(file, "소개 이미지");

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
