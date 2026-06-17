package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceSchedulingServiceTest {

    @Mock WorkOrderRepository workOrderRepo;
    @Mock WorkOrderScheduleRepository scheduleRepo;
    @Mock MaintenanceTeamRepository teamRepo;
    @Mock DowntimeWindowRepository downtimeRepo;
    @Mock EquipmentRepository equipmentRepo;
    @Mock MaintenancePlanRepository planRepo;

    @InjectMocks MaintenanceSchedulingService service;

    private MaintenanceTeam team1;
    private Equipment eq1;
    private WorkOrder order1;
    private WorkOrder order2;

    @BeforeEach
    void setUp() {
        team1 = new MaintenanceTeam();
        team1.setId(1L);
        team1.setName("A组");
        team1.setDailyCapacityHours(8.0);

        eq1 = new Equipment();
        eq1.setId(10L);
        eq1.setName("泵A");
        eq1.setType("pump");

        order1 = new WorkOrder();
        order1.setId(100L);
        order1.setEquipmentId(10L);
        order1.setTitle("巡检泵A");
        order1.setPriority("high");
        order1.setStatus("open");
        order1.setIsPreventive(true);
        order1.setCreatedAt(LocalDateTime.now().minusDays(5));

        order2 = new WorkOrder();
        order2.setId(101L);
        order2.setEquipmentId(10L);
        order2.setTitle("巡检泵A-2");
        order2.setPriority("low");
        order2.setStatus("open");
        order2.setIsPreventive(true);
        order2.setCreatedAt(LocalDateTime.now().minusDays(3));
    }

    private DowntimeWindow makeWindow(int dow, int start, int end) {
        DowntimeWindow w = new DowntimeWindow();
        w.setDayOfWeek(dow);
        w.setStartMinute(start);
        w.setEndMinute(end);
        return w;
    }

    @Nested
    class RunSmartScheduling {

        @Test
        void noTeams_returnsEmptyResultWithMetrics() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(teamRepo.findAll()).thenReturn(List.of());
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));

            var result = service.runSmartScheduling(7);

            assertThat(result.scheduledCount).isZero();
            assertThat(result.unscheduledCount).isZero();
            assertThat(result.metrics).isNotNull();
            assertThat(result.naiveMetrics).isNotNull();
        }

        @Test
        void noUnscheduledOrders_returnsEmptyResult() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));

            var result = service.runSmartScheduling(7);

            assertThat(result.scheduledCount).isZero();
            assertThat(result.unscheduledCount).isZero();
        }

        @Test
        void doneOrdersFilteredOut() {
            order1.setStatus("done");
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));

            var result = service.runSmartScheduling(7);

            assertThat(result.scheduledCount).isZero();
        }

        @Test
        void alreadyScheduledOrdersFilteredOut() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.of(new WorkOrderSchedule()));
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));

            var result = service.runSmartScheduling(7);

            assertThat(result.scheduledCount).isZero();
        }

        @Test
        void basicScheduling_schedulesOrderSuccessfully() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(order1.getPlanId())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.runSmartScheduling(7);

            assertThat(result.scheduledCount).isEqualTo(1);
            assertThat(result.unscheduledCount).isZero();
            assertThat(result.scheduled).hasSize(1);
            assertThat(result.scheduled.get(0).workOrderId).isEqualTo(100L);
            assertThat(result.scheduled.get(0).equipmentId).isEqualTo(10L);
        }

        @Test
        void priorityOrdering_urgentScheduledBeforeLow() {
            WorkOrder urgent = new WorkOrder();
            urgent.setId(200L);
            urgent.setEquipmentId(10L);
            urgent.setTitle("紧急工单");
            urgent.setPriority("urgent");
            urgent.setStatus("open");
            urgent.setIsPreventive(true);
            urgent.setCreatedAt(LocalDateTime.now().minusDays(1));

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order2, urgent));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.runSmartScheduling(7);

            assertThat(result.scheduled).isNotEmpty();
            assertThat(result.scheduled.get(0).workOrderId).isEqualTo(200L);
        }
    }

    @Nested
    class FindBestSlotConstraints {

        @Test
        void noDowntimeWindowForDay_skipsDay() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));

            int tomorrow = LocalDate.now().plusDays(1).getDayOfWeek().getValue();
            List<DowntimeWindow> windows = new ArrayList<>();
            for (int dow = 1; dow <= 7; dow++) {
                if (dow != tomorrow) {
                    windows.add(makeWindow(dow, 480, 1080));
                }
            }
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(windows);
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.runSmartScheduling(2);

            boolean scheduledOnTomorrow = result.scheduled.stream()
                    .anyMatch(s -> s.scheduledDate.equals(LocalDate.now().plusDays(1)));
            assertThat(scheduledOnTomorrow).isFalse();
        }

        @Test
        void durationExceedsWindow_skipsSlot() {
            MaintenancePlan longPlan = new MaintenancePlan();
            longPlan.setId(50L);
            longPlan.setStandardWorkHours(20.0);

            order1.setPlanId(50L);
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of(makeWindow(1, 480, 600)));
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(50L)).thenReturn(Optional.of(longPlan));

            var result = service.runSmartScheduling(7);

            assertThat(result.unscheduledCount).isEqualTo(1);
            assertThat(result.scheduledCount).isZero();
        }

        @Test
        void teamCapacityExceeded_skipsTeamOnDate() {
            MaintenanceTeam smallTeam = new MaintenanceTeam();
            smallTeam.setId(2L);
            smallTeam.setName("小组");
            smallTeam.setDailyCapacityHours(1.0);

            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(50L);
            plan.setStandardWorkHours(2.0);

            order1.setPlanId(50L);
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(smallTeam));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(50L)).thenReturn(Optional.of(plan));

            var result = service.runSmartScheduling(7);

            assertThat(result.unscheduledCount).isEqualTo(1);
        }

        @Test
        void equipmentNotFound_returnsUnscheduled() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.empty());
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());

            var result = service.runSmartScheduling(7);

            assertThat(result.unscheduledCount).isEqualTo(1);
        }

        @Test
        void clusteringBonus_prefersDateNearExistingSchedule() {
            WorkOrder first = new WorkOrder();
            first.setId(300L);
            first.setEquipmentId(10L);
            first.setTitle("第一单");
            first.setPriority("medium");
            first.setStatus("open");
            first.setIsPreventive(true);
            first.setCreatedAt(LocalDateTime.now().minusDays(5));

            WorkOrder second = new WorkOrder();
            second.setId(301L);
            second.setEquipmentId(10L);
            second.setTitle("第二单");
            second.setPriority("medium");
            second.setStatus("open");
            second.setIsPreventive(true);
            second.setCreatedAt(LocalDateTime.now().minusDays(3));

            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(first, second));
            when(scheduleRepo.findByWorkOrderId(anyLong())).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.runSmartScheduling(14);

            assertThat(result.scheduledCount).isEqualTo(2);
            if (result.scheduled.size() == 2) {
                long daysBetween = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                        result.scheduled.get(0).scheduledDate,
                        result.scheduled.get(1).scheduledDate));
                assertThat(daysBetween).isLessThanOrEqualTo(1);
            }
        }

        @Test
        void defaultDowntimeWindowUsed_whenNoWindowsConfigured() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.runSmartScheduling(7);

            assertThat(result.scheduledCount).isEqualTo(1);
            var item = result.scheduled.get(0);
            assertThat(item.startMinute).isEqualTo(480);
            assertThat(item.durationMinutes).isGreaterThanOrEqualTo(30);
        }
    }

    @Nested
    class ComputeOverdueLevel {

        @Test
        void noDueDate_returnsZero() {
            order1.setDueDate(null);
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.runSmartScheduling(7);

            assertThat(result.scheduledCount).isEqualTo(1);
        }

        @Test
        void dueDateAfterScheduled_returnsLevelZero() {
            order1.setDueDate(LocalDateTime.now().plusDays(10));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> {
                WorkOrderSchedule s = inv.getArgument(0);
                assertThat(s.getOverdueLevel()).isEqualTo(0);
                return s;
            });
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.runSmartScheduling(7);
        }

        @Test
        void daysLate1To3_returnsLevel1() {
            order1.setDueDate(LocalDateTime.now().minusDays(2));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> {
                WorkOrderSchedule s = inv.getArgument(0);
                assertThat(s.getOverdueLevel()).isEqualTo(1);
                return s;
            });
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.runSmartScheduling(7);
        }

        @Test
        void daysLate4To7_returnsLevel2() {
            order1.setDueDate(LocalDateTime.now().minusDays(5));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> {
                WorkOrderSchedule s = inv.getArgument(0);
                assertThat(s.getOverdueLevel()).isEqualTo(2);
                return s;
            });
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.runSmartScheduling(7);
        }

        @Test
        void daysLateOver7_returnsLevel3() {
            order1.setDueDate(LocalDateTime.now().minusDays(10));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> {
                WorkOrderSchedule s = inv.getArgument(0);
                assertThat(s.getOverdueLevel()).isEqualTo(3);
                return s;
            });
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.runSmartScheduling(7);
        }

        @Test
        void boundary_exactly3DaysLate_returnsLevel1() {
            order1.setDueDate(LocalDateTime.now().minusDays(3));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> {
                WorkOrderSchedule s = inv.getArgument(0);
                assertThat(s.getOverdueLevel()).isEqualTo(1);
                return s;
            });
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.runSmartScheduling(7);
        }

        @Test
        void boundary_exactly7DaysLate_returnsLevel2() {
            order1.setDueDate(LocalDateTime.now().minusDays(7));
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> {
                WorkOrderSchedule s = inv.getArgument(0);
                assertThat(s.getOverdueLevel()).isEqualTo(2);
                return s;
            });
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.runSmartScheduling(7);
        }
    }

    @Nested
    class Reschedule {

        @Test
        void reschedule_updatesDateAndTeam() {
            WorkOrderSchedule s = new WorkOrderSchedule();
            s.setId(1L);
            s.setWorkOrderId(100L);
            s.setScheduledDate(LocalDate.now());
            s.setTeamId(1L);
            s.setTeamName("A组");
            s.setSchedulingVersion(1);

            WorkOrder order = new WorkOrder();
            order.setId(100L);
            order.setPriority("high");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(s));
            when(teamRepo.findById(2L)).thenReturn(Optional.of(team1));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = service.reschedule(1L, LocalDate.now().plusDays(3), 2L);

            assertThat(result).isNotNull();
            assertThat(result.getScheduledDate()).isEqualTo(LocalDate.now().plusDays(3));
            assertThat(result.getTeamId()).isEqualTo(1L);
            assertThat(result.getSchedulingVersion()).isEqualTo(2);
        }

        @Test
        void reschedule_notFound_returnsNull() {
            when(scheduleRepo.findById(999L)).thenReturn(Optional.empty());

            WorkOrderSchedule result = service.reschedule(999L, LocalDate.now().plusDays(1), null);

            assertThat(result).isNull();
        }

        @Test
        void reschedule_noTeamChange() {
            WorkOrderSchedule s = new WorkOrderSchedule();
            s.setId(1L);
            s.setWorkOrderId(100L);
            s.setScheduledDate(LocalDate.now());
            s.setTeamId(1L);
            s.setSchedulingVersion(1);

            WorkOrder order = new WorkOrder();
            order.setId(100L);
            order.setPriority("high");

            when(scheduleRepo.findById(1L)).thenReturn(Optional.of(s));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order));
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkOrderSchedule result = service.reschedule(1L, LocalDate.now().plusDays(2), null);

            assertThat(result).isNotNull();
            assertThat(result.getSchedulingVersion()).isEqualTo(2);
        }
    }

    @Nested
    class GetCalendar {

        @Test
        void byEquipmentId() {
            LocalDate from = LocalDate.now();
            LocalDate to = from.plusDays(7);
            when(scheduleRepo.findByEquipmentAndDateRange(10L, from, to)).thenReturn(List.of());

            var result = service.getCalendar(from, to, 10L, null);

            assertThat(result).isEmpty();
            verify(scheduleRepo).findByEquipmentAndDateRange(10L, from, to);
        }

        @Test
        void byTeamId() {
            LocalDate from = LocalDate.now();
            LocalDate to = from.plusDays(7);
            when(scheduleRepo.findByTeamAndDateRange(1L, from, to)).thenReturn(List.of());

            var result = service.getCalendar(from, to, null, 1L);

            assertThat(result).isEmpty();
            verify(scheduleRepo).findByTeamAndDateRange(1L, from, to);
        }

        @Test
        void byDateRange() {
            LocalDate from = LocalDate.now();
            LocalDate to = from.plusDays(7);
            when(scheduleRepo.findByScheduledDateBetween(from, to)).thenReturn(List.of());

            var result = service.getCalendar(from, to, null, null);

            assertThat(result).isEmpty();
            verify(scheduleRepo).findByScheduledDateBetween(from, to);
        }
    }

    @Nested
    class ScheduleMetrics {

        @Test
        void metricsComputed_withScheduledItems() {
            when(workOrderRepo.findByIsPreventiveTrue()).thenReturn(List.of(order1));
            when(scheduleRepo.findByWorkOrderId(100L)).thenReturn(Optional.empty());
            when(teamRepo.findAll()).thenReturn(List.of(team1));
            when(equipmentRepo.findAll()).thenReturn(List.of(eq1));
            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq1));
            when(downtimeRepo.findByEquipmentId(10L)).thenReturn(List.of());
            when(downtimeRepo.findByEquipmentType("pump")).thenReturn(List.of());
            when(planRepo.findById(any())).thenReturn(Optional.empty());
            when(scheduleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var result = service.runSmartScheduling(7);

            assertThat(result.metrics).isNotNull();
            assertThat(result.metrics.totalDowntimeDays).isGreaterThanOrEqualTo(1);
            assertThat(result.naiveMetrics).isNotNull();
        }
    }
}
