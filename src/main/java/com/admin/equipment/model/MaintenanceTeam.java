package com.admin.equipment.model;

import jakarta.persistence.*;

@Entity
@Table(name = "maintenance_teams")
public class MaintenanceTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "daily_capacity_hours", nullable = false)
    private Double dailyCapacityHours = 8.0;

    @Column(name = "members", length = 256)
    private String members = "";

    @Column(name = "skills", length = 256)
    private String skills = "";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getDailyCapacityHours() { return dailyCapacityHours; }
    public void setDailyCapacityHours(Double dailyCapacityHours) { this.dailyCapacityHours = dailyCapacityHours; }
    public String getMembers() { return members; }
    public void setMembers(String members) { this.members = members; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
}
