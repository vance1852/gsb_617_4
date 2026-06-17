package com.admin.equipment.web;

import com.admin.equipment.model.DowntimeWindow;
import com.admin.equipment.model.MaintenanceTeam;
import com.admin.equipment.model.WorkOrderSchedule;
import com.admin.equipment.service.MaintenanceSchedulingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scheduling")
public class SchedulingController {

    private final MaintenanceSchedulingService service;

    public SchedulingController(MaintenanceSchedulingService service) {
        this.service = service;
    }

    public record RescheduleRequest(String scheduledDate, Long teamId) {}
    public record PostponeRequest(Integer days, String reason) {}

    @PostMapping("/run")
    public ResponseEntity<MaintenanceSchedulingService.ScheduleResult> runScheduling(
            @RequestParam(defaultValue = "14") int horizonDays) {
        return ResponseEntity.ok(service.runSmartScheduling(horizonDays));
    }

    @GetMapping("/calendar")
    public List<WorkOrderSchedule> getCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long teamId) {
        return service.getCalendar(from, to, equipmentId, teamId);
    }

    @GetMapping("/teams")
    public List<MaintenanceTeam> listTeams() {
        return service.listTeams();
    }

    @PostMapping("/teams")
    public ResponseEntity<MaintenanceTeam> createTeam(@RequestBody MaintenanceTeam team) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveTeam(team));
    }

    @PutMapping("/teams/{id}")
    public ResponseEntity<?> updateTeam(@PathVariable Long id, @RequestBody MaintenanceTeam team) {
        team.setId(id);
        return ResponseEntity.ok(service.saveTeam(team));
    }

    @GetMapping("/downtime-windows")
    public List<DowntimeWindow> listDowntimeWindows(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String equipmentType) {
        return service.listDowntimeWindows(equipmentId, equipmentType);
    }

    @PostMapping("/downtime-windows")
    public ResponseEntity<DowntimeWindow> createDowntimeWindow(@RequestBody DowntimeWindow w) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveDowntimeWindow(w));
    }

    @DeleteMapping("/downtime-windows/{id}")
    public ResponseEntity<?> deleteDowntimeWindow(@PathVariable Long id) {
        service.deleteDowntimeWindow(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/schedules/{id}/reschedule")
    public ResponseEntity<?> reschedule(@PathVariable Long id, @RequestBody RescheduleRequest req) {
        if (req.scheduledDate() == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("detail", "日期必填"));
        }
        LocalDate date = LocalDate.parse(req.scheduledDate());
        WorkOrderSchedule s = service.reschedule(id, date, req.teamId());
        return s != null ? ResponseEntity.ok(s) :
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "排程不存在"));
    }
}
