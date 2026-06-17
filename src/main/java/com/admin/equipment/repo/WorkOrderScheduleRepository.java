package com.admin.equipment.repo;

import com.admin.equipment.model.WorkOrderSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkOrderScheduleRepository extends JpaRepository<WorkOrderSchedule, Long> {
    Optional<WorkOrderSchedule> findByWorkOrderId(Long workOrderId);
    List<WorkOrderSchedule> findByPlanId(Long planId);
    List<WorkOrderSchedule> findByTeamId(Long teamId);
    List<WorkOrderSchedule> findByScheduledDateBetween(LocalDate from, LocalDate to);

    @Query("SELECT s FROM WorkOrderSchedule s WHERE s.teamId = :teamId AND s.scheduledDate BETWEEN :from AND :to")
    List<WorkOrderSchedule> findByTeamAndDateRange(@Param("teamId") Long teamId,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    @Query("SELECT ws FROM WorkOrderSchedule ws, WorkOrder w " +
           "WHERE w.id = ws.workOrderId AND w.equipmentId = :equipmentId " +
           "AND ws.scheduledDate BETWEEN :from AND :to")
    List<WorkOrderSchedule> findByEquipmentAndDateRange(@Param("equipmentId") Long equipmentId,
                                                        @Param("from") LocalDate from,
                                                        @Param("to") LocalDate to);

    @Query("SELECT s FROM WorkOrderSchedule s WHERE s.isPreventive = true AND s.overdueLevel > 0")
    List<WorkOrderSchedule> findOverduePreventiveOrders();
}
