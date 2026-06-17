package com.admin.equipment.web;

import com.admin.equipment.service.MaintenanceAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance-analytics")
public class AnalyticsController {

    private final MaintenanceAnalyticsService service;

    public AnalyticsController(MaintenanceAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/on-time-rate")
    public MaintenanceAnalyticsService.OnTimeRateStats getOnTimeRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return service.getOnTimeRate(from, to);
    }

    @GetMapping("/work-hours-deviation")
    public List<MaintenanceAnalyticsService.WorkHoursDeviation> getWorkHoursDeviation() {
        return service.getWorkHoursDeviation();
    }

    @GetMapping("/plan-execution-summary")
    public List<MaintenanceAnalyticsService.PlanExecutionSummary> getPlanExecutionSummary() {
        return service.getPlanExecutionSummary();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        return service.getDashboardStats();
    }
}
