package org.urbansafe.priority.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeTextScorerTest {

    private final KnowledgeTextScorer scorer = new KnowledgeTextScorer();

    @Test
    void shouldRankChineseOperationalEvidenceAboveUnrelatedContent() {
        double relevant = scorer.score(
                "巡检图片模糊时应该如何补拍？",
                "图片拍摄与补拍",
                "巡检图片模糊时，应保持镜头稳定并补拍整体照、近景照和带比例尺的细节照。");
        double unrelated = scorer.score(
                "巡检图片模糊时应该如何补拍？",
                "账号管理",
                "管理员可以创建账号并分配角色权限。");

        assertThat(relevant).isGreaterThan(0.55d);
        assertThat(unrelated).isLessThan(0.15d);
        assertThat(relevant).isGreaterThan(unrelated);
    }

    @Test
    void exactPhraseShouldReceiveStableHighScore() {
        double score = scorer.score(
                "数据缺失不能解释为低风险",
                "风险解释原则",
                "数据缺失不能解释为低风险，资料不足时应安排补充巡检或专业检测。");

        assertThat(score).isBetween(0.85d, 1.0d);
    }
}
