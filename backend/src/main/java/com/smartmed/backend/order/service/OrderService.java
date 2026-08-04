package com.smartmed.backend.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.drug.mapper.DrugPharmacyStockMapper;
import com.smartmed.backend.order.dto.DrugOrderVO;
import com.smartmed.backend.order.entity.DrugOrder;
import com.smartmed.backend.order.entity.MedicationReminder;
import com.smartmed.backend.order.mapper.DrugOrderMapper;
import com.smartmed.backend.order.mapper.MedicationReminderMapper;
import com.smartmed.backend.order.mapper.PharmacyMapper;
import com.smartmed.backend.prescription.entity.PrescriptionItem;
import com.smartmed.backend.prescription.mapper.PrescriptionItemMapper;
import com.smartmed.backend.prescription.mapper.PrescriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购药业务服务（14 ticket）：
 * 两段式：创建草稿 -> 确认扣库存。
 * <p>
 * 草稿存 Redis（TTL 30min），确认事务扣库存 + 写 drug_order + 自动生成 medication_reminder。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final DrugOrderMapper drugOrderMapper;
    private final MedicationReminderMapper reminderMapper;
    private final DrugPharmacyStockMapper stockMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final PharmacyMapper pharmacyMapper;
    private final OrderRedisService redisService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${smartmed.jwt.secret}")
    private String jwtSecret;

    // ==================== 创建草稿 ====================

    public Map<String, Object> createOrderDraft(Long patientId, Long prescriptionId, Long pharmacyId,
                                                 String deliveryInfo) {
        // 1. 校验药品库存（乐观检查，confirm 时最终扣减）
        // 获取处方明细，计算总价
        List<PrescriptionItem> items = prescriptionItemMapper.selectList(
                new LambdaQueryWrapper<PrescriptionItem>()
                        .eq(PrescriptionItem::getPrescriptionId, prescriptionId));
        if (items.isEmpty()) {
            throw new BusinessException(400, "处方明细为空");
        }

        // 检查每家药品库存是否充足
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PrescriptionItem item : items) {
            // 查询该药品在该药店的价格
            var stockList = stockMapper.selectByDrugId(item.getDrugId());
            var pharmacyStock = stockList.stream()
                    .filter(s -> s.getPharmacyId().equals(pharmacyId))
                    .findFirst();
            if (pharmacyStock.isEmpty()) {
                throw new BusinessException(400, "药品 " + item.getDrugId() + " 在该药店无库存");
            }
            if (pharmacyStock.get().getStock() <= 0) {
                throw new BusinessException(400, "药品 " + item.getDrugId() + " 库存不足");
            }
            totalAmount = totalAmount.add(pharmacyStock.get().getPrice());
        }

        // 2. 获取药店名称
        var pharmacy = pharmacyMapper.selectById(pharmacyId);
        String pharmacyName = pharmacy != null ? pharmacy.getName() : "";

        // 3. 生成 confirmToken
        long createdAtMillis = clock.millis();
        String confirmToken = redisService.generateConfirmToken(patientId, prescriptionId, pharmacyId,
                createdAtMillis, jwtSecret);

        // 4. 构建草稿
        String draftKey = redisService.buildDraftKey(patientId, prescriptionId);
        Map<String, Object> draftValue = new HashMap<>();
        draftValue.put("prescriptionId", prescriptionId);
        draftValue.put("pharmacyId", pharmacyId);
        draftValue.put("pharmacyName", pharmacyName);
        draftValue.put("totalAmount", totalAmount);
        draftValue.put("deliveryInfo", deliveryInfo);
        draftValue.put("createdAt", createdAtMillis);
        draftValue.put("confirmToken", confirmToken);

        try {
            redisService.saveDraft(draftKey, objectMapper.writeValueAsString(draftValue));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("草稿序列化失败", e);
        }

        // 5. 构建返回
        Map<String, Object> result = new HashMap<>();
        result.put("draftKey", draftKey);
        result.put("confirmToken", confirmToken);
        result.put("prescriptionId", prescriptionId);
        result.put("pharmacyId", pharmacyId);
        result.put("pharmacyName", pharmacyName);
        result.put("totalAmount", totalAmount);
        result.put("items", items.stream().map(i -> {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("drugId", i.getDrugId());
            itemMap.put("dosage", i.getDosage());
            itemMap.put("frequency", i.getFrequency());
            return itemMap;
        }).toList());
        return result;
    }

    // ==================== 确认订单 ====================

    @Transactional
    public DrugOrderVO confirmOrder(Long patientId, String draftKey, String confirmToken,
                                     Long prescriptionId, Long pharmacyId) {
        // 1. 消费草稿（读取 + 删除）
        String draftJson = redisService.getDraft(draftKey);
        if (draftJson == null) {
            throw new BusinessException(400, "草稿不存在或已过期，请重新选择药店");
        }
        redisService.deleteDraft(draftKey);

        // 2. 验证 confirmToken
        Map<String, Object> draft;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(draftJson, Map.class);
            draft = parsed;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("草稿反序列化失败", e);
        }
        String storedToken = (String) draft.get("confirmToken");
        if (!confirmToken.equals(storedToken)) {
            throw new BusinessException(400, "无效的确认令牌");
        }

        // 3. 获取处方明细，逐项扣减库存
        List<PrescriptionItem> items = prescriptionItemMapper.selectList(
                new LambdaQueryWrapper<PrescriptionItem>()
                        .eq(PrescriptionItem::getPrescriptionId, prescriptionId));
        if (items.isEmpty()) {
            throw new BusinessException(400, "处方明细为空");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PrescriptionItem item : items) {
            int affected = stockMapper.deductStock(item.getDrugId(), pharmacyId);
            if (affected == 0) {
                throw new BusinessException(400, "药品库存不足，请重试");
            }
            // 查询该药品在该药店的价格用于累加
            var stockList = stockMapper.selectByDrugId(item.getDrugId());
            var pharmacyStock = stockList.stream()
                    .filter(s -> s.getPharmacyId().equals(pharmacyId))
                    .findFirst();
            if (pharmacyStock.isPresent()) {
                totalAmount = totalAmount.add(pharmacyStock.get().getPrice());
            }
        }

        // 4. 写入 drug_order
        DrugOrder order = new DrugOrder();
        order.setPatientId(patientId);
        order.setPrescriptionId(prescriptionId);
        order.setPharmacyId(pharmacyId);
        order.setTotalAmount(totalAmount);
        order.setStatus("PENDING");
        order.setDeliveryInfo((String) draft.get("deliveryInfo"));
        drugOrderMapper.insert(order);

        // 5. 自动生成 medication_reminder（遍历处方明细，按 frequency 关键词匹配）
        for (PrescriptionItem item : items) {
            List<LocalTime> remindTimes = parseFrequency(item.getFrequency());
            for (LocalTime time : remindTimes) {
                MedicationReminder reminder = new MedicationReminder();
                reminder.setPatientId(patientId);
                reminder.setPrescriptionId(prescriptionId);
                reminder.setDrugId(item.getDrugId());
                // 今天该时间点，如果已过则明天
                LocalTime now = LocalTime.now(clock);
                LocalDate targetDate = time.isBefore(now) ? LocalDate.now(clock).plusDays(1) : LocalDate.now(clock);
                OffsetDateTime nextRemindAt = ZonedDateTime.of(targetDate, time, clock.getZone()).toOffsetDateTime();
                reminder.setNextRemindAt(nextRemindAt);
                reminder.setFrequency(item.getFrequency());
                reminder.setDosage(item.getDosage());
                reminder.setRemark(item.getRemark());
                reminder.setStatus("ACTIVE");
                reminderMapper.insert(reminder);
            }
        }

        // 6. 构建 DrugOrderVO
        String pharmacyName = (String) draft.get("pharmacyName");
        var pharmacy = pharmacyMapper.selectById(pharmacyId);
        return DrugOrderVO.builder()
                .id(order.getId())
                .patientId(patientId)
                .prescriptionId(prescriptionId)
                .pharmacyId(pharmacyId)
                .pharmacyName(pharmacyName)
                .pharmacyAddress(pharmacy != null ? pharmacy.getAddress() : null)
                .totalAmount(totalAmount)
                .status("PENDING")
                .deliveryInfo((String) draft.get("deliveryInfo"))
                .createdAt(order.getCreatedAt())
                .build();
    }

    // ==================== 频率解析 ====================

    /**
     * 简单关键词匹配频率。
     * "1次" -> 8:00
     * "2次" -> 8:00, 18:00
     * "3次" -> 8:00, 12:00, 18:00
     * 无法匹配 -> 默认 08:00 + frequency 原文保留
     */
    static List<LocalTime> parseFrequency(String frequency) {
        if (frequency == null || frequency.isBlank()) {
            return List.of(LocalTime.of(8, 0));
        }
        String clean = frequency.replaceAll("\\s+", "");
        if (clean.contains("1次") || clean.contains("每日1次") || clean.contains("一天1次")) {
            return List.of(LocalTime.of(8, 0));
        }
        if (clean.contains("2次") || clean.contains("每日2次") || clean.contains("一天2次")) {
            return List.of(LocalTime.of(8, 0), LocalTime.of(18, 0));
        }
        if (clean.contains("3次") || clean.contains("每日3次") || clean.contains("一天3次")) {
            return List.of(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(18, 0));
        }
        // 无法匹配 -> 默认 08:00
        return List.of(LocalTime.of(8, 0));
    }
}
