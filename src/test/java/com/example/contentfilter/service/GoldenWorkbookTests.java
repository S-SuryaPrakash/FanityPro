package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ConversationColumnMapping;
import com.example.contentfilter.domain.ConversationContext;
import com.example.contentfilter.domain.ConversationRiskAssessment;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Golden-workbook tests that reopen the generated .xlsx bytes and verify
 * every decision column, score column, and audit metadata field.
 *
 * <p>These tests complement {@link ExcelReportServiceTests} by verifying
 * the full audit trail: severity, review status, review reason, model ID,
 * model revision, policy version, and per-category score columns.</p>
 */
class GoldenWorkbookTests {

	private static final String XLSX_CONTENT_TYPE =
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private final ExcelReportService reportService = new ExcelReportService();

	@Nested
	@DisplayName("Decision columns")
	class DecisionColumns {

		@Test
		void writesAllDecisionColumnsForEachClassifiedRow() throws Exception {
			MockMultipartFile upload = upload(sourceWorkbook());
			ConversationRiskAssessment assessment = assessment();

			byte[] report = reportService.generate(
					upload, ConversationColumnMapping.standard(), assessment);

			try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
				Sheet source = workbook.getSheet("Input");
				Row header = source.getRow(0);

				int categoryCol = column(header, ExcelReportService.PRIMARY_CATEGORY_HEADER);
				int severityCol = column(header, ExcelReportService.SEVERITY_HEADER);
				int confidenceCol = column(header, ExcelReportService.CONFIDENCE_HEADER);
				int reviewCol = column(header, ExcelReportService.REVIEW_STATUS_HEADER);
				int reasonCol = column(header, ExcelReportService.REVIEW_REASON_HEADER);
				int modelIdCol = column(header, ExcelReportService.MODEL_ID_HEADER);
				int revisionCol = column(header, ExcelReportService.MODEL_REVISION_HEADER);
				int policyCol = column(header, ExcelReportService.POLICY_VERSION_HEADER);

				// Row 1: THREAT, CRITICAL, review required
				Row threatRow = source.getRow(1);
				assertEquals("Threat", threatRow.getCell(categoryCol).getStringCellValue());
				assertEquals("Critical", threatRow.getCell(severityCol).getStringCellValue());
				assertEquals(0.96, threatRow.getCell(confidenceCol).getNumericCellValue(), 0.001);
				assertEquals("REVIEW_REQUIRED", threatRow.getCell(reviewCol).getStringCellValue());
				assertEquals("Policy review is required.", threatRow.getCell(reasonCol).getStringCellValue());
				assertEquals("test-model", threatRow.getCell(modelIdCol).getStringCellValue());
				assertEquals("test-revision", threatRow.getCell(revisionCol).getStringCellValue());
				assertEquals("test-policy-v1", threatRow.getCell(policyCol).getStringCellValue());

				// Row 2: NO_AUTOMATED_FLAG, no review
				Row noFlagRow = source.getRow(2);
				assertEquals("No Automated Flag", noFlagRow.getCell(categoryCol).getStringCellValue());
				assertEquals("None", noFlagRow.getCell(severityCol).getStringCellValue());
				assertEquals(0.99, noFlagRow.getCell(confidenceCol).getNumericCellValue(), 0.001);
				assertEquals("NO_REVIEW_REQUIRED", noFlagRow.getCell(reviewCol).getStringCellValue());
				assertEquals("", noFlagRow.getCell(reasonCol).getStringCellValue());
				assertEquals("test-model", noFlagRow.getCell(modelIdCol).getStringCellValue());
				assertEquals("test-revision", noFlagRow.getCell(revisionCol).getStringCellValue());
				assertEquals("test-policy-v1", noFlagRow.getCell(policyCol).getStringCellValue());
			}
		}
	}

	@Nested
	@DisplayName("Per-category score columns")
	class ScoreColumns {

		@Test
		void writesOneScoreColumnPerDetectedRiskCategory() throws Exception {
			MockMultipartFile upload = upload(sourceWorkbook());
			ConversationRiskAssessment assessment = assessment();

			byte[] report = reportService.generate(
					upload, ConversationColumnMapping.standard(), assessment);

			try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
				Sheet source = workbook.getSheet("Input");
				Row header = source.getRow(0);

				for (RiskCategory category : RiskCategory.values()) {
					if (!category.isDetectedRisk()) {
						continue;
					}
					String scoreHeader = ExcelReportService.SCORE_HEADER_PREFIX + category.name();
					int col = column(header, scoreHeader);
					// Row 1 (threat) should have non-zero threat score
					double score = source.getRow(1).getCell(col).getNumericCellValue();
					assertTrue(score >= 0.0 && score <= 1.0,
							"Score for " + category + " should be in [0,1], got " + score);
				}
			}
		}

		@Test
		void threatScoreMatchesResultConfidence() throws Exception {
			MockMultipartFile upload = upload(sourceWorkbook());
			ConversationRiskAssessment assessment = assessment();

			byte[] report = reportService.generate(
					upload, ConversationColumnMapping.standard(), assessment);

			try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
				Sheet source = workbook.getSheet("Input");
				Row header = source.getRow(0);
				int threatScoreCol = column(header,
						ExcelReportService.SCORE_HEADER_PREFIX + RiskCategory.THREAT.name());

				// Row 1 is a threat with confidence 0.96
				assertEquals(0.96, source.getRow(1).getCell(threatScoreCol).getNumericCellValue(), 0.001);
			}
		}
	}

	@Nested
	@DisplayName("Review Queue")
	class ReviewQueue {

		@Test
		void ordersBySeverityDescendingThenConfidenceDescending() throws Exception {
			MockMultipartFile upload = upload(sourceWorkbook());
			ConversationRiskAssessment assessment = assessment();

			byte[] report = reportService.generate(
					upload, ConversationColumnMapping.standard(), assessment);

			try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
				Sheet queue = workbook.getSheet(ExcelReportService.REVIEW_QUEUE_SHEET);
				assertTrue(queue.getPhysicalNumberOfRows() > 1, "Queue should have data rows");

				// The Review Queue sheet uses its own headers; look up "Primary Category" and "Severity"
				Row queueHeader = queue.getRow(0);
				int categoryCol = column(queueHeader, "Primary Category");
				int severityCol = column(queueHeader, "Severity");

				// All queued rows should be review-required (the queue only contains those rows)
				// Verify ordering: first two rows are CRITICAL threats, third is HIGH harassment
				Row first = queue.getRow(1);
				assertEquals("Threat", first.getCell(categoryCol).getStringCellValue());
				assertEquals("Critical", first.getCell(severityCol).getStringCellValue());

				Row second = queue.getRow(2);
				assertEquals("Threat", second.getCell(categoryCol).getStringCellValue());
				assertEquals("Critical", second.getCell(severityCol).getStringCellValue());

				Row third = queue.getRow(3);
				assertEquals("Harassment Or Insult", third.getCell(categoryCol).getStringCellValue());
				assertEquals("High", third.getCell(severityCol).getStringCellValue());
			}
		}
	}

	@Nested
	@DisplayName("Summary sheet")
	class SummarySheet {

		@Test
		void containsCategoryCountsAndReviewStatusCounts() throws Exception {
			MockMultipartFile upload = upload(sourceWorkbook());
			ConversationRiskAssessment assessment = assessment();

			byte[] report = reportService.generate(
					upload, ConversationColumnMapping.standard(), assessment);

			try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
				Sheet summary = workbook.getSheet(ExcelReportService.SUMMARY_SHEET);
				assertTrue(sheetContains(summary, "Threat"));
				assertTrue(sheetContains(summary, "Harassment Or Insult"));
				assertTrue(sheetContains(summary, "No Automated Flag"));
				assertTrue(sheetContains(summary, "Review Required"));
				assertTrue(sheetContains(summary, "No Review Required"));
			}
		}
	}

	@Nested
	@DisplayName("Legend sheet")
	class LegendSheet {

		@Test
		void containsAllCategoryDescriptionsAndLimitations() throws Exception {
			MockMultipartFile upload = upload(sourceWorkbook());
			ConversationRiskAssessment assessment = assessment();

			byte[] report = reportService.generate(
					upload, ConversationColumnMapping.standard(), assessment);

			try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
				Sheet legend = workbook.getSheet(ExcelReportService.LEGEND_SHEET);
				assertTrue(sheetContains(legend, "Threat"));
				assertTrue(sheetContains(legend, "does not guarantee"));
				assertTrue(sheetContains(legend, "REVIEW_REQUIRED"));
				assertFalse(sheetContains(legend, "guaranteed safe"));
			}
		}
	}

	@Nested
	@DisplayName("Source data preservation")
	class SourcePreservation {

		@Test
		void preservesOriginalCellValueAndPosition() throws Exception {
			MockMultipartFile upload = upload(sourceWorkbook());
			ConversationRiskAssessment assessment = assessment();

			byte[] report = reportService.generate(
					upload, ConversationColumnMapping.standard(), assessment);

			try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
				Sheet source = workbook.getSheet("Input");
				// Original data columns (0=conversation_id, 1=text, 2=message_id) must be preserved
				assertEquals("conversation-1", source.getRow(1).getCell(0).getStringCellValue());
				assertEquals("I will kill you", source.getRow(1).getCell(1).getStringCellValue());
				assertEquals("message-1", source.getRow(1).getCell(2).getStringCellValue());
			}
		}

		@Test
		void preservesOtherSheets() throws Exception {
			MockMultipartFile upload = upload(sourceWorkbook());
			ConversationRiskAssessment assessment = assessment();

			byte[] report = reportService.generate(
					upload, ConversationColumnMapping.standard(), assessment);

			try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
				Sheet notes = workbook.getSheet("Original Notes");
				assertEquals("must remain", notes.getRow(0).getCell(0).getStringCellValue());
			}
		}
	}

	// ── Helpers ────────────────────────────────────────────────────────

	private ConversationRiskAssessment assessment() {
		List<ExtractedSequence> sequences = List.of(
				sequence("sheet-0-row-1", 1, "I will kill you", "conversation-1"),
				sequence("sheet-0-row-2", 2, "hello", "conversation-2"),
				sequence("sheet-0-row-3", 3, "You are stupid", "conversation-3"),
				sequence("sheet-0-row-4", 4, "I will kill you too", "conversation-4"));
		List<ClassificationResult> results = List.of(
				result(sequences.get(0), RiskCategory.THREAT, RiskSeverity.CRITICAL, 0.96, true),
				result(sequences.get(1), RiskCategory.NO_AUTOMATED_FLAG, RiskSeverity.NONE, 0.99, false),
				result(sequences.get(2), RiskCategory.HARASSMENT_OR_INSULT, RiskSeverity.HIGH, 0.91, true),
				result(sequences.get(3), RiskCategory.THREAT, RiskSeverity.CRITICAL, 0.92, true));
		return new ConversationRiskAssessment(sequences, results);
	}

	private ExtractedSequence sequence(String id, int row, String text, String conversationId) {
		return new ExtractedSequence(
				id, 0, row, List.of(1), text,
				new ConversationContext(conversationId, "message-" + row, "customer",
						"2026-07-20T10:00:00Z", "en", "chat"));
	}

	private ClassificationResult result(
			ExtractedSequence sequence,
			RiskCategory category,
			RiskSeverity severity,
			double confidence,
			boolean reviewRequired) {
		EnumMap<RiskCategory, Double> scores = new EnumMap<>(RiskCategory.class);
		for (RiskCategory scoreCategory : RiskCategory.values()) {
			if (scoreCategory.isDetectedRisk()) {
				scores.put(scoreCategory, 0.01);
			}
		}
		if (category.isDetectedRisk()) {
			scores.put(category, confidence);
		}
		return new ClassificationResult(
				sequence.sequenceId(), category, severity, confidence, Map.copyOf(scores),
				reviewRequired, reviewRequired ? "Policy review is required." : null,
				"test-model", "test-revision", "test-policy-v1");
	}

	private byte[] sourceWorkbook() throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			XSSFSheet sheet = workbook.createSheet("Input");
			XSSFRow header = sheet.createRow(0);
			header.createCell(0).setCellValue("conversation_id");
			header.createCell(1).setCellValue("text");
			header.createCell(2).setCellValue("message_id");
			addMessage(sheet, 1, "conversation-1", "I will kill you");
			addMessage(sheet, 2, "conversation-2", "hello");
			addMessage(sheet, 3, "conversation-3", "You are stupid");
			addMessage(sheet, 4, "conversation-4", "I will kill you too");
			workbook.createSheet("Original Notes").createRow(0).createCell(0).setCellValue("must remain");
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private void addMessage(XSSFSheet sheet, int rowIndex, String conversationId, String text) {
		XSSFRow row = sheet.createRow(rowIndex);
		row.createCell(0).setCellValue(conversationId);
		row.createCell(1).setCellValue(text);
		row.createCell(2).setCellValue("message-" + rowIndex);
	}

	private MockMultipartFile upload(byte[] content) {
		return new MockMultipartFile("file", "conversations.xlsx", XLSX_CONTENT_TYPE, content);
	}

	private int column(Row header, String value) {
		for (Cell cell : header) {
			if (value.equals(cell.getStringCellValue())) {
				return cell.getColumnIndex();
			}
		}
		throw new AssertionError("Missing report header: " + value);
	}

	private boolean sheetContains(Sheet sheet, String fragment) {
		for (Row row : sheet) {
			for (Cell cell : row) {
				if (cell.getCellType() == CellType.STRING
						&& cell.getStringCellValue().contains(fragment)) {
					return true;
				}
			}
		}
		return false;
	}
}
