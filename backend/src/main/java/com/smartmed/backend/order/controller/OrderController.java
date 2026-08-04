package com.smartmed.backend.order.controller;

import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.order.dto.DrugOrderVO;
import com.smartmed.backend.order.service.OrderService;
import com.smartmed.backend.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端购药订单确认接口（14 ticket）。
 * <p>
 * POST /api/c/orders/confirm — 确认购药订单（消费草稿 -> 扣库存 -> 写订单 -> 自动生成用药提醒）。
 */
@RestController
@RequestMapping("/api/c/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 确认购药订单。
     * Body: { draftKey, confirmToken, prescriptionId, pharmacyId }
     */
    @PostMapping("/confirm")
    public Result<DrugOrderVO> confirm(@RequestBody Map<String, Object> body) {
        String draftKey = (String) body.get("draftKey");
        String confirmToken = (String) body.get("confirmToken");
        Long prescriptionId = extractLong(body, "prescriptionId");
        Long pharmacyId = extractLong(body, "pharmacyId");

        if (draftKey == null || confirmToken == null || prescriptionId == null || pharmacyId == null) {
            throw new BusinessException(400, "缺少必要参数: draftKey, confirmToken, prescriptionId, pharmacyId");
        }

        Long patientId = SecurityUtil.current().getPatientId();
        if (patientId == null) {
            throw new BusinessException(401, "未登录");
        }

        DrugOrderVO vo = orderService.confirmOrder(patientId, draftKey, confirmToken, prescriptionId, pharmacyId);
        return Result.success(vo);
    }

    private static Long extractLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
