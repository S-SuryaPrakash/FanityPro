package com.example.contentfilter.dto;

/**
 * Response returned by the classification service.
 *
 * @param category  predicted category (e.g., "abusive", "professional")
 * @param confidence confidence score between 0.0 and 1.0
 * @param timestamp ISO-8601 timestamp of the classification
 */
public record ClassificationResponse(String category, double confidence, String timestamp) {
    /**
     * Return a copy of this response with the category lower-cased.
     * This keeps the public API consistent.
     */
    public ClassificationResponse categoryLower() {
        return new ClassificationResponse(category.toLowerCase(), confidence, timestamp);
    }
}
