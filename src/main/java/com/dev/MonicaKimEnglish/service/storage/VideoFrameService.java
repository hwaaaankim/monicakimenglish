package com.dev.MonicaKimEnglish.service.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import com.dev.MonicaKimEnglish.model.lecture.Lecture;
import lombok.RequiredArgsConstructor;

/** 회원 전용 마지막 위치/책갈피 썸네일. 업로드된 HTML/SVG 등의 임의 이미지 입력을 받지 않습니다. */
@Service
@RequiredArgsConstructor
public class VideoFrameService {
    private static final Logger log = LoggerFactory.getLogger(VideoFrameService.class);
    private static final Object[] FRAME_LOCKS = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();
    private final Semaphore encoders = new Semaphore(2);
    private final StorageProperties props;
    private final FileStorageService storage;

    public Resource frame(Lecture lecture, long positionSeconds) {
        if (positionSeconds < 0 || lecture.getDurationSeconds() <= 0 || positionSeconds > lecture.getDurationSeconds()
                || lecture.getVideoPath() == null || lecture.getVideoPath().isBlank()) {
            throw new IllegalArgumentException("영상 썸네일 시간을 확인해주세요.");
        }
        Path input = storage.resolve(lecture.getVideoPath());
        if (!Files.isRegularFile(input)) throw new IllegalArgumentException("영상 파일을 찾을 수 없습니다.");
        try {
            String fingerprint = lecture.getVideoPath() + ":" + Files.size(input) + ":" + Files.getLastModifiedTime(input).toMillis();
            String videoKey = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(StandardCharsets.UTF_8))).substring(0, 20);
            String relative = "lecture-frames/" + lecture.getId() + "/" + videoKey + "/" + positionSeconds + ".jpg";
            Path target = storage.resolve(relative);
            synchronized (FRAME_LOCKS[Math.floorMod(relative.hashCode(), FRAME_LOCKS.length)]) {
                if (!Files.isRegularFile(target) || Files.size(target) == 0) generate(input, target, positionSeconds, lecture.getDurationSeconds());
            }
            return storage.resource(relative);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Could not generate lecture frame. lecture={}, position={}, reason={}", lecture.getId(), positionSeconds, error.getMessage());
            throw new IllegalStateException("영상 썸네일 생성에 실패했습니다. FFmpeg 설정을 확인해주세요.", error);
        }
    }

    private void generate(Path input, Path output, long position, long duration) throws IOException, InterruptedException {
        if (!props.isFfmpegEnabled()) throw new IllegalStateException("영상 썸네일 생성에 FFmpeg가 필요합니다.");
        if (!encoders.tryAcquire(10, TimeUnit.SECONDS)) throw new IllegalStateException("썸네일을 준비 중입니다. 잠시 후 다시 시도해주세요.");
        Path temporary = null;
        Process process = null;
        try {
            Files.createDirectories(output.getParent());
            temporary = Files.createTempFile(output.getParent(), "frame-", ".jpg");
            // duration은 초 단위 반올림값이므로 영상 마지막 요청은 실제 마지막 프레임 안으로 보정합니다.
            double seconds = Math.min(position, Math.max(0, duration - 1.0));
            List<String> command = List.of(props.getFfmpegPath(), "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                    "-ss", String.format(java.util.Locale.ROOT, "%.3f", seconds), "-i", input.toString(), "-an", "-sn", "-dn",
                    "-threads", "1", "-vf", "scale=640:360:force_original_aspect_ratio=decrease,pad=640:360:(ow-iw)/2:(oh-ih)/2:color=0x14223A",
                    "-frames:v", "1", "-q:v", "4", temporary.toString());
            process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0 || Files.size(temporary) == 0) {
                process.destroyForcibly();
                throw new IOException("FFmpeg did not return a video frame");
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            trimCache(output.getParent(), output);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (temporary != null) Files.deleteIfExists(temporary);
            encoders.release();
        }
    }

    /** 많은 회원이 위치를 저장해도 한 영상의 캐시가 무한히 커지지 않도록 오래된 파일을 정리합니다. */
    private void trimCache(Path directory, Path keep) {
        try (var stream = Files.list(directory)) {
            var files = stream.filter(p -> p.getFileName().toString().matches("[0-9]+\\.jpg"))
                    .sorted(java.util.Comparator.comparingLong(this::modifiedAt)).toList();
            for (int i = 0; i < files.size() - 512; i++) {
                if (!files.get(i).equals(keep)) Files.deleteIfExists(files.get(i));
            }
        } catch (IOException ignored) { }
    }

    private long modifiedAt(Path file) {
        try { return Files.getLastModifiedTime(file).toMillis(); }
        catch (IOException ignored) { return 0; }
    }
}
