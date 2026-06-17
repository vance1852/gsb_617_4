package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_plans")
public class MaintenancePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description = "";

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "equipment_type", length = 32)
    private String equipmentType;

    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType = "TIME_BASED";

    @Column(name = "cycle_days")
    private Integer cycleDays;

    @Column(name = "cycle_months")
    private Integer cycleMonths;

    @Column(name = "cycle_hours")
    private Integer cycleHours;

    @Column(name = "cycle_cycles")
    private Integer cycleCycles;

    @Column(name = "advance_notice_days")
    private Integer advanceNoticeDays = 3;

    @Column(name = "standard_work_hours", nullable = false)
    private Double standardWorkHours = 1.0;

    @Column(name = "required_skill", length = 64)
    private String requiredSkill = "";

    @Column(length = 16)
    private String priority = "medium";

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @Column(name = "next_trigger_at")
    private LocalDateTime nextTriggerAt;

    @Column(name = "last_trigger_running_hours")
    private Double lastTriggerRunningHours;

    @Column(name = "last_trigger_running_cycles")
    private Long lastTriggerRunningCycles;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public Integer getCycleDays() { return cycleDays; }
    public void setCycleDays(Integer cycleDays) { this.cycleDays = cycleDays; }
    public Integer getCycleMonths() { return cycleMonths; }
    public void setCycleMonths(Integer cycleMonths) { this.cycleMonths = cycleMonths; }
    public Integer getCycleHours() { return cycleHours; }
    public void setCycleHours(Integer cycleHours) { this.cycleHours = cycleHours; }
    public Integer getCycleCycles() { return cycleCycles; }
    public void setCycleCycles(Integer cycleCycles) { this.cycleCycles = cycleCycles; }
    public Integer getAdvanceNoticeDays() { return advanceNoticeDays; }
    public void setAdvanceNoticeDays(Integer advanceNoticeDays) { this.advanceNoticeDays = advanceNoticeDays; }
    public Double getStandardWorkHours() { return standardWorkHours; }
    public void setStandardWorkHours(Double standardWorkHours) { this.standardWorkHours = standardWorkHours; }
    public String getRequiredSkill() { return requiredSkill; }
    public void setRequiredSkill(String requiredSkill) { this.requiredSkill = requiredSkill; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
    public LocalDateTime getNextTriggerAt() { return nextTriggerAt; }
    public void setNextTriggerAt(LocalDateTime nextTriggerAt) { this.nextTriggerAt = nextTriggerAt; }
    public Double getLastTriggerRunningHours() { return lastTriggerRunningHours; }
    public void setLastTriggerRunningHours(Double lastTriggerRunningHours) { this.lastTriggerRunningHours = lastTriggerRunningHours; }
    public Long getLastTriggerRunningCycles() { return lastTriggerRunningCycles; }
    public void setLastTriggerRunningCycles(Long lastTriggerRunningCycles) { this.lastTriggerRunningCycles = lastTriggerRunningCycles; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
