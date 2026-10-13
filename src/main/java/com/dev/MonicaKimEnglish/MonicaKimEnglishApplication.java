package com.dev.MonicaKimEnglish;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MonicaKimEnglishApplication {

	public static void main(String[] args) {
		// 모집·수강 일정의 입력, 표시, 서버 판정 기준을 대한민국 표준시로 통일합니다.
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(MonicaKimEnglishApplication.class, args);
	}

}
