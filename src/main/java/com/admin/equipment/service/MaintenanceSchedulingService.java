package com.admin.equipment.service;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MaintenanceSchedulingService {

    private final WorkOrderRepository workOrderRepo;
    private final WorkOrderScheduleRepository scheduleRepo;
    private final MaintenanceTeamRepository teamRepo;
    private final DowntimeWindowRepository downtimeRepo;
    private final EquipmentRepository equipmentRepo;
    private final MaintenancePlanRepository planRepo;

    public MaintenanceSchedulingService(WorkOrderRepository workOrderRepo,
                                        WorkOrderScheduleRepository scheduleRepo,
                                        MaintenanceTeamRepository teamRepo,
                                        DowntimeWindowRepository downtimeRepo,
                                        EquipmentRepository equipmentRepo,
                                        MaintenancePlanRepository planRepo) {
        this.workOrderRepo = workOrderRepo;
        this.scheduleRepo = scheduleRepo;
        this.teamRepo = teamRepo;
        this.downtimeRepo = downtimeRepo;
        this.equipmentRepo = equipmentRepo;
        this.planRepo = planRepo;
    }

    public static class ScheduleResult {
        public int scheduledCount;
        public int unscheduledCount;
        public List<ScheduledItem> scheduled = new ArrayList<>();
        public ScheduleMetrics metrics;
        public ScheduleMetrics naiveMetrics;
    }

    public static class ScheduledItem {
        public Long workOrderId;
        public Long equipmentId;
        public String equipmentName;
        public LocalDate scheduledDate;
        public Long teamId;
        public String teamName;
        public int startMinute;
        public int durationMinutes;
        public int priorityWeight;
    }

    public static class ScheduleMetrics {
        public int totalDowntimeDays;
        public double avgTeamLoad;
        public double maxTeamLoad;
        public double teamLoadStdDev;
        public int conflictCount;
        public int overdueCount;
    }

    @Transactional
    public ScheduleResult runSmartScheduling(int horizonDays) {
        List<WorkOrder> unscheduled = getUnscheduledPreventiveOrders();
        List<MaintenanceTeam> teams = teamRepo.findAll();
        List<Equipment> equipments = equipmentRepo.findAll();
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(horizonDays);

        ScheduleResult result = new ScheduleResult();
        if (teams.isEmpty() || unscheduled.isEmpty()) {
            result.metrics = new ScheduleMetrics();
            result.naiveMetrics = computeNaiveMetrics(unscheduled, teams, today);
            return result;
        }

        Map<Long, List<DowntimeWindow>> equipmentWindows = buildDowntimeWindows(equipments);
        Map<Long, Double> teamCapacity = new HashMap<>();
        for (MaintenanceTeam t : teams) teamCapacity.put(t.getId(), t.getDailyCapacityHours());

        Map<Long, Map<LocalDate, Double>> teamDailyLoad = new HashMap<>();
        Map<Long, Set<LocalDate>> equipmentScheduledDates = new HashMap<>();
        for (WorkOrder o : unscheduled) {
            equipmentScheduledDates.putIfAbsent(o.getEquipmentId(), new HashSet<>());
        }

        List<WorkOrder> prioritized = prioritizeOrders(unscheduled);

        for (WorkOrder order : prioritized) {
            Optional<MaintenancePlan> planOpt = order.getPlanId() != null ?
                    planRepo.findById(order.getPlanId()) : Optional.empty();
            double hoursNeeded = planOpt.map(MaintenancePlan::getStandardWorkHours).orElse(2.0);

            ScheduledItem best = findBestSlot(order, hoursNeeded, teams, equipmentWindows,
                    teamDailyLoad, teamCapacity, equipmentScheduledDates, today, horizon);

            if (best != null) {
                saveSchedule(order, best, planOpt.orElse(null));
                result.scheduled.add(best);
                result.scheduledCount++;
                teamDailyLoad.computeIfAbsent(best.teamId, k -> new HashMap<>())
                        .merge(best.scheduledDate, hoursNeeded, Double::sum);
                equipmentScheduledDates.get(order.getEquipmentId()).add(best.scheduledDate);
            } else {
                result.unscheduledCount++;
            }
        }
        result.metrics = computeMetrics(result.scheduled, teams, teamDailyLoad, today, horizon);
        result.naiveMetrics = computeNaiveMetrics(unscheduled, teams, today);
        return result;
    }

    private List<WorkOrder> getUnscheduledPreventiveOrders() {
        List<WorkOrder> preventive = workOrderRepo.findByIsPreventiveTrue();
        return preventive.stream()
                .filter(o -> !"done".equals(o.getStatus()))
                .filter(o -> scheduleRepo.findByWorkOrderId(o.getId()).isEmpty())
                .collect(Collectors.toList());
    }

    private List<WorkOrder> prioritizeOrders(List<WorkOrder> orders) {
        Map<String, Integer> priorityWeight = Map.of("urgent", 4, "high", 3, "medium", 2, "low", 1);
        return orders.stream()
                .sorted((a, b) -> {
                    int wa = priorityWeight.getOrDefault(a.getPriority(), 2);
                    int wb = priorityWeight.getOrDefault(b.getPriority(), 2);
                    if (wa != wb) return wb - wa;
                    LocalDateTime da = a.getDueDate() != null ? a.getDueDate() : a.getCreatedAt();
                    LocalDateTime db = b.getDueDate() != null ? b.getDueDate() : b.getCreatedAt();
                    return da.compareTo(db);
                })
                .collect(Collectors.toList());
    }

    private Map<Long, List<DowntimeWindow>> buildDowntimeWindows(List<Equipment> equipments) {
        Map<Long, List<DowntimeWindow>> result = new HashMap<>();
        for (Equipment e : equipments) {
            List<DowntimeWindow> windows = new ArrayList<>();
            windows.addAll(downtimeRepo.findByEquipmentId(e.getId()));
            if (e.getType() != null) {
                windows.addAll(downtimeRepo.findByEquipmentType(e.getType()));
            }
            if (windows.isEmpty()) {
                for (int dow = 1; dow <= 7; dow++) {
                    DowntimeWindow w = new DowntimeWindow();
                    w.setDayOfWeek(dow);
                    w.setStartMinute(8 * 60);
                    w.setEndMinute(18 * 60);
                    windows.add(w);
                }
            }
            result.put(e.getId(), windows);
        }
        return result;
    }

    private ScheduledItem findBestSlot(WorkOrder order, double hoursNeeded,
                                       List<MaintenanceTeam> teams,
                                       Map<Long, List<DowntimeWindow>> equipmentWindows,
                                       Map<Long, Map<LocalDate, Double>> teamDailyLoad,
                                       Map<Long, Double> teamCapacity,
                                       Map<Long, Set<LocalDate>> equipmentScheduledDates,
                                       LocalDate today, LocalDate horizon) {
        int durationMin = (int) Math.max(30, Math.ceil(hoursNeeded * 60));
        Equipment eq = equipmentRepo.findById(order.getEquipmentId()).orElse(null);
        if (eq == null) return null;
        List<DowntimeWindow> windows = equipmentWindows.getOrDefault(order.getEquipmentId(), List.of());

        ScheduledItem best = null;
        int bestScore = Integer.MIN_VALUE;

        for (MaintenanceTeam team : teams) {
            for (LocalDate date = today; !date.isAfter(horizon); date = date.plusDays(1)) {
                int dow = date.getDayOfWeek().getValue();
                Optional<DowntimeWindow> dowWin = windows.stream()
                        .filter(w -> w.getDayOfWeek() == dow).findFirst();
                if (dowWin.isEmpty()) continue;
                DowntimeWindow win = dowWin.get();

                double currentLoad = teamDailyLoad.getOrDefault(team.getId(), new HashMap<>())
                        .getOrDefault(date, 0.0);
                if (currentLoad + hoursNeeded > teamCapacity.get(team.getId()) * 1.2) continue;

                int availableMin = win.getEndMinute() - win.getStartMinute();
                if (durationMin > availableMin) continue;

                Set<LocalDate> eqDates = equipmentScheduledDates.get(order.getEquipmentId());
                boolean clustering = false;
                if (eqDates != null) {
                    if (eqDates.contains(date)) {
                        clustering = true;
                    } else {
                        for (LocalDate ed : eqDates) {
                            if (Math.abs(ChronoUnit.DAYS.between(ed, date)) <= 1) {
                                clustering = true;
                                break;
                            }
                        }
                    }
                }

                int score = 0;
                score += clustering ? 1000 : 0;
                score += (10000 - (int) ChronoUnit.DAYS.between(today, date) * 10);
                double loadRatio = (currentLoad + hoursNeeded) / teamCapacity.get(team.getId());
                score += (int) ((1.0 - loadRatio) * 500);
                Map<String, Integer> priorityWeight = Map.of("urgent", 400, "high", 300, "medium", 200, "low", 100);
                score += priorityWeight.getOrDefault(order.getPriority(), 200);

                if (score > bestScore) {
                    bestScore = score;
                    best = new ScheduledItem();
                    best.workOrderId = order.getId();
                    best.equipmentId = order.getEquipmentId();
                    best.equipmentName = eq.getName();
                    best.scheduledDate = date;
                    best.teamId = team.getId();
                    best.teamName = team.getName();
                    best.startMinute = win.getStartMinute();
                    best.durationMinutes = durationMin;
                    best.priorityWeight = priorityWeight.getOrDefault(order.getPriority(), 2);
                }
            }
        }
        return best;
    }

    private void saveSchedule(WorkOrder order, ScheduledItem item, MaintenancePlan plan) {
        WorkOrderSchedule s = new WorkOrderSchedule();
        s.setWorkOrderId(order.getId());
        s.setPlanId(order.getPlanId());
        s.setScheduledDate(item.scheduledDate);
        s.setTeamId(item.teamId);
        s.setTeamName(item.teamName);
        s.setIsPreventive(true);
        s.setScheduledStartMinute(item.startMinute);
        s.setScheduledDurationMinutes(item.durationMinutes);
        s.setOverdueLevel(computeOverdueLevel(order, item.scheduledDate));
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        scheduleRepo.save(s);

        order.setAssignee(item.teamName);
        workOrderRepo.save(order);
    }

    private int computeOverdueLevel(WorkOrder order, LocalDate scheduled) {
        if (order.getDueDate() == null) return 0;
        long daysLate = ChronoUnit.DAYS.between(order.getDueDate().toLocalDate(), scheduled);
        if (daysLate <= 0) return 0;
        if (daysLate <= 3) return 1;
        if (daysLate <= 7) return 2;
        return 3;
    }

    private ScheduleMetrics computeMetrics(List<ScheduledItem> scheduled,
                                           List<MaintenanceTeam> teams,
                                           Map<Long, Map<LocalDate, Double>> teamDailyLoad,
                                           LocalDate today, LocalDate horizon) {
        ScheduleMetrics m = new ScheduleMetrics();
        Set<String> eqDatePairs = new HashSet<>();
        for (ScheduledItem s : scheduled) {
            eqDatePairs.add(s.equipmentId + "_" + s.scheduledDate);
        }
        m.totalDowntimeDays = eqDatePairs.size();

        List<Double> teamTotals = new ArrayList<>();
        for (MaintenanceTeam t : teams) {
            double total = 0;
            Map<LocalDate, Double> loads = teamDailyLoad.getOrDefault(t.getId(), new HashMap<>());
            for (double v : loads.values()) total += v;
            teamTotals.add(total);
        }
        if (!teamTotals.isEmpty()) {
            double sum = teamTotals.stream().mapToDouble(Double::doubleValue).sum();
            m.avgTeamLoad = sum / teamTotals.size();
            m.maxTeamLoad = teamTotals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double variance = teamTotals.stream()
                    .mapToDouble(v -> (v - m.avgTeamLoad) * (v - m.avgTeamLoad))
                    .average().orElse(0);
            m.teamLoadStdDev = Math.sqrt(variance);
        }
        return m;
    }

    private ScheduleMetrics computeNaiveMetrics(List<WorkOrder> orders,
                                                List<MaintenanceTeam> teams,
                                                LocalDate today) {
        ScheduleMetrics m = new ScheduleMetrics();
        if (teams.isEmpty() || orders.isEmpty()) return m;
        Set<String> eqDatePairs = new HashSet<>();
        Map<Long, Double> teamLoad = new HashMap<>();
        for (MaintenanceTeam t : teams) teamLoad.put(t.getId(), 0.0);
        int teamIdx = 0;
        int dayOffset = 0;
        for (WorkOrder o : orders) {
            LocalDate date = today.plusDays(dayOffset % 7);
            eqDatePairs.add(o.getEquipmentId() + "_" + date);
            MaintenanceTeam t = teams.get(teamIdx % teams.size());
            double hours = 2.0;
            teamLoad.merge(t.getId(), hours, Double::sum);
            teamIdx++;
            dayOffset++;
        }
        m.totalDowntimeDays = eqDatePairs.size();
        List<Double> loads = new ArrayList<>(teamLoad.values());
        double sum = loads.stream().mapToDouble(Double::doubleValue).sum();
        m.avgTeamLoad = sum / Math.max(1, loads.size());
        m.maxTeamLoad = loads.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double variance = loads.stream()
                .mapToDouble(v -> (v - m.avgTeamLoad) * (v - m.avgTeamLoad))
                .average().orElse(0);
        m.teamLoadStdDev = Math.sqrt(variance);
        return m;
    }

    public List<WorkOrderSchedule> getCalendar(LocalDate from, LocalDate to,
                                               Long equipmentId, Long teamId) {
        List<WorkOrderSchedule> all;
        if (equipmentId != null) {
            all = scheduleRepo.findByEquipmentAndDateRange(equipmentId, from, to);
        } else if (teamId != null) {
            all = scheduleRepo.findByTeamAndDateRange(teamId, from, to);
        } else {
            all = scheduleRepo.findByScheduledDateBetween(from, to);
        }
        return all;
    }

    public List<MaintenanceTeam> listTeams() { return teamRepo.findAll(); }

    @Transactional
    public MaintenanceTeam saveTeam(MaintenanceTeam team) { return teamRepo.save(team); }

    public List<DowntimeWindow> listDowntimeWindows(Long equipmentId, String equipmentType) {
        if (equipmentId != null) return downtimeRepo.findByEquipmentId(equipmentId);
        if (equipmentType != null) return downtimeRepo.findByEquipmentType(equipmentType);
        return downtimeRepo.findAll();
    }

    @Transactional
    public DowntimeWindow saveDowntimeWindow(DowntimeWindow w) { return downtimeRepo.save(w); }

    @Transactional
    public void deleteDowntimeWindow(Long id) { downtimeRepo.deleteById(id); }

    @Transactional
    public WorkOrderSchedule reschedule(Long scheduleId, LocalDate newDate, Long newTeamId) {
        WorkOrderSchedule s = scheduleRepo.findById(scheduleId).orElse(null);
        if (s == null) return null;
        s.setScheduledDate(newDate);
        if (newTeamId != null) {
            MaintenanceTeam t = teamRepo.findById(newTeamId).orElse(null);
            if (t != null) {
                s.setTeamId(t.getId());
                s.setTeamName(t.getName());
            }
        }
        s.setSchedulingVersion(s.getSchedulingVersion() + 1);
        s.setUpdatedAt(LocalDateTime.now());
        WorkOrder o = workOrderRepo.findById(s.getWorkOrderId()).orElse(null);
        if (o != null) {
            s.setOverdueLevel(computeOverdueLevel(o, newDate));
            o.setAssignee(s.getTeamName());
            workOrderRepo.save(o);
        }
        return scheduleRepo.save(s);
    }
}
