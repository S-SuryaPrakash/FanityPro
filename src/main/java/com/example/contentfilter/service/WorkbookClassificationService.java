package com.example.contentfilter.service;

import com.example.contentfilter.domain.ClassifiedWorkbook;
import com.example.contentfilter.domain.ConversationColumnMapping;
import com.example.contentfilter.domain.ConversationRiskAssessment;
import com.example.contentfilter.exception.WorkbookProcessingException;
import com.example.contentfilter.exception.WorkbookProcessingException.Reason;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Coordinates extraction, classification, policy evaluation, and workbook reporting. */
@Service
public class WorkbookClassificationService {

	private static final int MAX_OUTPUT_FILENAME_LENGTH = 255;
	private static final String OUTPUT_PREFIX = "classified-";

	private final ConversationRiskWorkflowService riskWorkflow;
	private final ExcelReportService reportService;

	public WorkbookClassificationService(
			ConversationRiskWorkflowService riskWorkflow,
			ExcelReportService reportService) {
		this.riskWorkflow = riskWorkflow;
		this.reportService = reportService;
	}

	/** Runs the standard V1 conversation workbook contract from upload to download. */
	public ClassifiedWorkbook classify(MultipartFile file) {
		ConversationColumnMapping mapping = ConversationColumnMapping.standard();
		ConversationRiskAssessment assessment = riskWorkflow.assess(file, mapping);
		if (assessment.sequences().isEmpty()) {
			throw new WorkbookProcessingException(
					Reason.INVALID_WORKBOOK,
					"The first worksheet does not contain any non-empty messages.");
		}
		byte[] report = reportService.generate(file, mapping, assessment);
		return new ClassifiedWorkbook(outputFilename(file.getOriginalFilename()), report);
	}

	private String outputFilename(String originalFilename) {
		String original = originalFilename == null ? "workbook.xlsx" : originalFilename;
		String extension = ".xlsx";
		String basename = original.toLowerCase(java.util.Locale.ROOT).endsWith(extension)
				? original.substring(0, original.length() - extension.length())
				: original;
		String sanitizedBasename = basename.replaceAll("[\\p{Cntrl}]", "_");
		int maximumBasenameLength = MAX_OUTPUT_FILENAME_LENGTH
				- OUTPUT_PREFIX.length() - extension.length();
		if (sanitizedBasename.length() > maximumBasenameLength) {
			sanitizedBasename = sanitizedBasename.substring(0, maximumBasenameLength);
		}
		return OUTPUT_PREFIX + sanitizedBasename + extension;
	}
}
