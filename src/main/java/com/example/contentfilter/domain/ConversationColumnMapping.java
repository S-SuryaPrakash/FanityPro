package com.example.contentfilter.domain;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Maps workbook headers to message text and optional conversation metadata.
 * Header matching is case-insensitive and ignores surrounding whitespace.
 */
public record ConversationColumnMapping(
		int headerRowIndex,
		String textHeader,
		String conversationIdHeader,
		String messageIdHeader,
		String speakerRoleHeader,
		String timestampHeader,
		String languageHeader,
		String channelHeader) {

	public ConversationColumnMapping {
		if (headerRowIndex < 0) {
			throw new IllegalArgumentException("Header row index must not be negative.");
		}
		textHeader = requiredHeader(textHeader, "Text");
		conversationIdHeader = optionalHeader(conversationIdHeader);
		messageIdHeader = optionalHeader(messageIdHeader);
		speakerRoleHeader = optionalHeader(speakerRoleHeader);
		timestampHeader = optionalHeader(timestampHeader);
		languageHeader = optionalHeader(languageHeader);
		channelHeader = optionalHeader(channelHeader);

		Set<String> uniqueHeaders = new HashSet<>();
		for (String header : new String[] {textHeader, conversationIdHeader, messageIdHeader,
				speakerRoleHeader, timestampHeader, languageHeader, channelHeader}) {
			if (header != null && !uniqueHeaders.add(normalizedHeader(header))) {
				throw new IllegalArgumentException("Each mapped field must use a different header.");
			}
		}
	}

	/** Mapping for the documented V1 conversation workbook contract. */
	public static ConversationColumnMapping standard() {
		return new ConversationColumnMapping(
				0, "text", "conversation_id", "message_id", "speaker_role",
				"timestamp", "language", "channel");
	}

	public static String normalizedHeader(String value) {
		return value.strip().toLowerCase(Locale.ROOT);
	}

	private static String requiredHeader(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " header must not be blank.");
		}
		return value.strip();
	}

	private static String optionalHeader(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
