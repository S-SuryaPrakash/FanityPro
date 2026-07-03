package com.example.contentfilter.dto;

public record ClassificationResponse(String category, double confidence) {
    public ClassificationResponse categoryLower() {
        return new ClassificationResponse(category.toLowerCase(), confidence);
    }
}
