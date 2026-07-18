package com.example.contentfilter.domain;

/**
 * Optional conversation metadata retained alongside a message but never joined
 * into the text sent to a classifier.
 */
public record ConversationContext(
		String conversationId,
		String messageId,
		String speakerRole,
		String timestamp,
		String language,
		String channel) {

	private static final ConversationContext EMPTY =
			new ConversationContext(null, null, null, null, null, null);

	public ConversationContext {
		conversationId = normalize(conversationId);
		messageId = normalize(messageId);
		speakerRole = normalize(speakerRole);
		timestamp = normalize(timestamp);
		language = normalize(language);
		channel = normalize(channel);
	}

	public static ConversationContext empty() {
		return EMPTY;
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
