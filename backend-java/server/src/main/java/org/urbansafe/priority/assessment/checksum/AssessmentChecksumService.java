package org.urbansafe.priority.assessment.checksum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Service;

/** 对规范化 UTF-8 JSON 计算可复现 SHA-256。 */
@Service
public class AssessmentChecksumService {

    private final AssessmentInputCanonicalizer canonicalizer;

    public AssessmentChecksumService(AssessmentInputCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public String checksum(Object input) {
        return sha256(canonicalizer.canonicalize(input));
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境不支持 SHA-256", ex);
        }
    }
}
