package com.example.contentfilter.dto;

/**
 * Request payload for classification operations.
 *
 * @param text the text to classify
 */
public record ClassificationRequest(String text) {
}
