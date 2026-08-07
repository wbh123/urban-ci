package org.urbansafe.priority.assessment.service;

import java.util.LinkedHashSet;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.assessment.model.AssessmentResults.CompletenessResult;

/** 统一整理完整度解释，避免重复缺失项和建议。 */
@Component
public class CompletenessExplanationBuilder {

    public CompletenessResult build(CompletenessResult result) {
        return new CompletenessResult(
                result.score(), result.level(), result.dimensions(),
                new LinkedHashSet<>(result.availableItems()).stream().toList(),
                new LinkedHashSet<>(result.missingItems()).stream().toList(),
                new LinkedHashSet<>(result.suggestions()).stream().toList());
    }
}
