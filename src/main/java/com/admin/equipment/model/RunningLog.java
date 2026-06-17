package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "running_logs")
public class RunningLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "running_hours")
    private Double runningHours = 0.0;

    @Column(name = "running_cycles")
    private Long runningCycles = 0L;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(length = 256)
    private String remark = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public Double getRunningHours() { return runningHours; }
    public void setRunningHours(Double runningHours) { this.runningHours = runningHours; }
    public Long getRunningCycles() { return runningCycles; }
    public void setRunningCycles(Long runningCycles) { this.runningCycles = runningCycles; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
