package com.example.contentfilter.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Extracts row-oriented text from Excel workbooks using Apache POI.
 *
 * <p>Only the first worksheet is processed. Cell values in a row are joined
 * with tab characters so downstream classifiers receive one string per row.</p>
 */
@Service
public class ExcelService {

	/**
	 * Extracts each non-empty row from the first worksheet as tab-separated text.
	 * Formulas are evaluated and displayed cell values are preserved through
	 * Apache POI's {@code DataFormatter}.
	 *
	 * @param file uploaded Excel workbook
	 * @return immutable list containing one string per non-empty row
	 * @throws IllegalArgumentException if the workbook cannot be opened or read
	 */
	public List<String> extractRows(MultipartFile file) {
		try (InputStream inputStream = file.getInputStream();
				Workbook workbook = WorkbookFactory.create(inputStream)) {
			if (workbook.getNumberOfSheets() == 0) {
				return List.of();
			}

			Sheet sheet = workbook.getSheetAt(0);
			DataFormatter formatter = new DataFormatter();
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
			List<String> rows = new ArrayList<>();

			for (Row row : sheet) {
				StringJoiner rowText = new StringJoiner("\t");
				for (Cell cell : row) {
					rowText.add(formatter.formatCellValue(cell, evaluator));
				}

				String extractedRow = rowText.toString().trim();
				if (!extractedRow.isEmpty()) {
					rows.add(extractedRow);
				}
			}

			return List.copyOf(rows);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Unable to read the uploaded Excel file", exception);
		}
	}
}
