package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderTriggerServiceTest {

    @Mock MaintenancePlanRepository planRepo;
    @Mock EquipmentRepository equipmentRepo;
    @Mock RunningLogRepository runningLogRepo;
    @Mock WorkOrderRepository workOrderRepo;
    @Mock MaintenanceItemRepository itemRepo;
    @Mock ExecutionRecordRepository executionRepo;
    @Mock MaintenancePlanService planService;

    @InjectMocks WorkOrderTriggerService service;

    private MaintenancePlan makeTimePlan(Long id, Integer cycleDays, Integer advanceDays,
                                          LocalDateTime nextTrigger, LocalDateTime lastTriggered) {
        MaintenancePlan p = new MaintenancePlan();
        p.setId(id);
        p.setName("计划" + id);
        p.setTriggerType("TIME_BASED");
        p.setCycleDays(cycleDays);
        p.setAdvanceNoticeDays(advanceDays);
        p.setNextTriggerAt(nextTrigger);
        p.setLastTriggeredAt(lastTriggered);
        p.setPriority("medium");
        p.setStandardWorkHours(2.0);
        p.setEnabled(true);
        p.setCreatedAt(LocalDateTime.now().minusDays(30));
        return p;
    }

    private MaintenancePlan makeUsagePlan(Long id, Integer cycleHours, Integer cycleCycles,
                                           Double lastHours, Long lastCycles) {
        MaintenancePlan p = new MaintenancePlan();
        p.setId(id);
        p.setName("使用量计划" + id);
        p.setTriggerType("USAGE_BASED");
        p.setCycleHours(cycleHours);
        p.setCycleCycles(cycleCycles);
        p.setLastTriggerRunningHours(lastHours);
        p.setLastTriggerRunningCycles(lastCycles);
        p.setPriority("high");
        p.setStandardWorkHours(3.0);
        p.setEnabled(true);
        p.setCreatedAt(LocalDateTime.now().minusDays(30));
        return p;
    }

    private Equipment makeEquipment(Long id, String code, String name) {
        Equipment e = new Equipment();
        e.setId(id);
        e.setCode(code);
        e.setName(name);
        e.setType("pump");
        return e;
    }

    @Nested
    class TimeBasedTrigger {

        @Test
        void triggerWithinAdvanceNotice_createsOrder() {
            MaintenancePlan plan = makeTimePlan(1L, 7, 3,
                    LocalDateTime.now().plusDays(2), LocalDateTime.now().minusDays(7));
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(workOrderRepo.findOpenPreventiveByEquipment(10L)).thenReturn(List.of());
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());
            when(planService.calculateNextTrigger(same(plan), any(LocalDateTime.class)))
                    .thenReturn(LocalDateTime.now().plusDays(7));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isEqualTo(1);
            assertThat(result.messages).hasSize(1);
        }

        @Test
        void triggerNotYetDue_doesNotCreate() {
            MaintenancePlan plan = makeTimePlan(1L, 30, 3,
                    LocalDateTime.now().plusDays(20), LocalDateTime.now().minusDays(10));
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isZero();
        }

        @Test
        void nextTriggerNull_calculatesAndTriggers() {
            MaintenancePlan plan = makeTimePlan(1L, 7, 0,
                    null, LocalDateTime.now().minusDays(7));
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(planService.calculateNextTrigger(same(plan), any(LocalDateTime.class)))
                    .thenReturn(LocalDateTime.now().minusDays(1));
            when(workOrderRepo.findOpenPreventiveByEquipment(10L)).thenReturn(List.of());
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isEqualTo(1);
        }

        @Test
        void advanceNoticeDaysNull_defaultsToZero() {
            MaintenancePlan plan = makeTimePlan(1L, 7, null,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(8));
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(workOrderRepo.findOpenPreventiveByEquipment(10L)).thenReturn(List.of());
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());
            when(planService.calculateNextTrigger(same(plan), any(LocalDateTime.class)))
                    .thenReturn(LocalDateTime.now().plusDays(7));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isEqualTo(1);
        }
    }

    @Nested
    class UsageBasedTrigger {

        @Test
        void hoursExceeded_createsOrder() {
            MaintenancePlan plan = makeUsagePlan(1L, 100, null, 500.0, 0L);
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(runningLogRepo.getTotalRunningHours(10L)).thenReturn(620.0);
            when(runningLogRepo.getTotalRunningCycles(10L)).thenReturn(0L);
            when(workOrderRepo.findOpenPreventiveByEquipment(10L)).thenReturn(List.of());
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isEqualTo(1);
            assertThat(result.messages.get(0)).contains("运行时长触发");
        }

        @Test
        void cyclesExceeded_createsOrder() {
            MaintenancePlan plan = makeUsagePlan(1L, null, 5000, 0.0, 10000L);
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(runningLogRepo.getTotalRunningHours(10L)).thenReturn(0.0);
            when(runningLogRepo.getTotalRunningCycles(10L)).thenReturn(16000L);
            when(workOrderRepo.findOpenPreventiveByEquipment(10L)).thenReturn(List.of());
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isEqualTo(1);
            assertThat(result.messages.get(0)).contains("运行次数触发");
        }

        @Test
        void hoursNotExceeded_noTrigger() {
            MaintenancePlan plan = makeUsagePlan(1L, 100, null, 500.0, 0L);
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(runningLogRepo.getTotalRunningHours(10L)).thenReturn(550.0);
            when(runningLogRepo.getTotalRunningCycles(10L)).thenReturn(0L);

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isZero();
        }

        @Test
        void nullRunningHours_treatedAsZero() {
            MaintenancePlan plan = makeUsagePlan(1L, 100, null, 0.0, 0L);
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(runningLogRepo.getTotalRunningHours(10L)).thenReturn(null);
            when(runningLogRepo.getTotalRunningCycles(10L)).thenReturn(null);

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isZero();
        }

        @Test
        void bothHoursAndCyclesExceeded_triggersOnce() {
            MaintenancePlan plan = makeUsagePlan(1L, 100, 5000, 500.0, 10000L);
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(runningLogRepo.getTotalRunningHours(10L)).thenReturn(620.0);
            when(runningLogRepo.getTotalRunningCycles(10L)).thenReturn(16000L);
            when(workOrderRepo.findOpenPreventiveByEquipment(10L)).thenReturn(List.of());
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isEqualTo(1);
        }
    }

    @Nested
    class DuplicatePrevention {

        @Test
        void openOrderExists_doesNotCreate() {
            MaintenancePlan plan = makeTimePlan(1L, 7, 3,
                    LocalDateTime.now().plusDays(2), LocalDateTime.now().minusDays(7));
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            WorkOrder existing = new WorkOrder();
            existing.setId(99L);
            existing.setPlanId(1L);

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(workOrderRepo.findOpenPreventiveByEquipment(10L)).thenReturn(List.of(existing));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isZero();
        }

        @Test
        void openOrderDifferentPlan_doesCreate() {
            MaintenancePlan plan = makeTimePlan(1L, 7, 3,
                    LocalDateTime.now().plusDays(2), LocalDateTime.now().minusDays(7));
            Equipment equip = makeEquipment(10L, "P001", "泵A");

            WorkOrder existing = new WorkOrder();
            existing.setId(99L);
            existing.setPlanId(2L);

            when(planRepo.findByEnabledTrue()).thenReturn(List.of(plan));
            when(planService.getApplicableEquipments(plan)).thenReturn(List.of(equip));
            when(workOrderRepo.findOpenPreventiveByEquipment(10L)).thenReturn(List.of(existing));
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());
            when(planService.calculateNextTrigger(same(plan), any(LocalDateTime.class)))
                    .thenReturn(LocalDateTime.now().plusDays(7));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.checkAndTriggerDuePlans();

            assertThat(result.createdCount).isEqualTo(1);
        }
    }

    @Nested
    class CreatePreventiveWorkOrder {

        @Test
        void createsOrderWithExecutionRecords() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(1L);
            plan.setName("月度巡检");
            plan.setPriority("high");
            plan.setStandardWorkHours(2.0);
            plan.setDescription("测试计划");
            plan.setRequiredSkill("电气");
            plan.setNextTriggerAt(LocalDateTime.now().plusDays(3));

            Equipment equip = makeEquipment(10L, "P001", "泵A");

            MaintenanceItem item1 = new MaintenanceItem();
            item1.setId(50L);
            item1.setName("检查电机");
            MaintenanceItem item2 = new MaintenanceItem();
            item2.setId(51L);
            item2.setName("检查轴承");

            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of(item1, item2));
            when(executionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrder result = service.createPreventiveWorkOrder(plan, equip, "测试触发");

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).contains("预防性维保");
            assertThat(result.getIsPreventive()).isTrue();
            assertThat(result.getPlanId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo("open");
            verify(executionRepo, times(2)).save(any());
        }

        @Test
        void createsOrderWithNoItems() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(1L);
            plan.setName("空计划");
            plan.setPriority("low");
            plan.setStandardWorkHours(1.0);
            plan.setNextTriggerAt(null);

            Equipment equip = makeEquipment(10L, "P001", "泵A");

            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(itemRepo.findByPlanIdOrderBySequenceNumAsc(1L)).thenReturn(List.of());

            WorkOrder result = service.createPreventiveWorkOrder(plan, equip, "原因");

            assertThat(result).isNotNull();
            assertThat(result.getDueDate()).isNotNull();
            verify(executionRepo, never()).save(any());
        }
    }

    @Nested
    class RunningLogTests {

        @Test
        void addRunningLog_savesCorrectly() {
            when(runningLogRepo.save(any())).thenAnswer(inv -> {
                com.admin.equipment.model.RunningLog saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            com.admin.equipment.model.RunningLog result = service.addRunningLog(10L, 5.5, 100L, "测试");

            assertThat(result).isNotNull();
            verify(runningLogRepo).save(argThat(rlog ->
                    rlog.getEquipmentId().equals(10L) &&
                    rlog.getRunningHours() == 5.5 &&
                    rlog.getRunningCycles() == 100L &&
                    "测试".equals(rlog.getRemark())
            ));
        }

        @Test
        void addRunningLog_nullRemark_defaultsToEmpty() {
            when(runningLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.addRunningLog(10L, 1.0, 10L, null);

            verify(runningLogRepo).save(argThat(rlog -> "".equals(rlog.getRemark())));
        }
    }

    @Nested
    class GetDuePlansPreview {

        @Test
        void returnsPlansDueInNext7Days() {
            when(planRepo.findPlansDueBetween(any(), any())).thenReturn(List.of());

            var result = service.getDuePlansPreview();

            assertThat(result).isEmpty();
            verify(planRepo).findPlansDueBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        }
    }
}
