package com.example.contentfilter.review;

import com.example.contentfilter.account.AppUser;
import com.example.contentfilter.account.AppUserRepository;
import com.example.contentfilter.audit.AuditAction;
import com.example.contentfilter.audit.AuditService;
import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ConversationColumnMapping;
import com.example.contentfilter.domain.ConversationRiskAssessment;
import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.exception.ResourceNotFoundException;
import com.example.contentfilter.exception.WorkbookProcessingException;
import com.example.contentfilter.exception.WorkbookProcessingException.Reason;
import com.example.contentfilter.service.ConversationRiskWorkflowService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Classifies an uploaded workbook with the existing V1 pipeline and turns every
 * review-required result into a persisted {@link ReviewCase}.
 */
@Service
@Profile("v2")
public class ClassificationBatchService {

	private static final int MAX_FILENAME_LENGTH = 255;

	/** Same ordering as the V1 workbook's Review Queue sheet. */
	private static final Comparator<Assessed> QUEUE_ORDER = Comparator
			.<Assessed>comparingInt(item -> item.result().severity().ordinal()).reversed()
			.thenComparing(Comparator.comparingDouble(
					(Assessed item) -> item.result().confidence()).reversed());

	private final ConversationRiskWorkflowService riskWorkflow;
	private final ClassificationBatchRepository batches;
	private final ReviewCaseRepository cases;
	private final AppUserRepository users;
	private final AuditService audit;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public ClassificationBatchService(
			ConversationRiskWorkflowService riskWorkflow,
			ClassificationBatchRepository batches,
			ReviewCaseRepository cases,
			AppUserRepository users,
			AuditService audit,
			TransactionTemplate transactions,
			Clock clock) {
		this.riskWorkflow = riskWorkflow;
		this.batches = batches;
		this.cases = cases;
		this.users = users;
		this.audit = audit;
		this.transactions = transactions;
		this.clock = clock;
	}

	/**
	 * Classification can call the remote model service, so it runs before and
	 * outside the database transaction; only the persistence step is atomic.
	 */
	public BatchView upload(UUID uploaderId, MultipartFile file) {
		ConversationRiskAssessment assessment =
				riskWorkflow.assess(file, ConversationColumnMapping.standard());
		if (assessment.sequences().isEmpty()) {
			throw new WorkbookProcessingException(
					Reason.INVALID_WORKBOOK,
					"The first worksheet does not contain any non-empty messages.");
		}
		String filename = sanitizeFilename(file.getOriginalFilename());
		return transactions.execute(status -> persist(uploaderId, filename, assessment));
	}

	private BatchView persist(
			UUID uploaderId,
			String filename,
			ConversationRiskAssessment assessment) {
		List<Assessed> reviewQueue = IntStream.range(0, assessment.sequences().size())
				.mapToObj(index -> new Assessed(
						assessment.sequences().get(index), assessment.results().get(index)))
				.filter(item -> item.result().manualReviewRequired())
				.sorted(QUEUE_ORDER)
				.toList();

		// One classification run uses a single model and policy version.
		ClassificationResult first = assessment.results().getFirst();
		Instant now = clock.instant();
		AppUser uploader = users.getReferenceById(uploaderId);
		ClassificationBatch batch = batches.save(new ClassificationBatch(
				uploader,
				filename,
				assessment.sequences().size(),
				reviewQueue.size(),
				first.modelId(),
				first.modelRevision(),
				first.policyVersion(),
				now));

		List<ReviewCase> created = new ArrayList<>(reviewQueue.size());
		for (int index = 0; index < reviewQueue.size(); index++) {
			Assessed item = reviewQueue.get(index);
			created.add(new ReviewCase(batch, index + 1, item.sequence(), item.result(), now));
		}
		cases.saveAll(created);

		audit.record(uploaderId, AuditAction.BATCH_UPLOADED, "BATCH", batch.getId(), Map.of(
				"totalSequences", batch.getTotalSequences(),
				"reviewCaseCount", batch.getReviewCaseCount(),
				"modelId", batch.getModelId(),
				"policyVersion", batch.getPolicyVersion()));
		return BatchView.from(batch, reviewQueue.size(), 0);
	}

	@Transactional(readOnly = true)
	public BatchView get(UUID batchId) {
		ClassificationBatch batch = batches.findById(batchId)
				.orElseThrow(() -> new ResourceNotFoundException("Batch not found."));
		return withCaseCounts(batch);
	}

	@Transactional(readOnly = true)
	public Page<BatchView> list(Pageable pageable) {
		return batches.findAll(pageable).map(this::withCaseCounts);
	}

	private BatchView withCaseCounts(ClassificationBatch batch) {
		long open = 0;
		long resolved = 0;
		for (ReviewCaseRepository.StatusCount count : cases.countByStatus(batch.getId())) {
			if (count.getStatus() == ReviewCaseStatus.OPEN) {
				open = count.getTotal();
			} else {
				resolved = count.getTotal();
			}
		}
		return BatchView.from(batch, open, resolved);
	}

	private static String sanitizeFilename(String original) {
		String name = original == null || original.isBlank() ? "workbook.xlsx" : original.strip();
		name = name.replaceAll("[\\p{Cntrl}]", "_");
		return name.length() > MAX_FILENAME_LENGTH ? name.substring(0, MAX_FILENAME_LENGTH) : name;
	}

	private record Assessed(ExtractedSequence sequence, ClassificationResult result) {
	}

	public record BatchView(
			UUID id,
			String originalFilename,
			UUID uploadedBy,
			int totalSequences,
			int reviewCaseCount,
			long openCaseCount,
			long resolvedCaseCount,
			String modelId,
			String modelRevision,
			String policyVersion,
			Instant createdAt) {

		static BatchView from(ClassificationBatch batch, long open, long resolved) {
			return new BatchView(
					batch.getId(),
					batch.getOriginalFilename(),
					batch.getUploadedBy().getId(),
					batch.getTotalSequences(),
					batch.getReviewCaseCount(),
					open,
					resolved,
					batch.getModelId(),
					batch.getModelRevision(),
					batch.getPolicyVersion(),
					batch.getCreatedAt());
		}
	}
}
