package com.example.contentfilter.service;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ConversationColumnMapping;
import com.example.contentfilter.domain.ConversationRiskAssessment;
import com.example.contentfilter.domain.ExtractedSequence;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Coordinates mapped workbook extraction and batch risk classification. */
@Service
public class ConversationRiskWorkflowService {

	private final ExcelService excelService;
	private final ClassificationService classificationService;

	public ConversationRiskWorkflowService(
			ExcelService excelService,
			ClassificationService classificationService) {
		this.excelService = excelService;
		this.classificationService = classificationService;
	}

	/**
	 * Runs the internal Module 3 workflow while retaining source coordinates and
	 * context for the later report generator.
	 */
	public ConversationRiskAssessment assess(
			MultipartFile file,
			ConversationColumnMapping mapping) {
		List<ExtractedSequence> sequences =
				excelService.extractConversationSequences(file, mapping);
		List<ClassificationResult> results = classificationService.classify(sequences);
		return new ConversationRiskAssessment(sequences, results);
	}
}
