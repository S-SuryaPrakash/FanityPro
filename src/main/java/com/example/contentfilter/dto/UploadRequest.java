package com.example.contentfilter.dto;

/**
 * DTO representing a simple upload request payload.
 * Currently holds optional text associated with the upload.
 *
 * @param text optional descriptive text sent with the upload
 */
public record UploadRequest(String text) {
}
