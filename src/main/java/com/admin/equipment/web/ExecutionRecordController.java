package com.admin.equipment.web;

import com.admin.equipment.model.ExecutionRecord;
import com.admin.equipment.service.ExecutionRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/execution-records")
public class ExecutionRecordController {

    private final ExecutionRecordService service;

    public ExecutionRecordController(ExecutionRecordService service) {
        this.service = service;
    }

    public record UpdateRequest(String result, Integer actualMinutes, String executor, String status) {}
    public record SignRequest(String signedBy) {}

    @GetMapping("/work-order/{workOrderId}")
    public List<ExecutionRecord> getByWorkOrder(@PathVariable Long workOrderId) {
        return service.getRecordsByWorkOrder(workOrderId);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateRequest req) {
        ExecutionRecord r = service.updateRecord(id, req.result(), req.actualMinutes(),
                req.executor(), req.status());
        return r != null ? ResponseEntity.ok(r) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "记录不存在"));
    }

    @PostMapping("/{id}/sign")
    public ResponseEntity<?> sign(@PathVariable Long id, @RequestBody SignRequest req) {
        if (req.signedBy() == null || req.signedBy().isBlank()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "签字人必填"));
        }
        ExecutionRecord r = service.signRecord(id, req.signedBy());
        return r != null ? ResponseEntity.ok(r) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "记录不存在"));
    }

    @PostMapping("/work-order/{workOrderId}/complete")
    public ResponseEntity<?> completeWorkOrder(@PathVariable Long workOrderId) {
        boolean done = service.completeWorkOrderIfAllSigned(workOrderId);
        return ResponseEntity.ok(Map.of("completed", done));
    }
}
