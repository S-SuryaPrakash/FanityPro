package com.example.contentfilter.service;

import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelPreviewService {

	private static final int PREVIEW_LIMIT = 2_000;

	/**
	 * Reads the first worksheet and returns a tab-separated text preview.
	 */
	public String createPreview(MultipartFile file) {
		try (InputStream inputStream = file.getInputStream();
				Workbook workbook = WorkbookFactory.create(inputStream)) {
			if (workbook.getNumberOfSheets() == 0) {
				return null;
			}

			Sheet sheet = workbook.getSheetAt(0);
			DataFormatter formatter = new DataFormatter();
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
			StringBuilder preview = new StringBuilder();

			for (Row row : sheet) {
				for (Cell cell : row) {
					preview.append(formatter.formatCellValue(cell, evaluator)).append('\t');
					if (preview.length() >= PREVIEW_LIMIT) {
						return preview.substring(0, PREVIEW_LIMIT);
					}
				}
				preview.append('\n');
				if (preview.length() >= PREVIEW_LIMIT) {
					return preview.substring(0, PREVIEW_LIMIT);
				}
			}

			return preview.toString();
		} catch (Exception exception) {
			throw new IllegalArgumentException("Unable to read the uploaded Excel file", exception);
		}
	}
}
