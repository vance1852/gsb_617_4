package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_order_schedules")
public class WorkOrderSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false, unique = true)
    private Long workOrderId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "team_name", length = 64)
    private String teamName;

    @Column(name = "is_preventive", nullable = false)
    private Boolean isPreventive = false;

    @Column(name = "scheduled_start_minute")
    private Integer scheduledStartMinute;

    @Column(name = "scheduled_duration_minutes")
    private Integer scheduledDurationMinutes;

    @Column(name = "overdue_level")
    private Integer overdueLevel = 0;

    @Column(name = "scheduling_version")
    private Integer schedulingVersion = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Boolean getIsPreventive() { return isPreventive; }
    public void setIsPreventive(Boolean isPreventive) { this.isPreventive = isPreventive; }
    public Integer getScheduledStartMinute() { return scheduledStartMinute; }
    public void setScheduledStartMinute(Integer scheduledStartMinute) { this.scheduledStartMinute = scheduledStartMinute; }
    public Integer getScheduledDurationMinutes() { return scheduledDurationMinutes; }
    public void setScheduledDurationMinutes(Integer scheduledDurationMinutes) { this.scheduledDurationMinutes = scheduledDurationMinutes; }
    public Integer getOverdueLevel() { return overdueLevel; }
    public void setOverdueLevel(Integer overdueLevel) { this.overdueLevel = overdueLevel; }
    public Integer getSchedulingVersion() { return schedulingVersion; }
    public void setSchedulingVersion(Integer schedulingVersion) { this.schedulingVersion = schedulingVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
