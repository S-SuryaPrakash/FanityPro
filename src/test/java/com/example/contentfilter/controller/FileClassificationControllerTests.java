package com.example.contentfilter.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.contentfilter.service.ExcelReportService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end HTTP tests for the versioned annotated-workbook download. */
@SpringBootTest
@AutoConfigureMockMvc
class FileClassificationControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsAClassifiedWorkbookAsANonCacheableDownload() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "conversation.XLSX", FileClassificationController.XLSX_MEDIA_TYPE,
				conversationWorkbook());

		MvcResult response = mockMvc.perform(multipart("/api/v1/files/classify").file(file))
				.andExpect(status().isOk())
				.andExpect(content().contentType(FileClassificationController.XLSX_MEDIA_TYPE))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						containsString("classified-conversation.xlsx")))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andReturn();

		try (XSSFWorkbook workbook = new XSSFWorkbook(
				new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
			XSSFSheet source = workbook.getSheet("Input");
			assertNotNull(source);
			assertEquals("Threat", source.getRow(1).getCell(1).getStringCellValue());
			assertNotNull(workbook.getSheet(ExcelReportService.REVIEW_QUEUE_SHEET));
			assertNotNull(workbook.getSheet(ExcelReportService.SUMMARY_SHEET));
			assertNotNull(workbook.getSheet(ExcelReportService.LEGEND_SHEET));
		}
	}

	@Test
	void rejectsARequestWithoutTheRequiredFilePart() throws Exception {
		mockMvc.perform(multipart("/api/v1/files/classify"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_UPLOAD"));
	}

	@Test
	void rejectsAWorkbookWithoutAnyMessages() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "empty.xlsx", FileClassificationController.XLSX_MEDIA_TYPE,
				headerOnlyWorkbook());

		mockMvc.perform(multipart("/api/v1/files/classify").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_WORKBOOK"));
	}

	private byte[] conversationWorkbook() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			XSSFSheet sheet = workbook.createSheet("Input");
			XSSFRow header = sheet.createRow(0);
			header.createCell(0).setCellValue("text");
			XSSFRow message = sheet.createRow(1);
			message.createCell(0).setCellValue("I will kill you");
			workbook.write(output);
			return output.toByteArray();
		}
	}

	private byte[] headerOnlyWorkbook() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			workbook.createSheet("Input").createRow(0).createCell(0).setCellValue("text");
			workbook.write(output);
			return output.toByteArray();
		}
	}
}
