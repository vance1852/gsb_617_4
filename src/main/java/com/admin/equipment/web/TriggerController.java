package com.admin.equipment.web;

import com.admin.equipment.model.RunningLog;
import com.admin.equipment.service.WorkOrderTriggerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance-trigger")
public class TriggerController {

    private final WorkOrderTriggerService service;

    public TriggerController(WorkOrderTriggerService service) {
        this.service = service;
    }

    public record RunningLogRequest(Double runningHours, Long runningCycles, String remark) {}

    @PostMapping("/run")
    public ResponseEntity<WorkOrderTriggerService.TriggerResult> runTrigger() {
        return ResponseEntity.ok(service.checkAndTriggerDuePlans());
    }

    @GetMapping("/preview")
    public ResponseEntity<?> getDuePlansPreview() {
        return ResponseEntity.ok(service.getDuePlansPreview());
    }

    @PostMapping("/running-logs/{equipmentId}")
    public ResponseEntity<?> addRunningLog(@PathVariable Long equipmentId, @RequestBody RunningLogRequest req) {
        if (req.runningHours() == null && req.runningCycles() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "运行时长或次数必填"));
        }
        RunningLog log = service.addRunningLog(equipmentId,
                req.runningHours() == null ? 0 : req.runningHours(),
                req.runningCycles() == null ? 0L : req.runningCycles(),
                req.remark());
        return ResponseEntity.ok(log);
    }

    @GetMapping("/running-logs/{equipmentId}")
    public List<RunningLog> getRunningLogs(@PathVariable Long equipmentId) {
        return service.getRunningLogs(equipmentId);
    }
}
