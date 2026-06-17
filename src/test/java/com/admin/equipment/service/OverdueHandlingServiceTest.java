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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverdueHandlingServiceTest {

    @Mock private WorkOrderRepository workOrderRepo;
    @Mock private WorkOrderScheduleRepository scheduleRepo;

    @InjectMocks
    private OverdueHandlingService service;

    private LocalDateTime now;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.of(2025, 6, 16, 12, 0);
        today = now.toLocalDate();
    }

    private WorkOrder createOrder(Long id, String priority, LocalDateTime dueDate) {
        WorkOrder order = new WorkOrder();
        order.setId(id);
        order.setTitle("工单-" + id);
        order.setPriority(priority);
        order.setStatus("open");
        order.setIsPreventive(true);
        order.setDueDate(dueDate);
        order.setAssignee("一班");
        order.setDescription("原始描述");
        return order;
    }

    private WorkOrderSchedule createSchedule(Long id, Long workOrderId, LocalDate scheduledDate, int overdueLevel) {
        WorkOrderSchedule s = new WorkOrderSchedule();
        s.setId(id);
        s.setWorkOrderId(workOrderId);
        s.setScheduledDate(scheduledDate);
        s.setOverdueLevel(overdueLevel);
        s.setSchedulingVersion(1);
        s.setTeamId(1L);
        s.setTeamName("一班");
        return s;
    }

    @Nested
    @DisplayName("getOverdueSummary 逾期汇总")
    class GetOverdueSummaryTests {

        @Test
        @DisplayName("无逾期工单时返回空汇总")
        void noOverdueOrders_returnsEmptySummary() {
            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of());

            OverdueHandlingService.OverdueSummary result = service.getOverdueSummary();

            assertEquals(0, result.level1Count);
            assertEquals(0, result.level2Count);
            assertEquals(0, result.level3Count);
            assertTrue(result.items.isEmpty());
        }

        @Test
        @DisplayName("一级逾期工单计入level1Count")
        void level1Overdue_countedInLevel1() {
            WorkOrder order = createOrder(1L, "medium", now.minusDays(2));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 1);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            OverdueHandlingService.OverdueSummary result = service.getOverdueSummary();

            assertEquals(1, result.level1Count);
            assertEquals(0, result.level2Count);
            assertEquals(0, result.level3Count);
            assertEquals(1, result.items.size());
            OverdueHandlingService.OverdueItem item = result.items.get(0);
            assertEquals(1L, item.workOrderId);
            assertEquals(10L, item.scheduleId);
            assertEquals(1, item.overdueLevel);
            assertTrue(item.daysOverdue > 0);
        }

        @Test
        @DisplayName("二级逾期工单计入level2Count")
        void level2Overdue_countedInLevel2() {
            WorkOrder order = createOrder(1L, "medium", now.minusDays(5));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 2);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            OverdueHandlingService.OverdueSummary result = service.getOverdueSummary();

            assertEquals(0, result.level1Count);
            assertEquals(1, result.level2Count);
            assertEquals(0, result.level3Count);
        }

        @Test
        @DisplayName("三级逾期工单计入level3Count")
        void level3Overdue_countedInLevel3() {
            WorkOrder order = createOrder(1L, "medium", now.minusDays(10));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 3);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            OverdueHandlingService.OverdueSummary result = service.getOverdueSummary();

            assertEquals(0, result.level1Count);
            assertEquals(0, result.level2Count);
            assertEquals(1, result.level3Count);
        }

        @Test
        @DisplayName("无排程的逾期工单默认按level1统计")
        void noSchedule_defaultsToLevel1() {
            WorkOrder order = createOrder(1L, "medium", now.minusDays(5));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.empty());

            OverdueHandlingService.OverdueSummary result = service.getOverdueSummary();

            assertEquals(1, result.level1Count);
            assertEquals(0, result.level2Count);
            assertEquals(0, result.level3Count);
            assertEquals(1, result.items.size());
            assertNull(result.items.get(0).scheduleId);
            assertEquals(0, result.items.get(0).overdueLevel);
        }

        @Test
        @DisplayName("混合等级逾期工单正确分级统计")
        void mixedLevels_correctCounts() {
            WorkOrder l1 = createOrder(1L, "medium", now.minusDays(2));
            WorkOrder l2 = createOrder(2L, "medium", now.minusDays(5));
            WorkOrder l3 = createOrder(3L, "medium", now.minusDays(10));
            WorkOrderSchedule s1 = createSchedule(10L, 1L, today, 1);
            WorkOrderSchedule s2 = createSchedule(20L, 2L, today, 2);
            WorkOrderSchedule s3 = createSchedule(30L, 3L, today, 3);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(l1, l2, l3));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.findByWorkOrderId(2L)).thenReturn(Optional.of(s2));
            when(scheduleRepo.findByWorkOrderId(3L)).thenReturn(Optional.of(s3));

            OverdueHandlingService.OverdueSummary result = service.getOverdueSummary();

            assertEquals(1, result.level1Count);
            assertEquals(1, result.level2Count);
            assertEquals(1, result.level3Count);
            assertEquals(3, result.items.size());
        }

        @Test
        @DisplayName("工单元数据正确填充到汇总项")
        void itemFields_populatedCorrectly() {
            WorkOrder order = createOrder(5L, "high", now.minusDays(3));
            order.setAssignee("张三");
            WorkOrderSchedule schedule = createSchedule(50L, 5L, today, 1);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(5L)).thenReturn(Optional.of(schedule));

            OverdueHandlingService.OverdueSummary result = service.getOverdueSummary();

            OverdueHandlingService.OverdueItem item = result.items.get(0);
            assertEquals(5L, item.workOrderId);
            assertEquals(50L, item.scheduleId);
            assertEquals("工单-5", item.title);
            assertNotNull(item.dueDate);
            assertEquals(today, item.scheduledDate);
            assertEquals("张三", item.assignee);
            assertTrue(item.daysOverdue >= 1);
        }
    }

    @Nested
    @DisplayName("escalateOverdueOrders 逾期升级")
    class EscalateOverdueOrdersTests {

        @Test
        @DisplayName("无逾期工单时返回0")
        void noOverdueOrders_returnsZero() {
            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of());

            int result = service.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("无排程的逾期工单跳过升级")
        void noSchedule_skipped() {
            WorkOrder order = createOrder(1L, "medium", now.minusDays(5));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.empty());

            int result = service.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
        }

        @Test
        @DisplayName("等级未变化时不升级")
        void noLevelChange_notEscalated() {
            WorkOrder order = createOrder(1L, "medium", now.minusDays(2));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 1);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("等级从0升到1-保存排程但不升级优先级")
        void level0To1_savesScheduleNoPriorityChange() {
            WorkOrder order = createOrder(1L, "low", now.minusDays(2));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertEquals(1, result);
            verify(scheduleRepo).save(argThat(s -> s.getOverdueLevel() == 1));
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("等级升到2且优先级为low-升级优先级到medium")
        void level2WithLowPriority_upgradesToMedium() {
            WorkOrder order = createOrder(1L, "low", now.minusDays(5));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertEquals(1, result);
            verify(workOrderRepo).save(argThat(o -> "medium".equals(o.getPriority())));
        }

        @Test
        @DisplayName("等级升到2但优先级为medium-不升级优先级")
        void level2WithMediumPriority_noPriorityUpgrade() {
            WorkOrder order = createOrder(1L, "medium", now.minusDays(5));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertEquals(1, result);
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("等级升到3且优先级为medium-升级优先级到high")
        void level3WithMediumPriority_upgradesToHigh() {
            WorkOrder order = createOrder(1L, "medium", now.minusDays(10));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertEquals(1, result);
            verify(workOrderRepo).save(argThat(o -> "high".equals(o.getPriority())));
        }

        @Test
        @DisplayName("等级升到3但优先级为high-不升级优先级")
        void level3WithHighPriority_noPriorityUpgrade() {
            WorkOrder order = createOrder(1L, "high", now.minusDays(10));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 1);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertEquals(1, result);
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("等级从1升到3-一次跳两级")
        void levelJumpFrom1To3() {
            WorkOrder order = createOrder(1L, "low", now.minusDays(10));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 1);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertEquals(1, result);
            verify(scheduleRepo).save(argThat(s -> s.getOverdueLevel() == 3));
            verify(workOrderRepo).save(argThat(o -> "medium".equals(o.getPriority())));
        }

        @Test
        @DisplayName("多个工单部分升级")
        void multipleOrders_partialEscalation() {
            WorkOrder order1 = createOrder(1L, "low", now.minusDays(2));
            WorkOrder order2 = createOrder(2L, "medium", now.minusDays(10));
            WorkOrder order3 = createOrder(3L, "high", now.minusDays(5));
            WorkOrderSchedule s1 = createSchedule(10L, 1L, today, 0);
            WorkOrderSchedule s2 = createSchedule(20L, 2L, today, 1);
            WorkOrderSchedule s3 = createSchedule(30L, 3L, today, 2);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order1, order2, order3));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.findByWorkOrderId(2L)).thenReturn(Optional.of(s2));
            when(scheduleRepo.findByWorkOrderId(3L)).thenReturn(Optional.of(s3));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertEquals(2, result);
        }

        @Test
        @DisplayName("截止日期为null时等级为0不升级")
        void nullDueDate_level0NoEscalation() {
            WorkOrder order = createOrder(1L, "medium", null);
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule));

            int result = service.escalateOverdueOrders();

            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("postponeSchedule 顺延排程")
    class PostponeScheduleTests {

        @Test
        @DisplayName("排程不存在时返回null")
        void scheduleNotFound_returnsNull() {
            when(scheduleRepo.findById(999L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = service.postponeSchedule(999L, 3, "设备故障");

            assertNull(result);
            verify(scheduleRepo, never()).save(any());
        }

        @Test
        @DisplayName("顺延成功-日期增加指定天数")
        void postpone_increasesScheduledDate() {
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(1L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = service.postponeSchedule(10L, 5, "原因");

            assertEquals(today.plusDays(5), result.getScheduledDate());
        }

        @Test
        @DisplayName("顺延后版本号递增")
        void postpone_incrementsVersion() {
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);
            schedule.setSchedulingVersion(3);

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(1L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = service.postponeSchedule(10L, 2, "原因");

            assertEquals(4, result.getSchedulingVersion());
        }

        @Test
        @DisplayName("顺延后重新计算逾期等级")
        void postpone_recalculatesOverdueLevel() {
            WorkOrder order = createOrder(1L, "medium", now.plusDays(1));
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(1L)).thenReturn(Optional.of(order));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = service.postponeSchedule(10L, 5, "原因");

            assertTrue(result.getOverdueLevel() >= 1);
        }

        @Test
        @DisplayName("顺延原因追加到工单描述")
        void postpone_appendsReasonToDescription() {
            WorkOrder order = createOrder(1L, "medium", now.plusDays(1));
            order.setDescription("原始描述");
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(1L)).thenReturn(Optional.of(order));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            service.postponeSchedule(10L, 3, "备件未到");

            verify(workOrderRepo).save(argThat(o ->
                    o.getDescription().contains("顺延 3 天") &&
                    o.getDescription().contains("备件未到")
            ));
        }

        @Test
        @DisplayName("顺延原因为null时仍记录顺延天数")
        void postpone_nullReason_stillRecordsDays() {
            WorkOrder order = createOrder(1L, "medium", now.plusDays(1));
            order.setDescription("原始描述");
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(1L)).thenReturn(Optional.of(order));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            service.postponeSchedule(10L, 2, null);

            verify(workOrderRepo).save(argThat(o ->
                    o.getDescription().contains("顺延 2 天")
            ));
        }

        @Test
        @DisplayName("工单描述为null时顺延仍正常工作")
        void postpone_nullDescription_handlesGracefully() {
            WorkOrder order = createOrder(1L, "medium", now.plusDays(1));
            order.setDescription(null);
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(1L)).thenReturn(Optional.of(order));
            when(workOrderRepo.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() -> service.postponeSchedule(10L, 2, "原因"));
        }

        @Test
        @DisplayName("工单不存在时仅更新排程日期")
        void postpone_workOrderNotFound_stillUpdatesSchedule() {
            WorkOrderSchedule schedule = createSchedule(10L, 1L, today, 0);

            when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
            when(scheduleRepo.save(any(WorkOrderSchedule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.findById(1L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = service.postponeSchedule(10L, 3, "原因");

            assertNotNull(result);
            assertEquals(today.plusDays(3), result.getScheduledDate());
            verify(workOrderRepo, never()).save(any());
        }
    }
}
