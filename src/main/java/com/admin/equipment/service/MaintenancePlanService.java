package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MaintenancePlanService {

    private final MaintenancePlanRepository planRepo;
    private final MaintenanceItemRepository itemRepo;
    private final EquipmentRepository equipmentRepo;

    public MaintenancePlanService(MaintenancePlanRepository planRepo,
                                  MaintenanceItemRepository itemRepo,
                                  EquipmentRepository equipmentRepo) {
        this.planRepo = planRepo;
        this.itemRepo = itemRepo;
        this.equipmentRepo = equipmentRepo;
    }

    @Transactional
    public MaintenancePlan createPlan(MaintenancePlan plan, List<MaintenanceItem> items) {
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setNextTriggerAt(calculateNextTrigger(plan, LocalDateTime.now()));
        MaintenancePlan saved = planRepo.save(plan);
        if (items != null) {
            for (MaintenanceItem item : items) {
                item.setPlanId(saved.getId());
                itemRepo.save(item);
            }
        }
        return saved;
    }

    @Transactional
    public MaintenancePlan updatePlan(Long id, MaintenancePlan plan, List<MaintenanceItem> items) {
        MaintenancePlan existing = planRepo.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setName(plan.getName() != null ? plan.getName() : existing.getName());
        existing.setDescription(plan.getDescription() != null ? plan.getDescription() : existing.getDescription());
        existing.setEquipmentId(plan.getEquipmentId() != null ? plan.getEquipmentId() : existing.getEquipmentId());
        existing.setEquipmentType(plan.getEquipmentType() != null ? plan.getEquipmentType() : existing.getEquipmentType());
        existing.setTriggerType(plan.getTriggerType() != null ? plan.getTriggerType() : existing.getTriggerType());
        existing.setCycleDays(plan.getCycleDays() != null ? plan.getCycleDays() : existing.getCycleDays());
        existing.setCycleMonths(plan.getCycleMonths() != null ? plan.getCycleMonths() : existing.getCycleMonths());
        existing.setCycleHours(plan.getCycleHours() != null ? plan.getCycleHours() : existing.getCycleHours());
        existing.setCycleCycles(plan.getCycleCycles() != null ? plan.getCycleCycles() : existing.getCycleCycles());
        existing.setAdvanceNoticeDays(plan.getAdvanceNoticeDays() != null ? plan.getAdvanceNoticeDays() : existing.getAdvanceNoticeDays());
        existing.setStandardWorkHours(plan.getStandardWorkHours() != null ? plan.getStandardWorkHours() : existing.getStandardWorkHours());
        existing.setRequiredSkill(plan.getRequiredSkill() != null ? plan.getRequiredSkill() : existing.getRequiredSkill());
        existing.setPriority(plan.getPriority() != null ? plan.getPriority() : existing.getPriority());
        existing.setEnabled(plan.getEnabled() != null ? plan.getEnabled() : existing.getEnabled());
        existing.setNextTriggerAt(calculateNextTrigger(existing,
                existing.getLastTriggeredAt() != null ? existing.getLastTriggeredAt() : LocalDateTime.now()));
        existing.setUpdatedAt(LocalDateTime.now());
        MaintenancePlan saved = planRepo.save(existing);
        if (items != null) {
            itemRepo.deleteByPlanId(saved.getId());
            for (MaintenanceItem item : items) {
                item.setPlanId(saved.getId());
                itemRepo.save(item);
            }
        }
        return saved;
    }

    public List<MaintenancePlan> listPlans(Boolean enabled, Long equipmentId, String equipmentType) {
        if (equipmentId != null) return planRepo.findByEquipmentId(equipmentId);
        if (equipmentType != null) return planRepo.findByEquipmentType(equipmentType);
        if (enabled != null && enabled) return planRepo.findByEnabledTrue();
        return planRepo.findAll();
    }

    public MaintenancePlan getPlan(Long id) { return planRepo.findById(id).orElse(null); }

    @Transactional
    public boolean deletePlan(Long id) {
        if (!planRepo.existsById(id)) return false;
        itemRepo.deleteByPlanId(id);
        planRepo.deleteById(id);
        return true;
    }

    @Transactional
    public MaintenancePlan togglePlan(Long id, boolean enabled) {
        MaintenancePlan plan = planRepo.findById(id).orElse(null);
        if (plan == null) return null;
        plan.setEnabled(enabled);
        plan.setUpdatedAt(LocalDateTime.now());
        if (enabled && plan.getNextTriggerAt() == null) {
            plan.setNextTriggerAt(calculateNextTrigger(plan, LocalDateTime.now()));
        }
        return planRepo.save(plan);
    }

    public List<MaintenanceItem> getPlanItems(Long planId) {
        return itemRepo.findByPlanIdOrderBySequenceNumAsc(planId);
    }

    public LocalDateTime calculateNextTrigger(MaintenancePlan plan, LocalDateTime fromTime) {
        if (fromTime == null) fromTime = LocalDateTime.now();
        if ("TIME_BASED".equals(plan.getTriggerType())) {
            LocalDateTime next = fromTime;
            if (plan.getCycleDays() != null && plan.getCycleDays() > 0) {
                next = next.plusDays(plan.getCycleDays());
            } else if (plan.getCycleMonths() != null && plan.getCycleMonths() > 0) {
                next = next.plusMonths(plan.getCycleMonths());
            } else {
                next = next.plusDays(30);
            }
            return next;
        }
        return fromTime.plusDays(7);
    }

    public List<Equipment> getApplicableEquipments(MaintenancePlan plan) {
        if (plan.getEquipmentId() != null) {
            return equipmentRepo.findById(plan.getEquipmentId()).map(List::of).orElse(List.of());
        }
        if (plan.getEquipmentType() != null && !plan.getEquipmentType().isBlank()) {
            return equipmentRepo.findAll().stream()
                    .filter(e -> plan.getEquipmentType().equals(e.getType()))
                    .toList();
        }
        return List.of();
    }
}
