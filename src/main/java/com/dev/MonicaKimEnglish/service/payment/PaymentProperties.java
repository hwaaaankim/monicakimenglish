package com.dev.MonicaKimEnglish.service.payment;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.payment")
@Getter
@Setter
public class PaymentProperties {
    public enum Mode { MOCK, TOSS }
    private Mode mode = Mode.MOCK;
    private String clientKey = "";
    private String secretKey = "";
    private String publicBaseUrl = "http://localhost:8080";

    public boolean isMock() { return mode == Mode.MOCK; }
    public boolean isTestKey() { return clientKey.startsWith("test_ck_"); }
    public String provider() { return isMock() ? "MOCK" : isTestKey() ? "TOSS_TEST" : "TOSS"; }

    public void requireTossConfigured() {
        if (mode != Mode.TOSS) throw new IllegalArgumentException("실제 결제 연동이 활성화되어 있지 않습니다.");
        boolean test = clientKey.startsWith("test_ck_") && secretKey.startsWith("test_sk_");
        boolean live = clientKey.startsWith("live_ck_") && secretKey.startsWith("live_sk_");
        if (!test && !live) throw new IllegalArgumentException("결제 서비스 설정을 확인해 주세요. 고객센터에 문의해 주세요.");
        URI uri;
        try { uri = URI.create(publicBaseUrl); }
        catch (RuntimeException e) { throw new IllegalArgumentException("결제 주소 설정을 확인해 주세요."); }
        if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null
                || (!"https".equals(uri.getScheme()) && !(test && "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))))) {
            throw new IllegalArgumentException("결제 주소는 HTTPS로 설정해 주세요.");
        }
    }

    public String callbackUrl(String path) {
        requireTossConfigured();
        return publicBaseUrl.replaceAll("/+$", "") + path;
    }
}
