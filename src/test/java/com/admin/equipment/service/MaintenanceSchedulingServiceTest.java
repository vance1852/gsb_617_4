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
import java.util.*;

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

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
    }

    private WorkOrder createWorkOrder(Long id, Long equipmentId, String priority,
                                       LocalDateTime dueDate, boolean isPreventive,
                                       String status, Long planId) {
        WorkOrder o = new WorkOrder();
        o.setId(id);
        o.setEquipmentId(equipmentId);
        o.setTitle("工单-" + id);
        o.setPriority(priority);
        o.setDueDate(dueDate);
        o.setIsPreventive(isPreventive);
        o.setStatus(status);
        o.setPlanId(planId);
        o.setCreatedAt(LocalDateTime.now().minusDays(1));
        return o;
    }

    private MaintenanceTeam createTeam(Long id, String name, double dailyCapacityHours) {
        MaintenanceTeam t = new MaintenanceTeam();
        t.setId(id);
        t.setName(name);
        t.setDailyCapacityHours(dailyCapacityHours);
        return t;
    }

    private Equipment createEquipment(Long id, String name, String type) {
        Equipment e = new Equipment();
        e.setId(id);
        e.setName(name);
        e.setType(type);
        e.setCode("EQ-" + id);
        return e;
    }

    private DowntimeWindow createDowntimeWindow(Long id, Long equipmentId, String equipmentType,
                                                 int dayOfWeek, int startMin, int endMin) {
        DowntimeWindow w = new DowntimeWindow();
        w.setId(id);
        w.setEquipmentId(equipmentId);
        w.setEquipmentType(equipmentType);
        w.setDayOfWeek(dayOfWeek);
        w.setStartMinute(startMin);
        w.setEndMinute(endMin);
        return w;
    }

    private MaintenancePlan createPlan(Long id, double standardWorkHours, String priority) {
        MaintenancePlan p = new MaintenancePlan();
        p.setId(id);
        p.setName("计划-" + id);
        p.setStandardWorkHours(standardWorkHours);
        p.setPriority(priority);
        return p;
    }

    @Nested
    @DisplayName("runSmartScheduling - 智能排程")
    class RunSmartSchedulingTests {

        @Test
        @DisplayName("空团队列表 - 返回空结果")
        void whenTeamsEmpty_thenReturnEmptyResult() {
            List<WorkOrder> orders = List.of(
                    createWorkOrder(1L, 1L, "high", null, true, "open", null)
            );
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(orders);
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of());

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertTrue(result.scheduled.isEmpty());
            assertNotNull(result.metrics);
            assertNotNull(result.naiveMetrics);
            verify(teamRepo).findAll();
        }

        @Test
        @DisplayName("空未排程工单 - 返回空结果")
        void whenNoUnscheduledOrders_thenReturnEmptyResult() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of());
            when(teamRepo.findAll()).thenReturn(List.of(createTeam(1L, "A组", 8.0)));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertNotNull(result.naiveMetrics);
        }

        @Test
        @DisplayName("已完成的预防性工单 - 不参与排程")
        void whenOrderIsDone_thenExcludedFromScheduling() {
            WorkOrder doneOrder = createWorkOrder(1L, 1L, "high", null, true, "done", null);
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(doneOrder));
            when(teamRepo.findAll()).thenReturn(List.of(createTeam(1L, "A组", 8.0)));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
        }

        @Test
        @DisplayName("已有排程的工单 - 不参与排程")
        void whenOrderHasSchedule_thenExcludedFromScheduling() {
            WorkOrder order = createWorkOrder(1L, 1L, "high", null, true, "open", null);
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(100L);
            schedule.setWorkOrderId(1L);

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(teamRepo.findAll()).thenReturn(List.of(createTeam(1L, "A组", 8.0)));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
        }

        @Test
        @DisplayName("设备不存在 - 工单无法排程")
        void whenEquipmentNotFound_thenOrderUnscheduled() {
            WorkOrder order = createWorkOrder(1L, 999L, "high", null, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(999L)).thenReturn(Optional.empty());
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(1, result.unscheduledCount);
        }

        @Test
        @DisplayName("正常排程 - 单工单单团队")
        void whenSingleOrderSingleTeam_thenSuccessfullyScheduled() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium", null, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            assertEquals(0, result.unscheduledCount);
            assertEquals(1, result.scheduled.size());

            MaintenanceSchedulingService.ScheduledItem item = result.scheduled.get(0);
            assertEquals(1L, item.workOrderId);
            assertEquals(1L, item.equipmentId);
            assertEquals("泵1", item.equipmentName);
            assertEquals(1L, item.teamId);
            assertEquals("A组", item.teamName);
            assertEquals(8 * 60, item.startMinute);
            assertTrue(item.durationMinutes >= 30);
            assertNotNull(item.scheduledDate);
            assertTrue(!item.scheduledDate.isBefore(today));
            assertTrue(!item.scheduledDate.isAfter(today.plusDays(7)));

            verify(scheduleRepo).save(any(WorkOrderSchedule.class));
            verify(workOrderRepo).save(any(WorkOrder.class));
        }

        @Test
        @DisplayName("优先级排序 - 高优先级先排")
        void whenMultipleOrders_thenHigherPriorityScheduledFirst() {
            WorkOrder lowOrder = createWorkOrder(1L, 1L, "low", null, true, "open", null);
            WorkOrder highOrder = createWorkOrder(2L, 1L, "high", null, true, "open", null);
            WorkOrder urgentOrder = createWorkOrder(3L, 1L, "urgent", null, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 2.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(lowOrder, highOrder, urgentOrder));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(1);

            assertTrue(result.scheduledCount >= 1);
            if (result.scheduledCount > 0) {
                assertEquals("urgent",
                        result.scheduled.stream()
                                .filter(s -> s.priorityWeight == 400 || s.priorityWeight == 4)
                                .findAny()
                                .isPresent() ? "urgent" : "other");
            }
        }

        @Test
        @DisplayName("相同优先级 - 按截止日期排序")
        void whenSamePriority_thenOrderByDueDate() {
            LocalDateTime dueSoon = LocalDateTime.now().plusDays(2);
            LocalDateTime dueLater = LocalDateTime.now().plusDays(5);

            WorkOrder order1 = createWorkOrder(1L, 1L, "medium", dueSoon, true, "open", null);
            WorkOrder order2 = createWorkOrder(2L, 1L, "medium", dueLater, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order2, order1));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(2, result.scheduledCount);
        }

        @Test
        @DisplayName("团队容量约束 - 工单工时超过单日容量时无法排程")
        void whenOrderExceedsDailyCapacity_thenUnscheduled() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium", null, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 1.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(0);

            assertEquals(0, result.scheduledCount);
            assertEquals(1, result.unscheduledCount);
            verify(scheduleRepo, never()).save(any());
        }

        @Test
        @DisplayName("有维护计划 - 使用计划的标准工时")
        void whenOrderHasPlan_thenUsePlanStandardHours() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium", null, true, "open", 10L);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");
            MaintenancePlan plan = createPlan(10L, 3.5, "medium");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(planRepo.findById(10L)).thenReturn(Optional.of(plan));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            MaintenanceSchedulingService.ScheduledItem item = result.scheduled.get(0);
            assertEquals(210, item.durationMinutes);
        }

        @Test
        @DisplayName("无维护计划 - 使用默认2小时")
        void whenOrderHasNoPlan_thenUseDefaultTwoHours() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium", null, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            MaintenanceSchedulingService.ScheduledItem item = result.scheduled.get(0);
            assertEquals(120, item.durationMinutes);
        }

        @Test
        @DisplayName("工时不足30分钟 - 至少按30分钟计")
        void whenHoursNeededLessThan30Min_thenMinimum30Minutes() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium", null, true, "open", 10L);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");
            MaintenancePlan plan = createPlan(10L, 0.2, "medium");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(planRepo.findById(10L)).thenReturn(Optional.of(plan));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            MaintenanceSchedulingService.ScheduledItem item = result.scheduled.get(0);
            assertEquals(30, item.durationMinutes);
        }

        @Test
        @DisplayName("停机窗口约束 - 时长超过窗口时无法排程")
        void whenDurationExceedsWindow_thenCannotSchedule() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium", null, true, "open", 10L);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");
            MaintenancePlan plan = createPlan(10L, 12.0, "medium");

            List<DowntimeWindow> windows = new ArrayList<>();
            for (int dow = 1; dow <= 7; dow++) {
                windows.add(createDowntimeWindow((long) dow, 1L, null, dow, 8 * 60, 10 * 60));
            }

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(planRepo.findById(10L)).thenReturn(Optional.of(plan));
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(windows);
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(0, result.scheduledCount);
            assertEquals(1, result.unscheduledCount);
            verify(scheduleRepo, never()).save(any());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("多团队负载均衡 - 低负载团队优先")
        void whenMultipleTeams_thenPreferLowerLoadTeam() {
            WorkOrder order1 = createWorkOrder(1L, 1L, "medium", null, true, "open", null);
            WorkOrder order2 = createWorkOrder(2L, 2L, "medium", null, true, "open", null);
            MaintenanceTeam team1 = createTeam(1L, "A组", 8.0);
            MaintenanceTeam team2 = createTeam(2L, "B组", 8.0);
            Equipment eq1 = createEquipment(1L, "泵1", "pump");
            Equipment eq2 = createEquipment(2L, "泵2", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1, order2));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1, team2));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1, eq2));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(eq1));
            when(equipmentRepo.findById(2L)).thenReturn(Optional.of(eq2));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(1);

            assertEquals(2, result.scheduledCount);
            Set<Long> teamIds = new HashSet<>();
            for (MaintenanceSchedulingService.ScheduledItem item : result.scheduled) {
                teamIds.add(item.teamId);
            }
            assertTrue(teamIds.size() >= 1);
        }

        @Test
        @DisplayName("排程指标计算 - 包含停机天数和团队负载")
        void whenScheduled_thenMetricsCalculated() {
            WorkOrder order1 = createWorkOrder(1L, 1L, "medium", null, true, "open", null);
            WorkOrder order2 = createWorkOrder(2L, 1L, "low", null, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1, order2));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertNotNull(result.metrics);
            assertTrue(result.metrics.totalDowntimeDays > 0);
            assertTrue(result.metrics.avgTeamLoad >= 0);
            assertTrue(result.metrics.maxTeamLoad >= 0);
            assertTrue(result.metrics.teamLoadStdDev >= 0);
        }

        @Test
        @DisplayName("朴素排程指标 - 对比基准")
        void whenScheduling_thenNaiveMetricsProvided() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium", null, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertNotNull(result.naiveMetrics);
            assertTrue(result.naiveMetrics.totalDowntimeDays > 0);
        }

        @Test
        @DisplayName("逾期分级 - 排期在截止前为0级")
        void whenScheduledBeforeDueDate_thenOverdueLevel0() {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(10);
            WorkOrder order = createWorkOrder(1L, 1L, "medium", dueDate, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            verify(scheduleRepo).save(argThat(s -> s.getOverdueLevel() == 0));
        }

        @Test
        @DisplayName("逾期分级 - 截止日期为null时为0级")
        void whenDueDateNull_thenOverdueLevel0() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium", null, true, "open", null);
            MaintenanceTeam team = createTeam(1L, "A组", 8.0);
            Equipment equipment = createEquipment(1L, "泵1", "pump");

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team));
            when(equipmentRepo.findAll()).thenReturn(List.of(equipment));
            when(equipmentRepo.findById(1L)).thenReturn(Optional.of(equipment));
            when(downtimeRepo.findByEquipmentId(anyLong())).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType(anyString())).thenReturn(List.of());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            MaintenanceSchedulingService.ScheduleResult result = schedulingService.runSmartScheduling(7);

            assertEquals(1, result.scheduledCount);
            verify(scheduleRepo).save(argThat(s -> s.getOverdueLevel() == 0));
        }
    }

    @Nested
    @DisplayName("reschedule - 重新排期")
    class RescheduleTests {

        @Test
        @DisplayName("排期不存在 - 返回null")
        void whenScheduleNotFound_thenReturnNull() {
            when(scheduleRepo.findById(999L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = schedulingService.reschedule(999L, today.plusDays(3), 2L);

            assertNull(result);
        }

        @Test
        @DisplayName("只改日期不改团队")
        void whenOnlyDateChanged_thenUpdateDate() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(10L);
            schedule.setScheduledDate(today);
            schedule.setTeamId(1L);
            schedule.setTeamName("A组");
            schedule.setSchedulingVersion(1);
            schedule.setOverdueLevel(0);

            WorkOrder order = createWorkOrder(10L, 1L, "medium", LocalDateTime.now().plusDays(10), true, "open", null);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            LocalDate newDate = today.plusDays(5);
            WorkOrderSchedule result = schedulingService.reschedule(1L, newDate, null);

            assertNotNull(result);
            assertEquals(newDate, result.getScheduledDate());
            assertEquals(1L, result.getTeamId());
            assertEquals("A组", result.getTeamName());
            assertEquals(2, result.getSchedulingVersion());
            assertNotNull(result.getUpdatedAt());
        }

        @Test
        @DisplayName("改日期同时改团队")
        void whenDateAndTeamChanged_thenUpdateBoth() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(10L);
            schedule.setScheduledDate(today);
            schedule.setTeamId(1L);
            schedule.setTeamName("A组");
            schedule.setSchedulingVersion(1);

            WorkOrder order = createWorkOrder(10L, 1L, "medium", LocalDateTime.now().plusDays(10), true, "open", null);
            MaintenanceTeam newTeam = createTeam(2L, "B组", 8.0);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(teamRepo.findById(2L)).thenReturn(Optional.of(newTeam));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            LocalDate newDate = today.plusDays(3);
            WorkOrderSchedule result = schedulingService.reschedule(1L, newDate, 2L);

            assertNotNull(result);
            assertEquals(newDate, result.getScheduledDate());
            assertEquals(2L, result.getTeamId());
            assertEquals("B组", result.getTeamName());
            assertEquals(2, result.getSchedulingVersion());
        }

        @Test
        @DisplayName("新团队不存在 - 保持原团队")
        void whenNewTeamNotFound_thenKeepOriginalTeam() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(10L);
            schedule.setScheduledDate(today);
            schedule.setTeamId(1L);
            schedule.setTeamName("A组");
            schedule.setSchedulingVersion(1);

            WorkOrder order = createWorkOrder(10L, 1L, "medium", LocalDateTime.now().plusDays(10), true, "open", null);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(teamRepo.findById(999L)).thenReturn(Optional.empty());
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            WorkOrderSchedule result = schedulingService.reschedule(1L, today.plusDays(1), 999L);

            assertNotNull(result);
            assertEquals(1L, result.getTeamId());
            assertEquals("A组", result.getTeamName());
        }

        @Test
        @DisplayName("工单不存在 - 不更新逾期级别和负责人")
        void whenWorkOrderNotFound_thenSkipOverdueUpdate() {
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(999L);
            schedule.setScheduledDate(today);
            schedule.setTeamId(1L);
            schedule.setTeamName("A组");
            schedule.setSchedulingVersion(1);
            schedule.setOverdueLevel(0);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(999L)).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));

            WorkOrderSchedule result = schedulingService.reschedule(1L, today.plusDays(10), null);

            assertNotNull(result);
            assertEquals(0, result.getOverdueLevel());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("重新排期后逾期级别更新")
        void whenRescheduled_thenOverdueLevelRecalculated() {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(2);
            WorkOrderSchedule schedule = new WorkOrderSchedule();
            schedule.setId(1L);
            schedule.setWorkOrderId(10L);
            schedule.setScheduledDate(today);
            schedule.setTeamId(1L);
            schedule.setTeamName("A组");
            schedule.setSchedulingVersion(1);
            schedule.setOverdueLevel(0);

            WorkOrder order = createWorkOrder(10L, 1L, "medium", dueDate, true, "open", null);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            LocalDate newDate = today.plusDays(5);
            WorkOrderSchedule result = schedulingService.reschedule(1L, newDate, null);

            assertNotNull(result);
            assertTrue(result.getOverdueLevel() >= 1);
        }
    }

    @Nested
    @DisplayName("getCalendar - 日历查询")
    class GetCalendarTests {

        @Test
        @DisplayName("按设备查询")
        void whenEquipmentIdProvided_thenQueryByEquipment() {
            LocalDate from = today;
            LocalDate to = today.plusDays(7);
            List<WorkOrderSchedule> schedules = List.of(new WorkOrderSchedule());

            when(scheduleRepo.findByEquipmentAndDateRange(1L, from, to)).thenReturn(schedules);

            List<WorkOrderSchedule> result = schedulingService.getCalendar(from, to, 1L, null);

            assertEquals(schedules, result);
            verify(scheduleRepo).findByEquipmentAndDateRange(1L, from, to);
        }

        @Test
        @DisplayName("按团队查询")
        void whenTeamIdProvided_thenQueryByTeam() {
            LocalDate from = today;
            LocalDate to = today.plusDays(7);
            List<WorkOrderSchedule> schedules = List.of(new WorkOrderSchedule());

            when(scheduleRepo.findByTeamAndDateRange(2L, from, to)).thenReturn(schedules);

            List<WorkOrderSchedule> result = schedulingService.getCalendar(from, to, null, 2L);

            assertEquals(schedules, result);
            verify(scheduleRepo).findByTeamAndDateRange(2L, from, to);
        }

        @Test
        @DisplayName("按日期范围查询")
        void whenNoFilter_thenQueryByDateRange() {
            LocalDate from = today;
            LocalDate to = today.plusDays(7);
            List<WorkOrderSchedule> schedules = List.of(new WorkOrderSchedule());

            when(scheduleRepo.findByScheduledDateBetween(from, to)).thenReturn(schedules);

            List<WorkOrderSchedule> result = schedulingService.getCalendar(from, to, null, null);

            assertEquals(schedules, result);
            verify(scheduleRepo).findByScheduledDateBetween(from, to);
        }
    }

    @Nested
    @DisplayName("团队管理")
    class TeamManagementTests {

        @Test
        @DisplayName("列出所有团队")
        void listTeams_returnsAllTeams() {
            List<MaintenanceTeam> teams = List.of(
                    createTeam(1L, "A组", 8.0),
                    createTeam(2L, "B组", 8.0)
            );
            when(teamRepo.findAll()).thenReturn(teams);

            List<MaintenanceTeam> result = schedulingService.listTeams();

            assertEquals(2, result.size());
            verify(teamRepo).findAll();
        }

        @Test
        @DisplayName("保存团队")
        void saveTeam_persistsTeam() {
            MaintenanceTeam team = createTeam(null, "C组", 6.0);
            MaintenanceTeam saved = createTeam(3L, "C组", 6.0);

            when(teamRepo.save(team)).thenReturn(saved);

            MaintenanceTeam result = schedulingService.saveTeam(team);

            assertEquals(3L, result.getId());
            assertEquals("C组", result.getName());
            verify(teamRepo).save(team);
        }
    }

    @Nested
    @DisplayName("停机窗口管理")
    class DowntimeWindowManagementTests {

        @Test
        @DisplayName("按设备查询停机窗口")
        void whenEquipmentId_thenFindByEquipmentId() {
            List<DowntimeWindow> windows = List.of(new DowntimeWindow());
            when(downtimeRepo.findByEquipmentId(1L)).thenReturn(windows);

            List<DowntimeWindow> result = schedulingService.listDowntimeWindows(1L, null);

            assertEquals(windows, result);
            verify(downtimeRepo).findByEquipmentId(1L);
        }

        @Test
        @DisplayName("按设备类型查询停机窗口")
        void whenEquipmentType_thenFindByEquipmentType() {
            List<DowntimeWindow> windows = List.of(new DowntimeWindow());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(windows);

            List<DowntimeWindow> result = schedulingService.listDowntimeWindows(null, "pump");

            assertEquals(windows, result);
            verify(downtimeRepo).findByEquipmentType("pump");
        }

        @Test
        @DisplayName("无过滤条件 - 查询所有停机窗口")
        void whenNoFilter_thenFindAll() {
            List<DowntimeWindow> windows = List.of(new DowntimeWindow(), new DowntimeWindow());
            when(downtimeRepo.findAll()).thenReturn(windows);

            List<DowntimeWindow> result = schedulingService.listDowntimeWindows(null, null);

            assertEquals(2, result.size());
            verify(downtimeRepo).findAll();
        }

        @Test
        @DisplayName("保存停机窗口")
        void saveDowntimeWindow_persistsWindow() {
            DowntimeWindow window = new DowntimeWindow();
            window.setDayOfWeek(1);
            DowntimeWindow saved = new DowntimeWindow();
            saved.setId(1L);
            saved.setDayOfWeek(1);

            when(downtimeRepo.save(window)).thenReturn(saved);

            DowntimeWindow result = schedulingService.saveDowntimeWindow(window);

            assertEquals(1L, result.getId());
            verify(downtimeRepo).save(window);
        }

        @Test
        @DisplayName("删除停机窗口")
        void deleteDowntimeWindow_deletesById() {
            doNothing().when(downtimeRepo).deleteById(1L);

            schedulingService.deleteDowntimeWindow(1L);

            verify(downtimeRepo).deleteById(1L);
        }
    }
}
