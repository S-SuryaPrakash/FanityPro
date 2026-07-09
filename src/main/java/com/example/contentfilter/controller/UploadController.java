package com.example.contentfilter.controller;

import com.example.contentfilter.dto.UploadResponse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Iterator;
import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

@RestController
public class UploadController {

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UploadResponse uploadFile(MultipartHttpServletRequest request) {
		Iterator<String> fileNames = request.getFileNames();
		if (fileNames == null || !fileNames.hasNext()) {
			throw new IllegalArgumentException("No file part in the request");
		}

		String firstName = fileNames.next();
		MultipartFile file = request.getFile(firstName);
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Uploaded file must not be empty");
		}

		String preview = null;
		String filename = file.getOriginalFilename();
		String contentType = file.getContentType();

		// Only attempt to parse Excel files
		if (filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls")
				|| (contentType != null && contentType.contains("spreadsheet")))) {
			try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
				Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
				if (sheet != null) {
					StringBuilder sb = new StringBuilder();
					for (Row row : sheet) {
						for (Cell cell : row) {
							String cellValue = switch (cell.getCellType()) {
								case STRING -> cell.getStringCellValue();
								case NUMERIC -> String.valueOf(cell.getNumericCellValue());
								case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
								case FORMULA -> cell.getCellFormula();
								case BLANK -> "";
								default -> cell.toString();
							};
							sb.append(cellValue).append('\t');
						}
						sb.append('\n');
						if (sb.length() > 2000) { // limit preview size
							break;
						}
					}
					preview = sb.length() > 2000 ? sb.substring(0, 2000) : sb.toString();
				}
			} catch (Exception e) {
				// If parsing fails, return a generic note in the preview (avoid leaking internal details)
				preview = "[unreadable Excel content]";
			}
		}

		return new UploadResponse(
				filename,
				file.getSize(),
				contentType,
				preview
		);
	}
}