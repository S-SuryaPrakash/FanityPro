package com.example.contentfilter.service;

import com.example.contentfilter.domain.ExtractedSequence;
import com.example.contentfilter.domain.ModelPrediction;
import java.util.List;

/** Provider-neutral port implemented now by a deterministic development model. */
public interface RiskModel {

	List<ModelPrediction> predict(List<ExtractedSequence> sequences);
}
