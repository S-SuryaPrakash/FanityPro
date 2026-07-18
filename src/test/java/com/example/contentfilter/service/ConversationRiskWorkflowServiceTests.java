package com.example.contentfilter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.contentfilter.config.RiskPolicyProperties;
import com.example.contentfilter.config.UploadLimitsProperties;
import com.example.contentfilter.domain.ConversationColumnMapping;
import com.example.contentfilter.domain.ConversationRiskAssessment;
import com.example.contentfilter.domain.RiskCategory;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/** Exercises the complete internal Module 3 workbook-to-decision workflow. */
class ConversationRiskWorkflowServiceTests {

	private static final String XLSX_CONTENT_TYPE =
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Test
	void mapsClassifiesAndCorrelatesAConversationMessage() throws Exception {
		ExcelService excelService = new ExcelService(new UploadLimitsProperties(
				DataSize.ofMegabytes(5), 10, 10_000, 2_000, 100, 4_000,
				1_000_000, Duration.ofSeconds(60)));
		VersionedRiskPolicy policy = new VersionedRiskPolicy(policyProperties());
		ClassificationService classifier = new RiskClassificationService(
				new DeterministicRiskModel(), policy);
		ConversationRiskWorkflowService workflow =
				new ConversationRiskWorkflowService(excelService, classifier);

		ConversationRiskAssessment assessment = workflow.assess(
				upload(), ConversationColumnMapping.standard());

		assertEquals(1, assessment.sequences().size());
		assertEquals("conversation-7", assessment.sequences().getFirst().context().conversationId());
		assertEquals(RiskCategory.HARASSMENT_OR_INSULT,
				assessment.results().getFirst().primaryCategory());
		assertTrue(assessment.results().getFirst().manualReviewRequired());
		assertEquals(assessment.sequences().getFirst().sequenceId(),
				assessment.results().getFirst().sequenceId());
	}

	private MockMultipartFile upload() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			XSSFSheet sheet = workbook.createSheet("Input");
			XSSFRow header = sheet.createRow(0);
			header.createCell(0).setCellValue("conversation_id");
			header.createCell(1).setCellValue("text");
			XSSFRow message = sheet.createRow(1);
			message.createCell(0).setCellValue("conversation-7");
			message.createCell(1).setCellValue("You are stupid");
			workbook.write(output);
			return new MockMultipartFile(
					"file", "conversation.xlsx", XLSX_CONTENT_TYPE, output.toByteArray());
		}
	}

	private RiskPolicyProperties policyProperties() {
		return new RiskPolicyProperties(
				"risk-policy-test-v1",
				0.05,
				Map.of(
						RiskCategory.THREAT, 0.80,
						RiskCategory.HATE_OR_IDENTITY_ATTACK, 0.80,
						RiskCategory.HARASSMENT_OR_INSULT, 0.75,
						RiskCategory.OBSCENE_OR_PROFANE, 0.75,
						RiskCategory.GENERAL_TOXICITY, 0.70),
				List.of(
						RiskCategory.THREAT,
						RiskCategory.HATE_OR_IDENTITY_ATTACK,
						RiskCategory.HARASSMENT_OR_INSULT,
						RiskCategory.OBSCENE_OR_PROFANE,
						RiskCategory.GENERAL_TOXICITY));
	}
}
