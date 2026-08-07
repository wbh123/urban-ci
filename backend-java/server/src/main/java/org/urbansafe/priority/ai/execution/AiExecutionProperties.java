package org.urbansafe.priority.ai.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 人工智能异步执行工作器配置。 */
@ConfigurationProperties(prefix = "urban-safe.ai.execution")
public class AiExecutionProperties {
    private boolean enabled = true;
    private int pollDelayMs = 1000;
    private int leaseSeconds = 360;
    private int retryBaseDelaySeconds = 15;
    private int batchSize = 2;
    private String workerId = "urban-safe-worker";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPollDelayMs() { return pollDelayMs; }
    public void setPollDelayMs(int pollDelayMs) { this.pollDelayMs = pollDelayMs; }
    public int getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(int leaseSeconds) { this.leaseSeconds = leaseSeconds; }
    public int getRetryBaseDelaySeconds() { return retryBaseDelaySeconds; }
    public void setRetryBaseDelaySeconds(int retryBaseDelaySeconds) { this.retryBaseDelaySeconds = retryBaseDelaySeconds; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
}
