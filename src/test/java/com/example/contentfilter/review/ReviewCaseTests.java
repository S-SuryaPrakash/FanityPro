package com.example.contentfilter.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import com.example.contentfilter.exception.ConflictException;
import com.example.contentfilter.exception.InvalidReviewDecisionException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Decision rules enforced by {@link ReviewCase#resolve}, independent of HTTP and the database. */
class ReviewCaseTests {

	private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

	@Test
	void createsAnOpenCaseCarryingTheAutomatedEvidence() {
		ReviewCase reviewCase = caseFor(RiskCategory.THREAT, RiskSeverity.CRITICAL);

		assertEquals(ReviewCaseStatus.OPEN, reviewCase.getStatus());
		assertEquals(3, reviewCase.getExcelRow());
		assertEquals(0.96, reviewCase.getScoreThreat());
		assertEquals(0.0, reviewCase.getScoreGeneralToxicity());
		assertNull(reviewCase.getDecision());
	}

	@Test
	void rejectsResultsThatDoNotRequireReview() {
		ClassificationResult noFlag = new ClassificationResult("sheet-0-row-2",
				RiskCategory.NO_AUTOMATED_FLAG, RiskSeverity.NONE, 0.99, Map.of(), false, null,
				"model", "rev", "policy");

		assertThrows(IllegalArgumentException.class,
				() -> new ReviewCase(null, 1, sequence(), noFlag, NOW));
	}

	@Test
	void confirmationKeepsTheAutomatedCategory() {
		ReviewCase reviewCase = caseFor(RiskCategory.THREAT, RiskSeverity.CRITICAL);

		reviewCase.resolve(ReviewDecision.CONFIRMED, null, "  ", null, NOW);

		assertEquals(ReviewCaseStatus.RESOLVED, reviewCase.getStatus());
		assertEquals(RiskCategory.THREAT, reviewCase.getFinalCategory());
		assertNull(reviewCase.getDecisionNote());
		assertEquals(NOW, reviewCase.getDecidedAt());
	}

	@Test
	void cannotConfirmAnUncertainManualReviewResult() {
		ReviewCase reviewCase = caseFor(RiskCategory.MANUAL_REVIEW, RiskSeverity.LOW);

		assertThrows(InvalidReviewDecisionException.class,
				() -> reviewCase.resolve(ReviewDecision.CONFIRMED, null, null, null, NOW));
		assertEquals(ReviewCaseStatus.OPEN, reviewCase.getStatus());
	}

	@Test
	void confirmationCannotChangeTheCategory() {
		ReviewCase reviewCase = caseFor(RiskCategory.THREAT, RiskSeverity.CRITICAL);

		assertThrows(InvalidReviewDecisionException.class, () -> reviewCase.resolve(
				ReviewDecision.CONFIRMED, RiskCategory.GENERAL_TOXICITY, null, null, NOW));
	}

	@Test
	void overrideRequiresADifferentRiskCategoryAndANote() {
		ReviewCase reviewCase = caseFor(RiskCategory.HARASSMENT_OR_INSULT, RiskSeverity.MEDIUM);

		assertThrows(InvalidReviewDecisionException.class, () -> reviewCase.resolve(
				ReviewDecision.OVERRIDDEN, null, "note", null, NOW));
		assertThrows(InvalidReviewDecisionException.class, () -> reviewCase.resolve(
				ReviewDecision.OVERRIDDEN, RiskCategory.HARASSMENT_OR_INSULT, "note", null, NOW));
		assertThrows(InvalidReviewDecisionException.class, () -> reviewCase.resolve(
				ReviewDecision.OVERRIDDEN, RiskCategory.NO_AUTOMATED_FLAG, "note", null, NOW));
		assertThrows(InvalidReviewDecisionException.class, () -> reviewCase.resolve(
				ReviewDecision.OVERRIDDEN, RiskCategory.THREAT, null, null, NOW));

		reviewCase.resolve(ReviewDecision.OVERRIDDEN, RiskCategory.THREAT,
				" Implied physical harm. ", null, NOW);

		assertEquals(RiskCategory.THREAT, reviewCase.getFinalCategory());
		assertEquals("Implied physical harm.", reviewCase.getDecisionNote());
	}

	@Test
	void dismissalResolvesToNoFlagAndRequiresANote() {
		ReviewCase reviewCase = caseFor(RiskCategory.MANUAL_REVIEW, RiskSeverity.LOW);

		assertThrows(InvalidReviewDecisionException.class,
				() -> reviewCase.resolve(ReviewDecision.DISMISSED, null, null, null, NOW));
		assertThrows(InvalidReviewDecisionException.class, () -> reviewCase.resolve(
				ReviewDecision.DISMISSED, RiskCategory.THREAT, "note", null, NOW));

		reviewCase.resolve(ReviewDecision.DISMISSED, null, "Quoted song lyric.", null, NOW);

		assertEquals(RiskCategory.NO_AUTOMATED_FLAG, reviewCase.getFinalCategory());
	}

	@Test
	void rejectsOverlongNotes() {
		ReviewCase reviewCase = caseFor(RiskCategory.THREAT, RiskSeverity.CRITICAL);
		String note = "x".repeat(ReviewCase.MAX_NOTE_LENGTH + 1);

		assertThrows(InvalidReviewDecisionException.class,
				() -> reviewCase.resolve(ReviewDecision.DISMISSED, null, note, null, NOW));
	}

	@Test
	void aDecisionIsFinal() {
		ReviewCase reviewCase = caseFor(RiskCategory.THREAT, RiskSeverity.CRITICAL);
		reviewCase.resolve(ReviewDecision.CONFIRMED, null, null, null, NOW);

		ConflictException conflict = assertThrows(ConflictException.class, () -> reviewCase.resolve(
				ReviewDecision.DISMISSED, null, "changed my mind", null, NOW));
		assertEquals("CASE_ALREADY_RESOLVED", conflict.errorCode());
		assertEquals(ReviewDecision.CONFIRMED, reviewCase.getDecision());
	}

	private static ReviewCase caseFor(RiskCategory category, RiskSeverity severity) {
		ClassificationResult result = new ClassificationResult("sheet-0-row-2", category, severity,
				0.96, Map.of(RiskCategory.THREAT, 0.96), true, "Needs a person.",
				"model", "rev", "policy");
		return new ReviewCase(null, 1, sequence(), result, NOW);
	}

	private static ExtractedSequence sequence() {
		return new ExtractedSequence("sheet-0-row-2", 0, 2, List.of(0), "I will kill you");
	}
}
