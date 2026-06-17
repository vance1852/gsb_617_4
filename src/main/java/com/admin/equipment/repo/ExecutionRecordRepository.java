package com.admin.equipment.repo;

import com.admin.equipment.model.ExecutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExecutionRecordRepository extends JpaRepository<ExecutionRecord, Long> {
    List<ExecutionRecord> findByWorkOrderIdOrderByIdAsc(Long workOrderId);

    @Query("SELECT e FROM ExecutionRecord e WHERE e.executedAt BETWEEN :from AND :to")
    List<ExecutionRecord> findByExecutedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT e FROM ExecutionRecord e WHERE e.workOrderId IN " +
           "(SELECT w.id FROM WorkOrder w WHERE w.equipmentId = :equipmentId)")
    List<ExecutionRecord> findByEquipmentId(@Param("equipmentId") Long equipmentId);
}
