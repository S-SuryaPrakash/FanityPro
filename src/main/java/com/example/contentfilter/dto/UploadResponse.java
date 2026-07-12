package com.example.contentfilter.dto;

/**
 * Response returned after a file upload.
 *
 * @param fileName name of the uploaded file
 * @param size size of the uploaded file in bytes
 * @param contentType reported MIME type
 * @param preview short text preview of the file's contents (may be null)
 */
public record UploadResponse(String fileName, long size, String contentType, String preview) {
}
