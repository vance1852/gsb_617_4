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
class MaintenancePlanServiceTest {

    @Mock MaintenancePlanRepository planRepo;
    @Mock MaintenanceItemRepository itemRepo;
    @Mock EquipmentRepository equipmentRepo;

    @InjectMocks MaintenancePlanService service;

    @Nested
    class CalculateNextTrigger {

        @Test
        void timeBased_cycleDays() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setTriggerType("TIME_BASED");
            plan.setCycleDays(14);

            LocalDateTime from = LocalDateTime.of(2025, 1, 1, 10, 0);
            LocalDateTime result = service.calculateNextTrigger(plan, from);

            assertThat(result).isEqualTo(from.plusDays(14));
        }

        @Test
        void timeBased_cycleMonths() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setTriggerType("TIME_BASED");
            plan.setCycleMonths(3);

            LocalDateTime from = LocalDateTime.of(2025, 1, 15, 10, 0);
            LocalDateTime result = service.calculateNextTrigger(plan, from);

            assertThat(result).isEqualTo(from.plusMonths(3));
        }

        @Test
        void timeBased_noCycle_defaultsTo30Days() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setTriggerType("TIME_BASED");
            plan.setCycleDays(null);
            plan.setCycleMonths(null);

            LocalDateTime from = LocalDateTime.of(2025, 3, 1, 10, 0);
            LocalDateTime result = service.calculateNextTrigger(plan, from);

            assertThat(result).isEqualTo(from.plusDays(30));
        }

        @Test
        void timeBased_cycleDaysZero_defaultsTo30Days() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setTriggerType("TIME_BASED");
            plan.setCycleDays(0);

            LocalDateTime from = LocalDateTime.of(2025, 3, 1, 10, 0);
            LocalDateTime result = service.calculateNextTrigger(plan, from);

            assertThat(result).isEqualTo(from.plusDays(30));
        }

        @Test
        void timeBased_cycleDaysTakesPrecedenceOverMonths() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setTriggerType("TIME_BASED");
            plan.setCycleDays(7);
            plan.setCycleMonths(1);

            LocalDateTime from = LocalDateTime.of(2025, 1, 1, 10, 0);
            LocalDateTime result = service.calculateNextTrigger(plan, from);

            assertThat(result).isEqualTo(from.plusDays(7));
        }

        @Test
        void usageBased_defaultsTo7Days() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setTriggerType("USAGE_BASED");

            LocalDateTime from = LocalDateTime.of(2025, 1, 1, 10, 0);
            LocalDateTime result = service.calculateNextTrigger(plan, from);

            assertThat(result).isEqualTo(from.plusDays(7));
        }

        @Test
        void nullFromTime_defaultsToNow() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setTriggerType("TIME_BASED");
            plan.setCycleDays(7);

            LocalDateTime before = LocalDateTime.now();
            LocalDateTime result = service.calculateNextTrigger(plan, null);
            LocalDateTime after = LocalDateTime.now();

            assertThat(result).isBetween(before.plusDays(7), after.plusDays(7));
        }
    }

    @Nested
    class GetApplicableEquipments {

        @Test
        void byEquipmentId_found() {
            Equipment eq = new Equipment();
            eq.setId(10L);
            MaintenancePlan plan = new MaintenancePlan();
            plan.setEquipmentId(10L);

            when(equipmentRepo.findById(10L)).thenReturn(Optional.of(eq));

            var result = service.getApplicableEquipments(plan);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(10L);
        }

        @Test
        void byEquipmentId_notFound() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setEquipmentId(99L);

            when(equipmentRepo.findById(99L)).thenReturn(Optional.empty());

            var result = service.getApplicableEquipments(plan);

            assertThat(result).isEmpty();
        }

        @Test
        void byEquipmentType_filtersCorrectly() {
            Equipment eq1 = new Equipment();
            eq1.setId(1L);
            eq1.setType("pump");
            Equipment eq2 = new Equipment();
            eq2.setId(2L);
            eq2.setType("motor");

            MaintenancePlan plan = new MaintenancePlan();
            plan.setEquipmentType("pump");

            when(equipmentRepo.findAll()).thenReturn(List.of(eq1, eq2));

            var result = service.getApplicableEquipments(plan);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo("pump");
        }

        @Test
        void byEquipmentType_blankString_returnsEmpty() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setEquipmentType("  ");

            var result = service.getApplicableEquipments(plan);

            assertThat(result).isEmpty();
        }

        @Test
        void noCriteria_returnsEmpty() {
            MaintenancePlan plan = new MaintenancePlan();

            var result = service.getApplicableEquipments(plan);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class CreatePlan {

        @Test
        void createsPlanWithItems() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setName("测试计划");
            plan.setTriggerType("TIME_BASED");
            plan.setCycleDays(7);

            MaintenanceItem item = new MaintenanceItem();
            item.setName("检查项1");

            when(planRepo.save(any())).thenAnswer(inv -> {
                MaintenancePlan p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });
            when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenancePlan result = service.createPlan(plan, List.of(item));

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getNextTriggerAt()).isNotNull();
            verify(itemRepo).save(any());
        }

        @Test
        void createsPlanWithNullItems() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setName("空计划");
            plan.setTriggerType("TIME_BASED");
            plan.setCycleDays(7);

            when(planRepo.save(any())).thenAnswer(inv -> {
                MaintenancePlan p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            MaintenancePlan result = service.createPlan(plan, null);

            assertThat(result).isNotNull();
            verify(itemRepo, never()).save(any());
        }
    }

    @Nested
    class UpdatePlan {

        @Test
        void updatesExistingPlan() {
            MaintenancePlan existing = new MaintenancePlan();
            existing.setId(1L);
            existing.setName("旧名");
            existing.setTriggerType("TIME_BASED");
            existing.setCycleDays(7);
            existing.setEnabled(true);

            MaintenancePlan update = new MaintenancePlan();
            update.setName("新名");
            update.setCycleDays(14);

            when(planRepo.findById(1L)).thenReturn(Optional.of(existing));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenancePlan result = service.updatePlan(1L, update, null);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("新名");
            assertThat(result.getCycleDays()).isEqualTo(14);
        }

        @Test
        void updatePlanNotFound_returnsNull() {
            when(planRepo.findById(99L)).thenReturn(Optional.empty());

            MaintenancePlan result = service.updatePlan(99L, new MaintenancePlan(), null);

            assertThat(result).isNull();
        }

        @Test
        void updatesWithNewItems() {
            MaintenancePlan existing = new MaintenancePlan();
            existing.setId(1L);
            existing.setName("计划");
            existing.setTriggerType("TIME_BASED");
            existing.setCycleDays(7);
            existing.setEnabled(true);

            MaintenanceItem newItem = new MaintenanceItem();
            newItem.setName("新检查项");

            when(planRepo.findById(1L)).thenReturn(Optional.of(existing));
            when(planRepo.save(any())).thenAnswer(inv -> {
                MaintenancePlan p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });
            when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenancePlan result = service.updatePlan(1L, existing, List.of(newItem));

            assertThat(result).isNotNull();
            verify(itemRepo).deleteByPlanId(1L);
            verify(itemRepo).save(any());
        }
    }

    @Nested
    class DeletePlan {

        @Test
        void existingPlan_deletesAndReturnsTrue() {
            when(planRepo.existsById(1L)).thenReturn(true);

            boolean result = service.deletePlan(1L);

            assertThat(result).isTrue();
            verify(itemRepo).deleteByPlanId(1L);
            verify(planRepo).deleteById(1L);
        }

        @Test
        void nonExistentPlan_returnsFalse() {
            when(planRepo.existsById(99L)).thenReturn(false);

            boolean result = service.deletePlan(99L);

            assertThat(result).isFalse();
            verify(itemRepo, never()).deleteByPlanId(any());
        }
    }

    @Nested
    class TogglePlan {

        @Test
        void enablePlan_setsNextTriggerIfNull() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(1L);
            plan.setEnabled(false);
            plan.setNextTriggerAt(null);
            plan.setTriggerType("TIME_BASED");
            plan.setCycleDays(7);

            when(planRepo.findById(1L)).thenReturn(Optional.of(plan));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenancePlan result = service.togglePlan(1L, true);

            assertThat(result.getEnabled()).isTrue();
            assertThat(result.getNextTriggerAt()).isNotNull();
        }

        @Test
        void disablePlan_doesNotSetNextTrigger() {
            MaintenancePlan plan = new MaintenancePlan();
            plan.setId(1L);
            plan.setEnabled(true);
            plan.setNextTriggerAt(LocalDateTime.now().plusDays(7));

            when(planRepo.findById(1L)).thenReturn(Optional.of(plan));
            when(planRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MaintenancePlan result = service.togglePlan(1L, false);

            assertThat(result.getEnabled()).isFalse();
        }

        @Test
        void togglePlanNotFound_returnsNull() {
            when(planRepo.findById(99L)).thenReturn(Optional.empty());

            MaintenancePlan result = service.togglePlan(99L, true);

            assertThat(result).isNull();
        }
    }

    @Nested
    class ListPlans {

        @Test
        void byEquipmentId() {
            when(planRepo.findByEquipmentId(10L)).thenReturn(List.of(new MaintenancePlan()));

            var result = service.listPlans(null, 10L, null);

            assertThat(result).hasSize(1);
        }

        @Test
        void byEquipmentType() {
            when(planRepo.findByEquipmentType("pump")).thenReturn(List.of(new MaintenancePlan()));

            var result = service.listPlans(null, null, "pump");

            assertThat(result).hasSize(1);
        }

        @Test
        void enabledOnly() {
            when(planRepo.findByEnabledTrue()).thenReturn(List.of(new MaintenancePlan()));

            var result = service.listPlans(true, null, null);

            assertThat(result).hasSize(1);
        }

        @Test
        void allPlans() {
            when(planRepo.findAll()).thenReturn(List.of(new MaintenancePlan(), new MaintenancePlan()));

            var result = service.listPlans(null, null, null);

            assertThat(result).hasSize(2);
        }
    }
}
