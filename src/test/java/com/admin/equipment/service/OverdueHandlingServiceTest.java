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
import java.util.Collections;
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

    private WorkOrder testOrder;
    private WorkOrderSchedule testSchedule;

    @BeforeEach
    void setUp() {
        testOrder = new WorkOrder();
        testOrder.setId(100L);
        testOrder.setEquipmentId(1L);
        testOrder.setTitle("测试工单");
        testOrder.setPriority("medium");
        testOrder.setStatus("open");
        testOrder.setIsPreventive(true);
        testOrder.setAssignee("A组");
        testOrder.setDueDate(LocalDateTime.now().minusDays(5));

        testSchedule = new WorkOrderSchedule();
        testSchedule.setId(200L);
        testSchedule.setWorkOrderId(100L);
        testSchedule.setScheduledDate(LocalDate.now().plusDays(2));
        testSchedule.setOverdueLevel(1);
        testSchedule.setSchedulingVersion(1);
        testSchedule.setTeamId(1L);
        testSchedule.setTeamName("A组");
    }

    @Nested
    @DisplayName("逾期汇总 getOverdueSummary")
    class GetOverdueSummaryTests {

        @Test
        @DisplayName("无逾期工单时返回空汇总")
        void noOverdueOrders_returnsEmptySummary() {
            when(workOrderRepo.findOverduePreventive(any())).thenReturn(Collections.emptyList());

            OverdueHandlingService.OverdueSummary result = overdueService.getOverdueSummary();

            assertNotNull(result);
            assertEquals(0, result.level1Count);
            assertEquals(0, result.level2Count);
            assertEquals(0, result.level3Count);
            assertTrue(result.items.isEmpty());
        }

        @Test
        @DisplayName("有排程的逾期工单 - 等级1")
        void overdueWithSchedule_level1() {
            testSchedule.setOverdueLevel(1);
            testSchedule.setScheduledDate(LocalDate.now().plusDays(2));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));

            OverdueHandlingService.OverdueSummary result = overdueService.getOverdueSummary();

            assertEquals(1, result.level1Count);
            assertEquals(0, result.level2Count);
            assertEquals(0, result.level3Count);
            assertEquals(1, result.items.size());

            OverdueHandlingService.OverdueItem item = result.items.get(0);
            assertEquals(100L, item.workOrderId);
            assertEquals(200L, item.scheduleId);
            assertEquals("测试工单", item.title);
            assertEquals(1, item.overdueLevel);
            assertEquals("A组", item.assignee);
        }

        @Test
        @DisplayName("有排程的逾期工单 - 等级2")
        void overdueWithSchedule_level2() {
            testSchedule.setOverdueLevel(2);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));

            OverdueHandlingService.OverdueSummary result = overdueService.getOverdueSummary();

            assertEquals(0, result.level1Count);
            assertEquals(1, result.level2Count);
            assertEquals(0, result.level3Count);
        }

        @Test
        @DisplayName("有排程的逾期工单 - 等级3")
        void overdueWithSchedule_level3() {
            testSchedule.setOverdueLevel(3);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));

            OverdueHandlingService.OverdueSummary result = overdueService.getOverdueSummary();

            assertEquals(0, result.level1Count);
            assertEquals(0, result.level2Count);
            assertEquals(1, result.level3Count);
        }

        @Test
        @DisplayName("无排程的逾期工单 - 默认等级1")
        void overdueWithoutSchedule_defaultLevel1() {
            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());

            OverdueHandlingService.OverdueSummary result = overdueService.getOverdueSummary();

            assertEquals(1, result.level1Count);
            assertEquals(0, result.level2Count);
            assertEquals(0, result.level3Count);
            assertEquals(1, result.items.size());
            assertNull(result.items.get(0).scheduleId);
        }

        @Test
        @DisplayName("多级逾期工单混合统计")
        void mixedOverdueLevels_correctCounts() {
            WorkOrder order1 = createOrder(1L, "urgent工单", "urgent");
            order1.setDueDate(LocalDateTime.now().minusDays(10));
            WorkOrderSchedule schedule1 = new WorkOrderSchedule();
            schedule1.setId(1L);
            schedule1.setOverdueLevel(3);

            WorkOrder order2 = createOrder(2L, "high工单", "high");
            order2.setDueDate(LocalDateTime.now().minusDays(5));
            WorkOrderSchedule schedule2 = new WorkOrderSchedule();
            schedule2.setId(2L);
            schedule2.setOverdueLevel(2);

            WorkOrder order3 = createOrder(3L, "medium工单", "medium");
            order3.setDueDate(LocalDateTime.now().minusDays(2));
            WorkOrderSchedule schedule3 = new WorkOrderSchedule();
            schedule3.setId(3L);
            schedule3.setOverdueLevel(1);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order1, order2, order3));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule1));
            when(scheduleRepo.findByWorkOrderId(2L)).thenReturn(Optional.of(schedule2));
            when(scheduleRepo.findByWorkOrderId(3L)).thenReturn(Optional.of(schedule3));

            OverdueHandlingService.OverdueSummary result = overdueService.getOverdueSummary();

            assertEquals(1, result.level1Count);
            assertEquals(1, result.level2Count);
            assertEquals(1, result.level3Count);
            assertEquals(3, result.items.size());
        }

        @Test
        @DisplayName("工单有截止日期时计算逾期天数")
        void orderWithDueDate_computesDaysOverdue() {
            testOrder.setDueDate(LocalDateTime.now().minusDays(5));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());

            OverdueHandlingService.OverdueSummary result = overdueService.getOverdueSummary();

            assertEquals(1, result.items.size());
            assertTrue(result.items.get(0).daysOverdue >= 5);
        }

        @Test
        @DisplayName("工单无截止日期时逾期天数为0")
        void orderWithoutDueDate_zeroDaysOverdue() {
            testOrder.setDueDate(null);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());

            OverdueHandlingService.OverdueSummary result = overdueService.getOverdueSummary();

            assertEquals(1, result.items.size());
            assertEquals(0, result.items.get(0).daysOverdue);
        }
    }

    @Nested
    @DisplayName("逾期升级 escalateOverdueOrders")
    class EscalateOverdueOrdersTests {

        @Test
        @DisplayName("无逾期工单时返回0")
        void noOverdueOrders_returnsZero() {
            when(workOrderRepo.findOverduePreventive(any())).thenReturn(Collections.emptyList());

            int result = overdueService.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("工单无排程时不升级")
        void orderWithoutSchedule_notEscalated() {
            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());

            int result = overdueService.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        @DisplayName("等级未提升时不升级")
        void levelNotIncreased_notEscalated() {
            testSchedule.setOverdueLevel(2);
            testSchedule.setScheduledDate(LocalDate.now().plusDays(2));
            testOrder.setDueDate(LocalDate.now().minusDays(5).atStartOfDay());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(0, result);
            verify(scheduleRepo, never()).save(any());
        }

        @Test
        @DisplayName("等级提升时升级排程")
        void levelIncreased_escalatesSchedule() {
            testSchedule.setOverdueLevel(0);
            testSchedule.setScheduledDate(LocalDate.now().plusDays(5));
            testOrder.setDueDate(LocalDate.now().minusDays(1).atStartOfDay());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(scheduleRepo, times(1)).save(any());
        }

        @Test
        @DisplayName("升级到等级2且原优先级为low时提升为medium")
        void escalateToLevel2_lowPriorityUpgradedToMedium() {
            testOrder.setPriority("low");
            testSchedule.setOverdueLevel(1);
            testSchedule.setScheduledDate(LocalDate.now().plusDays(5));
            testOrder.setDueDate(LocalDate.now().minusDays(5).atStartOfDay());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(workOrderRepo, times(1)).save(any());
            assertEquals("medium", testOrder.getPriority());
        }

        @Test
        @DisplayName("升级到等级3且原优先级为medium时提升为high")
        void escalateToLevel3_mediumPriorityUpgradedToHigh() {
            testOrder.setPriority("medium");
            testSchedule.setOverdueLevel(2);
            testSchedule.setScheduledDate(LocalDate.now().plusDays(10));
            testOrder.setDueDate(LocalDate.now().minusDays(1).atStartOfDay());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(workOrderRepo, times(1)).save(any());
            assertEquals("high", testOrder.getPriority());
        }

        @Test
        @DisplayName("升级到等级2但原优先级为medium时不升级优先级")
        void escalateToLevel2_mediumPriorityNotUpgraded() {
            testOrder.setPriority("medium");
            testSchedule.setOverdueLevel(1);
            testSchedule.setScheduledDate(LocalDate.now().plusDays(5));
            testOrder.setDueDate(LocalDate.now().minusDays(1).atStartOfDay());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(workOrderRepo, never()).save(any());
            assertEquals("medium", testOrder.getPriority());
        }

        @Test
        @DisplayName("升级到等级3但原优先级为high时不升级优先级")
        void escalateToLevel3_highPriorityNotUpgraded() {
            testOrder.setPriority("high");
            testSchedule.setOverdueLevel(2);
            testSchedule.setScheduledDate(LocalDate.now().plusDays(10));
            testOrder.setDueDate(LocalDate.now().minusDays(1).atStartOfDay());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(testOrder));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(testSchedule));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(1, result);
            verify(workOrderRepo, never()).save(any());
            assertEquals("high", testOrder.getPriority());
        }

        @Test
        @DisplayName("多个逾期工单批量升级")
        void multipleOverdueOrders_bulkEscalation() {
            WorkOrder order1 = createOrder(1L, "工单1", "low");
            order1.setDueDate(LocalDateTime.now().minusDays(10));
            WorkOrderSchedule schedule1 = new WorkOrderSchedule();
            schedule1.setId(1L);
            schedule1.setWorkOrderId(1L);
            schedule1.setOverdueLevel(0);
            schedule1.setScheduledDate(LocalDate.now().plusDays(5));

            WorkOrder order2 = createOrder(2L, "工单2", "medium");
            order2.setDueDate(LocalDateTime.now().minusDays(2));
            WorkOrderSchedule schedule2 = new WorkOrderSchedule();
            schedule2.setId(2L);
            schedule2.setWorkOrderId(2L);
            schedule2.setOverdueLevel(1);
            schedule2.setScheduledDate(LocalDate.now().plusDays(3));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(order1, order2));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(schedule1));
            when(scheduleRepo.findByWorkOrderId(2L)).thenReturn(Optional.of(schedule2));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = overdueService.escalateOverdueOrders();

            assertEquals(2, result);
            verify(scheduleRepo, times(2)).save(any());
        }
    }

    @Nested
    @DisplayName("顺延排程 postponeSchedule")
    class PostponeScheduleTests {

        @Test
        @DisplayName("排程不存在时返回null")
        void scheduleNotFound_returnsNull() {
            when(scheduleRepo.findById(999L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = overdueService.postponeSchedule(999L, 3, "设备故障");

            assertNull(result);
            verify(scheduleRepo, never()).save(any());
        }

        @Test
        @DisplayName("正常顺延 - 更新日期和版本")
        void normalPostpone_updatesDateAndVersion() {
            LocalDate originalDate = LocalDate.now().plusDays(2);
            testSchedule.setScheduledDate(originalDate);

            when(scheduleRepo.findById(200L)).thenReturn(Optional.of(testSchedule));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(200L, 5, "备件未到货");

            assertNotNull(result);
            assertEquals(originalDate.plusDays(5), result.getScheduledDate());
            assertEquals(2, result.getSchedulingVersion());
            verify(scheduleRepo, times(1)).save(any());
            verify(workOrderRepo, times(1)).save(any());
        }

        @Test
        @DisplayName("顺延天数为0时日期不变")
        void postponeZeroDays_dateUnchanged() {
            LocalDate originalDate = LocalDate.now().plusDays(2);
            testSchedule.setScheduledDate(originalDate);

            when(scheduleRepo.findById(200L)).thenReturn(Optional.of(testSchedule));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(200L, 0, "测试");

            assertNotNull(result);
            assertEquals(originalDate, result.getScheduledDate());
            assertEquals(2, result.getSchedulingVersion());
        }

        @Test
        @DisplayName("顺延时更新工单描述")
        void postpone_updatesOrderDescription() {
            testOrder.setDescription("原始描述");
            testSchedule.setScheduledDate(LocalDate.now().plusDays(2));

            when(scheduleRepo.findById(200L)).thenReturn(Optional.of(testSchedule));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            overdueService.postponeSchedule(200L, 3, "备件未到货");

            assertTrue(testOrder.getDescription().contains("原始描述"));
            assertTrue(testOrder.getDescription().contains("顺延 3 天"));
            assertTrue(testOrder.getDescription().contains("备件未到货"));
        }

        @Test
        @DisplayName("顺延原因null时只添加顺延天数")
        void postponeWithNullReason_onlyAddsDays() {
            testOrder.setDescription("原始描述");
            testSchedule.setScheduledDate(LocalDate.now().plusDays(2));

            when(scheduleRepo.findById(200L)).thenReturn(Optional.of(testSchedule));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            overdueService.postponeSchedule(200L, 2, null);

            assertTrue(testOrder.getDescription().contains("顺延 2 天"));
        }

        @Test
        @DisplayName("工单描述为null时直接设置顺延记录")
        void postponeWithNullDescription_setsPostponeRecord() {
            testOrder.setDescription(null);
            testSchedule.setScheduledDate(LocalDate.now().plusDays(2));

            when(scheduleRepo.findById(200L)).thenReturn(Optional.of(testSchedule));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            overdueService.postponeSchedule(200L, 2, "测试原因");

            assertNotNull(testOrder.getDescription());
            assertTrue(testOrder.getDescription().contains("顺延 2 天"));
            assertTrue(testOrder.getDescription().contains("测试原因"));
        }

        @Test
        @DisplayName("顺延时重新计算逾期等级")
        void postpone_recalculatesOverdueLevel() {
            testOrder.setDueDate(LocalDate.now().minusDays(1).atStartOfDay());
            testSchedule.setScheduledDate(LocalDate.now().plusDays(2));
            testSchedule.setOverdueLevel(1);

            when(scheduleRepo.findById(200L)).thenReturn(Optional.of(testSchedule));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(testOrder));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(200L, 3, "测试");

            assertNotNull(result);
            assertEquals(2, result.getOverdueLevel());
        }

        @Test
        @DisplayName("工单不存在时不更新工单但仍保存排程")
        void workOrderNotFound_stillSavesSchedule() {
            testSchedule.setScheduledDate(LocalDate.now().plusDays(2));

            when(scheduleRepo.findById(200L)).thenReturn(Optional.of(testSchedule));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = overdueService.postponeSchedule(200L, 3, "测试");

            assertNotNull(result);
            verify(scheduleRepo, times(1)).save(any());
            verify(workOrderRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("逾期等级计算 computeOverdueLevel")
    class ComputeOverdueLevelTests {

        @Test
        @DisplayName("无截止日期时等级为0")
        void noDueDate_levelZero() {
            int level = computeOverdueLevelViaReflection(null, LocalDate.now());
            assertEquals(0, level);
        }

        @Test
        @DisplayName("排程在截止日期前 - 等级0")
        void scheduledBeforeDue_levelZero() {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(5);
            int level = computeOverdueLevelViaReflection(dueDate, LocalDate.now());
            assertEquals(0, level);
        }

        @Test
        @DisplayName("排程在截止日期当天 - 等级0")
        void scheduledOnDueDate_levelZero() {
            LocalDate today = LocalDate.now();
            int level = computeOverdueLevelViaReflection(today.atStartOfDay(), today);
            assertEquals(0, level);
        }

        @Test
        @DisplayName("逾期1天 - 等级1")
        void overdue1Day_level1() {
            LocalDate dueDate = LocalDate.now().minusDays(1);
            int level = computeOverdueLevelViaReflection(dueDate.atStartOfDay(), LocalDate.now());
            assertEquals(1, level);
        }

        @Test
        @DisplayName("逾期3天 - 等级1（上边界）")
        void overdue3Days_level1() {
            LocalDate dueDate = LocalDate.now().minusDays(3);
            int level = computeOverdueLevelViaReflection(dueDate.atStartOfDay(), LocalDate.now());
            assertEquals(1, level);
        }

        @Test
        @DisplayName("逾期4天 - 等级2（下边界）")
        void overdue4Days_level2() {
            LocalDate dueDate = LocalDate.now().minusDays(4);
            int level = computeOverdueLevelViaReflection(dueDate.atStartOfDay(), LocalDate.now());
            assertEquals(2, level);
        }

        @Test
        @DisplayName("逾期7天 - 等级2（上边界）")
        void overdue7Days_level2() {
            LocalDate dueDate = LocalDate.now().minusDays(7);
            int level = computeOverdueLevelViaReflection(dueDate.atStartOfDay(), LocalDate.now());
            assertEquals(2, level);
        }

        @Test
        @DisplayName("逾期8天 - 等级3（下边界）")
        void overdue8Days_level3() {
            LocalDate dueDate = LocalDate.now().minusDays(8);
            int level = computeOverdueLevelViaReflection(dueDate.atStartOfDay(), LocalDate.now());
            assertEquals(3, level);
        }

        @Test
        @DisplayName("逾期30天 - 等级3")
        void overdue30Days_level3() {
            LocalDate dueDate = LocalDate.now().minusDays(30);
            int level = computeOverdueLevelViaReflection(dueDate.atStartOfDay(), LocalDate.now());
            assertEquals(3, level);
        }
    }

    private WorkOrder createOrder(Long id, String title, String priority) {
        WorkOrder order = new WorkOrder();
        order.setId(id);
        order.setEquipmentId(1L);
        order.setTitle(title);
        order.setPriority(priority);
        order.setStatus("open");
        order.setIsPreventive(true);
        order.setAssignee("A组");
        return order;
    }

    private int computeOverdueLevelViaReflection(LocalDateTime dueDate, LocalDate scheduledDate) {
        try {
            java.lang.reflect.Method method = OverdueHandlingService.class
                    .getDeclaredMethod("computeOverdueLevel", LocalDateTime.class, LocalDate.class);
            method.setAccessible(true);
            return (int) method.invoke(overdueService, dueDate, scheduledDate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
