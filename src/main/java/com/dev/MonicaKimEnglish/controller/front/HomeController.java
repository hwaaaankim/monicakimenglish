package com.dev.MonicaKimEnglish.controller.front;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	@GetMapping({ "/index", "/" })
	public String index() {
		return "front/index";
	}

	@GetMapping("/program")
	public String program() {
		return "front/program";
	}

	@GetMapping("/member")
	public String member() {
		return "front/member";
	}

	@GetMapping("/recruit")
	public String recruit() {
		return "front/recruit";
	}

	@GetMapping("/contact")
	public String contact() {
		return "front/contact";
	}

	@GetMapping("/client")
	public String client() {
		return "front/client";
	}

}
