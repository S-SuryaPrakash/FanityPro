package com.example.contentfilter.dto;

public record ErrorResponse(int statusCode, String error, String message) {
}
