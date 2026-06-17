package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkOrderTriggerService {

    private final MaintenancePlanRepository planRepo;
    private final EquipmentRepository equipmentRepo;
    private final RunningLogRepository runningLogRepo;
    private final WorkOrderRepository workOrderRepo;
    private final MaintenanceItemRepository itemRepo;
    private final ExecutionRecordRepository executionRepo;
    private final MaintenancePlanService planService;

    public WorkOrderTriggerService(MaintenancePlanRepository planRepo,
                                   EquipmentRepository equipmentRepo,
                                   RunningLogRepository runningLogRepo,
                                   WorkOrderRepository workOrderRepo,
                                   MaintenanceItemRepository itemRepo,
                                   ExecutionRecordRepository executionRepo,
                                   MaintenancePlanService planService) {
        this.planRepo = planRepo;
        this.equipmentRepo = equipmentRepo;
        this.runningLogRepo = runningLogRepo;
        this.workOrderRepo = workOrderRepo;
        this.itemRepo = itemRepo;
        this.executionRepo = executionRepo;
        this.planService = planService;
    }

    public static class TriggerResult {
        public int createdCount;
        public List<WorkOrder> createdOrders = new ArrayList<>();
        public List<String> messages = new ArrayList<>();
    }

    @Transactional
    public TriggerResult checkAndTriggerDuePlans() {
        TriggerResult result = new TriggerResult();
        LocalDateTime now = LocalDateTime.now();
        List<MaintenancePlan> plans = planRepo.findByEnabledTrue();

        for (MaintenancePlan plan : plans) {
            List<Equipment> equipments = planService.getApplicableEquipments(plan);
            for (Equipment equipment : equipments) {
                boolean shouldTrigger = false;
                String triggerReason = "";

                if ("TIME_BASED".equals(plan.getTriggerType())) {
                    LocalDateTime triggerTime = plan.getNextTriggerAt();
                    if (triggerTime == null) {
                        triggerTime = planService.calculateNextTrigger(plan,
                                plan.getLastTriggeredAt() != null ? plan.getLastTriggeredAt() : plan.getCreatedAt());
                        plan.setNextTriggerAt(triggerTime);
                    }
                    LocalDateTime earliestTrigger = triggerTime.minusDays(
                            plan.getAdvanceNoticeDays() != null ? plan.getAdvanceNoticeDays() : 0);
                    if (!now.isBefore(earliestTrigger)) {
                        shouldTrigger = true;
                        triggerReason = "时间周期触发: 下次触发时间 " + triggerTime;
                    }
                } else if ("USAGE_BASED".equals(plan.getTriggerType())) {
                    double totalHours = runningLogRepo.getTotalRunningHours(equipment.getId()) != null ?
                            runningLogRepo.getTotalRunningHours(equipment.getId()) : 0.0;
                    long totalCycles = runningLogRepo.getTotalRunningCycles(equipment.getId()) != null ?
                            runningLogRepo.getTotalRunningCycles(equipment.getId()) : 0L;

                    double lastHours = plan.getLastTriggerRunningHours() != null ? plan.getLastTriggerRunningHours() : 0.0;
                    long lastCycles = plan.getLastTriggerRunningCycles() != null ? plan.getLastTriggerRunningCycles() : 0L;

                    if (plan.getCycleHours() != null && plan.getCycleHours() > 0) {
                        if ((totalHours - lastHours) >= plan.getCycleHours()) {
                            shouldTrigger = true;
                            triggerReason = String.format("运行时长触发: 已运行 %.1f 小时 (阈值 %d 小时)",
                                    totalHours - lastHours, plan.getCycleHours());
                        }
                    }
                    if (!shouldTrigger && plan.getCycleCycles() != null && plan.getCycleCycles() > 0) {
                        if ((totalCycles - lastCycles) >= plan.getCycleCycles()) {
                            shouldTrigger = true;
                            triggerReason = String.format("运行次数触发: 已运行 %d 次 (阈值 %d 次)",
                                    totalCycles - lastCycles, plan.getCycleCycles());
                        }
                    }
                }

                if (shouldTrigger && !hasOpenPreventiveOrder(equipment.getId(), plan.getId())) {
                    WorkOrder order = createPreventiveWorkOrder(plan, equipment, triggerReason);
                    result.createdOrders.add(order);
                    result.createdCount++;
                    result.messages.add(String.format("设备[%s] 计划[%s] - %s",
                            equipment.getCode(), plan.getName(), triggerReason));

                    plan.setLastTriggeredAt(now);
                    if ("TIME_BASED".equals(plan.getTriggerType())) {
                        plan.setNextTriggerAt(planService.calculateNextTrigger(plan, now));
                    }
                    double totalHours = runningLogRepo.getTotalRunningHours(equipment.getId()) != null ?
                            runningLogRepo.getTotalRunningHours(equipment.getId()) : 0.0;
                    long totalCycles = runningLogRepo.getTotalRunningCycles(equipment.getId()) != null ?
                            runningLogRepo.getTotalRunningCycles(equipment.getId()) : 0L;
                    plan.setLastTriggerRunningHours(totalHours);
                    plan.setLastTriggerRunningCycles(totalCycles);
                    plan.setUpdatedAt(now);
                    planRepo.save(plan);
                }
            }
        }
        return result;
    }

    private boolean hasOpenPreventiveOrder(Long equipmentId, Long planId) {
        List<WorkOrder> openOrders = workOrderRepo.findOpenPreventiveByEquipment(equipmentId);
        return openOrders.stream().anyMatch(o -> planId.equals(o.getPlanId()));
    }

    @Transactional
    public WorkOrder createPreventiveWorkOrder(MaintenancePlan plan, Equipment equipment, String reason) {
        WorkOrder order = new WorkOrder();
        order.setEquipmentId(equipment.getId());
        order.setTitle("【预防性维保】" + plan.getName() + " - " + equipment.getName());
        order.setType("maintenance");
        order.setPriority(plan.getPriority());
        order.setDescription("计划: " + plan.getName() + "\n" +
                (plan.getDescription() != null ? plan.getDescription() + "\n" : "") +
                "触发原因: " + reason + "\n" +
                "标准工时: " + plan.getStandardWorkHours() + " 小时\n" +
                (plan.getRequiredSkill() != null && !plan.getRequiredSkill().isBlank() ?
                        "所需技能: " + plan.getRequiredSkill() : ""));
        order.setIsPreventive(true);
        order.setPlanId(plan.getId());
        order.setStatus("open");
        order.setDueDate(plan.getNextTriggerAt() != null ? plan.getNextTriggerAt() : LocalDateTime.now().plusDays(3));
        order.setCreatedAt(LocalDateTime.now());
        WorkOrder saved = workOrderRepo.save(order);

        List<MaintenanceItem> items = itemRepo.findByPlanIdOrderBySequenceNumAsc(plan.getId());
        for (MaintenanceItem item : items) {
            ExecutionRecord record = new ExecutionRecord();
            record.setWorkOrderId(saved.getId());
            record.setItemId(item.getId());
            record.setItemName(item.getName());
            record.setStatus("pending");
            executionRepo.save(record);
        }
        return saved;
    }

    public List<MaintenancePlan> getDuePlansPreview() {
        LocalDateTime now = LocalDateTime.now();
        return planRepo.findPlansDueBetween(now.minusDays(1), now.plusDays(7));
    }

    @Transactional
    public RunningLog addRunningLog(Long equipmentId, double hours, long cycles, String remark) {
        RunningLog log = new RunningLog();
        log.setEquipmentId(equipmentId);
        log.setRunningHours(hours);
        log.setRunningCycles(cycles);
        log.setRecordedAt(LocalDateTime.now());
        log.setRemark(remark != null ? remark : "");
        return runningLogRepo.save(log);
    }

    public List<RunningLog> getRunningLogs(Long equipmentId) {
        return runningLogRepo.findByEquipmentIdOrderByRecordedAtDesc(equipmentId);
    }
}
