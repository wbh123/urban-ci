package org.urbansafe.priority.assessment.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.assessment.repository.AssessmentResultRepository;

/** 维护并读取服务端正式稳定排名。 */
@Service
public class RenewalRankingService {

    private final AssessmentResultRepository repository;
    private final RankingTieBreaker tieBreaker;

    public RenewalRankingService(
            AssessmentResultRepository repository,
            RankingTieBreaker tieBreaker) {
        this.repository = repository;
        this.tieBreaker = tieBreaker;
    }

    @Transactional
    public List<Map<String, Object>> refresh(String scopeKey) {
        List<Map<String, Object>> ordered = repository.rankingCandidates(scopeKey).stream()
                .sorted(tieBreaker.comparator())
                .toList();
        repository.updateRankings(ordered);
        int rank = 1;
        for (Map<String, Object> row : ordered) {
            row.put("ranking", rank++);
            row.put("mainReasons", java.util.List.of());
            row.put("disclaimer", AssessmentResultRepository.disclaimer());
        }
        return ordered;
    }

    public List<Map<String, Object>> current(String scopeKey) {
        return repository.rankingCandidates(scopeKey).stream()
                .sorted(java.util.Comparator.comparingInt(row -> {
                    Object value = row.get("ranking");
                    return value instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
                }))
                .peek(row -> {
                    row.put("mainReasons", java.util.List.of());
                    row.put("disclaimer", AssessmentResultRepository.disclaimer());
                })
                .toList();
    }
}
