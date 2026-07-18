package com.example.contentfilter.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Integration tests for the multipart-to-workbook HTTP boundary. */
@SpringBootTest
@AutoConfigureMockMvc
class UploadControllerTests {

	private static final String XLSX_CONTENT_TYPE =
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void requiresTheExactFilePartName() throws Exception {
		MockMultipartFile wrongPart =
				new MockMultipartFile("workbook", "sample.xlsx", XLSX_CONTENT_TYPE, validWorkbook());

		mockMvc.perform(multipart("/upload").file(wrongPart))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_UPLOAD"));
	}

	@Test
	void rejectsUnsupportedWorkbookWithProblemDetails() throws Exception {
		MockMultipartFile legacyExtension =
				new MockMultipartFile("file", "sample.xls", XLSX_CONTENT_TYPE, validWorkbook());

		mockMvc.perform(multipart("/upload").file(legacyExtension))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_WORKBOOK_TYPE"));
	}

	@Test
	void returnsPhysicalRowNumberForAValidWorkbook() throws Exception {
		MockMultipartFile file =
				new MockMultipartFile("file", "sample.xlsx", XLSX_CONTENT_TYPE, validWorkbook());

		mockMvc.perform(multipart("/upload").file(file))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].rowNumber").value(3))
				.andExpect(jsonPath("$.results[0].text").value("hello"));
	}

	private byte[] validWorkbook() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			workbook.createSheet("Input").createRow(2).createCell(1).setCellValue("hello");
			workbook.write(output);
			return output.toByteArray();
		}
	}
}
