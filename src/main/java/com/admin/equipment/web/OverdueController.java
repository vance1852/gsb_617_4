package com.admin.equipment.web;

import com.admin.equipment.service.OverdueHandlingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/overdue")
public class OverdueController {

    private final OverdueHandlingService service;

    public OverdueController(OverdueHandlingService service) {
        this.service = service;
    }

    public record PostponeRequest(Integer days, String reason) {}

    @GetMapping("/summary")
    public OverdueHandlingService.OverdueSummary getSummary() {
        return service.getOverdueSummary();
    }

    @PostMapping("/escalate")
    public ResponseEntity<?> escalate() {
        int count = service.escalateOverdueOrders();
        return ResponseEntity.ok(Map.of("escalatedCount", count));
    }

    @PostMapping("/schedules/{scheduleId}/postpone")
    public ResponseEntity<?> postpone(@PathVariable Long scheduleId, @RequestBody PostponeRequest req) {
        if (req.days() == null || req.days() <= 0) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "顺延天数必须大于0"));
        }
        Object result = service.postponeSchedule(scheduleId, req.days(), req.reason());
        return result != null ? ResponseEntity.ok(result) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "排程不存在"));
    }
}
