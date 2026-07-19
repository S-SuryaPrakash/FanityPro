package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ConversationColumnMapping;
import com.example.contentfilter.domain.ConversationContext;
import com.example.contentfilter.domain.ConversationRiskAssessment;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import com.example.contentfilter.exception.WorkbookProcessingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Verifies the generated workbook contract by reopening the actual `.xlsx` bytes. */
class ExcelReportServiceTests {

	private static final String XLSX_CONTENT_TYPE =
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private final ExcelReportService reportService = new ExcelReportService();

	@Test
	void preservesSourceDataAndCreatesAuditableReportSheets() throws Exception {
		MockMultipartFile upload = upload(sourceWorkbook(false));
		ConversationRiskAssessment assessment = assessment();

		byte[] report = reportService.generate(
				upload, ConversationColumnMapping.standard(), assessment);

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
			assertEquals(5, workbook.getNumberOfSheets());
			assertEquals("Original Notes", workbook.getSheetAt(1).getSheetName());
			assertEquals("must remain", workbook.getSheetAt(1).getRow(0).getCell(0).getStringCellValue());

			Sheet source = workbook.getSheet("Input");
			assertEquals("I will kill you", source.getRow(1).getCell(1).getStringCellValue());
			assertEquals("hello", source.getRow(2).getCell(1).getStringCellValue());
			assertEquals(FillPatternType.SOLID_FOREGROUND,
					source.getRow(1).getCell(1).getCellStyle().getFillPattern());
			assertNotEquals(source.getRow(1).getCell(1).getCellStyle().getFillForegroundColor(),
					source.getRow(2).getCell(1).getCellStyle().getFillForegroundColor());
			assertEquals(source.getRow(1).getCell(1).getCellStyle().getIndex(),
					source.getRow(4).getCell(1).getCellStyle().getIndex());

			int categoryColumn = column(source.getRow(0), ExcelReportService.PRIMARY_CATEGORY_HEADER);
			int confidenceColumn = column(source.getRow(0), ExcelReportService.CONFIDENCE_HEADER);
			int threatScoreColumn = column(source.getRow(0),
					ExcelReportService.SCORE_HEADER_PREFIX + RiskCategory.THREAT.name());
			assertEquals("Threat", source.getRow(1).getCell(categoryColumn).getStringCellValue());
			assertEquals(0.96, source.getRow(1).getCell(confidenceColumn).getNumericCellValue());
			assertEquals(0.96, source.getRow(1).getCell(threatScoreColumn).getNumericCellValue());

			Sheet queue = workbook.getSheet(ExcelReportService.REVIEW_QUEUE_SHEET);
			assertEquals("I will kill you", queue.getRow(1).getCell(9).getStringCellValue());
			assertEquals("I will kill you too", queue.getRow(2).getCell(9).getStringCellValue());
			assertEquals("You are stupid", queue.getRow(3).getCell(9).getStringCellValue());

			Sheet summary = workbook.getSheet(ExcelReportService.SUMMARY_SHEET);
			assertEquals("Threat", summary.getRow(1).getCell(0).getStringCellValue());
			assertEquals(2, summary.getRow(1).getCell(1).getNumericCellValue());
			assertEquals(4, summary.getRow(9).getCell(1).getNumericCellValue());

			Sheet legend = workbook.getSheet(ExcelReportService.LEGEND_SHEET);
			assertTrue(sheetContains(legend, "do not identify individual words"));
			assertTrue(sheetContains(legend, "does not guarantee"));
		}
	}

	@Test
	void rejectsAWorkbookThatWouldOverwriteAReservedReportSheet() throws Exception {
		MockMultipartFile upload = upload(sourceWorkbook(true));

		WorkbookProcessingException exception = assertThrows(
				WorkbookProcessingException.class,
				() -> reportService.generate(
						upload, ConversationColumnMapping.standard(), assessment()));

		assertEquals(WorkbookProcessingException.Reason.INVALID_WORKBOOK, exception.reason());
		assertTrue(exception.getMessage().contains(ExcelReportService.REVIEW_QUEUE_SHEET));
	}

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
				id,
				0,
				row,
				List.of(1),
				text,
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

	private byte[] sourceWorkbook(boolean addReservedSheet) throws IOException {
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
			if (addReservedSheet) {
				workbook.createSheet(ExcelReportService.REVIEW_QUEUE_SHEET);
			}
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
		return new MockMultipartFile(
				"file", "conversations.xlsx", XLSX_CONTENT_TYPE, content);
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
				if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
						&& cell.getStringCellValue().contains(fragment)) {
					return true;
				}
			}
		}
		return false;
	}
}
