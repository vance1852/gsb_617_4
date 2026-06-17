package com.admin.equipment.model;

import jakarta.persistence.*;

@Entity
@Table(name = "maintenance_items")
public class MaintenanceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description = "";

    @Column(name = "sequence_num")
    private Integer sequenceNum = 1;

    @Column(name = "standard_minutes", nullable = false)
    private Integer standardMinutes = 30;

    @Column(name = "required_skill", length = 64)
    private String requiredSkill = "";

    @Column(name = "required_spare_parts", length = 512)
    private String requiredSpareParts = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSequenceNum() { return sequenceNum; }
    public void setSequenceNum(Integer sequenceNum) { this.sequenceNum = sequenceNum; }
    public Integer getStandardMinutes() { return standardMinutes; }
    public void setStandardMinutes(Integer standardMinutes) { this.standardMinutes = standardMinutes; }
    public String getRequiredSkill() { return requiredSkill; }
    public void setRequiredSkill(String requiredSkill) { this.requiredSkill = requiredSkill; }
    public String getRequiredSpareParts() { return requiredSpareParts; }
    public void setRequiredSpareParts(String requiredSpareParts) { this.requiredSpareParts = requiredSpareParts; }
}
