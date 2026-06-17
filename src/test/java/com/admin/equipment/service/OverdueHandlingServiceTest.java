package com.admin.equipment.service;

import com.admin.equipment.model.WorkOrder;
import com.admin.equipment.model.WorkOrderSchedule;
import com.admin.equipment.repo.WorkOrderRepository;
import com.admin.equipment.repo.WorkOrderScheduleRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueHandlingServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepo;

    @Mock
    private WorkOrderScheduleRepository scheduleRepo;

    @InjectMocks
    private OverdueHandlingService overdueService;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
    }

    private WorkOrder createWorkOrder(Long id, Long equipmentId, String priority,
                                       LocalDateTime dueDate, boolean isPreventive,
                                       String status, String assignee, String title) {
        WorkOrder o = new WorkOrder();
        o.setId(id);
        o.setEquipmentId(equipmentId);
        o.setTitle(title);
        o.setPriority(priority);
        o.setDueDate(dueDate);
        o.setIsPreventive(isPreventive);
        o.setStatus(status);
        o.setAssignee(assignee);
        o.setDescription("");
        o.setCreatedAt(now.minusDays(5));
        return o;
    }

    private WorkOrderSchedule createSchedule(Long id, Long workOrderId, LocalDate scheduledDate,
                                              int overdueLevel, int version) {
        WorkOrderSchedule s = new WorkOrderSchedule();
        s.setId(id);
        s.setWorkOrderId(workOrderId);
        s.setScheduledDate(scheduledDate);
        s.setOverdueLevel(overdueLevel);
        s.setSchedulingVersion(version);
        s.setTeamId(1L);
        s.setTeamName("A组");
        s.setCreatedAt(now.minusDays(3));
        s.setUpdatedAt(now.minusDays(1));
        return s;
    }

    @Nested
    @DisplayName("getOverdueSummary - 逾期汇总")
    class GetOverdueSummaryTests {

        @Test
        @DisplayName("无逾期工单 - 返回空汇总")
        void whenNoOverdueOrders_thenReturnEmptySummary() {
            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of());

            OverdueHandlingService.OverdueSummary summary = overdueService.getOverdueSummary();

            assertNotNull(summary);
            assertEquals(0, summary.level1Count);
            assertEquals(0, summary.level2Count);
            assertEquals(0, summary.level3Count);
            assertTrue(summary.items.isEmpty());
        }

        @Test
        @DisplayName("单条逾期工单 - 无排程记录")
        void whenOverdueOrderWithoutSchedule_thenLevel1Default() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium",
                    now.minusDays(2), true, "open", "张三", "泵1定期巡检");

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.empty());

            OverdueHandlingService.OverdueSummary summary = overdueService.getOverdueSummary();

            assertEquals(1, summary.items.size());
            assertEquals(1, summary.level1Count);
            assertEquals(0, summary.level2Count);
            assertEquals(0, summary.level3Count);

            OverdueHandlingService.OverdueItem item = summary.items.get(0);
            assertEquals(1L, item.workOrderId);
            assertNull(item.scheduleId);
            assertEquals("泵1定期巡检", item.title);
            assertEquals("张三", item.assignee);
            assertTrue(item.daysOverdue >= 0);
        }

        @Test
        @DisplayName("逾期级别1级 - 逾期1-3天")
        void whenOverdueLevel1_thenCountedInLevel1() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium",
                    now.minusDays(2), true, "open", "张三", "泵1巡检");
            WorkOrderSchedule schedule = createSchedule(10L, 1L,
                    LocalDate.now().plusDays(1), 1, 1);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            OverdueHandlingService.OverdueSummary summary = overdueService.getOverdueSummary();

            assertEquals(1, summary.level1Count);
            assertEquals(0, summary.level2Count);
            assertEquals(0, summary.level3Count);
            assertEquals(1, summary.items.size());
            assertEquals(10L, summary.items.get(0).scheduleId);
        }

        @Test
        @DisplayName("逾期级别2级 - 逾期4-7天")
        void whenOverdueLevel2_thenCountedInLevel2() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium",
                    now.minusDays(5), true, "open", "张三", "泵1巡检");
            WorkOrderSchedule schedule = createSchedule(10L, 1L,
                    LocalDate.now().plusDays(5), 2, 1);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            OverdueHandlingService.OverdueSummary summary = overdueService.getOverdueSummary();

            assertEquals(0, summary.level1Count);
            assertEquals(1, summary.level2Count);
            assertEquals(0, summary.level3Count);
        }

        @Test
        @DisplayName("逾期级别3级 - 逾期7天以上")
        void whenOverdueLevel3_thenCountedInLevel3() {
            WorkOrder order = createWorkOrder(1L, 1L, "high",
                    now.minusDays(10), true, "open", "李四", "电机保养");
            WorkOrderSchedule schedule = createSchedule(10L, 1L,
                    LocalDate.now().plusDays(10), 3, 2);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            OverdueHandlingService.OverdueSummary summary = overdueService.getOverdueSummary();

            assertEquals(0, summary.level1Count);
            assertEquals(0, summary.level2Count);
            assertEquals(1, summary.level3Count);
        }

        @Test
        @DisplayName("多条混合级别工单 - 正确分级统计")
        void whenMixedLevels_thenCorrectlyCounted() {
            WorkOrder order1 = createWorkOrder(1L, 1L, "low",
                    now.minusDays(1), true, "open", "甲", "工单1");
            WorkOrder order2 = createWorkOrder(2L, 2L, "medium",
                    now.minusDays(5), true, "open", "乙", "工单2");
            WorkOrder order3 = createWorkOrder(3L, 3L, "high",
                    now.minusDays(15), true, "open", "丙", "工单3");

            WorkOrderSchedule s1 = createSchedule(10L, 1L, LocalDate.now(), 1, 1);
            WorkOrderSchedule s2 = createSchedule(20L, 2L, LocalDate.now().plusDays(3), 2, 1);
            WorkOrderSchedule s3 = createSchedule(30L, 3L, LocalDate.now().plusDays(10), 3, 2);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class)))
                    .thenReturn(List.of(order1, order2, order3));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.findByWorkOrderId(2L)).thenReturn(Optional.of(s2));
            when(scheduleRepo.findByWorkOrderId(3L)).thenReturn(Optional.of(s3));

            OverdueHandlingService.OverdueSummary summary = overdueService.getOverdueSummary();

            assertEquals(1, summary.level1Count);
            assertEquals(1, summary.level2Count);
            assertEquals(1, summary.level3Count);
            assertEquals(3, summary.items.size());
        }

        @Test
        @DisplayName("截止日期为null - 逾期天数为0")
        void whenDueDateNull_thenDaysOverdueZero() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium",
                    null, true, "open", "张三", "巡检工单");

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.empty());

            OverdueHandlingService.OverdueSummary summary = overdueService.getOverdueSummary();

            assertEquals(1, summary.items.size());
            assertEquals(0, summary.items.get(0).daysOverdue);
        }
    }

    @Nested
    @DisplayName("escalateOverdueOrders - 超期升级")
    class EscalateOverdueOrdersTests {

        @Test
        @DisplayName("无逾期工单 - 返回0")
        void whenNoOverdueOrders_thenReturnZero() {
            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of());

            int result = overdueService.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("工单无排程 - 跳过升级")
        void whenOrderHasNoSchedule_thenSkipEscalation() {
            WorkOrder order = createWorkOrder(1L, 1L, "low",
                    now.minusDays(5), true, "open", "", "泵1巡检");

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.empty());

            int result = overdueService.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
        }

        @Test
        @DisplayName("级别未变化 - 不升级")
        void whenLevelNotChanged_thenNoEscalation() {
            WorkOrder order = createWorkOrder(1L, 1L, "medium",
                    now.minusDays(1), true, "open", "A组", "巡检");
            WorkOrderSchedule schedule = createSchedule(10L, 1L,
                    LocalDate.now().plusDays(1), 1, 1);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("级别从1升为2 - 低优先级升为中")
        void whenLevelEscalatesFrom1To2_andLowPriority_thenUpgradeToMedium() {
            LocalDate scheduledDate = LocalDate.now().plusDays(5);
            LocalDateTime dueDate = now.minusDays(1);

            WorkOrder order = createWorkOrder(1L, 1L, "low",
                    dueDate, true, "open", "A组", "泵1巡检");
            WorkOrderSchedule schedule = createSchedule(10L, 1L, scheduledDate, 1, 1);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(scheduleRepo).save(argThat(s -> s.getOverdueLevel() >= 2));
            verify(workOrderRepo).save(argThat(o -> "medium".equals(o.getPriority())));
        }

        @Test
        @DisplayName("级别从2升为3 - 中优先级升为高")
        void whenLevelEscalatesFrom2To3_andMediumPriority_thenUpgradeToHigh() {
            LocalDate scheduledDate = LocalDate.now().plusDays(10);
            LocalDateTime dueDate = now.minusDays(1);

            WorkOrder order = createWorkOrder(1L, 1L, "medium",
                    dueDate, true, "open", "A组", "电机大修");
            WorkOrderSchedule schedule = createSchedule(10L, 1L, scheduledDate, 2, 1);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(scheduleRepo).save(argThat(s -> s.getOverdueLevel() >= 3));
            verify(workOrderRepo).save(argThat(o -> "high".equals(o.getPriority())));
        }

        @Test
        @DisplayName("级别升级但优先级已是高 - 不升级优先级")
        void whenLevelEscalates_andHighPriority_thenNoPriorityChange() {
            LocalDate scheduledDate = LocalDate.now().plusDays(10);
            LocalDateTime dueDate = now.minusDays(1);

            WorkOrder order = createWorkOrder(1L, 1L, "high",
                    dueDate, true, "open", "A组", "紧急维修");
            WorkOrderSchedule schedule = createSchedule(10L, 1L, scheduledDate, 1, 1);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(scheduleRepo).save(any(WorkOrderSchedule.class));
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("级别升级但优先级已是urgent - 不升级优先级")
        void whenLevelEscalates_andUrgentPriority_thenNoPriorityChange() {
            LocalDate scheduledDate = LocalDate.now().plusDays(10);
            LocalDateTime dueDate = now.minusDays(1);

            WorkOrder order = createWorkOrder(1L, 1L, "urgent",
                    dueDate, true, "open", "A组", "紧急抢修");
            WorkOrderSchedule schedule = createSchedule(10L, 1L, scheduledDate, 2, 1);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(scheduleRepo).save(any(WorkOrderSchedule.class));
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("升级后更新时间戳")
        void whenEscalated_thenUpdatedAtRefreshed() {
            LocalDate scheduledDate = LocalDate.now().plusDays(5);
            LocalDateTime dueDate = now.minusDays(1);

            WorkOrder order = createWorkOrder(1L, 1L, "low",
                    dueDate, true, "open", "A组", "泵1巡检");
            WorkOrderSchedule schedule = createSchedule(10L, 1L, scheduledDate, 1, 1);
            schedule.setUpdatedAt(now.minusDays(5));

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(scheduleRepo).save(argThat(s -> s.getUpdatedAt() != null));
        }

        @Test
        @DisplayName("截止日期为null - 级别为0不升级")
        void whenDueDateNull_thenLevel0NoEscalation() {
            WorkOrder order = createWorkOrder(1L, 1L, "low",
                    null, true, "open", "", "巡检");
            WorkOrderSchedule schedule = createSchedule(10L, 1L,
                    LocalDate.now().plusDays(5), 0, 1);

            when(workOrderRepo.findOverduePreventive(any(LocalDateTime.class))).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("postponeSchedule - 顺延排期")
    class PostponeScheduleTests {

        @Test
        @DisplayName("排期不存在 - 返回null")
        void whenScheduleNotFound_thenReturnNull() {
            when(scheduleRepo.findById(999L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = overdueService.postponeSchedule(999L, 3, "设备故障");

            assertNull(result);
            verify(scheduleRepo, never()).save(any());
        }

        @Test
        @DisplayName("正常顺延 - 日期增加指定天数")
        void whenPostponed_thenDateIncreased() {
            LocalDate originalDate = LocalDate.now();
            WorkOrderSchedule schedule = createSchedule(1L, 10L, originalDate, 0, 1);
            WorkOrder order = createWorkOrder(10L, 1L, "medium",
                    now.plusDays(10), true, "open", "A组", "泵1巡检");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(1L, 5, "设备还在运行");

            assertNotNull(result);
            assertEquals(originalDate.plusDays(5), result.getScheduledDate());
            assertEquals(2, result.getSchedulingVersion());
            assertNotNull(result.getUpdatedAt());
        }

        @Test
        @DisplayName("顺延 - 版本号递增")
        void whenPostponed_thenVersionIncremented() {
            WorkOrderSchedule schedule = createSchedule(1L, 10L, LocalDate.now(), 0, 3);
            WorkOrder order = createWorkOrder(10L, 1L, "medium",
                    now.plusDays(10), true, "open", "A组", "巡检");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(1L, 2, "原因");

            assertEquals(4, result.getSchedulingVersion());
        }

        @Test
        @DisplayName("顺延 - 工单不存在时不更新描述和逾期级别")
        void whenWorkOrderNotFound_thenSkipOrderUpdates() {
            WorkOrderSchedule schedule = createSchedule(1L, 999L, LocalDate.now(), 0, 1);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(999L)).thenReturn(Optional.empty());
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(1L, 3, "原因");

            assertNotNull(result);
            assertEquals(0, result.getOverdueLevel());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("顺延 - 逾期级别重新计算")
        void whenPostponed_thenOverdueLevelRecalculated() {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(2);
            WorkOrderSchedule schedule = createSchedule(1L, 10L, LocalDate.now(), 0, 1);
            WorkOrder order = createWorkOrder(10L, 1L, "medium",
                    dueDate, true, "open", "A组", "巡检");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(1L, 5, "原因");

            assertTrue(result.getOverdueLevel() >= 1);
        }

        @Test
        @DisplayName("顺延 - 原因追加到工单描述")
        void whenPostponedWithReason_thenDescriptionUpdated() {
            WorkOrderSchedule schedule = createSchedule(1L, 10L, LocalDate.now(), 0, 1);
            WorkOrder order = createWorkOrder(10L, 1L, "medium",
                    now.plusDays(10), true, "open", "A组", "巡检");
            order.setDescription("原始描述");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            overdueService.postponeSchedule(1L, 2, "设备延迟到货");

            verify(workOrderRepo).save(argThat(o ->
                    o.getDescription().contains("顺延 2 天")
                            && o.getDescription().contains("设备延迟到货")
                            && o.getDescription().contains("原始描述")));
        }

        @Test
        @DisplayName("顺延 - 原描述为空时也能追加")
        void whenDescriptionEmpty_thenReasonAppended() {
            WorkOrderSchedule schedule = createSchedule(1L, 10L, LocalDate.now(), 0, 1);
            WorkOrder order = createWorkOrder(10L, 1L, "medium",
                    now.plusDays(10), true, "open", "A组", "巡检");
            order.setDescription("");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            overdueService.postponeSchedule(1L, 3, "等待备件");

            verify(workOrderRepo).save(argThat(o ->
                    o.getDescription().contains("顺延 3 天")
                            && o.getDescription().contains("等待备件")));
        }

        @Test
        @DisplayName("顺延 - 原因null时也能正常追加")
        void whenReasonNull_thenPostponeWithoutReason() {
            WorkOrderSchedule schedule = createSchedule(1L, 10L, LocalDate.now(), 0, 1);
            WorkOrder order = createWorkOrder(10L, 1L, "medium",
                    now.plusDays(10), true, "open", "A组", "巡检");
            order.setDescription("原描述");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            overdueService.postponeSchedule(1L, 3, null);

            verify(workOrderRepo).save(argThat(o ->
                    o.getDescription().contains("顺延 3 天")));
        }

        @Test
        @DisplayName("顺延 - 原描述为null时处理")
        void whenDescriptionNull_thenHandleGracefully() {
            WorkOrderSchedule schedule = createSchedule(1L, 10L, LocalDate.now(), 0, 1);
            WorkOrder order = createWorkOrder(10L, 1L, "medium",
                    now.plusDays(10), true, "open", "A组", "巡检");
            order.setDescription(null);

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(schedule));
            when(workOrderRepo.findById(10L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(i -> i.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(i -> i.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(1L, 2, "原因");

            assertNotNull(result);
            verify(workOrderRepo).save(argThat(o -> o.getDescription() != null));
        }
    }
}
