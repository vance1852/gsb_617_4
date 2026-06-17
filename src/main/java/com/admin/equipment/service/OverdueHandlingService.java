package com.admin.equipment.service;

import com.admin.equipment.model.WorkOrder;
import com.admin.equipment.model.WorkOrderSchedule;
import com.admin.equipment.repo.WorkOrderRepository;
import com.admin.equipment.repo.WorkOrderScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class OverdueHandlingService {

    private final WorkOrderRepository workOrderRepo;
    private final WorkOrderScheduleRepository scheduleRepo;

    public OverdueHandlingService(WorkOrderRepository workOrderRepo,
                                  WorkOrderScheduleRepository scheduleRepo) {
        this.workOrderRepo = workOrderRepo;
        this.scheduleRepo = scheduleRepo;
    }

    public static class OverdueSummary {
        public int level1Count;
        public int level2Count;
        public int level3Count;
        public List<OverdueItem> items = new ArrayList<>();
    }

    public static class OverdueItem {
        public Long workOrderId;
        public Long scheduleId;
        public String title;
        public LocalDateTime dueDate;
        public LocalDate scheduledDate;
        public int overdueLevel;
        public long daysOverdue;
        public String assignee;
    }

    public OverdueSummary getOverdueSummary() {
        OverdueSummary summary = new OverdueSummary();
        LocalDateTime now = LocalDateTime.now();
        List<WorkOrder> overdueOrders = workOrderRepo.findOverduePreventive(now);
        for (WorkOrder order : overdueOrders) {
            OverdueItem item = new OverdueItem();
            item.workOrderId = order.getId();
            item.title = order.getTitle();
            item.dueDate = order.getDueDate();
            item.assignee = order.getAssignee();
            if (order.getDueDate() != null) {
                item.daysOverdue = ChronoUnit.DAYS.between(order.getDueDate(), now);
            }
            WorkOrderSchedule schedule = scheduleRepo.findByWorkOrderId(order.getId()).orElse(null);
            if (schedule != null) {
                item.scheduleId = schedule.getId();
                item.scheduledDate = schedule.getScheduledDate();
                item.overdueLevel = schedule.getOverdueLevel();
            }
            if (item.overdueLevel >= 3) summary.level3Count++;
            else if (item.overdueLevel >= 2) summary.level2Count++;
            else summary.level1Count++;
            summary.items.add(item);
        }
        return summary;
    }

    @Transactional
    public int escalateOverdueOrders() {
        int escalated = 0;
        LocalDateTime now = LocalDateTime.now();
        List<WorkOrder> overdueOrders = workOrderRepo.findOverduePreventive(now);
        for (WorkOrder order : overdueOrders) {
            WorkOrderSchedule schedule = scheduleRepo.findByWorkOrderId(order.getId()).orElse(null);
            if (schedule == null) continue;
            int oldLevel = schedule.getOverdueLevel();
            int newLevel = computeOverdueLevel(order.getDueDate(), schedule.getScheduledDate());
            if (newLevel > oldLevel) {
                schedule.setOverdueLevel(newLevel);
                schedule.setUpdatedAt(now);
                scheduleRepo.save(schedule);
                if (newLevel >= 2 && "low".equals(order.getPriority())) {
                    order.setPriority("medium");
                    workOrderRepo.save(order);
                } else if (newLevel >= 3 && "medium".equals(order.getPriority())) {
                    order.setPriority("high");
                    workOrderRepo.save(order);
                }
                escalated++;
            }
        }
        return escalated;
    }

    @Transactional
    public WorkOrderSchedule postponeSchedule(Long scheduleId, int daysToPostpone, String reason) {
        WorkOrderSchedule schedule = scheduleRepo.findById(scheduleId).orElse(null);
        if (schedule == null) return null;
        LocalDate newDate = schedule.getScheduledDate().plusDays(daysToPostpone);
        schedule.setScheduledDate(newDate);
        schedule.setSchedulingVersion(schedule.getSchedulingVersion() + 1);
        schedule.setUpdatedAt(LocalDateTime.now());
        WorkOrder order = workOrderRepo.findById(schedule.getWorkOrderId()).orElse(null);
        if (order != null) {
            schedule.setOverdueLevel(computeOverdueLevel(order.getDueDate(), newDate));
            String desc = order.getDescription() == null ? "" : order.getDescription();
            order.setDescription(desc + "\n[顺延 " + daysToPostpone + " 天] " + (reason != null ? reason : ""));
            workOrderRepo.save(order);
        }
        return scheduleRepo.save(schedule);
    }

    private int computeOverdueLevel(LocalDateTime dueDate, LocalDate scheduled) {
        if (dueDate == null) return 0;
        long daysLate = ChronoUnit.DAYS.between(dueDate.toLocalDate(), scheduled);
        if (daysLate <= 0) return 0;
        if (daysLate <= 3) return 1;
        if (daysLate <= 7) return 2;
        return 3;
    }
}
