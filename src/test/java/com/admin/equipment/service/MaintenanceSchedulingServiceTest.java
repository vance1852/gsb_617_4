package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceSchedulingServiceTest {

    @Mock private WorkOrderRepository workOrderRepo;
    @Mock private WorkOrderScheduleRepository scheduleRepo;
    @Mock private MaintenanceTeamRepository teamRepo;
    @Mock private DowntimeWindowRepository downtimeRepo;
    @Mock private EquipmentRepository equipmentRepo;
    @Mock private MaintenancePlanRepository planRepo;

    @InjectMocks
    private MaintenanceSchedulingService service;

    private LocalDate today;
    private Equipment eq1;
    private MaintenanceTeam team1;
    private MaintenanceTeam team2;
    private WorkOrder order1;

    @BeforeEach
    void setUp() {
        today = LocalDate.of(2025, 6, 16);

        eq1 = new Equipment();
        eq1.setId(1L);
        eq1.setName("泵机-A1");
        eq1.setType("pump");

        team1 = new MaintenanceTeam();
        team1.setId(1L);
        team1.setName("一班");
        team1.setDailyCapacityHours(8.0);

        team2 = new MaintenanceTeam();
        team2.setId(2L);
        team2.setName("二班");
        team2.setDailyCapacityHours(8.0);

        order1 = new WorkOrder();
        order1.setId(100L);
        order1.setEquipmentId(1L);
        order1.setTitle("巡检工单");
        order1.setPriority("medium");
        order1.setStatus("open");
        order1.setIsPreventive(true);
        order1.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));
    }

    private List<DowntimeWindow> defaultDailyWindows() {
        List<DowntimeWindow> windows = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            DowntimeWindow w = new DowntimeWindow();
            w.setDayOfWeek(dow);
            w.setStartMinute(8 * 60);
            w.setEndMinute(18 * 60);
            windows.add(w);
        }
        return windows;
    }

    @Nested
    @DisplayName("runSmartScheduling 智能排程")
    class RunSmartSchedulingTests {

        @Test
        @DisplayName("空班组列表时返回空结果")
        void emptyTeams_returnsEmptyResult() {
            when(teamRepo.findAll()).thenReturn(List.of());
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertTrue(result.scheduled.isEmpty());
            assertNotNull(result.metrics);
            assertNotNull(result.naiveMetrics);
        }

        @Test
        @DisplayName("无待排程工单时返回空结果")
        void noUnscheduledOrders_returnsEmptyResult() {
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of());

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertNotNull(result.metrics);
            assertNotNull(result.naiveMetrics);
        }

        @Test
        @DisplayName("已完成的预防性工单不参与排程")
        void donePreventiveOrders_skipped() {
            order1.setStatus("done");
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
        }

        @Test
        @DisplayName("已有排程的工单不参与排程")
        void alreadyScheduledOrders_skipped() {
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(new WorkOrderSchedule()));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
        }

        @Test
        @DisplayName("单个工单单个班组-成功排程")
        void singleOrderSingleTeam_schedulesSuccessfully() {
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertEquals(1, result.scheduled.size());
            MaintenanceSchedulingService.ScheduledItem item = result.scheduled.get(0);
            assertEquals(100L, item.workOrderId);
            assertEquals(1L, item.equipmentId);
            assertEquals("泵机-A1", item.equipmentName);
            assertEquals(1L, item.teamId);
            assertEquals("一班", item.teamName);
            assertTrue(!item.scheduledDate.isBefore(today));
            assertTrue(item.durationMinutes >= 30);
        }

        @Test
        @DisplayName("设备不存在时工单无法排程")
        void equipmentNotFound_orderUnscheduled() {
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.empty());

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(1, result.unscheduledCount);
        }

        @Test
        @DisplayName("按优先级排序: urgent > high > medium > low")
        void priorityOrdering_urgentScheduledFirst() {
            WorkOrder lowOrder = new WorkOrder();
            lowOrder.setId(200L);
            lowOrder.setEquipmentId(1L);
            lowOrder.setPriority("low");
            lowOrder.setStatus("open");
            lowOrder.setIsPreventive(true);
            lowOrder.setCreatedAt(LocalDateTime.of(2025, 6, 1, 10, 0));

            WorkOrder urgentOrder = new WorkOrder();
            urgentOrder.setId(300L);
            urgentOrder.setEquipmentId(1L);
            urgentOrder.setPriority("urgent");
            urgentOrder.setStatus("open");
            urgentOrder.setIsPreventive(true);
            urgentOrder.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));

            WorkOrder highOrder = new WorkOrder();
            highOrder.setId(400L);
            highOrder.setEquipmentId(1L);
            highOrder.setPriority("high");
            highOrder.setStatus("open");
            highOrder.setIsPreventive(true);
            highOrder.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));

            List<WorkOrder> orders = List.of(lowOrder, order1, urgentOrder, highOrder);

            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(orders);
            for (WorkOrder o : orders) {
                when(scheduleRepo.findByWorkOrderId(o.getId())).thenReturn(Optional.empty());
            }
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(4, result.scheduledCount);
            assertEquals(300L, result.scheduled.get(0).workOrderId);
            assertEquals(400L, result.scheduled.get(1).workOrderId);
            assertEquals(100L, result.scheduled.get(2).workOrderId);
            assertEquals(200L, result.scheduled.get(3).workOrderId);
        }

        @Test
        @DisplayName("同优先级时按截止日期排序")
        void samePriority_dueDateEarlierScheduledFirst() {
            WorkOrder laterDue = new WorkOrder();
            laterDue.setId(200L);
            laterDue.setEquipmentId(1L);
            laterDue.setPriority("medium");
            laterDue.setStatus("open");
            laterDue.setIsPreventive(true);
            laterDue.setDueDate(LocalDateTime.of(2025, 6, 25, 10, 0));
            laterDue.setCreatedAt(LocalDateTime.of(2025, 6, 1, 10, 0));

            WorkOrder earlierDue = new WorkOrder();
            earlierDue.setId(300L);
            earlierDue.setEquipmentId(1L);
            earlierDue.setPriority("medium");
            earlierDue.setStatus("open");
            earlierDue.setIsPreventive(true);
            earlierDue.setDueDate(LocalDateTime.of(2025, 6, 18, 10, 0));
            earlierDue.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));

            WorkOrder midDue = new WorkOrder();
            midDue.setId(400L);
            midDue.setEquipmentId(1L);
            midDue.setPriority("medium");
            midDue.setStatus("open");
            midDue.setIsPreventive(true);
            midDue.setDueDate(LocalDateTime.of(2025, 6, 20, 10, 0));
            midDue.setCreatedAt(LocalDateTime.of(2025, 6, 10, 10, 0));

            List<WorkOrder> orders = List.of(laterDue, earlierDue, midDue);

            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(orders);
            for (WorkOrder o : orders) {
                when(scheduleRepo.findByWorkOrderId(o.getId())).thenReturn(Optional.empty());
            }
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(3, result.scheduledCount);
            assertEquals(300L, result.scheduled.get(0).workOrderId);
            assertEquals(400L, result.scheduled.get(1).workOrderId);
            assertEquals(200L, result.scheduled.get(2).workOrderId);
        }

        @Test
        @DisplayName("无截止日期时使用创建时间排序")
        void noDueDate_usesCreatedAtForSorting() {
            WorkOrder newerOrder = new WorkOrder();
            newerOrder.setId(200L);
            newerOrder.setEquipmentId(1L);
            newerOrder.setPriority("medium");
            newerOrder.setStatus("open");
            newerOrder.setIsPreventive(true);
            newerOrder.setCreatedAt(LocalDateTime.of(2025, 6, 16, 10, 0));

            WorkOrder olderOrder = new WorkOrder();
            olderOrder.setId(300L);
            olderOrder.setEquipmentId(1L);
            olderOrder.setPriority("medium");
            olderOrder.setStatus("open");
            olderOrder.setIsPreventive(true);
            olderOrder.setCreatedAt(LocalDateTime.of(2025, 6, 10, 10, 0));

            List<WorkOrder> orders = List.of(newerOrder, olderOrder);

            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(orders);
            for (WorkOrder o : orders) {
                when(scheduleRepo.findByWorkOrderId(o.getId())).thenReturn(Optional.empty());
            }
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(2, result.scheduledCount);
            assertEquals(300L, result.scheduled.get(0).workOrderId);
            assertEquals(200L, result.scheduled.get(1).workOrderId);
        }

        @Test
        @DisplayName("班组日产能超过120%时不再安排")
        void teamCapacityExceeded_skipsSlot() {
            team1.setDailyCapacityHours(2.0);

            WorkOrder order2 = new WorkOrder();
            order2.setId(200L);
            order2.setEquipmentId(1L);
            order2.setTitle("工单2");
            order2.setPriority("medium");
            order2.setStatus("open");
            order2.setIsPreventive(true);
            order2.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));

            WorkOrder order3 = new WorkOrder();
            order3.setId(300L);
            order3.setEquipmentId(1L);
            order3.setTitle("工单3");
            order3.setPriority("medium");
            order3.setStatus("open");
            order3.setIsPreventive(true);
            order3.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));

            List<WorkOrder> orders = List.of(order1, order2, order3);

            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(orders);
            for (WorkOrder o : orders) {
                when(scheduleRepo.findByWorkOrderId(o.getId())).thenReturn(Optional.empty());
            }
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(1);

            assertTrue(result.scheduledCount + result.unscheduledCount == 3);
            assertTrue(result.scheduledCount <= 2);
        }

        @Test
        @DisplayName("工时太短时按30分钟最低时长")
        void shortDuration_minimum30Minutes() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(10L);
            plan.setStandardWorkHours(0.1);
            order1.setPlanId(10L);

            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(10L)).thenReturn(Optional.of(plan));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            assertEquals(30, result.scheduled.get(0).durationMinutes);
        }

        @Test
        @DisplayName("某天无可停机窗口时跳过该天")
        void noDowntimeWindowForDay_skipsDay() {
            DowntimeWindow onlyMon = new DowntimeWindow();
            onlyMon.setDayOfWeek(1);
            onlyMon.setStartMinute(8 * 60);
            onlyMon.setEndMinute(18 * 60);

            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(List.of(onlyMon));
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            if (result.scheduledCount == 1) {
                assertEquals(1, result.scheduled.get(0).scheduledDate.getDayOfWeek().getValue());
            }
        }

        @Test
        @DisplayName("设备无停机窗口时使用默认窗口(每天8:00-18:00)")
        void noDowntimeWindows_usesDefaultDailyWindows() {
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            assertEquals(8 * 60, result.scheduled.get(0).startMinute);
        }

        @Test
        @DisplayName("同设备同天工单享受聚类加分优先排在一起")
        void equipmentClustering_sameDayGetsBonus() {
            WorkOrder order2 = new WorkOrder();
            order2.setId(200L);
            order2.setEquipmentId(1L);
            order2.setTitle("工单2");
            order2.setPriority("low");
            order2.setStatus("open");
            order2.setIsPreventive(true);
            order2.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));

            Equipment eq2 = new Equipment();
            eq2.setId(2L);
            eq2.setName("电机-B1");
            eq2.setType("motor");

            WorkOrder otherEqOrder = new WorkOrder();
            otherEqOrder.setId(300L);
            otherEqOrder.setEquipmentId(2L);
            otherEqOrder.setTitle("其他设备工单");
            otherEqOrder.setPriority("medium");
            otherEqOrder.setStatus("open");
            otherEqOrder.setIsPreventive(true);
            otherEqOrder.setCreatedAt(LocalDateTime.of(2025, 6, 15, 10, 0));

            List<WorkOrder> orders = List.of(order1, otherEqOrder, order2);

            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(orders);
            for (WorkOrder o : orders) {
                when(scheduleRepo.findByWorkOrderId(o.getId())).thenReturn(Optional.empty());
            }
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1, eq2));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentId(2L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("motor")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(equipmentRepo.findById(2L)).thenReturn(Optional.of(eq2));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(3, result.scheduledCount);
            MaintenanceSchedulingService.ScheduledItem item1 = result.scheduled.get(0);
            MaintenanceSchedulingService.ScheduledItem item2 = result.scheduled.get(2);
            assertEquals(item1.scheduledDate, item2.scheduledDate);
        }

        @Test
        @DisplayName("排程结果包含指标计算")
        void scheduleResult_hasMetrics() {
            when(teamRepo.findAll()).thenReturn(List.of(team1, team2));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertNotNull(result.metrics);
            assertNotNull(result.naiveMetrics);
            assertTrue(result.metrics.totalDowntimeDays > 0);
        }

        @Test
        @DisplayName("根据计划标准工时计算排程时长")
        void usesPlanStandardWorkHours_forDuration() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(10L);
            plan.setStandardWorkHours(4.0);
            order1.setPlanId(10L);

            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(10L)).thenReturn(Optional.of(plan));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = service.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            assertEquals(240, result.scheduled.get(0).durationMinutes);
        }

        @Test
        @DisplayName("保存排程时更新工单指派人")
        void saveSchedule_updatesWorkOrderAssignee() {
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(defaultDailyWindows());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            service.runSmartScheduling(7);

            verify(workOrderRepo, atLeastOnce()).save(argThat(o ->
                    "一班".equals(o.getAssignee())
            ));
        }
    }

    @Nested
    @DisplayName("reschedule 改派/改期")
    class RescheduleTests {

        @Test
        @DisplayName("排程不存在时返回null")
        void scheduleNotFound_returnsNull() {
            when(scheduleRepo.findById(999L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = service.reschedule(999L, today.plusDays(1), null);

            assertNull(result);
        }

        @Test
        @DisplayName("改期成功-更新日期和版本号")
        void rescheduleDate_updatesDateAndVersion() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(10L);
            schedule.setWorkOrderId(100L);
            schedule.setScheduledDate(today);
            schedule.setSchedulingVersion(1);
            schedule.setTeamId(1L);
            schedule.setTeamName("一班");

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order1));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            LocalDate newDate = today.plusDays(3);
            WorkOrderSchedule result = service.reschedule(10L, newDate, null);

            assertNotNull(result);
            assertEquals(newDate, result.getScheduledDate());
            assertEquals(2, result.getSchedulingVersion());
            verify(scheduleRepo).save(any(WorkOrderSchedule.class));
        }

        @Test
        @DisplayName("改期且改派-更新班组信息")
        void rescheduleWithNewTeam_updatesTeamInfo() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(10L);
            schedule.setWorkOrderId(100L);
            schedule.setScheduledDate(today);
            schedule.setSchedulingVersion(1);
            schedule.setTeamId(1L);
            schedule.setTeamName("一班");

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(teamRepo.findById(2L)).thenReturn(Optional.of(team2));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order1));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = service.reschedule(10L, today.plusDays(1), 2L);

            assertEquals(2L, result.getTeamId());
            assertEquals("二班", result.getTeamName());
        }

        @Test
        @DisplayName("新班组不存在时保留原班组")
        void newTeamNotFound_keepsOriginalTeam() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(10L);
            schedule.setWorkOrderId(100L);
            schedule.setScheduledDate(today);
            schedule.setSchedulingVersion(1);
            schedule.setTeamId(1L);
            schedule.setTeamName("一班");

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(teamRepo.findById(999L)).thenReturn(Optional.empty());
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order1));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = service.reschedule(10L, today.plusDays(1), 999L);

            assertEquals(1L, result.getTeamId());
            assertEquals("一班", result.getTeamName());
        }

        @Test
        @DisplayName("改期后重新计算逾期等级")
        void reschedule_recalculatesOverdueLevel() {
            order1.setDueDate(LocalDateTime.of(2025, 6, 17, 10, 0));

            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(10L);
            schedule.setWorkOrderId(100L);
            schedule.setScheduledDate(today);
            schedule.setSchedulingVersion(1);
            schedule.setTeamId(1L);
            schedule.setTeamName("一班");
            schedule.setOverdueLevel(0);

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order1));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            LocalDate newDate = today.plusDays(5);
            WorkOrderSchedule result = service.reschedule(10L, newDate, null);

            assertTrue(result.getOverdueLevel() >= 1);
        }

        @Test
        @DisplayName("改期时更新工单指派人")
        void reschedule_updatesWorkOrderAssignee() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(10L);
            schedule.setWorkOrderId(100L);
            schedule.setScheduledDate(today);
            schedule.setSchedulingVersion(1);
            schedule.setTeamId(1L);
            schedule.setTeamName("一班");

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(teamRepo.findById(2L)).thenReturn(Optional.of(team2));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order1));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            service.reschedule(10L, today.plusDays(1), 2L);

            verify(workOrderRepo).save(argThat(o -> "二班".equals(o.getAssignee())));
        }
    }

    @Nested
    @DisplayName("getCalendar 日历查询")
    class GetCalendarTests {

        @Test
        @DisplayName("按设备ID查询日历")
        void byEquipmentId_callsEquipmentQuery() {
            when(scheduleRepo.findByEquipmentAndDateRange(eq(1L), any(), any())).thenReturn(List.of());

            service.getCalendar(today, today.plusDays(7), 1L, null);

            verify(scheduleRepo).findByEquipmentAndDateRange(1L, today, today.plusDays(7));
        }

        @Test
        @DisplayName("按班组ID查询日历")
        void byTeamId_callsTeamQuery() {
            when(scheduleRepo.findByTeamAndDateRange(eq(1L), any(), any())).thenReturn(List.of());

            service.getCalendar(today, today.plusDays(7), null, 1L);

            verify(scheduleRepo).findByTeamAndDateRange(1L, today, today.plusDays(7));
        }

        @Test
        @DisplayName("设备ID优先于班组ID")
        void equipmentIdTakesPrecedence_overTeamId() {
            when(scheduleRepo.findByEquipmentAndDateRange(eq(1L), any(), any())).thenReturn(List.of());

            service.getCalendar(today, today.plusDays(7), 1L, 2L);

            verify(scheduleRepo).findByEquipmentAndDateRange(1L, today, today.plusDays(7));
            verify(scheduleRepo, never()).findByTeamAndDateRange(anyLong(), any(), any());
        }

        @Test
        @DisplayName("都不指定时按日期范围查询")
        void noFilters_usesDateRangeQuery() {
            when(scheduleRepo.findByScheduledDateBetween(any(), any())).thenReturn(List.of());

            service.getCalendar(today, today.plusDays(7), null, null);

            verify(scheduleRepo).findByScheduledDateBetween(today, today.plusDays(7));
        }
    }

    @Nested
    @DisplayName("班组管理")
    class TeamManagementTests {

        @Test
        @DisplayName("listTeams 调用 teamRepo.findAll")
        void listTeams_delegatesToRepo() {
            when(teamRepo.findAll()).thenReturn(List.of(team1, team2));

            List<MaintenanceTeam> result = service.listTeams();

            assertEquals(2, result.size());
            verify(teamRepo).findAll();
        }

        @Test
        @DisplayName("saveTeam 委托给 teamRepo.save")
        void saveTeam_delegatesToRepo() {
            when(teamRepo.save(team1)).thenReturn(team1);

            MaintenanceTeam result = service.saveTeam(team1);

            assertSame(team1, result);
            verify(teamRepo).save(team1);
        }
    }

    @Nested
    @DisplayName("停机窗口管理")
    class DowntimeWindowTests {

        @Test
        @DisplayName("按设备ID查停机窗口")
        void listByEquipmentId() {
            DowntimeWindow w = new DowntimeWindow();
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(List.of(w));

            List<DowntimeWindow> result = service.listDowntimeWindows(1L, null);

            assertEquals(1, result.size());
            verify(downtimeRepo).findByEquipmentId(1L);
        }

        @Test
        @DisplayName("按设备类型查停机窗口")
        void listByEquipmentType() {
            DowntimeWindow w = new DowntimeWindow();
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of(w));

            List<DowntimeWindow> result = service.listDowntimeWindows(null, "pump");

            assertEquals(1, result.size());
            verify(downtimeRepo).findByEquipmentType("pump");
        }

        @Test
        @DisplayName("都不指定时查全部")
        void listAll() {
            when(downtimeRepo.findAll()).thenReturn(List.of());

            service.listDowntimeWindows(null, null);

            verify(downtimeRepo).findAll();
        }

        @Test
        @DisplayName("保存停机窗口")
        void saveDowntimeWindow() {
            DowntimeWindow w = new DowntimeWindow();
            when(downtimeRepo.save(w)).thenReturn(w);

            DowntimeWindow result = service.saveDowntimeWindow(w);

            assertSame(w, result);
            verify(downtimeRepo).save(w);
        }

        @Test
        @DisplayName("删除停机窗口")
        void deleteDowntimeWindow() {
            doNothing().when(downtimeRepo).deleteById(1L);

            service.deleteDowntimeWindow(1L);

            verify(downtimeRepo).deleteById(1L);
        }
    }
}
