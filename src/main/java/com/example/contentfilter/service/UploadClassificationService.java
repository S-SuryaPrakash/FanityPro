package com.example.contentfilter.service;

import java.util.ArrayList;
import java.util.List;

import com.example.contentfilter.domain.ExtractedSequence;
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
	private final LegacyClassificationService classificationService;

	public UploadClassificationService(
			ExcelService excelService,
			LegacyClassificationService classificationService) {
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
		List<ExtractedSequence> sequences = excelService.extractSequences(file);
		List<RowClassificationResponse> results = new ArrayList<>(sequences.size());

		for (ExtractedSequence sequence : sequences) {
			results.add(new RowClassificationResponse(
					sequence.rowIndex() + 1,
					sequence.text(),
					classificationService.classify(sequence.text())));
		}

		return List.copyOf(results);
	}
}
