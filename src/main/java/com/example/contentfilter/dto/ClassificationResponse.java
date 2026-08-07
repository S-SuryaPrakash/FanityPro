package com.example.contentfilter.dto;

/**
 * Response returned by the V0 prototype classification service.
 *
 * @deprecated Used only by the legacy {@link com.example.contentfilter.service.LegacyClassificationService} chain.
 * @param category  predicted category (e.g., "abusive", "professional")
 * @param confidence confidence score between 0.0 and 1.0
 * @param timestamp ISO-8601 timestamp of the classification
 */
@Deprecated(forRemoval = true)
public record ClassificationResponse(String category, double confidence, String timestamp) {
	/**
	 * Returns a copy with a normalized lowercase category.
	 *
	 * @return response containing the same metadata and a lowercase category
	 */
	public ClassificationResponse categoryLower() {
		return new ClassificationResponse(category.toLowerCase(), confidence, timestamp);
	}
}
