package com.example.contentfilter.review;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassificationBatchRepository extends JpaRepository<ClassificationBatch, UUID> {
}
