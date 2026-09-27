package com.example.contentfilter;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.contentfilter.controller.FileClassificationController;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V2 secure review, end to end against a real PostgreSQL: Flyway migrations,
 * Hibernate schema validation, JWT login, role checks, the review queue,
 * decisions, and the append-only audit trail. Skipped when Docker is absent.
 */
@SpringBootTest(properties = {
		"content-filter.security.jwt.secret=test-only-signing-secret-of-at-least-32-bytes",
		"content-filter.security.bootstrap-admin.email=" + V2ReviewWorkflowIntegrationTests.ADMIN_EMAIL,
		"content-filter.security.bootstrap-admin.password=" + V2ReviewWorkflowIntegrationTests.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@ActiveProfiles("v2")
@Testcontainers(disabledWithoutDocker = true)
class V2ReviewWorkflowIntegrationTests {

	static final String ADMIN_EMAIL = "admin@contentfilter.test";
	static final String ADMIN_PASSWORD = "bootstrap-admin-password";
	private static final String ANALYST_PASSWORD = "analyst-password-123";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void bootstrapAdminCanLogInAndIdentifyItself() throws Exception {
		String token = login(ADMIN_EMAIL.toUpperCase(), ADMIN_PASSWORD);

		mockMvc.perform(get("/api/v2/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void wrongPasswordIsRejectedWithoutRevealingWhy() throws Exception {
		mockMvc.perform(post("/api/v2/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(ADMIN_EMAIL, "not-the-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.correlationId").exists());

		mockMvc.perform(post("/api/v2/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials("nobody@contentfilter.test", "not-the-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
	}

	@Test
	void v2EndpointsRequireAValidBearerToken() throws Exception {
		mockMvc.perform(get("/api/v2/cases"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

		String tampered = login(ADMIN_EMAIL, ADMIN_PASSWORD) + "x";
		mockMvc.perform(get("/api/v2/cases").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
	}

	@Test
	void analystsCannotManageAccountsOrReadTheAuditTrail() throws Exception {
		String analyst = login(createAnalyst(), ANALYST_PASSWORD);

		mockMvc.perform(get("/api/v2/users").header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/v2/audit-events").header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isForbidden());
	}

	@Test
	void duplicateAndWeakAccountsAreRejected() throws Exception {
		String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
		String email = createAnalyst();

		mockMvc.perform(post("/api/v2/users")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(newUser(email.toUpperCase(), ANALYST_PASSWORD, "ANALYST")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"));

		mockMvc.perform(post("/api/v2/users")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(newUser(uniqueEmail(), "short", "ANALYST")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void analystUploadsReviewsAndDecidesCasesWithAFullAuditTrail() throws Exception {
		String analyst = login(createAnalyst(), ANALYST_PASSWORD);

		// Three messages: a threat, an insult, and one with no automated flag.
		String batchJson = mockMvc.perform(multipart("/api/v2/batches")
						.file(workbook("I will kill you", "you are stupid", "hello there"))
						.header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalSequences").value(3))
				.andExpect(jsonPath("$.reviewCaseCount").value(2))
				.andExpect(jsonPath("$.openCaseCount").value(2))
				.andReturn().getResponse().getContentAsString();
		String batchId = JsonPath.read(batchJson, "$.id");

		// Data minimization: the unflagged message is never persisted.
		assertEquals(0, jdbc.queryForObject(
				"select count(*) from review_cases where message_text = 'hello there'", Integer.class));

		String queueJson = mockMvc.perform(get("/api/v2/cases")
						.param("batchId", batchId)
						.param("status", "OPEN")
						.header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content", hasSize(2)))
				// Most severe first.
				.andExpect(jsonPath("$.content[0].primaryCategory").value("THREAT"))
				.andExpect(jsonPath("$.content[0].severity").value("CRITICAL"))
				.andExpect(jsonPath("$.content[1].primaryCategory").value("HARASSMENT_OR_INSULT"))
				.andReturn().getResponse().getContentAsString();
		String threatCase = JsonPath.read(queueJson, "$.content[0].id");
		String insultCase = JsonPath.read(queueJson, "$.content[1].id");

		mockMvc.perform(get("/api/v2/cases/{id}", threatCase)
						.header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.text").value("I will kill you"))
				.andExpect(jsonPath("$.excelRow").value(2))
				.andExpect(jsonPath("$.scores.THREAT").value(0.96));

		mockMvc.perform(decide(analyst, threatCase, "{\"decision\":\"CONFIRMED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.finalCategory").value("THREAT"));

		mockMvc.perform(decide(analyst, threatCase,
						"{\"decision\":\"DISMISSED\",\"note\":\"second thoughts\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("CASE_ALREADY_RESOLVED"));

		mockMvc.perform(decide(analyst, insultCase,
						"{\"decision\":\"OVERRIDDEN\",\"finalCategory\":\"THREAT\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REVIEW_DECISION"));

		mockMvc.perform(decide(analyst, insultCase, "{\"decision\":\"MAYBE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));

		mockMvc.perform(decide(analyst, insultCase,
						"{\"decision\":\"OVERRIDDEN\",\"finalCategory\":\"THREAT\","
								+ "\"note\":\"Implied threat in context.\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.finalCategory").value("THREAT"))
				.andExpect(jsonPath("$.decisionNote").value("Implied threat in context."));

		mockMvc.perform(get("/api/v2/batches/{id}", batchId)
						.header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openCaseCount").value(0))
				.andExpect(jsonPath("$.resolvedCaseCount").value(2));

		String admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
		mockMvc.perform(get("/api/v2/audit-events")
						.param("entityType", "REVIEW_CASE")
						.param("entityId", insultCase)
						.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].action").value("CASE_DECIDED"))
				.andExpect(jsonPath("$.content[0].details.decision").value("OVERRIDDEN"))
				.andExpect(jsonPath("$.content[0].details.finalCategory").value("THREAT"));
	}

	@Test
	void unknownCasesAndMalformedIdsAreClientErrors() throws Exception {
		String analyst = login(createAnalyst(), ANALYST_PASSWORD);

		mockMvc.perform(get("/api/v2/cases/{id}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
		mockMvc.perform(get("/api/v2/cases/not-a-uuid")
						.header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_PARAMETER"));
		mockMvc.perform(get("/api/v2/cases").param("size", "500")
						.header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theAuditTrailIsAppendOnlyInTheDatabase() throws Exception {
		login(ADMIN_EMAIL, ADMIN_PASSWORD);

		DataAccessException update = assertThrows(DataAccessException.class,
				() -> jdbc.update("update audit_events set action = 'TAMPERED'"));
		assertEquals(true, update.getMessage().contains("append-only"));
		assertThrows(DataAccessException.class, () -> jdbc.update("delete from audit_events"));
	}

	@Test
	void v1ClassificationStaysAvailableWithoutATokenUnderTheV2Profile() throws Exception {
		mockMvc.perform(multipart("/api/v1/files/classify").file(workbook("I will kill you")))
				.andExpect(status().isOk());
	}

	private String createAnalyst() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/v2/users")
						.header(HttpHeaders.AUTHORIZATION, bearer(login(ADMIN_EMAIL, ADMIN_PASSWORD)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(newUser(email, ANALYST_PASSWORD, "ANALYST")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("ANALYST"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
		return email;
	}

	private String login(String email, String password) throws Exception {
		String body = mockMvc.perform(post("/api/v2/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private RequestBuilder decide(
			String token, String caseId, String json) {
		return post("/api/v2/cases/{id}/decision", caseId)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String uniqueEmail() {
		return "analyst-" + UUID.randomUUID() + "@contentfilter.test";
	}

	private static String credentials(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String newUser(String email, String password, String role) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}";
	}

	private static MockMultipartFile workbook(String... messages) throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			XSSFSheet sheet = workbook.createSheet("Input");
			sheet.createRow(0).createCell(0).setCellValue("text");
			for (int index = 0; index < messages.length; index++) {
				sheet.createRow(index + 1).createCell(0).setCellValue(messages[index]);
			}
			workbook.write(output);
			return new MockMultipartFile("file", "conversation.xlsx",
					FileClassificationController.XLSX_MEDIA_TYPE, output.toByteArray());
		}
	}
}
