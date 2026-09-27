package com.example.contentfilter.review;

import com.example.contentfilter.domain.RiskCategory;
import com.example.contentfilter.domain.RiskSeverity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ReviewCaseRepository
		extends JpaRepository<ReviewCase, UUID>, JpaSpecificationExecutor<ReviewCase> {

	@Query("""
			select c.status as status, count(c) as total
			from ReviewCase c
			where c.batch.id = :batchId
			group by c.status
			""")
	List<StatusCount> countByStatus(UUID batchId);

	interface StatusCount {
		ReviewCaseStatus getStatus();

		long getTotal();
	}

	/** Optional queue filters; a null argument means "any". */
	static Specification<ReviewCase> matching(
			ReviewCaseStatus status,
			UUID batchId,
			RiskSeverity severity,
			RiskCategory category) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) {
				predicates.add(builder.equal(root.get("status"), status));
			}
			if (batchId != null) {
				predicates.add(builder.equal(root.get("batch").get("id"), batchId));
			}
			if (severity != null) {
				predicates.add(builder.equal(root.get("severity"), severity));
			}
			if (category != null) {
				predicates.add(builder.equal(root.get("primaryCategory"), category));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}
}
