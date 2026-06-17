package com.admin.equipment.repo;

import com.admin.equipment.model.MaintenancePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, Long> {
    List<MaintenancePlan> findByEnabledTrue();
    List<MaintenancePlan> findByEquipmentId(Long equipmentId);
    List<MaintenancePlan> findByEquipmentType(String equipmentType);

    @Query("SELECT p FROM MaintenancePlan p WHERE p.enabled = true AND p.nextTriggerAt <= :now")
    List<MaintenancePlan> findDuePlans(@Param("now") LocalDateTime now);

    @Query("SELECT p FROM MaintenancePlan p WHERE p.enabled = true AND p.nextTriggerAt BETWEEN :from AND :to")
    List<MaintenancePlan> findPlansDueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
