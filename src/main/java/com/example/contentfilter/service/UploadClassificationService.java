package com.example.contentfilter.service;

import java.util.ArrayList;
import java.util.List;

import com.example.contentfilter.dto.RowClassificationResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Coordinates the complete upload-classification use case.
 *
 * <p>Excel parsing and text classification remain separate concerns: this
 * service obtains row text from {@link ExcelService} and sends each row to the
 * configured {@link ClassificationService} implementation.</p>
 */
@Service
public class UploadClassificationService {

	private final ExcelService excelService;
	private final ClassificationService classificationService;

	public UploadClassificationService(
			ExcelService excelService,
			ClassificationService classificationService) {
		this.excelService = excelService;
		this.classificationService = classificationService;
	}

	/**
	 * Extracts and classifies every non-empty row in the uploaded workbook.
	 *
	 * @param file workbook received through the upload endpoint
	 * @return immutable row-level classification results
	 */
	public List<RowClassificationResponse> classifyRows(MultipartFile file) {
		List<String> rows = excelService.extractRows(file);
		List<RowClassificationResponse> results = new ArrayList<>(rows.size());

		for (int index = 0; index < rows.size(); index++) {
			String row = rows.get(index);
			results.add(new RowClassificationResponse(
					index + 1,
					row,
					classificationService.classify(row)));
		}

		return List.copyOf(results);
	}
}
