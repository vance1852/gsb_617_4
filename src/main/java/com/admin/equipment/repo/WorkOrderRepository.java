package com.admin.equipment.repo;

import com.admin.equipment.model.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    List<WorkOrder> findAllByOrderByIdDesc();
    List<WorkOrder> findByEquipmentIdOrderByIdDesc(Long equipmentId);
    List<WorkOrder> findByStatusOrderByIdDesc(String status);
    long countByStatus(String status);
    List<WorkOrder> findByIsPreventiveTrue();
    List<WorkOrder> findByPlanId(Long planId);

    @Query("SELECT w FROM WorkOrder w WHERE w.isPreventive = true AND w.status <> 'done' AND w.dueDate < :now")
    List<WorkOrder> findOverduePreventive(@Param("now") LocalDateTime now);

    @Query("SELECT w FROM WorkOrder w WHERE w.equipmentId = :equipmentId AND w.status <> 'done' AND w.isPreventive = true")
    List<WorkOrder> findOpenPreventiveByEquipment(@Param("equipmentId") Long equipmentId);

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.isPreventive = true AND w.status = 'done' AND w.closedAt BETWEEN :from AND :to")
    long countCompletedPreventiveBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.isPreventive = true AND w.dueDate BETWEEN :from AND :to")
    long countScheduledPreventiveBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
