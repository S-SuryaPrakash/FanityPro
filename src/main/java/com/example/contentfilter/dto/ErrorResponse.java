package com.example.contentfilter.dto;

/**
 * Standard error payload returned by the API.
 *
 * @param statusCode HTTP status code
 * @param error short error code identifier
 * @param message human-readable error message
 * @param timestamp ISO-8601 timestamp when the error occurred
 */
public record ErrorResponse(int statusCode, String error, String message, String timestamp) {
}
