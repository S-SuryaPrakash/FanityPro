package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contentfilter.config.UploadLimitsProperties;
import com.example.contentfilter.exception.WorkbookProcessingException;
import com.example.contentfilter.exception.WorkbookProcessingException.Reason;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * Security-focused tests for upload validation.
 *
 * <p>Covers disguised files, ZIP bombs, malformed OOXML variants,
 * unsafe filenames, and other adversarial inputs that the service must
 * reject before reaching Apache POI parsing.</p>
 */
class UploadSecurityTests {

	private static final String XLSX_CONTENT_TYPE =
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	// ── Disguised files: non-Excel content with .xlsx extension ────────

	@Nested
	@DisplayName("Disguised files")
	class DisguisedFiles {

		@Test
		void rejectsPlainTextFileWithXlsxExtension() {
			MockMultipartFile disguised = new MockMultipartFile(
					"file", "report.xlsx", XLSX_CONTENT_TYPE,
					"This is just plain text, not Excel.".getBytes());

			assertReason(Reason.UNSUPPORTED_TYPE, () ->
					service(defaultLimits()).extractSequences(disguised));
		}

		@Test
		void rejectsHtmlFileWithXlsxExtension() {
			String html = "<html><body><table><tr><td>data</td></tr></table></body></html>";
			MockMultipartFile disguised = new MockMultipartFile(
					"file", "data.xlsx", XLSX_CONTENT_TYPE, html.getBytes());

			assertReason(Reason.UNSUPPORTED_TYPE, () ->
					service(defaultLimits()).extractSequences(disguised));
		}

		@Test
		void rejectsPdfFileWithXlsxExtension() {
			byte[] pdfHeader = "%PDF-1.4 fake pdf content".getBytes();
			MockMultipartFile disguised = new MockMultipartFile(
					"file", "spreadsheet.xlsx", XLSX_CONTENT_TYPE, pdfHeader);

			assertReason(Reason.UNSUPPORTED_TYPE, () ->
					service(defaultLimits()).extractSequences(disguised));
		}

		@Test
		void rejectsCsvFileWithXlsxExtension() {
			String csv = "name,value\nalice,1\nbob,2\n";
			MockMultipartFile disguised = new MockMultipartFile(
					"file", "export.xlsx", XLSX_CONTENT_TYPE, csv.getBytes());

			assertReason(Reason.UNSUPPORTED_TYPE, () ->
					service(defaultLimits()).extractSequences(disguised));
		}

		@Test
		void rejectsExecutableFileWithXlsxExtension() {
			byte[] exeHeader = new byte[] {(byte) 0x4D, (byte) 0x5A, (byte) 0x90, 0x00};
			MockMultipartFile disguised = new MockMultipartFile(
					"file", "malware.xlsx", XLSX_CONTENT_TYPE, exeHeader);

			assertReason(Reason.UNSUPPORTED_TYPE, () ->
					service(defaultLimits()).extractSequences(disguised));
		}
	}

	// ── ZIP bombs: archives that expand to enormous size ───────────────

	@Nested
	@DisplayName("ZIP bombs and compressed exploits")
	class ZipBombTests {

		@Test
		void rejectsZipFileWithOoxmlContentTypesButNoWorkbook() throws IOException {
			byte[] fakeOoxml;
			try (ByteArrayOutputStream output = new ByteArrayOutputStream();
					ZipOutputStream zip = new ZipOutputStream(output)) {
				zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
				zip.write("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"></Types>".getBytes());
				zip.closeEntry();
				zip.finish();
				fakeOoxml = output.toByteArray();
			}

			assertReason(Reason.INVALID_WORKBOOK, () ->
					service(defaultLimits()).extractSequences(upload(fakeOoxml)));
		}

		@Test
		void rejectsZipWithOnlyContentTypesAndNoSheetData() throws IOException {
			byte[] partialOoxml;
			try (ByteArrayOutputStream output = new ByteArrayOutputStream();
					ZipOutputStream zip = new ZipOutputStream(output)) {
				zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
				zip.write("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>".getBytes());
				zip.closeEntry();
				zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
				zip.write("<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"></workbook>".getBytes());
				zip.closeEntry();
				zip.finish();
				partialOoxml = output.toByteArray();
			}

			assertReason(Reason.INVALID_WORKBOOK, () ->
					service(defaultLimits()).extractSequences(upload(partialOoxml)));
		}

		@Test
		void rejectsDeeplyNestedZipEntries() throws IOException {
			byte[] deeplyNested;
			try (ByteArrayOutputStream output = new ByteArrayOutputStream();
					ZipOutputStream zip = new ZipOutputStream(output)) {
				// Create a deeply nested entry path that exceeds reasonable depth
				zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
				zip.write("<?xml version=\"1.0\"?><Types/>".getBytes());
				zip.closeEntry();
				zip.putNextEntry(new ZipEntry("a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/data.xml"));
				zip.write("<data/>".getBytes());
				zip.closeEntry();
				zip.finish();
				deeplyNested = output.toByteArray();
			}

			// The service should either reject or handle gracefully — must not hang
			assertThrows(WorkbookProcessingException.class, () ->
					service(defaultLimits()).extractSequences(upload(deeplyNested)));
		}
	}

	// ── Unsafe filenames ──────────────────────────────────────────────

	@Nested
	@DisplayName("Unsafe filenames")
	class UnsafeFilenameTests {

		@Test
		void rejectsPathTraversalWithDoubleDots() throws IOException {
			byte[] validWorkbook = workbook(book ->
					book.createSheet("Input").createRow(0).createCell(0).setCellValue("hello"));

			assertReason(Reason.INVALID_REQUEST, () -> service(defaultLimits()).extractSequences(
					new MockMultipartFile("file", "../../etc/passwd.xlsx", XLSX_CONTENT_TYPE, validWorkbook)));
		}

		@Test
		void rejectsAbsolutePathFilename() throws IOException {
			byte[] validWorkbook = workbook(book ->
					book.createSheet("Input").createRow(0).createCell(0).setCellValue("hello"));

			assertReason(Reason.INVALID_REQUEST, () -> service(defaultLimits()).extractSequences(
					new MockMultipartFile("file", "/tmp/evil.xlsx", XLSX_CONTENT_TYPE, validWorkbook)));
		}

		@Test
		void rejectsWindowsUncPathFilename() throws IOException {
			byte[] validWorkbook = workbook(book ->
					book.createSheet("Input").createRow(0).createCell(0).setCellValue("hello"));

			assertReason(Reason.INVALID_REQUEST, () -> service(defaultLimits()).extractSequences(
					new MockMultipartFile("file", "\\\\server\\share\\file.xlsx", XLSX_CONTENT_TYPE, validWorkbook)));
		}

		@Test
		void rejectsFilenameWithoutXlsxExtension() throws IOException {
			byte[] validWorkbook = workbook(book ->
					book.createSheet("Input").createRow(0).createCell(0).setCellValue("hello"));

			assertReason(Reason.UNSUPPORTED_TYPE, () -> service(defaultLimits()).extractSequences(
					new MockMultipartFile("file", "data.xls", XLSX_CONTENT_TYPE, validWorkbook)));
		}
	}

	// ── Empty and null-edge cases ──────────────────────────────────────

	@Nested
	@DisplayName("Empty and null-edge inputs")
	class EmptyInputTests {

		@Test
		void rejectsEmptyFile() {
			assertReason(Reason.INVALID_REQUEST, () ->
					service(defaultLimits()).extractSequences(
							new MockMultipartFile("file", "empty.xlsx", XLSX_CONTENT_TYPE, new byte[0])));
		}

		@Test
		void rejectsNullFile() {
			assertReason(Reason.INVALID_REQUEST, () ->
					service(defaultLimits()).extractSequences(null));
		}

		@Test
		void rejectsFileWithOnlyWhitespaceContent() {
			MockMultipartFile whitespace = new MockMultipartFile(
					"file", "blank.xlsx", XLSX_CONTENT_TYPE, "   \n\t  ".getBytes());

			assertReason(Reason.UNSUPPORTED_TYPE, () ->
					service(defaultLimits()).extractSequences(whitespace));
		}
	}

	// ── Content-type mismatch ──────────────────────────────────────────

	@Nested
	@DisplayName("Content-type and extension mismatches")
	class ContentTypeMismatchTests {

		@Test
		void rejectsXlsContentTypeEvenWithXlsxExtension() throws IOException {
			byte[] validWorkbook = workbook(book ->
					book.createSheet("Input").createRow(0).createCell(0).setCellValue("hello"));

			assertReason(Reason.UNSUPPORTED_TYPE, () -> service(defaultLimits()).extractSequences(
					new MockMultipartFile("file", "data.xlsx",
							"application/vnd.ms-excel", validWorkbook)));
		}

		@Test
		void rejectsXlsmContentType() throws IOException {
			byte[] validWorkbook = workbook(book ->
					book.createSheet("Input").createRow(0).createCell(0).setCellValue("hello"));

			assertReason(Reason.UNSUPPORTED_TYPE, () -> service(defaultLimits()).extractSequences(
					new MockMultipartFile("file", "data.xlsx",
							"application/vnd.ms-excel.sheet.macroEnabled.12", validWorkbook)));
		}
	}

	// ── Helpers ────────────────────────────────────────────────────────

	private ExcelService service(UploadLimitsProperties limits) {
		return new ExcelService(limits);
	}

	private UploadLimitsProperties defaultLimits() {
		return new UploadLimitsProperties(
				DataSize.ofMegabytes(5), 10, 10_000, 2_000, 100, 4_000, 1_000_000,
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
		assertEquals(expected, exception.reason(),
				() -> "Expected reason " + expected + " but got " + exception.reason()
						+ ": " + exception.getMessage());
	}
}
