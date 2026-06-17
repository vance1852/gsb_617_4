package com.admin.equipment.repo;

import com.admin.equipment.model.MaintenanceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceItemRepository extends JpaRepository<MaintenanceItem, Long> {
    List<MaintenanceItem> findByPlanIdOrderBySequenceNumAsc(Long planId);
    void deleteByPlanId(Long planId);
}
