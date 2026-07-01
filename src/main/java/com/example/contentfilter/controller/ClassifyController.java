package com.example.contentfilter.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClassifyController {

	@PostMapping("/classify")
	public String classify(@RequestBody String text) {
		text = text.toLowerCase();

		if (text.contains("stupid")) {
			return "abusive";
		}

		if (text.contains("idiot")) {
			return "abusive";
		}

		if (text.contains("regards")) {
			return "professional";
		}

		return "neutral";
	}
}
