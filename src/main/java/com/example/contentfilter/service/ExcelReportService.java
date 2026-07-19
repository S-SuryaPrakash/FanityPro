package com.example.contentfilter.service;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ConversationColumnMapping;
import com.example.contentfilter.domain.ConversationContext;
import com.example.contentfilter.domain.ConversationRiskAssessment;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import com.example.contentfilter.exception.ReportGenerationException;
import com.example.contentfilter.exception.WorkbookProcessingException;
import com.example.contentfilter.exception.WorkbookProcessingException.Reason;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Creates an auditable, annotated copy of a classified conversation workbook. */
@Service
public class ExcelReportService {

	public static final String REVIEW_QUEUE_SHEET = "Review Queue";
	public static final String SUMMARY_SHEET = "Summary";
	public static final String LEGEND_SHEET = "Legend";
	public static final String PRIMARY_CATEGORY_HEADER = "Content Filter - Primary Category";
	public static final String SEVERITY_HEADER = "Content Filter - Severity";
	public static final String CONFIDENCE_HEADER = "Content Filter - Confidence";
	public static final String REVIEW_STATUS_HEADER = "Content Filter - Review Status";
	public static final String REVIEW_REASON_HEADER = "Content Filter - Review Reason";
	public static final String MODEL_ID_HEADER = "Content Filter - Model ID";
	public static final String MODEL_REVISION_HEADER = "Content Filter - Model Revision";
	public static final String POLICY_VERSION_HEADER = "Content Filter - Policy Version";
	public static final String SCORE_HEADER_PREFIX = "Content Filter - Score - ";

	private static final List<RiskCategory> SCORE_CATEGORIES = List.of(
			RiskCategory.THREAT,
			RiskCategory.HATE_OR_IDENTITY_ATTACK,
			RiskCategory.HARASSMENT_OR_INSULT,
			RiskCategory.OBSCENE_OR_PROFANE,
			RiskCategory.GENERAL_TOXICITY);
	private static final List<String> BASE_REPORT_HEADERS = List.of(
			PRIMARY_CATEGORY_HEADER,
			SEVERITY_HEADER,
			CONFIDENCE_HEADER,
			REVIEW_STATUS_HEADER,
			REVIEW_REASON_HEADER,
			MODEL_ID_HEADER,
			MODEL_REVISION_HEADER,
			POLICY_VERSION_HEADER);
	private static final Set<String> RESERVED_SHEETS =
			Set.of(REVIEW_QUEUE_SHEET, SUMMARY_SHEET, LEGEND_SHEET);

	/**
	 * Reopens the validated upload, annotates its source cells, and adds the V1
	 * Review Queue, Summary, and Legend worksheets.
	 */
	public byte[] generate(
			MultipartFile file,
			ConversationColumnMapping mapping,
			ConversationRiskAssessment assessment) {
		if (file == null || mapping == null || assessment == null) {
			throw new IllegalArgumentException("Workbook, mapping, and assessment are required.");
		}

		try (InputStream input = file.getInputStream();
				XSSFWorkbook workbook = new XSSFWorkbook(input);
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ensureReportSheetsAvailable(workbook);
			Sheet sourceSheet = workbook.getSheetAt(0);
			Row headerRow = sourceSheet.getRow(mapping.headerRowIndex());
			if (headerRow == null) {
				throw invalidWorkbook("The configured header row does not exist.");
			}
			ensureNotAlreadyAnnotated(headerRow);

			ReportStyles styles = createStyles(workbook);
			ReportColumns columns = appendReportHeaders(headerRow, styles.header());
			List<AssessedSequence> assessed = correlate(assessment);
			annotateSourceRows(workbook, assessed, columns, styles);
			createReviewQueue(workbook, assessed, styles);
			createSummary(workbook, assessed, styles);
			createLegend(workbook, styles);

			workbook.write(output);
			return output.toByteArray();
		} catch (WorkbookProcessingException | ReportGenerationException exception) {
			throw exception;
		} catch (IOException | RuntimeException exception) {
			throw new ReportGenerationException(
					"The classified workbook could not be generated.", exception);
		}
	}

	private void ensureReportSheetsAvailable(XSSFWorkbook workbook) {
		for (String sheetName : RESERVED_SHEETS) {
			if (workbook.getSheet(sheetName) != null) {
				throw invalidWorkbook(
						"The workbook already contains the reserved sheet '" + sheetName + "'.");
			}
		}
	}

	private void ensureNotAlreadyAnnotated(Row headerRow) {
		for (Cell cell : headerRow) {
			String value = cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
					? cell.getStringCellValue().strip()
					: "";
			if (BASE_REPORT_HEADERS.contains(value) || value.startsWith(SCORE_HEADER_PREFIX)) {
				throw invalidWorkbook("The workbook already contains Content Filter report columns.");
			}
		}
	}

	private ReportColumns appendReportHeaders(Row headerRow, CellStyle headerStyle) {
		int nextColumn = Math.max(0, headerRow.getLastCellNum());
		int primaryCategory = addHeader(headerRow, nextColumn++, PRIMARY_CATEGORY_HEADER, headerStyle);
		int severity = addHeader(headerRow, nextColumn++, SEVERITY_HEADER, headerStyle);
		int confidence = addHeader(headerRow, nextColumn++, CONFIDENCE_HEADER, headerStyle);
		int reviewStatus = addHeader(headerRow, nextColumn++, REVIEW_STATUS_HEADER, headerStyle);
		int reviewReason = addHeader(headerRow, nextColumn++, REVIEW_REASON_HEADER, headerStyle);
		int modelId = addHeader(headerRow, nextColumn++, MODEL_ID_HEADER, headerStyle);
		int modelRevision = addHeader(headerRow, nextColumn++, MODEL_REVISION_HEADER, headerStyle);
		int policyVersion = addHeader(headerRow, nextColumn++, POLICY_VERSION_HEADER, headerStyle);
		EnumMap<RiskCategory, Integer> scoreColumns = new EnumMap<>(RiskCategory.class);
		for (RiskCategory category : SCORE_CATEGORIES) {
			scoreColumns.put(category, addHeader(
					headerRow, nextColumn++, SCORE_HEADER_PREFIX + category.name(), headerStyle));
		}
		return new ReportColumns(
				primaryCategory, severity, confidence, reviewStatus, reviewReason,
				modelId, modelRevision, policyVersion, Map.copyOf(scoreColumns));
	}

	private int addHeader(Row row, int column, String value, CellStyle style) {
		Cell cell = row.createCell(column);
		cell.setCellValue(value);
		cell.setCellStyle(style);
		return column;
	}

	private List<AssessedSequence> correlate(ConversationRiskAssessment assessment) {
		List<AssessedSequence> correlated = new ArrayList<>(assessment.sequences().size());
		for (int index = 0; index < assessment.sequences().size(); index++) {
			correlated.add(new AssessedSequence(
					assessment.sequences().get(index), assessment.results().get(index)));
		}
		return List.copyOf(correlated);
	}

	private void annotateSourceRows(
			XSSFWorkbook workbook,
			List<AssessedSequence> assessed,
			ReportColumns columns,
			ReportStyles styles) {
		for (AssessedSequence item : assessed) {
			ExtractedSequence sequence = item.sequence();
			ClassificationResult result = item.result();
			if (sequence.sheetIndex() >= workbook.getNumberOfSheets()) {
				throw new ReportGenerationException("A classified worksheet coordinate is invalid.");
			}
			Sheet sheet = workbook.getSheetAt(sequence.sheetIndex());
			Row row = sheet.getRow(sequence.rowIndex());
			if (row == null) {
				throw new ReportGenerationException("A classified row coordinate is invalid.");
			}

			CellStyle categoryStyle = styles.categories().get(result.primaryCategory());
			for (Integer sourceColumn : sequence.sourceColumnIndexes()) {
				Cell sourceCell = row.getCell(sourceColumn);
				if (sourceCell == null) {
					throw new ReportGenerationException("A classified cell coordinate is invalid.");
				}
				sourceCell.setCellStyle(categoryStyle);
			}

			writeText(row, columns.primaryCategory(), display(result.primaryCategory()), categoryStyle);
			writeText(row, columns.severity(), display(result.severity()), null);
			writeNumber(row, columns.confidence(), result.confidence(), styles.percentage());
			writeText(row, columns.reviewStatus(),
					result.manualReviewRequired() ? "REVIEW_REQUIRED" : "NO_REVIEW_REQUIRED", null);
			writeText(row, columns.reviewReason(), result.reviewReason(), styles.wrapped());
			writeText(row, columns.modelId(), result.modelId(), null);
			writeText(row, columns.modelRevision(), result.modelRevision(), null);
			writeText(row, columns.policyVersion(), result.policyVersion(), null);
			for (RiskCategory category : SCORE_CATEGORIES) {
				writeNumber(row, columns.scoreColumns().get(category),
						result.scores().getOrDefault(category, 0.0), styles.percentage());
			}
		}
	}

	private void createReviewQueue(
			XSSFWorkbook workbook,
			List<AssessedSequence> assessed,
			ReportStyles styles) {
		Sheet sheet = workbook.createSheet(REVIEW_QUEUE_SHEET);
		String[] headers = {
				"Priority", "Source Sheet", "Excel Row", "Conversation ID", "Message ID",
				"Speaker Role", "Timestamp", "Language", "Channel", "Text",
				"Primary Category", "Severity", "Confidence", "Review Reason",
				"Model ID", "Model Revision", "Policy Version"
		};
		writeHeaders(sheet.createRow(0), headers, styles.header());

		List<AssessedSequence> queue = assessed.stream()
				.filter(item -> item.result().manualReviewRequired())
				.sorted(Comparator
						.<AssessedSequence>comparingInt(item -> item.result().severity().ordinal())
						.reversed()
						.thenComparing(Comparator.comparingDouble(
								(AssessedSequence item) -> item.result().confidence()).reversed()))
				.toList();
		for (int index = 0; index < queue.size(); index++) {
			AssessedSequence item = queue.get(index);
			ExtractedSequence sequence = item.sequence();
			ClassificationResult result = item.result();
			ConversationContext context = sequence.context();
			Row row = sheet.createRow(index + 1);
			writeNumber(row, 0, index + 1, null);
			writeText(row, 1, workbook.getSheetAt(sequence.sheetIndex()).getSheetName(), null);
			writeNumber(row, 2, sequence.rowIndex() + 1, null);
			writeText(row, 3, context.conversationId(), null);
			writeText(row, 4, context.messageId(), null);
			writeText(row, 5, context.speakerRole(), null);
			writeText(row, 6, context.timestamp(), null);
			writeText(row, 7, context.language(), null);
			writeText(row, 8, context.channel(), null);
			writeText(row, 9, sequence.text(), styles.wrapped());
			writeText(row, 10, display(result.primaryCategory()),
					styles.categories().get(result.primaryCategory()));
			writeText(row, 11, display(result.severity()), null);
			writeNumber(row, 12, result.confidence(), styles.percentage());
			writeText(row, 13, result.reviewReason(), styles.wrapped());
			writeText(row, 14, result.modelId(), null);
			writeText(row, 15, result.modelRevision(), null);
			writeText(row, 16, result.policyVersion(), null);
		}

		sheet.createFreezePane(0, 1);
		sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, queue.size()), 0, headers.length - 1));
		setWidths(sheet, new int[] {10, 18, 11, 20, 20, 16, 22, 12, 14, 50, 28, 14, 14, 50, 28, 24, 22});
	}

	private void createSummary(
			XSSFWorkbook workbook,
			List<AssessedSequence> assessed,
			ReportStyles styles) {
		Sheet sheet = workbook.createSheet(SUMMARY_SHEET);
		writeHeaders(sheet.createRow(0),
				new String[] {"Primary Category", "Total", "Review Required", "No Review Required"},
				styles.header());
		int rowIndex = 1;
		for (RiskCategory category : RiskCategory.values()) {
			long total = assessed.stream()
					.filter(item -> item.result().primaryCategory() == category).count();
			long review = assessed.stream()
					.filter(item -> item.result().primaryCategory() == category)
					.filter(item -> item.result().manualReviewRequired()).count();
			Row row = sheet.createRow(rowIndex++);
			writeText(row, 0, display(category), styles.categories().get(category));
			writeNumber(row, 1, total, null);
			writeNumber(row, 2, review, null);
			writeNumber(row, 3, total - review, null);
		}
		Row totalRow = sheet.createRow(rowIndex + 1);
		writeText(totalRow, 0, "All Classified Sequences", styles.header());
		writeNumber(totalRow, 1, assessed.size(), null);
		long reviewCount = assessed.stream()
				.filter(item -> item.result().manualReviewRequired()).count();
		writeNumber(totalRow, 2, reviewCount, null);
		writeNumber(totalRow, 3, assessed.size() - reviewCount, null);
		sheet.createFreezePane(0, 1);
		setWidths(sheet, new int[] {32, 16, 20, 22});
	}

	private void createLegend(XSSFWorkbook workbook, ReportStyles styles) {
		Sheet sheet = workbook.createSheet(LEGEND_SHEET);
		writeHeaders(sheet.createRow(0),
				new String[] {"Primary Category", "Typical Severity", "Colour", "Meaning"},
				styles.header());
		int rowIndex = 1;
		for (RiskCategory category : RiskCategory.values()) {
			Row row = sheet.createRow(rowIndex++);
			writeText(row, 0, display(category), styles.categories().get(category));
			writeText(row, 1, typicalSeverity(category), null);
			writeText(row, 2, "Category fill", styles.categories().get(category));
			writeText(row, 3, meaning(category), styles.wrapped());
		}

		rowIndex++;
		writeText(sheet.createRow(rowIndex++), 0, "Important limitations", styles.header());
		writeText(sheet.createRow(rowIndex++), 0,
				"Colours summarize a complete sequence-level policy decision; they do not identify individual words.",
				styles.wrapped());
		writeText(sheet.createRow(rowIndex++), 0,
				"NO AUTOMATED FLAG does not guarantee that a message is safe or appropriate.",
				styles.wrapped());
		writeText(sheet.createRow(rowIndex), 0,
				"Rows marked REVIEW_REQUIRED must be assessed by an authorised human reviewer.",
				styles.wrapped());
		sheet.createFreezePane(0, 1);
		setWidths(sheet, new int[] {32, 20, 18, 80});
	}

	private ReportStyles createStyles(XSSFWorkbook workbook) {
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);
		headerFont.setColor(IndexedColors.WHITE.getIndex());
		CellStyle header = workbook.createCellStyle();
		header.setFont(headerFont);
		header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
		header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		header.setAlignment(HorizontalAlignment.CENTER);
		header.setVerticalAlignment(VerticalAlignment.CENTER);
		header.setWrapText(true);
		header.setBorderBottom(BorderStyle.THIN);

		CellStyle percentage = workbook.createCellStyle();
		percentage.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));

		CellStyle wrapped = workbook.createCellStyle();
		wrapped.setWrapText(true);
		wrapped.setVerticalAlignment(VerticalAlignment.TOP);

		EnumMap<RiskCategory, CellStyle> categories = new EnumMap<>(RiskCategory.class);
		categories.put(RiskCategory.THREAT, categoryStyle(workbook, IndexedColors.RED));
		categories.put(RiskCategory.HATE_OR_IDENTITY_ATTACK,
				categoryStyle(workbook, IndexedColors.CORAL));
		categories.put(RiskCategory.HARASSMENT_OR_INSULT,
				categoryStyle(workbook, IndexedColors.ORANGE));
		categories.put(RiskCategory.OBSCENE_OR_PROFANE,
				categoryStyle(workbook, IndexedColors.YELLOW));
		categories.put(RiskCategory.GENERAL_TOXICITY,
				categoryStyle(workbook, IndexedColors.LIGHT_ORANGE));
		categories.put(RiskCategory.NO_AUTOMATED_FLAG,
				categoryStyle(workbook, IndexedColors.LIGHT_GREEN));
		categories.put(RiskCategory.MANUAL_REVIEW,
				categoryStyle(workbook, IndexedColors.LIGHT_BLUE));
		return new ReportStyles(header, percentage, wrapped, Map.copyOf(categories));
	}

	private CellStyle categoryStyle(XSSFWorkbook workbook, IndexedColors colour) {
		CellStyle style = workbook.createCellStyle();
		style.setFillForegroundColor(colour.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setVerticalAlignment(VerticalAlignment.TOP);
		style.setWrapText(true);
		return style;
	}

	private void writeHeaders(Row row, String[] headers, CellStyle style) {
		for (int column = 0; column < headers.length; column++) {
			writeText(row, column, headers[column], style);
		}
	}

	private void writeText(Row row, int column, String value, CellStyle style) {
		Cell cell = row.createCell(column);
		cell.setCellValue(value == null ? "" : value);
		if (style != null) {
			cell.setCellStyle(style);
		}
	}

	private void writeNumber(Row row, int column, double value, CellStyle style) {
		Cell cell = row.createCell(column);
		cell.setCellValue(value);
		if (style != null) {
			cell.setCellStyle(style);
		}
	}

	private void setWidths(Sheet sheet, int[] characterWidths) {
		for (int column = 0; column < characterWidths.length; column++) {
			sheet.setColumnWidth(column, characterWidths[column] * 256);
		}
	}

	private String display(Enum<?> value) {
		String[] words = value.name().toLowerCase(Locale.ROOT).split("_");
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return result.toString();
	}

	private String typicalSeverity(RiskCategory category) {
		return switch (category) {
			case THREAT -> display(RiskSeverity.CRITICAL);
			case HATE_OR_IDENTITY_ATTACK, HARASSMENT_OR_INSULT -> display(RiskSeverity.HIGH);
			case OBSCENE_OR_PROFANE, GENERAL_TOXICITY -> display(RiskSeverity.MEDIUM);
			case MANUAL_REVIEW -> "Policy assigned";
			case NO_AUTOMATED_FLAG -> display(RiskSeverity.NONE);
		};
	}

	private String meaning(RiskCategory category) {
		return switch (category) {
			case THREAT -> "Potential threat or violent intent; prioritise authorised review.";
			case HATE_OR_IDENTITY_ATTACK -> "Potential attack targeting an identity or protected characteristic.";
			case HARASSMENT_OR_INSULT -> "Potential harassment, degrading language, or personal insult.";
			case OBSCENE_OR_PROFANE -> "Potential obscene or profane language.";
			case GENERAL_TOXICITY -> "Potentially toxic or hostile language not assigned above.";
			case MANUAL_REVIEW -> "Automated evidence was uncertain, conflicting, or incomplete.";
			case NO_AUTOMATED_FLAG -> "No configured automated risk threshold was exceeded.";
		};
	}

	private WorkbookProcessingException invalidWorkbook(String message) {
		return new WorkbookProcessingException(Reason.INVALID_WORKBOOK, message);
	}

	private record AssessedSequence(
			ExtractedSequence sequence,
			ClassificationResult result) {
	}

	private record ReportColumns(
			int primaryCategory,
			int severity,
			int confidence,
			int reviewStatus,
			int reviewReason,
			int modelId,
			int modelRevision,
			int policyVersion,
			Map<RiskCategory, Integer> scoreColumns) {
	}

	private record ReportStyles(
			CellStyle header,
			CellStyle percentage,
			CellStyle wrapped,
			Map<RiskCategory, CellStyle> categories) {
	}
}
