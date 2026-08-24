package com.dev.MonicaKimEnglish.service.storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 강의 영상의 서버측 검증/웹 표준화 전용 서비스입니다.
 *
 * 업로드 원본의 확장자에 의존하지 않고 ffprobe로 실제 비디오 스트림을 확인한 뒤,
 * 고객에게 제공되는 파일은 항상 MP4 컨테이너로 정규화합니다.
 *
 * - H.264 + yuv420p + AAC(또는 무음) : 재인코딩 없이 remux하여 원본 화질을 100% 유지
 * - H.264이지만 오디오만 비호환 : 영상 copy + 오디오 AAC 변환
 * - HEVC/VP9/AV1/ProRes/기타 : 원본 해상도/프레임을 유지하고 H.264 CRF 18로 변환
 * - 모든 결과 : +faststart, metadata 제거, yuv420p(재인코딩 시), MP4
 */
@Service
@RequiredArgsConstructor
public class FfmpegService {
    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);

    private static final Duration PROBE_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration CONVERT_TIMEOUT = Duration.ofHours(12);
    private static final int LOG_TAIL_LINES = 35;

    private final StorageProperties props;
    private final FileStorageService storage;

    /**
     * 기존 호출부와의 호환용입니다. 이제 "선택 최적화"가 아니라 항상 웹 표준화합니다.
     */
    public String optimizeIfEnabled(String relativeInput) {
        return normalizeForWeb(relativeInput).relativePath();
    }

    /**
     * 원본을 검사하고 브라우저 공통 재생에 적합한 MP4(H.264/AAC)로 정규화합니다.
     * 성공한 경우 원본 업로드 파일은 삭제되고 결과 파일만 남습니다.
     * 실패한 경우 원본을 조용히 살려서 등록하지 않고 예외를 발생시킵니다.
     */
    public NormalizedVideo normalizeForWeb(String relativeInput) {
        requireEnabled();
        requireToolAvailable(props.getFfprobePath(), "FFprobe");
        requireToolAvailable(props.getFfmpegPath(), "FFmpeg");

        Path input = storage.resolve(relativeInput);
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("업로드한 영상 파일을 찾을 수 없습니다.");
        }

        VideoProbe source = probe(input);
        if (!source.hasVideo()) {
            throw new IllegalArgumentException("선택한 파일에서 재생 가능한 영상 스트림을 찾지 못했습니다.");
        }

        String fileName = input.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path output = input.resolveSibling(baseName + "_web.mp4");

        try {
            Files.deleteIfExists(output);
        } catch (IOException e) {
            throw new IllegalStateException("영상 변환용 임시 파일을 준비하지 못했습니다.", e);
        }

        boolean videoCopy = source.isWebCompatibleVideo();
        boolean audioCopy = !source.hasAudio() || source.isWebCompatibleAudio();
        List<String> command = buildNormalizationCommand(input, output, source, videoCopy, audioCopy);

        ProcessResult result = run(command, CONVERT_TIMEOUT);
        if (!result.success() || !Files.isRegularFile(output)) {
            deleteQuietly(output);
            String detail = result.tail().isBlank() ? "FFmpeg 실행 결과를 확인할 수 없습니다." : result.tail();
            log.warn("Lecture video normalization failed. input={}, exit={}, tail={} ", relativeInput, result.exitCode(), detail);
            throw new IllegalStateException(
                    "영상 자동 최적화에 실패했습니다. 파일이 손상되었거나 서버 FFmpeg가 지원하지 않는 코덱일 수 있습니다.");
        }

        VideoProbe normalized = probe(output);
        if (!normalized.hasVideo()
                || !"h264".equalsIgnoreCase(normalized.videoCodec())
                || (normalized.hasAudio() && !"aac".equalsIgnoreCase(normalized.audioCodec()))) {
            deleteQuietly(output);
            throw new IllegalStateException("영상 최적화 결과를 검증하지 못했습니다. 서버 FFmpeg 설정을 확인해주세요.");
        }

        try {
            long size = Files.size(output);
            if (size <= 0) {
                throw new IllegalStateException("영상 최적화 결과 파일의 크기가 올바르지 않습니다.");
            }

            // DB 트랜잭션 롤백 시 결과 파일 정리는 호출부 TransactionFileCleanup이 담당합니다.
            // 여기에서는 정상 변환이 끝난 원본만 제거하여 서버에 중복 대용량 파일이 남지 않게 합니다.
            Files.deleteIfExists(input);

            String relativeOutput = storage.relative(output);
            boolean transcoded = !videoCopy || (source.hasAudio() && !audioCopy);
            log.info(
                    "Lecture video normalized. source={} {}x{} v={} a={} -> {} {}x{} duration={}s mode={}",
                    relativeInput,
                    source.width(),
                    source.height(),
                    source.videoCodec(),
                    source.audioCodec(),
                    relativeOutput,
                    normalized.width(),
                    normalized.height(),
                    normalized.durationSeconds(),
                    transcoded ? "transcode" : "remux");

            return new NormalizedVideo(
                    relativeOutput,
                    Math.max(0, normalized.durationSeconds()),
                    size,
                    normalized.width(),
                    normalized.height(),
                    normalized.videoCodec(),
                    normalized.audioCodec(),
                    transcoded);
        } catch (IOException e) {
            deleteQuietly(output);
            throw new IllegalStateException("최적화된 영상 파일 정보를 확인하지 못했습니다.", e);
        }
    }

    /**
     * 저장된 영상의 duration을 서버에서 읽습니다. 브라우저가 넘긴 duration 값은 신뢰하지 않습니다.
     */
    public long readDurationSeconds(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return 0;
        }
        try {
            requireEnabled();
            return Math.max(0, probe(storage.resolve(relativePath)).durationSeconds());
        } catch (Exception e) {
            log.debug("Could not read lecture video duration: {}", e.getMessage());
            return 0;
        }
    }

    /** 관리자 화면/헬스체크 등에서 사용할 수 있는 가벼운 실행 가능 여부 확인입니다. */
    public boolean isAvailable() {
        if (!props.isFfmpegEnabled()) {
            return false;
        }
        try {
            return toolAvailable(props.getFfmpegPath()) && toolAvailable(props.getFfprobePath());
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> buildNormalizationCommand(
            Path input,
            Path output,
            VideoProbe source,
            boolean videoCopy,
            boolean audioCopy) {

        List<String> cmd = new ArrayList<>();
        cmd.add(props.getFfmpegPath());
        cmd.add("-hide_banner");
        cmd.add("-nostdin");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(input.toString());

        // 첫 비디오/오디오 스트림만 사용하고 자막/데이터 스트림은 MP4 결과에서 제외합니다.
        cmd.add("-map");
        cmd.add("0:v:0");
        cmd.add("-map");
        cmd.add("0:a:0?");
        cmd.add("-sn");
        cmd.add("-dn");
        cmd.add("-map_metadata");
        cmd.add("-1");
        cmd.add("-map_chapters");
        cmd.add("-1");

        if (videoCopy) {
            cmd.add("-c:v");
            cmd.add("copy");
        } else {
            // 해상도와 프레임레이트는 변경하지 않습니다. CRF 18은 강의용으로 원본에 가까운 고화질을 목표로 합니다.
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("medium");
            cmd.add("-crf");
            cmd.add("18");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
        }

        if (source.hasAudio()) {
            if (audioCopy) {
                cmd.add("-c:a");
                cmd.add("copy");
            } else {
                cmd.add("-c:a");
                cmd.add("aac");
                cmd.add("-b:a");
                cmd.add("192k");
            }
        } else {
            cmd.add("-an");
        }

        cmd.add("-max_muxing_queue_size");
        cmd.add("4096");
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add(output.toString());
        return cmd;
    }

    private VideoProbe probe(Path input) {
        Map<String, String> video = probeStream(input, "v:0", "codec_name,width,height,pix_fmt");
        Map<String, String> audio = probeStream(input, "a:0", "codec_name");
        long duration = probeDuration(input);

        String videoCodec = clean(video.get("codec_name"));
        String audioCodec = clean(audio.get("codec_name"));
        String pixelFormat = clean(video.get("pix_fmt"));
        int width = parseInt(video.get("width"));
        int height = parseInt(video.get("height"));

        return new VideoProbe(
                videoCodec,
                audioCodec,
                pixelFormat,
                width,
                height,
                duration);
    }

    private Map<String, String> probeStream(Path input, String selector, String entries) {
        List<String> cmd = List.of(
                props.getFfprobePath(),
                "-v", "error",
                "-select_streams", selector,
                "-show_entries", "stream=" + entries,
                "-of", "default=noprint_wrappers=1",
                input.toString());

        ProcessResult result = run(cmd, PROBE_TIMEOUT);
        if (!result.success()) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String line : result.outputLines()) {
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            values.put(line.substring(0, equals).trim(), line.substring(equals + 1).trim());
        }
        return values;
    }

    private long probeDuration(Path input) {
        List<String> cmd = List.of(
                props.getFfprobePath(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                input.toString());

        ProcessResult result = run(cmd, PROBE_TIMEOUT);
        if (!result.success()) {
            return 0;
        }
        for (String line : result.outputLines()) {
            try {
                double seconds = Double.parseDouble(line.trim());
                if (Double.isFinite(seconds) && seconds > 0) {
                    return Math.max(1, Math.round(seconds));
                }
            } catch (Exception ignored) {
                // 다음 출력 줄을 확인합니다.
            }
        }
        return 0;
    }

    private void requireEnabled() {
        if (!props.isFfmpegEnabled()) {
            throw new IllegalStateException(
                    "영상 자동 최적화가 비활성화되어 있습니다. app.storage.ffmpeg-enabled=true 설정이 필요합니다.");
        }
    }

    private void requireToolAvailable(String executable, String label) {
        if (!toolAvailable(executable)) {
            throw new IllegalStateException(
                    label + " 실행 파일을 찾을 수 없습니다. 서버 PATH 또는 app.storage 설정을 확인해주세요.");
        }
    }

    private boolean toolAvailable(String executable) {
        ProcessResult result = run(List.of(executable, "-version"), Duration.ofSeconds(10));
        return result.success();
    }

    private ProcessResult run(List<String> command, Duration timeout) {
        Process process = null;
        ProcessOutputCollector collector = new ProcessOutputCollector();
        Thread readerThread = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            final Process runningProcess = process;
            readerThread = new Thread(
                    () -> collector.collect(runningProcess),
                    "monica-ffmpeg-output");
            readerThread.setDaemon(true);
            readerThread.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            }

            if (readerThread != null) {
                readerThread.join(5000);
            }

            if (!finished) {
                return new ProcessResult(false, -1, collector.outputLines(), collector.tail());
            }

            int exit = process.exitValue();
            return new ProcessResult(exit == 0, exit, collector.outputLines(), collector.tail());
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new ProcessResult(false, -1, collector.outputLines(),
                    collector.tail().isBlank()
                            ? e.getClass().getSimpleName() + ": " + e.getMessage()
                            : collector.tail());
        }
    }

    private static final class ProcessOutputCollector {
        private final Deque<String> tail = new ArrayDeque<>();
        private final List<String> outputLines = new ArrayList<>();

        private void collect(Process process) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputLines.size() < 200) {
                        outputLines.add(line);
                    }
                    tail.addLast(line);
                    while (tail.size() > LOG_TAIL_LINES) {
                        tail.removeFirst();
                    }
                }
            } catch (IOException ignored) {
                // 프로세스 종료/강제 종료 시 스트림이 닫히는 것은 정상입니다.
            }
        }

        private List<String> outputLines() {
            return List.copyOf(outputLines);
        }

        private String tail() {
            return String.join(" | ", tail);
        }
    }

    private int parseInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value == null ? "0" : value.trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 후속 오류를 가리지 않습니다.
        }
    }

    public record NormalizedVideo(
            String relativePath,
            long durationSeconds,
            long fileSizeBytes,
            int width,
            int height,
            String videoCodec,
            String audioCodec,
            boolean transcoded) {
    }

    private record VideoProbe(
            String videoCodec,
            String audioCodec,
            String pixelFormat,
            int width,
            int height,
            long durationSeconds) {

        boolean hasVideo() {
            return videoCodec != null && !videoCodec.isBlank() && width > 0 && height > 0;
        }

        boolean hasAudio() {
            return audioCodec != null && !audioCodec.isBlank();
        }

        boolean isWebCompatibleVideo() {
            if (!"h264".equalsIgnoreCase(videoCodec)) {
                return false;
            }
            // yuv420p가 가장 폭넓은 브라우저/하드웨어 디코더 호환성을 가집니다.
            return "yuv420p".equalsIgnoreCase(pixelFormat)
                    || "yuvj420p".equalsIgnoreCase(pixelFormat);
        }

        boolean isWebCompatibleAudio() {
            return "aac".equalsIgnoreCase(audioCodec);
        }
    }

    private record ProcessResult(
            boolean success,
            int exitCode,
            List<String> outputLines,
            String tail) {
    }
}
