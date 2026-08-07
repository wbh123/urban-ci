package org.urbansafe.priority.evidence.command;

import java.time.OffsetDateTime;

/** 楼栋证据创建的内部业务命令。 */
public record CreateEvidenceCommand(String evidenceType, String title, String description,
        OffsetDateTime occurredAt, String source, String reliabilityLevel, Object evidenceData) {
    public String getEvidenceType() { return evidenceType; } public String getTitle() { return title; }
    public String getDescription() { return description; } public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getSource() { return source; } public String getReliabilityLevel() { return reliabilityLevel; }
    public Object getEvidenceData() { return evidenceData; }
}
