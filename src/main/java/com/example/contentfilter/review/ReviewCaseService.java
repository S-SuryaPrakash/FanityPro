package com.example.contentfilter.review;

import com.example.contentfilter.account.AppUser;
import com.example.contentfilter.account.AppUserRepository;
import com.example.contentfilter.audit.AuditAction;
import com.example.contentfilter.audit.AuditService;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import com.example.contentfilter.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The reviewer queue: list, inspect, and decide review cases. */
@Service
@Profile("v2")
public class ReviewCaseService {

	public static final int MAX_PAGE_SIZE = 100;
	private static final int PREVIEW_LENGTH = 200;

	/** Most severe first, then most confident, then oldest; id breaks ties for stable paging. */
	private static final Sort QUEUE_SORT = Sort.by(
			Sort.Order.desc("severityRank"),
			Sort.Order.desc("confidence"),
			Sort.Order.asc("createdAt"),
			Sort.Order.asc("id"));

	private final ReviewCaseRepository cases;
	private final AppUserRepository users;
	private final AuditService audit;
	private final Clock clock;

	public ReviewCaseService(
			ReviewCaseRepository cases,
			AppUserRepository users,
			AuditService audit,
			Clock clock) {
		this.cases = cases;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<CaseSummary> list(
			ReviewCaseStatus status,
			UUID batchId,
			RiskSeverity severity,
			RiskCategory category,
			int page,
			int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException(
					"page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return cases.findAll(
						ReviewCaseRepository.matching(status, batchId, severity, category),
						PageRequest.of(page, size, QUEUE_SORT))
				.map(CaseSummary::from);
	}

	@Transactional(readOnly = true)
	public CaseDetail get(UUID caseId) {
		return CaseDetail.from(find(caseId));
	}

	/**
	 * Records a decision once. A second attempt gets 409 CASE_ALREADY_RESOLVED;
	 * a truly concurrent one loses on the version check with 409 as well.
	 */
	@Transactional
	public CaseDetail decide(
			UUID caseId,
			UUID reviewerId,
			ReviewDecision decision,
			RiskCategory finalCategory,
			String note) {
		ReviewCase reviewCase = find(caseId);
		AppUser reviewer = users.getReferenceById(reviewerId);
		reviewCase.resolve(decision, finalCategory, note, reviewer, clock.instant());
		cases.saveAndFlush(reviewCase);

		audit.record(reviewerId, AuditAction.CASE_DECIDED, "REVIEW_CASE", caseId, Map.of(
				"decision", reviewCase.getDecision().name(),
				"primaryCategory", reviewCase.getPrimaryCategory().name(),
				"finalCategory", reviewCase.getFinalCategory().name()));
		return CaseDetail.from(reviewCase);
	}

	private ReviewCase find(UUID caseId) {
		return cases.findById(caseId)
				.orElseThrow(() -> new ResourceNotFoundException("Review case not found."));
	}

	private static String preview(String text) {
		return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
	}

	/** Queue row: enough to triage without loading the full message. */
	public record CaseSummary(
			UUID id,
			UUID batchId,
			int priority,
			String textPreview,
			RiskCategory primaryCategory,
			RiskSeverity severity,
			double confidence,
			ReviewCaseStatus status,
			ReviewDecision decision,
			Instant createdAt) {

		static CaseSummary from(ReviewCase c) {
			return new CaseSummary(c.getId(), c.getBatch().getId(), c.getPriority(),
					preview(c.getMessageText()), c.getPrimaryCategory(), c.getSeverity(),
					c.getConfidence(), c.getStatus(), c.getDecision(), c.getCreatedAt());
		}
	}

	public record CaseDetail(
			UUID id,
			UUID batchId,
			int priority,
			int excelRow,
			String conversationId,
			String messageId,
			String speakerRole,
			String text,
			RiskCategory primaryCategory,
			RiskSeverity severity,
			double confidence,
			Map<RiskCategory, Double> scores,
			String reviewReason,
			ReviewCaseStatus status,
			ReviewDecision decision,
			RiskCategory finalCategory,
			String decisionNote,
			UUID decidedBy,
			Instant decidedAt,
			long version,
			Instant createdAt) {

		static CaseDetail from(ReviewCase c) {
			return new CaseDetail(
					c.getId(),
					c.getBatch().getId(),
					c.getPriority(),
					c.getExcelRow(),
					c.getConversationId(),
					c.getMessageId(),
					c.getSpeakerRole(),
					c.getMessageText(),
					c.getPrimaryCategory(),
					c.getSeverity(),
					c.getConfidence(),
					scoresOf(c),
					c.getReviewReason(),
					c.getStatus(),
					c.getDecision(),
					c.getFinalCategory(),
					c.getDecisionNote(),
					c.getDecidedBy() == null ? null : c.getDecidedBy().getId(),
					c.getDecidedAt(),
					c.getVersion(),
					c.getCreatedAt());
		}

		/** EnumMap keeps the categories in taxonomy order in the JSON output. */
		private static Map<RiskCategory, Double> scoresOf(ReviewCase c) {
			EnumMap<RiskCategory, Double> scores = new EnumMap<>(RiskCategory.class);
			scores.put(RiskCategory.THREAT, c.getScoreThreat());
			scores.put(RiskCategory.HATE_OR_IDENTITY_ATTACK, c.getScoreHateOrIdentityAttack());
			scores.put(RiskCategory.HARASSMENT_OR_INSULT, c.getScoreHarassmentOrInsult());
			scores.put(RiskCategory.OBSCENE_OR_PROFANE, c.getScoreObsceneOrProfane());
			scores.put(RiskCategory.GENERAL_TOXICITY, c.getScoreGeneralToxicity());
			return Collections.unmodifiableMap(scores);
		}
	}
}
