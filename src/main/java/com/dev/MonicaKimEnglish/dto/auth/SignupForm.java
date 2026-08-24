package com.dev.MonicaKimEnglish.dto.auth;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupForm {
    @NotBlank(message = "아이디를 입력해주세요.")
    @Pattern(regexp = "^[A-Za-z0-9]{5,50}$", message = "아이디는 영문 또는 숫자로 5~50자 입력해주세요.")
    private String username;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[^A-Za-z0-9]).{8,}$", message = "비밀번호는 영문과 특수문자를 포함해 8자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    private String passwordConfirm;

    @NotBlank(message = "성함을 입력해주세요.")
    @Size(max = 60)
    private String name;

    @NotBlank(message = "휴대폰번호를 입력해주세요.")
    @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "휴대폰번호를 정확히 입력해주세요.")
    private String phone;

    // 화면에서는 010-1234-5678 형태로 보이되 서버 검증/저장은 숫자만 사용합니다.
    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.replaceAll("\\D", "");
    }

    @Email(message = "이메일 형식을 확인해주세요.")
    @Size(max = 150)
    private String email;

    @NotNull(message = "생년월일을 입력해주세요.")
    @Past(message = "생년월일을 확인해주세요.")
    private LocalDate birthDate;

    @AssertTrue(message = "개인정보 수집에 동의해주세요.")
    private boolean privacyAgreed;
}
