package com.example.contentfilter.dto;

public record ClassificationResponse(String category, double confidence, String timestamp) {
    public ClassificationResponse categoryLower() {
        return new ClassificationResponse(category.toLowerCase(), confidence, timestamp);
    }
}
