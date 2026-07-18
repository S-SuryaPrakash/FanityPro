package com.example.contentfilter.service;

import com.example.contentfilter.domain.ClassificationResult;
import com.example.contentfilter.domain.ModelPrediction;

/** Converts provider-neutral model evidence into an auditable review decision. */
public interface ClassificationPolicy {

	ClassificationResult decide(ModelPrediction prediction);
}
