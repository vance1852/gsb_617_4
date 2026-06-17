package com.admin.equipment.seed;

import com.admin.equipment.model.*;
import com.admin.equipment.repo.*;
import com.admin.equipment.security.PasswordUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 启动时初始化管理员与种子业务数据（幂等）。 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final AppUserRepository userRepo;
    private final EquipmentRepository equipmentRepo;
    private final WorkOrderRepository workOrderRepo;
    private final MaintenancePlanRepository planRepo;
    private final MaintenanceItemRepository itemRepo;
    private final MaintenanceTeamRepository teamRepo;
    private final DowntimeWindowRepository downtimeRepo;
    private final RunningLogRepository runningLogRepo;

    @Value("${app.admin-username}")
    private String adminUsername;

    @Value("${app.admin-password}")
    private String adminPassword;

    public DataSeeder(AppUserRepository userRepo, EquipmentRepository equipmentRepo,
                      WorkOrderRepository workOrderRepo, MaintenancePlanRepository planRepo,
                      MaintenanceItemRepository itemRepo, MaintenanceTeamRepository teamRepo,
                      DowntimeWindowRepository downtimeRepo, RunningLogRepository runningLogRepo) {
        this.userRepo = userRepo;
        this.equipmentRepo = equipmentRepo;
        this.workOrderRepo = workOrderRepo;
        this.planRepo = planRepo;
        this.itemRepo = itemRepo;
        this.teamRepo = teamRepo;
        this.downtimeRepo = downtimeRepo;
        this.runningLogRepo = runningLogRepo;
    }

    @Override
    public void run(String... args) {
        if (!userRepo.existsByUsername(adminUsername)) {
            AppUser admin = new AppUser();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(PasswordUtil.hash(adminPassword));
            admin.setDisplayName("平台管理员");
            userRepo.save(admin);
            System.out.println("已创建管理员账号");
        }

        boolean equipmentExisted = equipmentRepo.count() > 0;
        List<Equipment> equipments;
        if (!equipmentExisted) {
            Equipment e1 = newEquip("EQ-1001", "一号注塑机", "注塑车间A区", "robot", "normal");
            Equipment e2 = newEquip("EQ-1002", "二号空压机", "动力站", "pump", "warning");
            Equipment e3 = newEquip("EQ-1003", "主输送带", "包装车间", "conveyor", "normal");
            Equipment e4 = newEquip("EQ-1004", "冷却循环水泵", "动力站", "pump", "normal");
            Equipment e5 = newEquip("EQ-1005", "二号注塑机", "注塑车间A区", "robot", "normal");
            Equipment e6 = newEquip("EQ-1006", "伺服电机组", "装配车间", "motor", "normal");
            Equipment e7 = newEquip("EQ-1007", "辅助输送带", "包装车间", "conveyor", "normal");
            Equipment e8 = newEquip("EQ-1008", "高压油泵站", "动力站", "pump", "normal");
            equipments = equipmentRepo.saveAll(List.of(e1, e2, e3, e4, e5, e6, e7, e8));

            workOrderRepo.saveAll(List.of(
                    newOrder(e2.getId(), "空压机压力异常巡检", "inspection", "high", "巡检发现排气压力波动，需排查", "王工", "open"),
                    newOrder(e4.getId(), "循环水泵季度保养", "maintenance", "medium", "按计划做季度保养换油", "张工", "open"),
                    newOrder(e1.getId(), "注塑机模具点检", "inspection", "low", "例行模具与液压点检", "赵工", "done")
            ));
        } else {
            equipments = equipmentRepo.findAll();
        }

        if (teamRepo.count() == 0) {
            MaintenanceTeam t1 = new MaintenanceTeam();
            t1.setName("甲班");
            t1.setDailyCapacityHours(16.0);
            t1.setMembers("王工,李工,张工");
            t1.setSkills("液压,机械,电气");
            MaintenanceTeam t2 = new MaintenanceTeam();
            t2.setName("乙班");
            t2.setDailyCapacityHours(16.0);
            t2.setMembers("赵工,陈工,刘工");
            t2.setSkills("机械,电气,自控");
            MaintenanceTeam t3 = new MaintenanceTeam();
            t3.setName("丙班");
            t3.setDailyCapacityHours(12.0);
            t3.setMembers("孙工,周工");
            t3.setSkills("液压,气动");
            teamRepo.saveAll(List.of(t1, t2, t3));
            System.out.println("已初始化维保班组数据");
        }

        if (downtimeRepo.count() == 0) {
            for (Equipment e : equipments) {
                for (int dow = 1; dow <= 5; dow++) {
                    DowntimeWindow w = new DowntimeWindow();
                    w.setEquipmentId(e.getId());
                    w.setDayOfWeek(dow);
                    w.setStartMinute(8 * 60);
                    w.setEndMinute(18 * 60);
                    w.setEffectiveFrom(LocalDate.now().minusMonths(1));
                    w.setEffectiveTo(LocalDate.now().plusYears(1));
                    downtimeRepo.save(w);
                }
            }
            System.out.println("已初始化设备停机窗口数据");
        }

        if (planRepo.count() == 0) {
            Equipment e1 = equipments.stream().filter(e -> "EQ-1001".equals(e.getCode())).findFirst().orElse(null);
            Equipment e2 = equipments.stream().filter(e -> "EQ-1002".equals(e.getCode())).findFirst().orElse(null);
            Equipment e3 = equipments.stream().filter(e -> "EQ-1003".equals(e.getCode())).findFirst().orElse(null);
            Equipment e6 = equipments.stream().filter(e -> "EQ-1006".equals(e.getCode())).findFirst().orElse(null);

            if (e1 != null) {
                MaintenancePlan p1 = new MaintenancePlan();
                p1.setName("注塑机月度保养");
                p1.setDescription("液压系统检查、润滑油更换、模具清洁");
                p1.setEquipmentId(e1.getId());
                p1.setTriggerType("TIME_BASED");
                p1.setCycleDays(30);
                p1.setAdvanceNoticeDays(3);
                p1.setStandardWorkHours(4.0);
                p1.setRequiredSkill("液压");
                p1.setPriority("medium");
                p1.setEnabled(true);
                p1.setLastTriggeredAt(LocalDateTime.now().minusDays(32));
                p1.setNextTriggerAt(LocalDateTime.now().minusDays(2));
                planRepo.save(p1);
                savePlanItems(p1.getId(), List.of(
                        newItem("液压油位检查", "检查油箱油位，不足则补充", 1, 15, "液压", "液压油46#"),
                        newItem("滤芯更换", "更换吸油、回油滤芯", 2, 45, "液压", "液压滤芯套装"),
                        newItem("模具清洁与检查", "清洁模腔、检查导柱导套", 3, 60, "机械", "脱模剂、无尘布"),
                        newItem("紧固螺栓检查", "检查并紧固关键连接螺栓", 4, 30, "机械", "扭矩扳手")
                ));
            }

            if (e2 != null) {
                MaintenancePlan p2 = new MaintenancePlan();
                p2.setName("空压机每500小时保养");
                p2.setDescription("运行累计500小时后进行润滑油更换、三滤检查");
                p2.setEquipmentId(e2.getId());
                p2.setTriggerType("USAGE_BASED");
                p2.setCycleHours(500);
                p2.setAdvanceNoticeDays(5);
                p2.setStandardWorkHours(2.5);
                p2.setRequiredSkill("机械");
                p2.setPriority("high");
                p2.setEnabled(true);
                p2.setLastTriggerRunningHours(0.0);
                p2.setLastTriggerRunningCycles(0L);
                planRepo.save(p2);
                savePlanItems(p2.getId(), List.of(
                        newItem("润滑油更换", "排净旧油，更换空压机专用润滑油", 1, 40, "机械", "空压机专用润滑油"),
                        newItem("空气滤芯检查", "检查或更换空气过滤芯", 2, 20, "机械", "空气滤芯"),
                        newItem("油气分离器检查", "检查压差，必要时更换", 3, 30, "机械", "油气分离滤芯"),
                        newItem("皮带张力检查", "检查并调整皮带张紧度", 4, 15, "机械", "")
                ));
            }

            if (e3 != null) {
                MaintenancePlan p3 = new MaintenancePlan();
                p3.setName("输送带每周巡检保养");
                p3.setDescription("皮带张紧度、托辊转动、减速机油位");
                p3.setEquipmentId(e3.getId());
                p3.setTriggerType("TIME_BASED");
                p3.setCycleDays(7);
                p3.setAdvanceNoticeDays(1);
                p3.setStandardWorkHours(1.5);
                p3.setRequiredSkill("机械");
                p3.setPriority("low");
                p3.setEnabled(true);
                p3.setLastTriggeredAt(LocalDateTime.now().minusDays(8));
                p3.setNextTriggerAt(LocalDateTime.now().minusDays(1));
                planRepo.save(p3);
                savePlanItems(p3.getId(), List.of(
                        newItem("皮带张紧度检查", "检查上下皮带张紧度并调整", 1, 20, "机械", ""),
                        newItem("托辊检查", "检查所有托辊转动是否灵活", 2, 30, "机械", ""),
                        newItem("减速机检查", "检查油位及运行声音", 3, 15, "机械", "齿轮油")
                ));
            }

            if (e6 != null) {
                MaintenancePlan p4 = new MaintenancePlan();
                p4.setName("伺服电机季度保养");
                p4.setDescription("编码器清洁、轴承润滑、连接检查");
                p4.setEquipmentId(e6.getId());
                p4.setTriggerType("TIME_BASED");
                p4.setCycleMonths(3);
                p4.setAdvanceNoticeDays(7);
                p4.setStandardWorkHours(3.0);
                p4.setRequiredSkill("电气");
                p4.setPriority("medium");
                p4.setEnabled(true);
                p4.setLastTriggeredAt(LocalDateTime.now().minusMonths(4));
                p4.setNextTriggerAt(LocalDateTime.now().plusDays(5));
                planRepo.save(p4);
                savePlanItems(p4.getId(), List.of(
                        newItem("编码器清洁", "清洁编码器光栅，检查信号线", 1, 30, "电气", "无水乙醇、棉签"),
                        newItem("轴承润滑", "前后轴承补充润滑脂", 2, 40, "机械", "锂基润滑脂"),
                        newItem("接线端子检查", "检查动力线与编码器线连接", 3, 20, "电气", "")
                ));
            }

            MaintenancePlan p5 = new MaintenancePlan();
            p5.setName("泵类设备月度检查");
            p5.setDescription("所有泵类设备统一检查计划：振动、温度、密封");
            p5.setEquipmentType("pump");
            p5.setTriggerType("TIME_BASED");
            p5.setCycleDays(30);
            p5.setAdvanceNoticeDays(3);
            p5.setStandardWorkHours(1.0);
            p5.setRequiredSkill("机械");
            p5.setPriority("medium");
            p5.setEnabled(true);
            p5.setLastTriggeredAt(LocalDateTime.now().minusDays(31));
            p5.setNextTriggerAt(LocalDateTime.now().minusDays(1));
            planRepo.save(p5);
            savePlanItems(p5.getId(), List.of(
                    newItem("运行振动检测", "测振仪检测泵体振动", 1, 15, "机械", ""),
                    newItem("温度检测", "红外测温检测轴承及电机温度", 2, 10, "机械", ""),
                    newItem("密封检查", "检查轴封是否渗漏", 3, 15, "机械", "")
            ));

            if (e1 != null) {
                MaintenancePlan p6 = new MaintenancePlan();
                p6.setName("注塑机每10000模次保养");
                p6.setDescription("每运行10000模次后进行深度保养");
                p6.setEquipmentId(e1.getId());
                p6.setTriggerType("USAGE_BASED");
                p6.setCycleCycles(10000);
                p6.setAdvanceNoticeDays(3);
                p6.setStandardWorkHours(6.0);
                p6.setRequiredSkill("液压");
                p6.setPriority("high");
                p6.setEnabled(true);
                p6.setLastTriggerRunningHours(0.0);
                p6.setLastTriggerRunningCycles(0L);
                planRepo.save(p6);
                savePlanItems(p6.getId(), List.of(
                        newItem("液压系统深度检查", "检查阀组、蓄能器、冷却器", 1, 90, "液压", ""),
                        newItem("曲臂润滑", "大杠、曲臂部位润滑脂加注", 2, 45, "机械", "高温润滑脂"),
                        newItem("模板平行度校准", "检查并校准动定模板平行度", 3, 60, "机械", ""),
                        newItem("注射系统检查", "检查料筒、螺杆磨损情况", 4, 60, "机械", "")
                ));
            }
            System.out.println("已初始化维保计划与保养项数据");
        }

        if (runningLogRepo.count() == 0) {
            List<RunningLog> logs = new ArrayList<>();
            for (Equipment e : equipments) {
                for (int i = 30; i >= 0; i--) {
                    RunningLog log = new RunningLog();
                    log.setEquipmentId(e.getId());
                    double hours = 8 + Math.random() * 6;
                    log.setRunningHours(Math.round(hours * 10) / 10.0);
                    log.setRunningCycles("robot".equals(e.getType()) ? Math.round(500 + Math.random() * 300) :
                            ("conveyor".equals(e.getType()) ? 0L : Math.round(50 + Math.random() * 50)));
                    log.setRecordedAt(LocalDateTime.now().minusDays(i).minusHours((long)(Math.random() * 4)));
                    log.setRemark(i == 0 ? "今日运行记录" : "日常运行");
                    logs.add(log);
                }
            }
            runningLogRepo.saveAll(logs);
            System.out.println("已初始化设备运行量历史数据");
        }

        if (!equipmentExisted) {
            System.out.println("种子数据初始化完成");
        }
    }

    private void savePlanItems(Long planId, List<MaintenanceItem> items) {
        for (MaintenanceItem item : items) {
            item.setPlanId(planId);
            itemRepo.save(item);
        }
    }

    private MaintenanceItem newItem(String name, String desc, int seq, int minutes, String skill, String parts) {
        MaintenanceItem it = new MaintenanceItem();
        it.setName(name);
        it.setDescription(desc);
        it.setSequenceNum(seq);
        it.setStandardMinutes(minutes);
        it.setRequiredSkill(skill);
        it.setRequiredSpareParts(parts);
        return it;
    }

    private Equipment newEquip(String code, String name, String location, String type, String status) {
        Equipment e = new Equipment();
        e.setCode(code);
        e.setName(name);
        e.setLocation(location);
        e.setType(type);
        e.setStatus(status);
        return e;
    }

    private WorkOrder newOrder(Long equipmentId, String title, String type, String priority,
                               String description, String assignee, String status) {
        WorkOrder w = new WorkOrder();
        w.setEquipmentId(equipmentId);
        w.setTitle(title);
        w.setType(type);
        w.setPriority(priority);
        w.setDescription(description);
        w.setAssignee(assignee);
        w.setStatus(status);
        w.setIsPreventive(false);
        return w;
    }
}
