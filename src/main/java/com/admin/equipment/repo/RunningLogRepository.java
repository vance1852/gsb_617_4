package com.admin.equipment.repo;

import com.admin.equipment.model.RunningLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RunningLogRepository extends JpaRepository<RunningLog, Long> {
    List<RunningLog> findByEquipmentIdOrderByRecordedAtDesc(Long equipmentId);

    @Query(value = "SELECT * FROM running_logs r WHERE r.equipment_id = :equipmentId ORDER BY r.recorded_at DESC LIMIT 1", nativeQuery = true)
    Optional<RunningLog> findLatestByEquipmentId(@Param("equipmentId") Long equipmentId);

    @Query("SELECT SUM(r.runningHours) FROM RunningLog r WHERE r.equipmentId = :equipmentId AND r.recordedAt >= :since")
    Double sumRunningHoursSince(@Param("equipmentId") Long equipmentId, @Param("since") LocalDateTime since);

    @Query("SELECT SUM(r.runningCycles) FROM RunningLog r WHERE r.equipmentId = :equipmentId AND r.recordedAt >= :since")
    Long sumRunningCyclesSince(@Param("equipmentId") Long equipmentId, @Param("since") LocalDateTime since);

    @Query(value = "SELECT COALESCE(SUM(r.running_hours), 0) FROM running_logs r WHERE r.equipment_id = :equipmentId", nativeQuery = true)
    Double getTotalRunningHours(@Param("equipmentId") Long equipmentId);

    @Query(value = "SELECT COALESCE(SUM(r.running_cycles), 0) FROM running_logs r WHERE r.equipment_id = :equipmentId", nativeQuery = true)
    Long getTotalRunningCycles(@Param("equipmentId") Long equipmentId);
}
