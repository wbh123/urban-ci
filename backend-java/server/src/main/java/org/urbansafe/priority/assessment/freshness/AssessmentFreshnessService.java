package org.urbansafe.priority.assessment.freshness;

import java.util.Map;
import org.springframework.stereotype.Service;

/** 判断保存结果是否仍与当前输入、规则和引擎一致。 */
@Service
public class AssessmentFreshnessService {

    public boolean reusable(
            Map<String, Object> result,
            String inputChecksum,
            String ruleVersionId,
            String engineVersion) {
        if (result == null || !"CURRENT".equals(String.valueOf(result.get("status")))) return false;
        return inputChecksum.equals(String.valueOf(result.get("inputChecksum")))
                && ruleVersionId.equals(String.valueOf(result.get("ruleVersionId")))
                && engineVersion.equals(String.valueOf(result.get("engineVersion")));
    }

    public String staleReason(
            Map<String, Object> result,
            String inputChecksum,
            String ruleVersionId,
            String engineVersion) {
        if (result == null) return "NO_RESULT";
        if (!engineVersion.equals(String.valueOf(result.get("engineVersion")))) return "ENGINE_CHANGED";
        if (!ruleVersionId.equals(String.valueOf(result.get("ruleVersionId")))) return "RULE_CHANGED";
        if (!inputChecksum.equals(String.valueOf(result.get("inputChecksum")))) return "INPUT_CHANGED";
        return "STATUS_NOT_CURRENT";
    }
}
