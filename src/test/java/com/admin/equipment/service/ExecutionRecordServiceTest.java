package com.admin.equipment.service;

import com.admin.equipment.model.ExecutionRecord;
import com.admin.equipment.model.WorkOrder;
import com.admin.equipment.repo.ExecutionRecordRepository;
import com.admin.equipment.repo.WorkOrderRepository;
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
class ExecutionRecordServiceTest {

    @Mock ExecutionRecordRepository recordRepo;
    @Mock WorkOrderRepository workOrderRepo;

    @InjectMocks ExecutionRecordService service;

    private ExecutionRecord makeRecord(Long id, Long workOrderId, String status) {
        ExecutionRecord r = new ExecutionRecord();
        r.setId(id);
        r.setWorkOrderId(workOrderId);
        r.setStatus(status);
        r.setResult("");
        r.setExecutor("");
        r.setSignedBy("");
        return r;
    }

    @Nested
    class UpdateRecord {

        @Test
        void updatesFields() {
            ExecutionRecord r = makeRecord(1L, 100L, "pending");

            when(recordRepo.findById(1L)).thenReturn(Optional.of(r));
            when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionRecord result = service.updateRecord(1L, "合格", 60, "张三", "done");

            assertThat(result).isNotNull();
            assertThat(result.getResult()).isEqualTo("合格");
            assertThat(result.getActualMinutes()).isEqualTo(60);
            assertThat(result.getExecutor()).isEqualTo("张三");
            assertThat(result.getStatus()).isEqualTo("done");
            assertThat(result.getExecutedAt()).isNotNull();
        }

        @Test
        void statusCompleted_alsoSetsExecutedAt() {
            ExecutionRecord r = makeRecord(1L, 100L, "pending");

            when(recordRepo.findById(1L)).thenReturn(Optional.of(r));
            when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionRecord result = service.updateRecord(1L, null, null, null, "completed");

            assertThat(result.getExecutedAt()).isNotNull();
        }

        @Test
        void statusNotDone_doesNotSetExecutedAt() {
            ExecutionRecord r = makeRecord(1L, 100L, "pending");

            when(recordRepo.findById(1L)).thenReturn(Optional.of(r));
            when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionRecord result = service.updateRecord(1L, "测试中", null, "李四", "in_progress");

            assertThat(result.getExecutedAt()).isNull();
        }

        @Test
        void nullFields_notUpdated() {
            ExecutionRecord r = makeRecord(1L, 100L, "pending");
            r.setResult("原始结果");
            r.setActualMinutes(30);
            r.setExecutor("原始执行人");

            when(recordRepo.findById(1L)).thenReturn(Optional.of(r));
            when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionRecord result = service.updateRecord(1L, null, null, null, "done");

            assertThat(result.getResult()).isEqualTo("原始结果");
            assertThat(result.getActualMinutes()).isEqualTo(30);
            assertThat(result.getExecutor()).isEqualTo("原始执行人");
        }

        @Test
        void notFound_returnsNull() {
            when(recordRepo.findById(999L)).thenReturn(Optional.empty());

            ExecutionRecord result = service.updateRecord(999L, "结果", 30, "执行人", "done");

            assertThat(result).isNull();
        }
    }

    @Nested
    class SignRecord {

        @Test
        void signsRecord() {
            ExecutionRecord r = makeRecord(1L, 100L, "done");

            when(recordRepo.findById(1L)).thenReturn(Optional.of(r));
            when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionRecord result = service.signRecord(1L, "王五");

            assertThat(result).isNotNull();
            assertThat(result.getSignedBy()).isEqualTo("王五");
            assertThat(result.getSignedAt()).isNotNull();
            assertThat(result.getStatus()).isEqualTo("signed");
        }

        @Test
        void notFound_returnsNull() {
            when(recordRepo.findById(999L)).thenReturn(Optional.empty());

            ExecutionRecord result = service.signRecord(999L, "王五");

            assertThat(result).isNull();
        }
    }

    @Nested
    class CompleteWorkOrderIfAllSigned {

        @Test
        void allSigned_completesWorkOrder() {
            ExecutionRecord r1 = makeRecord(1L, 100L, "signed");
            ExecutionRecord r2 = makeRecord(2L, 100L, "done");

            WorkOrder order = new WorkOrder();
            order.setId(100L);
            order.setStatus("in_progress");

            when(recordRepo.findByWorkOrderIdOrderByIdAsc(100L)).thenReturn(List.of(r1, r2));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean result = service.completeWorkOrderIfAllSigned(100L);

            assertThat(result).isTrue();
            assertThat(order.getStatus()).isEqualTo("done");
            assertThat(order.getClosedAt()).isNotNull();
        }

        @Test
        void notAllSigned_returnsFalse() {
            ExecutionRecord r1 = makeRecord(1L, 100L, "signed");
            ExecutionRecord r2 = makeRecord(2L, 100L, "pending");

            when(recordRepo.findByWorkOrderIdOrderByIdAsc(100L)).thenReturn(List.of(r1, r2));

            boolean result = service.completeWorkOrderIfAllSigned(100L);

            assertThat(result).isFalse();
            verify(workOrderRepo, never()).save(any());
        }

        @Test
        void emptyRecords_returnsFalse() {
            when(recordRepo.findByWorkOrderIdOrderByIdAsc(100L)).thenReturn(List.of());

            boolean result = service.completeWorkOrderIfAllSigned(100L);

            assertThat(result).isFalse();
        }

        @Test
        void workOrderNotFound_returnsFalse() {
            ExecutionRecord r1 = makeRecord(1L, 100L, "signed");

            when(recordRepo.findByWorkOrderIdOrderByIdAsc(100L)).thenReturn(List.of(r1));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.empty());

            boolean result = service.completeWorkOrderIfAllSigned(100L);

            assertThat(result).isFalse();
        }

        @Test
        void allDoneStatus_completesWorkOrder() {
            ExecutionRecord r1 = makeRecord(1L, 100L, "done");
            ExecutionRecord r2 = makeRecord(2L, 100L, "done");

            WorkOrder order = new WorkOrder();
            order.setId(100L);
            order.setStatus("in_progress");

            when(recordRepo.findByWorkOrderIdOrderByIdAsc(100L)).thenReturn(List.of(r1, r2));
            when(workOrderRepo.findById(100L)).thenReturn(Optional.of(order));
            when(workOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean result = service.completeWorkOrderIfAllSigned(100L);

            assertThat(result).isTrue();
        }
    }

    @Nested
    class GetRecords {

        @Test
        void byWorkOrder() {
            ExecutionRecord r = makeRecord(1L, 100L, "pending");
            when(recordRepo.findByWorkOrderIdOrderByIdAsc(100L)).thenReturn(List.of(r));

            var result = service.getRecordsByWorkOrder(100L);

            assertThat(result).hasSize(1);
        }

        @Test
        void byDateRange() {
            LocalDateTime from = LocalDateTime.now().minusDays(7);
            LocalDateTime to = LocalDateTime.now();
            when(recordRepo.findByExecutedAtBetween(from, to)).thenReturn(List.of());

            var result = service.getRecordsByDateRange(from, to);

            assertThat(result).isEmpty();
        }

        @Test
        void byEquipment() {
            when(recordRepo.findByEquipmentId(10L)).thenReturn(List.of());

            var result = service.getRecordsByEquipment(10L);

            assertThat(result).isEmpty();
        }
    }
}
