package com.dev.MonicaKimEnglish.service.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private String root = "./data/files";

    /**
     * 강의 영상은 등록 시 웹 표준 MP4(H.264/AAC)로 자동 정규화합니다.
     * 운영/개발 서버 모두 true 사용을 권장하며, false이면 신규 영상 등록/교체를 실패 처리합니다.
     */
    private boolean ffmpegEnabled = true;
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
}
