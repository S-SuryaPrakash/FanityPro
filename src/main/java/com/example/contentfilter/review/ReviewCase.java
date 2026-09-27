package com.example.contentfilter.review;

import com.example.contentfilter.account.AppUser;
import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ConversationContext;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import com.example.contentfilter.exception.ConflictException;
import com.example.contentfilter.exception.InvalidReviewDecisionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * One review-required message awaiting, or carrying, a human decision.
 *
 * <p>The automated evidence (category, severity, scores, versions) is fixed at
 * creation. Only {@link #resolve} changes state, exactly once; optimistic
 * locking stops two reviewers from deciding the same case concurrently.</p>
 */
@Entity
@Table(name = "review_cases")
public class ReviewCase {

	public static final int MAX_NOTE_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "batch_id", nullable = false)
	private ClassificationBatch batch;

	@Column(name = "sequence_id", nullable = false, length = 128)
	private String sequenceId;

	@Column(nullable = false)
	private int priority;

	@Column(name = "excel_row", nullable = false)
	private int excelRow;

	@Column(name = "conversation_id")
	private String conversationId;

	@Column(name = "message_id")
	private String messageId;

	@Column(name = "speaker_role")
	private String speakerRole;

	@Column(name = "message_text", nullable = false)
	private String messageText;

	@Enumerated(EnumType.STRING)
	@Column(name = "primary_category", nullable = false, length = 32)
	private RiskCategory primaryCategory;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RiskSeverity severity;

	@Column(name = "severity_rank", nullable = false)
	private int severityRank;

	@Column(nullable = false)
	private double confidence;

	@Column(name = "score_threat", nullable = false)
	private double scoreThreat;

	@Column(name = "score_hate_or_identity_attack", nullable = false)
	private double scoreHateOrIdentityAttack;

	@Column(name = "score_harassment_or_insult", nullable = false)
	private double scoreHarassmentOrInsult;

	@Column(name = "score_obscene_or_profane", nullable = false)
	private double scoreObsceneOrProfane;

	@Column(name = "score_general_toxicity", nullable = false)
	private double scoreGeneralToxicity;

	@Column(name = "review_reason", nullable = false)
	private String reviewReason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ReviewCaseStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private ReviewDecision decision;

	@Enumerated(EnumType.STRING)
	@Column(name = "final_category", length = 32)
	private RiskCategory finalCategory;

	@Column(name = "decision_note")
	private String decisionNote;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by")
	private AppUser decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ReviewCase() {
	}

	public ReviewCase(
			ClassificationBatch batch,
			int priority,
			ExtractedSequence sequence,
			ClassificationResult result,
			Instant createdAt) {
		if (!result.manualReviewRequired()) {
			throw new IllegalArgumentException("Only review-required results become cases.");
		}
		ConversationContext context = sequence.context();
		this.batch = batch;
		this.sequenceId = sequence.sequenceId();
		this.priority = priority;
		this.excelRow = sequence.rowIndex() + 1;
		this.conversationId = context.conversationId();
		this.messageId = context.messageId();
		this.speakerRole = context.speakerRole();
		this.messageText = sequence.text();
		this.primaryCategory = result.primaryCategory();
		this.severity = result.severity();
		this.severityRank = result.severity().ordinal();
		this.confidence = result.confidence();
		this.scoreThreat = score(result, RiskCategory.THREAT);
		this.scoreHateOrIdentityAttack = score(result, RiskCategory.HATE_OR_IDENTITY_ATTACK);
		this.scoreHarassmentOrInsult = score(result, RiskCategory.HARASSMENT_OR_INSULT);
		this.scoreObsceneOrProfane = score(result, RiskCategory.OBSCENE_OR_PROFANE);
		this.scoreGeneralToxicity = score(result, RiskCategory.GENERAL_TOXICITY);
		this.reviewReason = result.reviewReason();
		this.status = ReviewCaseStatus.OPEN;
		this.createdAt = createdAt;
	}

	/**
	 * Records the reviewer's decision.
	 *
	 * <ul>
	 *   <li>{@code CONFIRMED} keeps the automated category, so it is only valid
	 *       when the model actually picked a risk category.</li>
	 *   <li>{@code OVERRIDDEN} requires a different risk category and a note.</li>
	 *   <li>{@code DISMISSED} marks a false positive and requires a note.</li>
	 * </ul>
	 */
	public void resolve(
			ReviewDecision decision,
			RiskCategory requestedCategory,
			String note,
			AppUser reviewer,
			Instant now) {
		if (status == ReviewCaseStatus.RESOLVED) {
			throw new ConflictException("CASE_ALREADY_RESOLVED",
					"This case has already been decided.");
		}
		if (decision == null) {
			throw new InvalidReviewDecisionException("A decision is required.");
		}
		String normalizedNote = note == null || note.isBlank() ? null : note.strip();
		if (normalizedNote != null && normalizedNote.length() > MAX_NOTE_LENGTH) {
			throw new InvalidReviewDecisionException(
					"The decision note must not exceed " + MAX_NOTE_LENGTH + " characters.");
		}

		RiskCategory resolvedCategory = switch (decision) {
			case CONFIRMED -> {
				if (requestedCategory != null && requestedCategory != primaryCategory) {
					throw new InvalidReviewDecisionException(
							"A confirmation keeps the automated category; use OVERRIDDEN to change it.");
				}
				if (!primaryCategory.isDetectedRisk()) {
					throw new InvalidReviewDecisionException(
							"There is no automated risk category to confirm; "
									+ "use OVERRIDDEN or DISMISSED.");
				}
				yield primaryCategory;
			}
			case OVERRIDDEN -> {
				if (requestedCategory == null || !requestedCategory.isDetectedRisk()) {
					throw new InvalidReviewDecisionException(
							"An override requires a risk category as the final category.");
				}
				if (requestedCategory == primaryCategory) {
					throw new InvalidReviewDecisionException(
							"The final category matches the automated one; use CONFIRMED.");
				}
				requireNote(normalizedNote, decision);
				yield requestedCategory;
			}
			case DISMISSED -> {
				if (requestedCategory != null && requestedCategory != RiskCategory.NO_AUTOMATED_FLAG) {
					throw new InvalidReviewDecisionException(
							"A dismissal always resolves to NO_AUTOMATED_FLAG.");
				}
				requireNote(normalizedNote, decision);
				yield RiskCategory.NO_AUTOMATED_FLAG;
			}
		};

		this.status = ReviewCaseStatus.RESOLVED;
		this.decision = decision;
		this.finalCategory = resolvedCategory;
		this.decisionNote = normalizedNote;
		this.decidedBy = reviewer;
		this.decidedAt = now;
	}

	private static void requireNote(String note, ReviewDecision decision) {
		if (note == null) {
			throw new InvalidReviewDecisionException(
					"A note explaining the decision is required for " + decision + ".");
		}
	}

	private static double score(ClassificationResult result, RiskCategory category) {
		return result.scores().getOrDefault(category, 0.0);
	}

	public UUID getId() {
		return id;
	}

	public ClassificationBatch getBatch() {
		return batch;
	}

	public String getSequenceId() {
		return sequenceId;
	}

	public int getPriority() {
		return priority;
	}

	public int getExcelRow() {
		return excelRow;
	}

	public String getConversationId() {
		return conversationId;
	}

	public String getMessageId() {
		return messageId;
	}

	public String getSpeakerRole() {
		return speakerRole;
	}

	public String getMessageText() {
		return messageText;
	}

	public RiskCategory getPrimaryCategory() {
		return primaryCategory;
	}

	public RiskSeverity getSeverity() {
		return severity;
	}

	public double getConfidence() {
		return confidence;
	}

	public double getScoreThreat() {
		return scoreThreat;
	}

	public double getScoreHateOrIdentityAttack() {
		return scoreHateOrIdentityAttack;
	}

	public double getScoreHarassmentOrInsult() {
		return scoreHarassmentOrInsult;
	}

	public double getScoreObsceneOrProfane() {
		return scoreObsceneOrProfane;
	}

	public double getScoreGeneralToxicity() {
		return scoreGeneralToxicity;
	}

	public String getReviewReason() {
		return reviewReason;
	}

	public ReviewCaseStatus getStatus() {
		return status;
	}

	public ReviewDecision getDecision() {
		return decision;
	}

	public RiskCategory getFinalCategory() {
		return finalCategory;
	}

	public String getDecisionNote() {
		return decisionNote;
	}

	public AppUser getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
