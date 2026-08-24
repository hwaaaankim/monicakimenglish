package com.dev.MonicaKimEnglish.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

	@GetMapping("/admin")
	public String admin() {
		return "admin/login";
	}

	@GetMapping("/clientManager")
	public String clientManager() {
		return "admin/clientManager";
	}

	@GetMapping("/clientInsertF")
	public String clientInsertF() {
		return "admin/clientInsertF";
	}

	@GetMapping("/clientDetailF")
	public String clientDetailF() {
		return "admin/clientDetailF";
	}
}