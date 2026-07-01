package com.example.contentfilter.service;

import org.springframework.stereotype.Service;

@Service
public class ClassificationService {

	public String classify(String text) {
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
