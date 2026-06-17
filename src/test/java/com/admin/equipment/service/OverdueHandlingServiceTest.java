package com.admin.equipment.service;

import com.admin.equipment.model.WorkOrder;
import com.admin.equipment.model.WorkOrderSchedule;
import com.admin.equipment.repo.WorkOrderRepository;
import com.admin.equipment.repo.WorkOrderScheduleRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueHandlingServiceTest {

    @Mock WorkOrderRepository workOrderRepo;
    @Mock WorkOrderScheduleRepository scheduleRepo;

    @InjectMocks OverdueHandlingService service;

    private WorkOrder makeOrder(Long id, String priority, LocalDateTime dueDate) {
        WorkOrder o = new WorkOrder();
        o.setId(id);
        o.setTitle("工单" + id);
        o.setPriority(priority);
        o.setDueDate(dueDate);
        o.setStatus("open");
        o.setAssignee("A组");
        return o;
    }

    private WorkOrderSchedule makeSchedule(Long id, Long workOrderId, int overdueLevel, LocalDate scheduledDate) {
        WorkOrderSchedule s = new WorkOrderSchedule();
        s.setId(id);
        s.setWorkOrderId(workOrderId);
        s.setOverdueLevel(overdueLevel);
        s.setScheduledDate(scheduledDate);
        s.setSchedulingVersion(1);
        return s;
    }

    @Nested
    class GetOverdueSummary {

        @Test
        void emptyOverdue_returnsEmptySummary() {
            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of());

            var result = service.getOverdueSummary();

            assertThat(result.level1Count).isZero();
            assertThat(result.level2Count).isZero();
            assertThat(result.level3Count).isZero();
            assertThat(result.items).isEmpty();
        }

        @Test
        void level1Count_withScheduleLevel1() {
            WorkOrder o1 = makeOrder(1L, "medium", LocalDateTime.now().minusDays(2));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 1, LocalDate.now().plusDays(1));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));

            var result = service.getOverdueSummary();

            assertThat(result.level1Count).isEqualTo(1);
            assertThat(result.level2Count).isZero();
            assertThat(result.level3Count).isZero();
        }

        @Test
        void level2Count_withScheduleLevel2() {
            WorkOrder o1 = makeOrder(1L, "medium", LocalDateTime.now().minusDays(5));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 2, LocalDate.now().plusDays(5));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));

            var result = service.getOverdueSummary();

            assertThat(result.level2Count).isEqualTo(1);
            assertThat(result.level1Count).isZero();
        }

        @Test
        void level3Count_withScheduleLevel3() {
            WorkOrder o1 = makeOrder(1L, "high", LocalDateTime.now().minusDays(10));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 3, LocalDate.now().plusDays(10));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));

            var result = service.getOverdueSummary();

            assertThat(result.level3Count).isEqualTo(1);
        }

        @Test
        void noSchedule_defaultsToLevel1() {
            WorkOrder o1 = makeOrder(1L, "low", LocalDateTime.now().minusDays(2));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.empty());

            var result = service.getOverdueSummary();

            assertThat(result.level1Count).isEqualTo(1);
        }

        @Test
        void mixedLevels_countedCorrectly() {
            WorkOrder o1 = makeOrder(1L, "low", LocalDateTime.now().minusDays(2));
            WorkOrder o2 = makeOrder(2L, "medium", LocalDateTime.now().minusDays(5));
            WorkOrder o3 = makeOrder(3L, "high", LocalDateTime.now().minusDays(10));

            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 1, LocalDate.now());
            WorkOrderSchedule s2 = makeSchedule(20L, 2L, 2, LocalDate.now());
            WorkOrderSchedule s3 = makeSchedule(30L, 3L, 3, LocalDate.now());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1, o2, o3));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.findByWorkOrderId(2L)).thenReturn(Optional.of(s2));
            when(scheduleRepo.findByWorkOrderId(3L)).thenReturn(Optional.of(s3));

            var result = service.getOverdueSummary();

            assertThat(result.level1Count).isEqualTo(1);
            assertThat(result.level2Count).isEqualTo(1);
            assertThat(result.level3Count).isEqualTo(1);
            assertThat(result.items).hasSize(3);
        }

        @Test
        void daysOverdueComputed_whenDueDateNotNull() {
            LocalDateTime due = LocalDateTime.now().minusDays(5);
            WorkOrder o1 = makeOrder(1L, "medium", due);

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.empty());

            var result = service.getOverdueSummary();

            assertThat(result.items.get(0).daysOverdue).isGreaterThanOrEqualTo(5);
        }
    }

    @Nested
    class EscalateOverdueOrders {

        @Test
        void levelUpgrade_savesSchedule() {
            WorkOrder o1 = makeOrder(1L, "high", LocalDateTime.now().minusDays(5));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 1, LocalDate.now().plusDays(5));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertThat(result).isEqualTo(1);
            verify(scheduleRepo).save(any());
        }

        @Test
        void noSchedule_skipsOrder() {
            WorkOrder o1 = makeOrder(1L, "low", LocalDateTime.now().minusDays(5));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.empty());

            int result = service.escalateOverdueOrders();

            assertThat(result).isZero();
            verify(scheduleRepo, never()).save(any());
        }

        @Test
        void noLevelChange_returnsZero() {
            WorkOrder o1 = makeOrder(1L, "high", LocalDateTime.now().minusDays(2));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 1, LocalDate.now().plusDays(1));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));

            int result = service.escalateOverdueOrders();

            assertThat(result).isZero();
        }

        @Test
        void priorityUpgrade_lowToMedium_atLevel2() {
            WorkOrder o1 = makeOrder(1L, "low", LocalDateTime.now().minusDays(5));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 1, LocalDate.now().plusDays(5));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder saved = inv.getArgument(0);
                assertThat(saved.getPriority()).isEqualTo("medium");
                return saved;
            });

            service.escalateOverdueOrders();
        }

        @Test
        void priorityUpgrade_mediumToHigh_atLevel3() {
            WorkOrder o1 = makeOrder(1L, "medium", LocalDateTime.now().minusDays(10));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 2, LocalDate.now().plusDays(10));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder saved = inv.getArgument(0);
                assertThat(saved.getPriority()).isEqualTo("high");
                return saved;
            });

            service.escalateOverdueOrders();
        }

        @Test
        void noPriorityUpgrade_whenAlreadyHigh() {
            WorkOrder o1 = makeOrder(1L, "high", LocalDateTime.now().minusDays(10));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 2, LocalDate.now().plusDays(10));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.escalateOverdueOrders();

            verify(workOrderRepo, never()).save(any());
        }

        @Test
        void multipleOrders_escalatesAll() {
            WorkOrder o1 = makeOrder(1L, "low", LocalDateTime.now().minusDays(5));
            WorkOrder o2 = makeOrder(2L, "medium", LocalDateTime.now().minusDays(10));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 1, LocalDate.now().plusDays(5));
            WorkOrderSchedule s2 = makeSchedule(20L, 2L, 2, LocalDate.now().plusDays(10));

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1, o2));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.findByWorkOrderId(2L)).thenReturn(Optional.of(s2));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertThat(result).isEqualTo(2);
        }
    }

    @Nested
    class PostponeSchedule {

        @Test
        void postpone_movesDateAndRecalculatesLevel() {
            WorkOrderSchedule s = makeSchedule(1L, 100L, 1, LocalDate.now());
            WorkOrder order = makeOrder(100L, "medium", LocalDateTime.now().minusDays(2));

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(s));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = service.postponeSchedule(1L, 3, "设备故障");

            assertThat(result).isNotNull();
            assertThat(result.getScheduledDate()).isEqualTo(LocalDate.now().plusDays(3));
            assertThat(result.getSchedulingVersion()).isEqualTo(2);
        }

        @Test
        void postpone_notFound_returnsNull() {
            when(scheduleRepo.findById(999L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = service.postponeSchedule(999L, 3, "原因");

            assertThat(result).isNull();
        }

        @Test
        void postpone_noReason_appendsWithoutReason() {
            WorkOrderSchedule s = makeSchedule(1L, 100L, 0, LocalDate.now());
            WorkOrder order = makeOrder(100L, "medium", null);
            order.setDescription("原始描述");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(s));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> {
                WorkOrder saved = inv.getArgument(0);
                assertThat(saved.getDescription()).contains("顺延 5 天");
                return saved;
            });

            service.postponeSchedule(1L, 5, null);
        }

        @Test
        void postpone_noWorkOrder_stillSavesSchedule() {
            WorkOrderSchedule s = makeSchedule(1L, 100L, 0, LocalDate.now());

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(s));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = service.postponeSchedule(1L, 2, "测试");

            assertThat(result).isNotNull();
            assertThat(result.getScheduledDate()).isEqualTo(LocalDate.now().plusDays(2));
            verify(workOrderRepo, never()).save(any());
        }
    }

    @Nested
    class ComputeOverdueLevelBoundary {

        @Test
        void noDueDate_returnsZero() {
            WorkOrder o1 = makeOrder(1L, "low", null);
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 0, LocalDate.now());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));

            int result = service.escalateOverdueOrders();

            assertThat(result).isZero();
        }

        @Test
        void scheduledBeforeDue_returnsZero() {
            WorkOrder o1 = makeOrder(1L, "low", LocalDateTime.now().plusDays(5));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 0, LocalDate.now());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));

            int result = service.escalateOverdueOrders();

            assertThat(result).isZero();
        }

        @Test
        void exactly3DaysLate_level1NotEscalatedFrom1() {
            WorkOrder o1 = makeOrder(1L, "low", LocalDateTime.now().minusDays(3));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 1, LocalDate.now());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));

            int result = service.escalateOverdueOrders();

            assertThat(result).isZero();
        }

        @Test
        void exactly7DaysLate_level2NotEscalatedFrom2() {
            WorkOrder o1 = makeOrder(1L, "low", LocalDateTime.now().minusDays(7));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 2, LocalDate.now());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));

            int result = service.escalateOverdueOrders();

            assertThat(result).isZero();
        }

        @Test
        void exactly8DaysLate_escalatedFrom2To3() {
            WorkOrder o1 = makeOrder(1L, "medium", LocalDateTime.now().minusDays(8));
            WorkOrderSchedule s1 = makeSchedule(10L, 1L, 2, LocalDate.now());

            when(workOrderRepo.findOverduePreventive(any())).thenReturn(List.of(o1));
            when(scheduleRepo.findByWorkOrderId(1L)).thenReturn(Optional.of(s1));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = service.escalateOverdueOrders();

            assertThat(result).isEqualTo(1);
        }
    }
}
