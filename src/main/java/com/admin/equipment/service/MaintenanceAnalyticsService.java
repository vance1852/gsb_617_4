package com.admin.equipment.service;

import com.admin.equipment.model.ExecutionRecord;
import com.admin.equipment.model.MaintenancePlan;
import com.admin.equipment.model.MaintenanceItem;
import com.admin.equipment.model.WorkOrder;
import com.admin.equipment.repo.ExecutionRecordRepository;
import com.admin.equipment.repo.MaintenanceItemRepository;
import com.admin.equipment.repo.MaintenancePlanRepository;
import com.admin.equipment.repo.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MaintenanceAnalyticsService {

    private final WorkOrderRepository workOrderRepo;
    private final MaintenancePlanRepository planRepo;
    private final ExecutionRecordRepository recordRepo;
    private final MaintenanceItemRepository itemRepo;

    public MaintenanceAnalyticsService(WorkOrderRepository workOrderRepo,
                                       MaintenancePlanRepository planRepo,
                                       ExecutionRecordRepository recordRepo,
                                       MaintenanceItemRepository itemRepo) {
        this.workOrderRepo = workOrderRepo;
        this.planRepo = planRepo;
        this.recordRepo = recordRepo;
        this.itemRepo = itemRepo;
    }

    public static class OnTimeRateStats {
        public long totalScheduled;
        public long completedOnTime;
        public long completedOverdue;
        public long overdueIncomplete;
        public double onTimeRate;
        public double overdueRate;
    }

    public static class WorkHoursDeviation {
        public Long planId;
        public String planName;
        public Long itemId;
        public String itemName;
        public int standardMinutes;
        public double avgActualMinutes;
        public int sampleCount;
        public double deviationPercent;
    }

    public static class PlanExecutionSummary {
        public Long planId;
        public String planName;
        public long totalTriggers;
        public long completedCount;
        public long overdueCount;
        public double completionRate;
    }

    public OnTimeRateStats getOnTimeRate(LocalDateTime from, LocalDateTime to) {
        OnTimeRateStats s = new OnTimeRateStats();
        s.totalScheduled = workOrderRepo.countScheduledPreventiveBetween(from, to);
        List<WorkOrder> completed = workOrderRepo.findByIsPreventiveTrue().stream()
                .filter(o -> "done".equals(o.getStatus())
                        && o.getClosedAt() != null
                        && !o.getClosedAt().isBefore(from)
                        && !o.getClosedAt().isAfter(to))
                .toList();
        for (WorkOrder o : completed) {
            if (o.getDueDate() != null && o.getClosedAt() != null) {
                if (o.getClosedAt().isBefore(o.getDueDate()) || o.getClosedAt().isEqual(o.getDueDate())) {
                    s.completedOnTime++;
                } else {
                    s.completedOverdue++;
                }
            } else {
                s.completedOnTime++;
            }
        }
        List<WorkOrder> incomplete = workOrderRepo.findOverduePreventive(LocalDateTime.now());
        s.overdueIncomplete = incomplete.stream()
                .filter(o -> o.getDueDate() != null
                        && !o.getDueDate().isBefore(from)
                        && !o.getDueDate().isAfter(to))
                .count();
        long total = Math.max(1, s.totalScheduled);
        s.onTimeRate = (double) s.completedOnTime / total;
        s.overdueRate = (double) (s.completedOverdue + s.overdueIncomplete) / total;
        return s;
    }

    public List<WorkHoursDeviation> getWorkHoursDeviation() {
        List<WorkHoursDeviation> result = new ArrayList<>();
        List<MaintenancePlan> plans = planRepo.findAll();
        for (MaintenancePlan plan : plans) {
            List<MaintenanceItem> items = itemRepo.findByPlanIdOrderBySequenceNumAsc(plan.getId());
            for (MaintenanceItem item : items) {
                List<ExecutionRecord> records = recordRepo.findAll().stream()
                        .filter(r -> item.getId().equals(r.getItemId())
                                && r.getActualMinutes() != null
                                && r.getActualMinutes() > 0)
                        .toList();
                if (records.isEmpty()) continue;
                double avg = records.stream().mapToInt(ExecutionRecord::getActualMinutes).average().orElse(0);
                WorkHoursDeviation d = new WorkHoursDeviation();
                d.planId = plan.getId();
                d.planName = plan.getName();
                d.itemId = item.getId();
                d.itemName = item.getName();
                d.standardMinutes = item.getStandardMinutes();
                d.avgActualMinutes = Math.round(avg * 10) / 10.0;
                d.sampleCount = records.size();
                d.deviationPercent = item.getStandardMinutes() > 0 ?
                        Math.round((avg - item.getStandardMinutes()) / item.getStandardMinutes() * 1000) / 10.0 : 0;
                result.add(d);
            }
        }
        return result;
    }

    public List<PlanExecutionSummary> getPlanExecutionSummary() {
        List<PlanExecutionSummary> result = new ArrayList<>();
        List<MaintenancePlan> plans = planRepo.findAll();
        for (MaintenancePlan plan : plans) {
            List<WorkOrder> orders = workOrderRepo.findByPlanId(plan.getId());
            PlanExecutionSummary s = new PlanExecutionSummary();
            s.planId = plan.getId();
            s.planName = plan.getName();
            s.totalTriggers = orders.size();
            s.completedCount = orders.stream().filter(o -> "done".equals(o.getStatus())).count();
            s.overdueCount = orders.stream()
                    .filter(o -> o.getDueDate() != null
                            && ("done".equals(o.getStatus()) ?
                            (o.getClosedAt() != null && o.getClosedAt().isAfter(o.getDueDate())) :
                            LocalDateTime.now().isAfter(o.getDueDate())))
                    .count();
            s.completionRate = s.totalTriggers > 0 ?
                    Math.round((double) s.completedCount / s.totalTriggers * 1000) / 10.0 : 0;
            result.add(s);
        }
        return result;
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusDays(30);

        stats.put("activePlans", planRepo.findByEnabledTrue().size());
        stats.put("totalPlans", planRepo.count());
        stats.put("preventiveOrders", workOrderRepo.findByIsPreventiveTrue().size());
        stats.put("overdueOrders", workOrderRepo.findOverduePreventive(now).size());

        OnTimeRateStats week = getOnTimeRate(weekAgo, now);
        OnTimeRateStats month = getOnTimeRate(monthAgo, now);
        stats.put("weeklyOnTimeRate", Math.round(week.onTimeRate * 1000) / 10.0);
        stats.put("monthlyOnTimeRate", Math.round(month.onTimeRate * 1000) / 10.0);

        return stats;
    }
}
