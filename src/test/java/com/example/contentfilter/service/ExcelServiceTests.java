package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.contentfilter.config.UploadLimitsProperties;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.exception.WorkbookProcessingException;
import com.example.contentfilter.exception.WorkbookProcessingException.Reason;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/** Unit tests for secure, bounded, coordinate-aware `.xlsx` extraction. */
class ExcelServiceTests {

	private static final String XLSX_CONTENT_TYPE =
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Test
	void extractsPhysicalCoordinatesAndIgnoresBlankRowsAndOtherSheets() throws IOException {
		byte[] workbook = workbook(book -> {
			XSSFSheet firstSheet = book.createSheet("Input");
			XSSFRow sourceRow = firstSheet.createRow(2);
			sourceRow.createCell(1).setCellValue(" Hello ");
			sourceRow.createCell(3).setCellValue("world");
			firstSheet.createRow(4).createCell(0).setCellValue("   ");
			book.createSheet("Ignored").createRow(0).createCell(0).setCellValue("not processed");
		});

		List<ExtractedSequence> sequences = service(defaultLimits()).extractSequences(upload(workbook));

		assertEquals(1, sequences.size());
		ExtractedSequence sequence = sequences.getFirst();
		assertEquals("sheet-0-row-2", sequence.sequenceId());
		assertEquals(0, sequence.sheetIndex());
		assertEquals(2, sequence.rowIndex());
		assertEquals(List.of(1, 3), sequence.sourceColumnIndexes());
		assertEquals("Hello\tworld", sequence.text());
		assertThrows(UnsupportedOperationException.class,
				() -> sequence.sourceColumnIndexes().add(5));
	}

	@Test
	void usesCachedFormulaResultWithoutRecalculatingDuringExtraction() throws IOException {
		byte[] workbook = workbook(book -> {
			XSSFSheet sheet = book.createSheet("Input");
			XSSFCell formula = sheet.createRow(0).createCell(0);
			formula.setCellFormula("1+1");
			book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);
		});

		ExtractedSequence sequence = service(defaultLimits())
				.extractSequences(upload(workbook))
				.getFirst();

		assertEquals("2", sequence.text());
	}

	@Test
	void rejectsExtensionContentTypeAndSignatureMismatches() throws IOException {
		byte[] validWorkbook = workbook(book ->
				book.createSheet("Input").createRow(0).createCell(0).setCellValue("hello"));
		ExcelService excelService = service(defaultLimits());

		assertReason(Reason.UNSUPPORTED_TYPE, () -> excelService.extractSequences(
				new MockMultipartFile("file", "sample.xls", XLSX_CONTENT_TYPE, validWorkbook)));
		assertReason(Reason.UNSUPPORTED_TYPE, () -> excelService.extractSequences(
				new MockMultipartFile("file", "sample.xlsx", "text/plain", validWorkbook)));
		assertReason(Reason.UNSUPPORTED_TYPE, () -> excelService.extractSequences(
				new MockMultipartFile("file", "sample.xlsx", XLSX_CONTENT_TYPE, "not Excel".getBytes())));
		assertReason(Reason.INVALID_REQUEST, () -> excelService.extractSequences(
				new MockMultipartFile("file", "../sample.xlsx", XLSX_CONTENT_TYPE, validWorkbook)));
		assertReason(Reason.INVALID_REQUEST, () -> excelService.extractSequences(
				new MockMultipartFile("file", "sample.xlsx", XLSX_CONTENT_TYPE, new byte[0])));
	}

	@Test
	void rejectsMacroEnabledWorkbookEvenWhenRenamedToXlsx() throws IOException {
		byte[] macroWorkbook;
		try (XSSFWorkbook workbook = new XSSFWorkbook(XSSFWorkbookType.XLSM);
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			workbook.createSheet("Input").createRow(0).createCell(0).setCellValue("hello");
			workbook.write(output);
			macroWorkbook = output.toByteArray();
		}

		assertReason(Reason.UNSUPPORTED_TYPE, () ->
				service(defaultLimits()).extractSequences(upload(macroWorkbook)));
	}

	@Test
	void rejectsMalformedOoxmlPackage() throws IOException {
		byte[] malformedPackage;
		try (ByteArrayOutputStream output = new ByteArrayOutputStream();
				ZipOutputStream zip = new ZipOutputStream(output)) {
			zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
			zip.write("not valid OOXML".getBytes());
			zip.closeEntry();
			zip.finish();
			malformedPackage = output.toByteArray();
		}

		assertReason(Reason.INVALID_WORKBOOK, () ->
				service(defaultLimits()).extractSequences(upload(malformedPackage)));
	}

	@Test
	void enforcesFileSequenceCellAndTextLimits() throws IOException {
		byte[] twoRows = workbook(book -> {
			XSSFSheet sheet = book.createSheet("Input");
			sheet.createRow(0).createCell(0).setCellValue("one");
			sheet.createRow(1).createCell(0).setCellValue("two");
		});
		assertReason(Reason.LIMIT_EXCEEDED, () ->
				service(limits(DataSize.ofMegabytes(5), 1, 100, 4_000, 1_000_000))
						.extractSequences(upload(twoRows)));

		byte[] twoCells = workbook(book -> {
			XSSFRow row = book.createSheet("Input").createRow(0);
			row.createCell(0).setCellValue("one");
			row.createCell(1).setCellValue("two");
		});
		assertReason(Reason.LIMIT_EXCEEDED, () ->
				service(limits(DataSize.ofMegabytes(5), 2_000, 1, 4_000, 1_000_000))
						.extractSequences(upload(twoCells)));

		byte[] longSequence = workbook(book ->
				book.createSheet("Input").createRow(0).createCell(0).setCellValue("three"));
		assertReason(Reason.LIMIT_EXCEEDED, () ->
				service(limits(DataSize.ofMegabytes(5), 2_000, 100, 4, 1_000_000))
						.extractSequences(upload(longSequence)));

		assertReason(Reason.LIMIT_EXCEEDED, () ->
				service(limits(DataSize.ofBytes(1), 2_000, 100, 4_000, 1_000_000))
						.extractSequences(upload(twoRows)));

		assertReason(Reason.LIMIT_EXCEEDED, () ->
				service(limits(DataSize.ofMegabytes(5), 2_000, 100, 4_000, 5))
						.extractSequences(upload(twoRows)));

		UploadLimitsProperties nearZeroTime = new UploadLimitsProperties(
				DataSize.ofMegabytes(5), 10, 10_000, 2_000, 100, 4_000, 1_000_000,
				Duration.ofNanos(1));
		assertReason(Reason.LIMIT_EXCEEDED, () ->
				service(nearZeroTime).extractSequences(upload(twoRows)));

		UploadLimitsProperties onePhysicalRow = new UploadLimitsProperties(
				DataSize.ofMegabytes(5), 10, 1, 2_000, 100, 4_000, 1_000_000,
				Duration.ofSeconds(60));
		assertReason(Reason.LIMIT_EXCEEDED, () ->
				service(onePhysicalRow).extractSequences(upload(twoRows)));

		byte[] twoSheets = workbook(book -> {
			book.createSheet("Input").createRow(0).createCell(0).setCellValue("one");
			book.createSheet("Extra");
		});
		UploadLimitsProperties oneWorksheet = new UploadLimitsProperties(
				DataSize.ofMegabytes(5), 1, 10_000, 2_000, 100, 4_000, 1_000_000,
				Duration.ofSeconds(60));
		assertReason(Reason.LIMIT_EXCEEDED, () ->
				service(oneWorksheet).extractSequences(upload(twoSheets)));
	}

	@Test
	void configuresApachePoiZipBombSafeguards() {
		service(defaultLimits());

		assertEquals(0.01d, ZipSecureFile.getMinInflateRatio());
		assertEquals(DataSize.ofMegabytes(20).toBytes(), ZipSecureFile.getMaxEntrySize());
		assertEquals(1_000_000, ZipSecureFile.getMaxTextSize());
		assertEquals(10_000, ZipSecureFile.getMaxFileCount());
	}

	private ExcelService service(UploadLimitsProperties limits) {
		return new ExcelService(limits);
	}

	private UploadLimitsProperties defaultLimits() {
		return limits(DataSize.ofMegabytes(5), 2_000, 100, 4_000, 1_000_000);
	}

	private UploadLimitsProperties limits(
			DataSize maxFileSize,
			int maxSequences,
			int maxCells,
			int maxSequenceLength,
			int maxTotalTextLength) {
		return new UploadLimitsProperties(
				maxFileSize,
				10,
				10_000,
				maxSequences,
				maxCells,
				maxSequenceLength,
				maxTotalTextLength,
				Duration.ofSeconds(60));
	}

	private MockMultipartFile upload(byte[] content) {
		return new MockMultipartFile("file", "sample.xlsx", XLSX_CONTENT_TYPE, content);
	}

	private byte[] workbook(Consumer<XSSFWorkbook> content) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			content.accept(workbook);
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private void assertReason(Reason expected, Runnable operation) {
		WorkbookProcessingException exception =
				assertThrows(WorkbookProcessingException.class, operation::run);
		assertEquals(expected, exception.reason());
	}
}
