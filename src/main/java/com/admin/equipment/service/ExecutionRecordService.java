package com.admin.equipment.service;

import com.admin.equipment.model.ExecutionRecord;
import com.admin.equipment.model.WorkOrder;
import com.admin.equipment.repo.ExecutionRecordRepository;
import com.admin.equipment.repo.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExecutionRecordService {

    private final ExecutionRecordRepository recordRepo;
    private final WorkOrderRepository workOrderRepo;

    public ExecutionRecordService(ExecutionRecordRepository recordRepo,
                                  WorkOrderRepository workOrderRepo) {
        this.recordRepo = recordRepo;
        this.workOrderRepo = workOrderRepo;
    }

    public List<ExecutionRecord> getRecordsByWorkOrder(Long workOrderId) {
        return recordRepo.findByWorkOrderIdOrderByIdAsc(workOrderId);
    }

    @Transactional
    public ExecutionRecord updateRecord(Long recordId, String result, Integer actualMinutes,
                                        String executor, String status) {
        ExecutionRecord r = recordRepo.findById(recordId).orElse(null);
        if (r == null) return null;
        if (result != null) r.setResult(result);
        if (actualMinutes != null) r.setActualMinutes(actualMinutes);
        if (executor != null) r.setExecutor(executor);
        if (status != null) r.setStatus(status);
        if ("done".equals(status) || "completed".equals(status)) {
            r.setExecutedAt(LocalDateTime.now());
        }
        return recordRepo.save(r);
    }

    @Transactional
    public ExecutionRecord signRecord(Long recordId, String signedBy) {
        ExecutionRecord r = recordRepo.findById(recordId).orElse(null);
        if (r == null) return null;
        r.setSignedBy(signedBy);
        r.setSignedAt(LocalDateTime.now());
        r.setStatus("signed");
        return recordRepo.save(r);
    }

    @Transactional
    public boolean completeWorkOrderIfAllSigned(Long workOrderId) {
        List<ExecutionRecord> records = recordRepo.findByWorkOrderIdOrderByIdAsc(workOrderId);
        if (records.isEmpty()) return false;
        boolean allSigned = records.stream().allMatch(r -> "signed".equals(r.getStatus()) || "done".equals(r.getStatus()));
        if (allSigned) {
            WorkOrder order = workOrderRepo.findById(workOrderId).orElse(null);
            if (order != null) {
                order.setStatus("done");
                order.setClosedAt(LocalDateTime.now());
                workOrderRepo.save(order);
                return true;
            }
        }
        return false;
    }

    public List<ExecutionRecord> getRecordsByDateRange(LocalDateTime from, LocalDateTime to) {
        return recordRepo.findByExecutedAtBetween(from, to);
    }

    public List<ExecutionRecord> getRecordsByEquipment(Long equipmentId) {
        return recordRepo.findByEquipmentId(equipmentId);
    }
}
