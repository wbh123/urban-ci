package org.urbansafe.priority.ai.execution;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 有界轮询工作器；每轮最多领取配置数量的任务，避免占满业务线程。 */
@Component
public class AiExecutionTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(AiExecutionTaskWorker.class);

    private final AiExecutionTaskRepository repository;
    private final AiExecutionTaskService service;
    private final AiAccuracyExecutionTaskExecutor accuracyExecutor;
    private final AiIntelligentAnalysisExecutionTaskExecutor intelligentAnalysisExecutor;
    private final AiExecutionProperties properties;
    private final String workerId;

    public AiExecutionTaskWorker(
            AiExecutionTaskRepository repository,
            AiExecutionTaskService service,
            AiAccuracyExecutionTaskExecutor accuracyExecutor,
            AiIntelligentAnalysisExecutionTaskExecutor intelligentAnalysisExecutor,
            AiExecutionProperties properties) {
        this.repository = repository;
        this.service = service;
        this.accuracyExecutor = accuracyExecutor;
        this.intelligentAnalysisExecutor = intelligentAnalysisExecutor;
        this.properties = properties;
        this.workerId = properties.getWorkerId() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelayString = "${urban-safe.ai.execution.poll-delay-ms:1000}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        service.recoverExpiredLeases();
        for (int i = 0; i < Math.max(1, properties.getBatchSize()); i++) {
            var claimed = repository.claimNext(workerId, Duration.ofSeconds(properties.getLeaseSeconds()));
            if (claimed.isEmpty()) {
                return;
            }
            try {
                AiExecutionTask task = claimed.get();
                if (AiIntelligentAnalysisExecutionTaskExecutor.WORKFLOW_CODE.equals(task.workflowCode())) {
                    intelligentAnalysisExecutor.execute(task);
                    continue;
                }
                Object profile = task.inputs().get("inferenceProfile");
                if (profile != null && "ACCURACY".equalsIgnoreCase(String.valueOf(profile))) {
                    accuracyExecutor.execute(task);
                } else {
                    service.executeClaimed(task);
                }
            } catch (RuntimeException ex) {
                log.error("AI execution worker failed for task {}", claimed.get().id(), ex);
            }
        }
    }
}
