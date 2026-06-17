package com.admin.equipment.repo;

import com.admin.equipment.model.DowntimeWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DowntimeWindowRepository extends JpaRepository<DowntimeWindow, Long> {
    List<DowntimeWindow> findByEquipmentId(Long equipmentId);
    List<DowntimeWindow> findByEquipmentType(String equipmentType);
    List<DowntimeWindow> findByEquipmentIdOrEquipmentType(Long equipmentId, String equipmentType);
}
