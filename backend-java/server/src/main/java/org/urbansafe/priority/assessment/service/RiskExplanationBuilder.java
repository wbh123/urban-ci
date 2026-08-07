package org.urbansafe.priority.assessment.service;

import java.util.LinkedHashSet;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.assessment.model.AssessmentResults.RiskResult;

/** 统一整理风险、排除证据、缺失资料和人工复核建议。 */
@Component
public class RiskExplanationBuilder {

    public RiskResult build(RiskResult result) {
        return new RiskResult(
                result.riskScore(), result.riskLevel(), result.evidenceReliabilityScore(),
                result.confidenceScore(), result.confidenceLevel(), result.dimensions(),
                result.factors(), result.excludedEvidence(),
                new LinkedHashSet<>(result.missingData()).stream().toList(),
                new LinkedHashSet<>(result.recommendations()).stream().toList(),
                result.needManualReview(), result.needProfessionalInspection());
    }
}
