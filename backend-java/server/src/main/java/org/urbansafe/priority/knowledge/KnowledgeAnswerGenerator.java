package org.urbansafe.priority.knowledge;

import java.util.List;

/** 基于已授权知识证据生成自然语言回答；实现不得自行访问业务数据库。 */
public interface KnowledgeAnswerGenerator {

    GeneratedAnswer generate(String question, List<KnowledgeCitation> citations);

    record GeneratedAnswer(String answer, String providerCode, String modelCode) {
        public GeneratedAnswer {
            if (answer == null || answer.isBlank()) {
                throw new IllegalArgumentException("生成答案不能为空");
            }
            if (providerCode == null || providerCode.isBlank()) {
                throw new IllegalArgumentException("生成答案缺少提供者编号");
            }
            if (modelCode == null || modelCode.isBlank()) {
                throw new IllegalArgumentException("生成答案缺少模型编号");
            }
        }
    }
}
