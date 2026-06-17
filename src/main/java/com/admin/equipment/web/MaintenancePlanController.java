package com.admin.equipment.web;

import com.admin.equipment.model.MaintenanceItem;
import com.admin.equipment.model.MaintenancePlan;
import com.admin.equipment.service.MaintenancePlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/maintenance-plans")
public class MaintenancePlanController {

    private static final Set<String> TRIGGER_TYPES = Set.of("TIME_BASED", "USAGE_BASED");
    private static final Set<String> PRIORITIES = Set.of("low", "medium", "high", "urgent");

    private final MaintenancePlanService service;

    public MaintenancePlanController(MaintenancePlanService service) {
        this.service = service;
    }

    public record PlanItemRequest(String name, String description, Integer sequenceNum,
                                  Integer standardMinutes, String requiredSkill, String requiredSpareParts) {}

    public record PlanRequest(String name, String description, Long equipmentId, String equipmentType,
                              String triggerType, Integer cycleDays, Integer cycleMonths,
                              Integer cycleHours, Integer cycleCycles, Integer advanceNoticeDays,
                              Double standardWorkHours, String requiredSkill, String priority,
                              Boolean enabled, List<PlanItemRequest> items) {}

    @GetMapping
    public List<MaintenancePlan> list(@RequestParam(required = false) Boolean enabled,
                                      @RequestParam(required = false) Long equipmentId,
                                      @RequestParam(required = false) String equipmentType) {
        return service.listPlans(enabled, equipmentId, equipmentType);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        MaintenancePlan plan = service.getPlan(id);
        return plan != null ? ResponseEntity.ok(plan) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "计划不存在"));
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<?> getItems(@PathVariable Long id) {
        if (service.getPlan(id) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "计划不存在"));
        }
        return ResponseEntity.ok(service.getPlanItems(id));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PlanRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "名称必填"));
        }
        if (req.triggerType() != null && !TRIGGER_TYPES.contains(req.triggerType())) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "触发类型不合法"));
        }
        MaintenancePlan plan = new MaintenancePlan();
        plan.setName(req.name());
        plan.setDescription(req.description() == null ? "" : req.description());
        plan.setEquipmentId(req.equipmentId());
        plan.setEquipmentType(req.equipmentType());
        plan.setTriggerType(req.triggerType() == null ? "TIME_BASED" : req.triggerType());
        plan.setCycleDays(req.cycleDays());
        plan.setCycleMonths(req.cycleMonths());
        plan.setCycleHours(req.cycleHours());
        plan.setCycleCycles(req.cycleCycles());
        plan.setAdvanceNoticeDays(req.advanceNoticeDays() == null ? 3 : req.advanceNoticeDays());
        plan.setStandardWorkHours(req.standardWorkHours() == null ? 1.0 : req.standardWorkHours());
        plan.setRequiredSkill(req.requiredSkill() == null ? "" : req.requiredSkill());
        plan.setPriority(PRIORITIES.contains(req.priority()) ? req.priority() : "medium");
        plan.setEnabled(req.enabled() == null ? true : req.enabled());

        List<MaintenanceItem> items = null;
        if (req.items() != null) {
            items = req.items().stream().map(ir -> {
                MaintenanceItem it = new MaintenanceItem();
                it.setName(ir.name() == null ? "" : ir.name());
                it.setDescription(ir.description() == null ? "" : ir.description());
                it.setSequenceNum(ir.sequenceNum() == null ? 1 : ir.sequenceNum());
                it.setStandardMinutes(ir.standardMinutes() == null ? 30 : ir.standardMinutes());
                it.setRequiredSkill(ir.requiredSkill() == null ? "" : ir.requiredSkill());
                it.setRequiredSpareParts(ir.requiredSpareParts() == null ? "" : ir.requiredSpareParts());
                return it;
            }).toList();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPlan(plan, items));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PlanRequest req) {
        if (service.getPlan(id) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "计划不存在"));
        }
        MaintenancePlan plan = new MaintenancePlan();
        plan.setName(req.name());
        plan.setDescription(req.description());
        plan.setEquipmentId(req.equipmentId());
        plan.setEquipmentType(req.equipmentType());
        plan.setTriggerType(req.triggerType());
        plan.setCycleDays(req.cycleDays());
        plan.setCycleMonths(req.cycleMonths());
        plan.setCycleHours(req.cycleHours());
        plan.setCycleCycles(req.cycleCycles());
        plan.setAdvanceNoticeDays(req.advanceNoticeDays());
        plan.setStandardWorkHours(req.standardWorkHours());
        plan.setRequiredSkill(req.requiredSkill());
        plan.setPriority(req.priority());
        plan.setEnabled(req.enabled());

        List<MaintenanceItem> items = null;
        if (req.items() != null) {
            items = req.items().stream().map(ir -> {
                MaintenanceItem it = new MaintenanceItem();
                it.setName(ir.name() == null ? "" : ir.name());
                it.setDescription(ir.description() == null ? "" : ir.description());
                it.setSequenceNum(ir.sequenceNum() == null ? 1 : ir.sequenceNum());
                it.setStandardMinutes(ir.standardMinutes() == null ? 30 : ir.standardMinutes());
                it.setRequiredSkill(ir.requiredSkill() == null ? "" : ir.requiredSkill());
                it.setRequiredSpareParts(ir.requiredSpareParts() == null ? "" : ir.requiredSpareParts());
                return it;
            }).toList();
        }
        MaintenancePlan saved = service.updatePlan(id, plan, items);
        return saved != null ? ResponseEntity.ok(saved) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "计划不存在"));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        MaintenancePlan plan = service.togglePlan(id, enabled);
        return plan != null ? ResponseEntity.ok(plan) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "计划不存在"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return service.deletePlan(id) ? ResponseEntity.noContent().build() :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "计划不存在"));
    }
}
