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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceSchedulingServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepo;
    @Mock
    private WorkOrderScheduleRepository scheduleRepo;
    @Mock
    private MaintenanceTeamRepository teamRepo;
    @Mock
    private DowntimeWindowRepository downtimeRepo;
    @Mock
    private EquipmentRepository equipmentRepo;
    @Mock
    private MaintenancePlanRepository planRepo;

    @InjectMocks
    private MaintenanceSchedulingService schedulingService;

    private Equipment testEquipment;
    private MaintenanceTeam testTeam;
    private WorkOrder testOrder;

    @BeforeEach
    void setUp() {
        testEquipment = new Equipment();
        testEquipment.setId(1L);
        testEquipment.setName("测试设备");
        testEquipment.setType("pump");
        testEquipment.setCode("EQ-001");

        testTeam = new MaintenanceTeam();
        testTeam.setId(1L);
        testTeam.setName("A组");
        testTeam.setDailyCapacityHours(8.0);

        testOrder = new WorkOrder();
        testOrder.setId(100L);
        testOrder.setEquipmentId(1L);
        testOrder.setTitle("测试工单");
        testOrder.setPriority("medium");
        testOrder.setStatus("open");
        testOrder.setIsPreventive(true);
        testOrder.setCreatedAt(LocalDateTime.now().minusDays(2));
    }

    @Nested
    @DisplayName("智能排程 runSmartScheduling")
    class RunSmartSchedulingTests {

        @Test
        @DisplayName("空团队时返回空结果")
        void emptyTeams_returnsEmptyResult() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(Collections.emptyList());

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertTrue(result.scheduled.isEmpty());
            assertNotNull(result.metrics);
            assertNotNull(result.naiveMetrics);
            verify(teamRepo, times(1)).findAll();
        }

        @Test
        @DisplayName("空待排程工单时返回空结果")
        void emptyUnscheduledOrders_returnsEmptyResult() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(Collections.emptyList());
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertTrue(result.scheduled.isEmpty());
        }

        @Test
        @DisplayName("已完成的工单不参与排程")
        void doneOrders_excludedFromScheduling() {
            WorkOrder doneOrder = new WorkOrder();
            doneOrder.setId(200L);
            doneOrder.setEquipmentId(1L);
            doneOrder.setStatus("done");
            doneOrder.setIsPreventive(true);

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(doneOrder, testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));
            when(equipmentRepo.findAll()).thenReturn(List.of(testEquipment));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(Collections.emptyList());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(Collections.emptyList());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(testEquipment));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
        }

        @Test
        @DisplayName("已有排程的工单不重复排程")
        void alreadyScheduledOrders_excludedFromScheduling() {
            WorkOrderSchedule existingSchedule = new WorkOrderSchedule();
            existingSchedule.setId(500L);
            existingSchedule.setWorkOrderId(100L);

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(existingSchedule));
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
        }

        @Test
        @DisplayName("正常排程 - 单工单单团队")
        void normalScheduling_singleOrderSingleTeam() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));
            when(equipmentRepo.findAll()).thenReturn(List.of(testEquipment));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(Collections.emptyList());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(Collections.emptyList());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(testEquipment));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertEquals(1, result.scheduled.size());

            MaintenanceSchedulingService.ScheduledItem item = result.scheduled.get(0);
            assertEquals(100L, item.workOrderId);
            assertEquals(1L, item.equipmentId);
            assertEquals(1L, item.teamId);
            assertNotNull(item.scheduledDate);
            assertTrue(item.durationMinutes >= 30);
        }

        @Test
        @DisplayName("设备不存在时工单无法排程")
        void equipmentNotFound_orderCannotBeScheduled() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));
            when(equipmentRepo.findAll()).thenReturn(List.of(testEquipment));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(Collections.emptyList());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(Collections.emptyList());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.empty());

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(1, result.unscheduledCount);
        }

        @Test
        @DisplayName("优先级高的工单先排程")
        void higherPriorityOrders_scheduledFirst() {
            WorkOrder lowPriority = new WorkOrder();
            lowPriority.setId(200L);
            lowPriority.setEquipmentId(1L);
            lowPriority.setTitle("低优先级工单");
            lowPriority.setPriority("low");
            lowPriority.setStatus("open");
            lowPriority.setIsPreventive(true);
            lowPriority.setCreatedAt(LocalDateTime.now().minusDays(5));

            WorkOrder urgentPriority = new WorkOrder();
            urgentPriority.setId(300L);
            urgentPriority.setEquipmentId(1L);
            urgentPriority.setTitle("紧急工单");
            urgentPriority.setPriority("urgent");
            urgentPriority.setStatus("open");
            urgentPriority.setIsPreventive(true);
            urgentPriority.setCreatedAt(LocalDateTime.now().minusDays(1));

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(lowPriority, urgentPriority));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));
            when(equipmentRepo.findAll()).thenReturn(List.of(testEquipment));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(Collections.emptyList());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(Collections.emptyList());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(testEquipment));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(2, result.scheduledCount);
            assertTrue(result.scheduled.get(0).priorityWeight > result.scheduled.get(1).priorityWeight);
        }

        @Test
        @DisplayName("同优先级时截止日期近的先排程")
        void samePriority_earlierDueDateScheduledFirst() {
            WorkOrder orderSoon = new WorkOrder();
            orderSoon.setId(200L);
            orderSoon.setEquipmentId(1L);
            orderSoon.setTitle("即将到期");
            orderSoon.setPriority("medium");
            orderSoon.setStatus("open");
            orderSoon.setIsPreventive(true);
            orderSoon.setDueDate(LocalDateTime.now().plusDays(1));

            WorkOrder orderLater = new WorkOrder();
            orderLater.setId(300L);
            orderLater.setEquipmentId(1L);
            orderLater.setTitle("较晚到期");
            orderLater.setPriority("medium");
            orderLater.setStatus("open");
            orderLater.setIsPreventive(true);
            orderLater.setDueDate(LocalDateTime.now().plusDays(5));

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(orderLater, orderSoon));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));
            when(equipmentRepo.findAll()).thenReturn(List.of(testEquipment));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(Collections.emptyList());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(Collections.emptyList());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(testEquipment));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(2, result.scheduledCount);
            assertTrue(!result.scheduled.get(0).scheduledDate.isAfter(result.scheduled.get(1).scheduledDate));
        }

        @Test
        @DisplayName("工单有计划时使用计划的标准工时")
        void orderWithPlan_usesPlanStandardHours() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(10L);
            plan.setStandardWorkHours(4.0);

            testOrder.setPlanId(10L);

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));
            when(equipmentRepo.findAll()).thenReturn(List.of(testEquipment));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(Collections.emptyList());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(Collections.emptyList());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(testEquipment));
            when(planRepo.findById(10L)).thenReturn(Optional.of(plan));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            assertEquals(240, result.scheduled.get(0).durationMinutes);
        }

        @Test
        @DisplayName("团队容量超限时工单无法排程")
        void teamCapacityExceeded_orderCannotBeScheduled() {
            MaintenanceTeam smallTeam = new MaintenanceTeam();
            smallTeam.setId(2L);
            smallTeam.setName("B组");
            smallTeam.setDailyCapacityHours(1.0);

            MaintenancePlan bigPlan = new MaintenancePlan();
            bigPlan.setId(20L);
            bigPlan.setStandardWorkHours(3.0);

            testOrder.setPlanId(20L);

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(smallTeam));
            when(equipmentRepo.findAll()).thenReturn(List.of(testEquipment));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(Collections.emptyList());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(Collections.emptyList());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(testEquipment));
            when(planRepo.findById(20L)).thenReturn(Optional.of(bigPlan));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(1, result.unscheduledCount);
        }

        @Test
        @DisplayName("团队容量允许120%超载")
        void teamCapacity_allows120PercentOverload() {
            MaintenanceTeam team = new MaintenanceTeam();
            team.setId(2L);
            team.setName("B组");
            team.setDailyCapacityHours(2.0);

            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(20L);
            plan.setStandardWorkHours(2.0);

            testOrder.setPlanId(20L);

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(testEquipment));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(Collections.emptyList());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(Collections.emptyList());
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(testEquipment));
            when(planRepo.findById(20L)).thenReturn(Optional.of(plan));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
        }
    }

    @Nested
    @DisplayName("逾期等级计算 computeOverdueLevel")
    class ComputeOverdueLevelTests {

        @Test
        @DisplayName("无截止日期时等级为0")
        void noDueDate_levelZero() {
            WorkOrder order = new WorkOrder();
            order.setDueDate(null);

            int level = computeOverdueLevelViaReflection(order, LocalDate.now());

            assertEquals(0, level);
        }

        @Test
        @DisplayName("排程在截止日期前 - 等级0")
        void scheduledBeforeDue_levelZero() {
            WorkOrder order = new WorkOrder();
            order.setDueDate(LocalDateTime.now().plusDays(5));

            int level = computeOverdueLevelViaReflection(order, LocalDate.now());

            assertEquals(0, level);
        }

        @Test
        @DisplayName("排程刚好在截止日期当天 - 等级0")
        void scheduledOnDueDate_levelZero() {
            LocalDate dueDate = LocalDate.now();
            WorkOrder order = new WorkOrder();
            order.setDueDate(dueDate.atStartOfDay());

            int level = computeOverdueLevelViaReflection(order, dueDate);

            assertEquals(0, level);
        }

        @Test
        @DisplayName("逾期1天 - 等级1")
        void overdue1Day_level1() {
            LocalDate dueDate = LocalDate.now().minusDays(1);
            WorkOrder order = new WorkOrder();
            order.setDueDate(dueDate.atStartOfDay());

            int level = computeOverdueLevelViaReflection(order, LocalDate.now());

            assertEquals(1, level);
        }

        @Test
        @DisplayName("逾期3天 - 等级1（边界）")
        void overdue3Days_level1() {
            LocalDate dueDate = LocalDate.now().minusDays(3);
            WorkOrder order = new WorkOrder();
            order.setDueDate(dueDate.atStartOfDay());

            int level = computeOverdueLevelViaReflection(order, LocalDate.now());

            assertEquals(1, level);
        }

        @Test
        @DisplayName("逾期4天 - 等级2（边界）")
        void overdue4Days_level2() {
            LocalDate dueDate = LocalDate.now().minusDays(4);
            WorkOrder order = new WorkOrder();
            order.setDueDate(dueDate.atStartOfDay());

            int level = computeOverdueLevelViaReflection(order, LocalDate.now());

            assertEquals(2, level);
        }

        @Test
        @DisplayName("逾期7天 - 等级2（边界）")
        void overdue7Days_level2() {
            LocalDate dueDate = LocalDate.now().minusDays(7);
            WorkOrder order = new WorkOrder();
            order.setDueDate(dueDate.atStartOfDay());

            int level = computeOverdueLevelViaReflection(order, LocalDate.now());

            assertEquals(2, level);
        }

        @Test
        @DisplayName("逾期8天 - 等级3（边界）")
        void overdue8Days_level3() {
            LocalDate dueDate = LocalDate.now().minusDays(8);
            WorkOrder order = new WorkOrder();
            order.setDueDate(dueDate.atStartOfDay());

            int level = computeOverdueLevelViaReflection(order, LocalDate.now());

            assertEquals(3, level);
        }

        @Test
        @DisplayName("逾期30天 - 等级3")
        void overdue30Days_level3() {
            LocalDate dueDate = LocalDate.now().minusDays(30);
            WorkOrder order = new WorkOrder();
            order.setDueDate(dueDate.atStartOfDay());

            int level = computeOverdueLevelViaReflection(order, LocalDate.now());

            assertEquals(3, level);
        }
    }

    @Nested
    @DisplayName("重新排程 reschedule")
    class RescheduleTests {

        @Test
        @DisplayName("排程不存在时返回null")
        void scheduleNotFound_returnsNull() {
            when(scheduleRepo.findById(999L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = schedulingService.reschedule(999L, LocalDate.now(), null);

            assertNull(result);
        }

        @Test
        @DisplayName("正常重新排程 - 更新日期")
        void normalReschedule_updatesDate() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(100L);
            schedule.setScheduledDate(LocalDate.now().minusDays(5));
            schedule.setSchedulingVersion(1);
            schedule.setTeamId(1L);
            schedule.setTeamName("A组");

            LocalDate newDate = LocalDate.now().plusDays(3);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = schedulingService.reschedule(1L, newDate, null);

            assertNotNull(result);
            assertEquals(newDate, result.getScheduledDate());
            assertEquals(2, result.getSchedulingVersion());
            verify(scheduleRepo, times(1)).save(any());
            verify(workOrderRepo, times(1)).save(any());
        }

        @Test
        @DisplayName("重新排程并更换团队")
        void rescheduleWithNewTeam_updatesTeam() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(100L);
            schedule.setScheduledDate(LocalDate.now());
            schedule.setSchedulingVersion(1);
            schedule.setTeamId(1L);
            schedule.setTeamName("A组");

            MaintenanceTeam newTeam = new MaintenanceTeam();
            newTeam.setId(2L);
            newTeam.setName("B组");

            LocalDate newDate = LocalDate.now().plusDays(1);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(teamRepo.findById(2L)).thenReturn(Optional.of(newTeam));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = schedulingService.reschedule(1L, newDate, 2L);

            assertNotNull(result);
            assertEquals(2L, result.getTeamId());
            assertEquals("B组", result.getTeamName());
        }

        @Test
        @DisplayName("新团队不存在时保持原团队")
        void newTeamNotFound_keepsOriginalTeam() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(100L);
            schedule.setScheduledDate(LocalDate.now());
            schedule.setSchedulingVersion(1);
            schedule.setTeamId(1L);
            schedule.setTeamName("A组");

            LocalDate newDate = LocalDate.now().plusDays(1);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(teamRepo.findById(999L)).thenReturn(Optional.empty());
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = schedulingService.reschedule(1L, newDate, 999L);

            assertNotNull(result);
            assertEquals(1L, result.getTeamId());
            assertEquals("A组", result.getTeamName());
        }

        @Test
        @DisplayName("工单不存在时不更新逾期等级和处理人")
        void workOrderNotFound_doesNotUpdateOrder() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(999L);
            schedule.setScheduledDate(LocalDate.now());
            schedule.setSchedulingVersion(1);
            schedule.setOverdueLevel(0);

            LocalDate newDate = LocalDate.now().plusDays(10);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(999L)).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = schedulingService.reschedule(1L, newDate, null);

            assertNotNull(result);
            assertEquals(0, result.getOverdueLevel());
            verify(workOrderRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("日历查询 getCalendar")
    class GetCalendarTests {

        @Test
        @DisplayName("按设备查询日历")
        void queryByEquipment_usesEquipmentQuery() {
            LocalDate from = LocalDate.now().minusDays(7);
            LocalDate to = LocalDate.now().plusDays(7);

            when(scheduleRepo.findByEquipmentAndDateRange(1L, from, to))
                    .thenReturn(Collections.emptyList());

            List<WorkOrderSchedule> result = schedulingService.getCalendar(from, to, 1L, null);

            assertNotNull(result);
            verify(scheduleRepo, times(1)).findByEquipmentAndDateRange(1L, from, to);
            verify(scheduleRepo, never()).findByTeamAndDateRange(anyLong(), any(), any());
            verify(scheduleRepo, never()).findByScheduledDateBetween(any(), any());
        }

        @Test
        @DisplayName("按团队查询日历")
        void queryByTeam_usesTeamQuery() {
            LocalDate from = LocalDate.now().minusDays(7);
            LocalDate to = LocalDate.now().plusDays(7);

            when(scheduleRepo.findByTeamAndDateRange(1L, from, to))
                    .thenReturn(Collections.emptyList());

            List<WorkOrderSchedule> result = schedulingService.getCalendar(from, to, null, 1L);

            assertNotNull(result);
            verify(scheduleRepo, times(1)).findByTeamAndDateRange(1L, from, to);
            verify(scheduleRepo, never()).findByEquipmentAndDateRange(anyLong(), any(), any());
        }

        @Test
        @DisplayName("无过滤条件时按日期范围查询")
        void queryNoFilter_usesDateRangeQuery() {
            LocalDate from = LocalDate.now().minusDays(7);
            LocalDate to = LocalDate.now().plusDays(7);

            when(scheduleRepo.findByScheduledDateBetween(from, to))
                    .thenReturn(Collections.emptyList());

            List<WorkOrderSchedule> result = schedulingService.getCalendar(from, to, null, null);

            assertNotNull(result);
            verify(scheduleRepo, times(1)).findByScheduledDateBetween(from, to);
        }
    }

    @Nested
    @DisplayName("团队管理")
    class TeamManagementTests {

        @Test
        @DisplayName("列出所有团队")
        void listTeams_returnsAllTeams() {
            when(teamRepo.findAll()).thenReturn(List.of(testTeam));

            List<MaintenanceTeam> result = schedulingService.listTeams();

            assertEquals(1, result.size());
            assertEquals("A组", result.get(0).getName());
        }

        @Test
        @DisplayName("保存团队")
        void saveTeam_returnsSavedTeam() {
            when(teamRepo.save(testTeam)).thenReturn(testTeam);

            MaintenanceTeam result = schedulingService.saveTeam(testTeam);

            assertNotNull(result);
            verify(teamRepo, times(1)).save(testTeam);
        }
    }

    @Nested
    @DisplayName("停机窗口管理")
    class DowntimeWindowManagementTests {

        @Test
        @DisplayName("按设备ID查询停机窗口")
        void listByEquipmentId_usesEquipmentQuery() {
            DowntimeWindow window = new DowntimeWindow();
            window.setId(1L);
            window.setEquipmentId(1L);

            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(List.of(window));

            List<DowntimeWindow> result = schedulingService.listDowntimeWindows(1L, null);

            assertEquals(1, result.size());
            verify(downtimeRepo, times(1)).findByEquipmentId(1L);
        }

        @Test
        @DisplayName("按设备类型查询停机窗口")
        void listByEquipmentType_usesTypeQuery() {
            DowntimeWindow window = new DowntimeWindow();
            window.setId(1L);
            window.setEquipmentType("pump");

            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of(window));

            List<DowntimeWindow> result = schedulingService.listDowntimeWindows(null, "pump");

            assertEquals(1, result.size());
            verify(downtimeRepo, times(1)).findByEquipmentType("pump");
        }

        @Test
        @DisplayName("无过滤条件时查询所有停机窗口")
        void listNoFilter_returnsAll() {
            DowntimeWindow window = new DowntimeWindow();
            window.setId(1L);

            when(downtimeRepo.findAll()).thenReturn(List.of(window));

            List<DowntimeWindow> result = schedulingService.listDowntimeWindows(null, null);

            assertEquals(1, result.size());
            verify(downtimeRepo, times(1)).findAll();
        }

        @Test
        @DisplayName("保存停机窗口")
        void saveDowntimeWindow_returnsSaved() {
            DowntimeWindow window = new DowntimeWindow();
            window.setId(1L);

            when(downtimeRepo.save(window)).thenReturn(window);

            DowntimeWindow result = schedulingService.saveDowntimeWindow(window);

            assertNotNull(result);
            verify(downtimeRepo, times(1)).save(window);
        }

        @Test
        @DisplayName("删除停机窗口")
        void deleteDowntimeWindow_callsDelete() {
            doNothing().when(downtimeRepo).deleteById(1L);

            schedulingService.deleteDowntimeWindow(1L);

            verify(downtimeRepo, times(1)).deleteById(1L);
        }
    }

    private int computeOverdueLevelViaReflection(WorkOrder order, LocalDate scheduledDate) {
        try {
            java.lang.reflect.Method method = MaintenanceSchedulingService.class
                    .getDeclaredMethod("computeOverdueLevel", WorkOrder.class, LocalDate.class);
            method.setAccessible(true);
            return (int) method.invoke(schedulingService, order, scheduledDate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getPriorityName(int weight) {
        switch (weight) {
            case 4: return "urgent";
            case 3: return "high";
            case 2: return "medium";
            case 1: return "low";
            default: return "unknown";
        }
    }
}
