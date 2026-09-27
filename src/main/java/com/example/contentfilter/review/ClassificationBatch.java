package com.example.contentfilter.review;

import com.example.contentfilter.account.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One uploaded workbook and the model/policy versions that classified it. */
@Entity
@Table(name = "classification_batches")
public class ClassificationBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploaded_by", nullable = false)
	private AppUser uploadedBy;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(name = "total_sequences", nullable = false)
	private int totalSequences;

	@Column(name = "review_case_count", nullable = false)
	private int reviewCaseCount;

	@Column(name = "model_id", nullable = false)
	private String modelId;

	@Column(name = "model_revision", nullable = false)
	private String modelRevision;

	@Column(name = "policy_version", nullable = false, length = 64)
	private String policyVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ClassificationBatch() {
	}

	public ClassificationBatch(
			AppUser uploadedBy,
			String originalFilename,
			int totalSequences,
			int reviewCaseCount,
			String modelId,
			String modelRevision,
			String policyVersion,
			Instant createdAt) {
		this.uploadedBy = uploadedBy;
		this.originalFilename = originalFilename;
		this.totalSequences = totalSequences;
		this.reviewCaseCount = reviewCaseCount;
		this.modelId = modelId;
		this.modelRevision = modelRevision;
		this.policyVersion = policyVersion;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public AppUser getUploadedBy() {
		return uploadedBy;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public int getTotalSequences() {
		return totalSequences;
	}

	public int getReviewCaseCount() {
		return reviewCaseCount;
	}

	public String getModelId() {
		return modelId;
	}

	public String getModelRevision() {
		return modelRevision;
	}

	public String getPolicyVersion() {
		return policyVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
