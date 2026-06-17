package com.admin.equipment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "execution_records")
public class ExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_name", length = 128)
    private String itemName;

    @Column(length = 512)
    private String result = "";

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Column(name = "executor", length = 64)
    private String executor = "";

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "signed_by", length = 64)
    private String signedBy = "";

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(length = 16)
    private String status = "pending";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Integer getActualMinutes() { return actualMinutes; }
    public void setActualMinutes(Integer actualMinutes) { this.actualMinutes = actualMinutes; }
    public String getExecutor() { return executor; }
    public void setExecutor(String executor) { this.executor = executor; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    public String getSignedBy() { return signedBy; }
    public void setSignedBy(String signedBy) { this.signedBy = signedBy; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
