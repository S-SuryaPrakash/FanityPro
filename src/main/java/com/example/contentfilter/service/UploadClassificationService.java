package com.example.contentfilter.service;

import java.util.ArrayList;
import java.util.List;

import com.example.contentfilter.dto.RowClassificationResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
