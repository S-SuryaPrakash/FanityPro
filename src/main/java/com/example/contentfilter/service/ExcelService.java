package com.example.contentfilter.service;

import com.example.contentfilter.config.UploadLimitsProperties;
import com.example.contentfilter.domain.ConversationColumnMapping;
import com.example.contentfilter.domain.ConversationContext;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.exception.WorkbookProcessingException;
import com.example.contentfilter.exception.WorkbookProcessingException.Reason;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates and extracts bounded, coordinate-aware sequences from `.xlsx` files.
 *
 * <p>Only the first worksheet is processed. Formula cells use their cached
 * displayed results; formulas are not recalculated and external links are not
 * followed during classification.</p>
 */
@Service
public class ExcelService {

	private static final int FIRST_SHEET_INDEX = 0;
	private static final int MAX_FILENAME_LENGTH = 255;
	private static final long MAX_OOXML_PARTS = 10_000;
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"application/octet-stream");

	private final UploadLimitsProperties limits;

	public ExcelService(UploadLimitsProperties limits) {
		this.limits = limits;
		configurePoiSafeguards(limits);
	}

	/**
	 * Validates the upload and extracts one sequence per non-empty physical row.
	 *
	 * @param file multipart field named {@code file}
	 * @return immutable sequence list with zero-based physical coordinates
	 * @throws WorkbookProcessingException when validation, limits, or parsing fail
	 */
	public List<ExtractedSequence> extractSequences(MultipartFile file) {
		return extractSequences(file, null);
	}

	/**
	 * Extracts messages using an explicit header mapping. Only the mapped text
	 * column contributes to classifier input; other mapped fields become context.
	 */
	public List<ExtractedSequence> extractConversationSequences(
			MultipartFile file,
			ConversationColumnMapping mapping) {
		if (mapping == null) {
			throw new IllegalArgumentException("Conversation column mapping is required.");
		}
		return extractSequences(file, mapping);
	}

	private List<ExtractedSequence> extractSequences(
			MultipartFile file,
			ConversationColumnMapping mapping) {
		validateMetadata(file);
		long startedAt = System.nanoTime();

		try (InputStream rawInput = file.getInputStream();
				BufferedInputStream input = new BufferedInputStream(rawInput)) {
			validateFileSignature(input);

			try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
				validateWorkbook(workbook);
				return extractFirstSheet(workbook, startedAt, mapping);
			}
		} catch (WorkbookProcessingException exception) {
			throw exception;
		} catch (EncryptedDocumentException exception) {
			throw new WorkbookProcessingException(
					Reason.UNSUPPORTED_TYPE,
					"Encrypted workbooks are not supported.",
					exception);
		} catch (OLE2NotOfficeXmlFileException exception) {
			throw new WorkbookProcessingException(
					Reason.UNSUPPORTED_TYPE,
					"Only unencrypted .xlsx workbooks are supported.",
					exception);
		} catch (IOException | OpenXML4JException | RuntimeException exception) {
			throw new WorkbookProcessingException(
					Reason.INVALID_WORKBOOK,
					"The uploaded file is not a readable .xlsx workbook.",
					exception);
		}
	}

	private void validateMetadata(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new WorkbookProcessingException(
					Reason.INVALID_REQUEST,
					"The multipart field 'file' must contain a workbook.");
		}
		if (file.getSize() > limits.maxFileSize().toBytes()) {
			throw limitExceeded("The uploaded file exceeds the configured maximum size.");
		}

		String filename = file.getOriginalFilename();
		if (filename == null || filename.isBlank() || filename.length() > MAX_FILENAME_LENGTH
				|| filename.contains("/") || filename.contains("\\")) {
			throw new WorkbookProcessingException(
					Reason.INVALID_REQUEST,
					"The uploaded file name is invalid.");
		}
		if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
			throw new WorkbookProcessingException(
					Reason.UNSUPPORTED_TYPE,
					"Only .xlsx workbooks are supported.");
		}

		String contentType = file.getContentType();
		if (contentType != null && !contentType.isBlank()
				&& !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
			throw new WorkbookProcessingException(
					Reason.UNSUPPORTED_TYPE,
					"The uploaded file has an unsupported media type.");
		}
	}

	private void validateFileSignature(BufferedInputStream input) throws IOException {
		if (FileMagic.valueOf(FileMagic.prepareToCheckMagic(input)) != FileMagic.OOXML) {
			throw new WorkbookProcessingException(
					Reason.UNSUPPORTED_TYPE,
					"The uploaded content is not an OOXML .xlsx package.");
		}
	}

	private void validateWorkbook(XSSFWorkbook workbook) throws OpenXML4JException {
		if (workbook.isMacroEnabled()) {
			throw new WorkbookProcessingException(
					Reason.UNSUPPORTED_TYPE,
					"Macro-enabled workbooks are not supported.");
		}
		if (workbook.getNumberOfSheets() == 0) {
			throw new WorkbookProcessingException(
					Reason.INVALID_WORKBOOK,
					"The workbook does not contain a worksheet.");
		}
		if (workbook.getNumberOfSheets() > limits.maxWorksheets()) {
			throw limitExceeded("The workbook contains more worksheets than allowed.");
		}
	}

	private List<ExtractedSequence> extractFirstSheet(
			XSSFWorkbook workbook,
			long startedAt,
			ConversationColumnMapping mapping) {
		Sheet sheet = workbook.getSheetAt(FIRST_SHEET_INDEX);
		DataFormatter formatter = new DataFormatter(Locale.ROOT);
		formatter.setUseCachedValuesForFormulaCells(true);
		MappedColumns mappedColumns = mapping == null
				? null
				: resolveMappedColumns(sheet, formatter, mapping);
		List<ExtractedSequence> sequences = new ArrayList<>();
		int totalTextLength = 0;
		int physicalRows = 0;

		for (Row row : sheet) {
			ensureWithinProcessingTime(startedAt);
			physicalRows++;
			if (physicalRows > limits.maxPhysicalRows()) {
				throw limitExceeded("The worksheet contains more physical rows than allowed.");
			}
			if (mapping != null && row.getRowNum() <= mapping.headerRowIndex()) {
				continue;
			}

			List<Integer> sourceColumns;
			String sequenceText;
			ConversationContext context;
			if (mappedColumns == null) {
				List<Integer> populatedColumns = new ArrayList<>();
				StringJoiner text = new StringJoiner("\t");
				for (Cell cell : row) {
					ensureWithinProcessingTime(startedAt);
					String displayedValue = formatter.formatCellValue(cell).strip();
					if (displayedValue.isEmpty()) {
						continue;
					}
					populatedColumns.add(cell.getColumnIndex());
					if (populatedColumns.size() > limits.maxCellsPerSequence()) {
						throw limitExceeded("A row contains more populated cells than allowed.");
					}
					text.add(displayedValue);
				}
				if (populatedColumns.isEmpty()) {
					continue;
				}
				sourceColumns = populatedColumns;
				sequenceText = text.toString();
				context = ConversationContext.empty();
			} else {
				sequenceText = displayedValue(row, mappedColumns.text(), formatter);
				if (sequenceText.isEmpty()) {
					continue;
				}
				sourceColumns = List.of(mappedColumns.text());
				context = new ConversationContext(
						displayedValue(row, mappedColumns.conversationId(), formatter),
						displayedValue(row, mappedColumns.messageId(), formatter),
						displayedValue(row, mappedColumns.speakerRole(), formatter),
						displayedValue(row, mappedColumns.timestamp(), formatter),
						displayedValue(row, mappedColumns.language(), formatter),
						displayedValue(row, mappedColumns.channel(), formatter));
			}

			int sequenceLength = sequenceText.codePointCount(0, sequenceText.length());
			if (sequenceLength > limits.maxSequenceLength()) {
				throw limitExceeded("A sequence exceeds the configured text-length limit.");
			}
			totalTextLength = Math.addExact(totalTextLength, sequenceLength);
			if (totalTextLength > limits.maxTotalTextLength()) {
				throw limitExceeded("The workbook exceeds the total extracted-text limit.");
			}

			int rowIndex = row.getRowNum();
			sequences.add(new ExtractedSequence(
					"sheet-" + FIRST_SHEET_INDEX + "-row-" + rowIndex,
					FIRST_SHEET_INDEX,
					rowIndex,
					sourceColumns,
					sequenceText,
					context));
			if (sequences.size() > limits.maxSequences()) {
				throw limitExceeded("The workbook contains more sequences than allowed.");
			}
		}

		return List.copyOf(sequences);
	}

	private MappedColumns resolveMappedColumns(
			Sheet sheet,
			DataFormatter formatter,
			ConversationColumnMapping mapping) {
		Row headerRow = sheet.getRow(mapping.headerRowIndex());
		if (headerRow == null) {
			throw new WorkbookProcessingException(
					Reason.INVALID_WORKBOOK, "The configured header row does not exist.");
		}
		Map<String, Integer> headers = new HashMap<>();
		Set<String> duplicates = new java.util.HashSet<>();
		for (Cell cell : headerRow) {
			String value = formatter.formatCellValue(cell).strip();
			if (value.isEmpty()) {
				continue;
			}
			String normalized = ConversationColumnMapping.normalizedHeader(value);
			if (headers.putIfAbsent(normalized, cell.getColumnIndex()) != null) {
				duplicates.add(normalized);
			}
		}

		int text = requiredColumn(headers, duplicates, mapping.textHeader());
		return new MappedColumns(
				text,
				optionalColumn(headers, duplicates, mapping.conversationIdHeader()),
				optionalColumn(headers, duplicates, mapping.messageIdHeader()),
				optionalColumn(headers, duplicates, mapping.speakerRoleHeader()),
				optionalColumn(headers, duplicates, mapping.timestampHeader()),
				optionalColumn(headers, duplicates, mapping.languageHeader()),
				optionalColumn(headers, duplicates, mapping.channelHeader()));
	}

	private int requiredColumn(
			Map<String, Integer> headers,
			Set<String> duplicates,
			String header) {
		int column = optionalColumn(headers, duplicates, header);
		if (column < 0) {
			throw new WorkbookProcessingException(
					Reason.INVALID_WORKBOOK,
					"The required mapped text header '" + header + "' was not found.");
		}
		return column;
	}

	private int optionalColumn(
			Map<String, Integer> headers,
			Set<String> duplicates,
			String header) {
		if (header == null) {
			return -1;
		}
		String normalized = ConversationColumnMapping.normalizedHeader(header);
		if (duplicates.contains(normalized)) {
			throw new WorkbookProcessingException(
					Reason.INVALID_WORKBOOK,
					"The mapped header '" + header + "' occurs more than once.");
		}
		return headers.getOrDefault(normalized, -1);
	}

	private String displayedValue(Row row, int columnIndex, DataFormatter formatter) {
		if (columnIndex < 0) {
			return "";
		}
		Cell cell = row.getCell(columnIndex);
		return cell == null ? "" : formatter.formatCellValue(cell).strip();
	}

	private record MappedColumns(
			int text,
			int conversationId,
			int messageId,
			int speakerRole,
			int timestamp,
			int language,
			int channel) {
	}

	private void ensureWithinProcessingTime(long startedAt) {
		if (System.nanoTime() - startedAt > limits.maxProcessingTime().toNanos()) {
			throw limitExceeded("Workbook processing exceeded the configured time limit.");
		}
	}

	private WorkbookProcessingException limitExceeded(String message) {
		return new WorkbookProcessingException(Reason.LIMIT_EXCEEDED, message);
	}

	private void configurePoiSafeguards(UploadLimitsProperties uploadLimits) {
		ZipSecureFile.setMinInflateRatio(0.01d);
		ZipSecureFile.setMaxEntrySize(Math.multiplyExact(uploadLimits.maxFileSize().toBytes(), 4));
		ZipSecureFile.setMaxTextSize(uploadLimits.maxTotalTextLength());
		ZipSecureFile.setMaxFileCount(MAX_OOXML_PARTS);
	}
}
