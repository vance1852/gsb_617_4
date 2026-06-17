package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "downtime_windows")
public class DowntimeWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "equipment_type", length = 32)
    private String equipmentType;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_minute", nullable = false)
    private Integer startMinute = 0;

    @Column(name = "end_minute", nullable = false)
    private Integer endMinute = 1440;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public Integer getStartMinute() { return startMinute; }
    public void setStartMinute(Integer startMinute) { this.startMinute = startMinute; }
    public Integer getEndMinute() { return endMinute; }
    public void setEndMinute(Integer endMinute) { this.endMinute = endMinute; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
}
