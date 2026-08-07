package com.example.contentfilter.service;

import com.example.contentfilter.dto.ClassificationResponse;

/**
 * Compatibility port used only by the V0 prototype JSON endpoint.
 *
 * @deprecated New workflow code must use the batch-first {@link ClassificationService}
 *             via {@link WorkbookClassificationService}.
 */
@Deprecated(forRemoval = true)
public interface LegacyClassificationService {

	ClassificationResponse classify(String text);
}
