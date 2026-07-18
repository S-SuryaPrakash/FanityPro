package com.example.contentfilter.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Configurable resource limits for the synchronous V1 upload workflow.
 *
 * <p>Keeping these limits outside business code allows each deployment to
 * reduce them without rebuilding the application. Increasing a limit should
 * only happen after load and memory testing on the target infrastructure.</p>
 *
 * @param maxFileSize maximum accepted workbook size
 * @param maxWorksheets maximum worksheets allowed in the uploaded workbook
 * @param maxPhysicalRows maximum defined rows inspected in the first worksheet
 * @param maxSequences maximum non-empty sequences extracted from a workbook
 * @param maxCellsPerSequence maximum populated cells contributing to a sequence
 * @param maxSequenceLength maximum Unicode characters in one sequence
 * @param maxTotalTextLength maximum Unicode characters across all sequences
 * @param maxProcessingTime maximum end-to-end processing duration
 */
@Validated
@ConfigurationProperties("content-filter.upload")
public record UploadLimitsProperties(
		@NotNull DataSize maxFileSize,
		@Min(1) int maxWorksheets,
		@Min(1) int maxPhysicalRows,
		@Min(1) int maxSequences,
		@Min(1) int maxCellsPerSequence,
		@Min(1) int maxSequenceLength,
		@Min(1) int maxTotalTextLength,
		@NotNull Duration maxProcessingTime) {

	public UploadLimitsProperties {
		if (maxFileSize != null && maxFileSize.toBytes() < 1) {
			throw new IllegalArgumentException("Maximum file size must be positive.");
		}
		if (maxProcessingTime != null
				&& (maxProcessingTime.isZero() || maxProcessingTime.isNegative())) {
			throw new IllegalArgumentException("Maximum processing time must be positive.");
		}
	}
}
