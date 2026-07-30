package com.smartmed.backend.schedule.controller;

import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.schedule.dto.CopyWeekRequest;
import com.smartmed.backend.schedule.dto.CopyWeekResult;
import com.smartmed.backend.schedule.dto.ScheduleRequest;
import com.smartmed.backend.schedule.dto.ScheduleVO;
import com.smartmed.backend.schedule.dto.SlotAdjustRequest;
import com.smartmed.backend.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 排班管理接口（仅 ADMIN，04 ticket ADR-0009）。
 * <ul>
 *   <li>GET    /api/b/schedules              分页（departmentId?/doctorId?/date?）</li>
 *   <li>GET    /api/b/schedules/{id}         详情</li>
 *   <li>POST   /api/b/schedules              创建（即发布即写 Redis）</li>
 *   <li>PUT    /api/b/schedules/{id}         修改（全字段可编辑）</li>
 *   <li>DELETE /api/b/schedules/{id}         删除（有挂号 409）</li>
 *   <li>PATCH  /api/b/schedules/{id}/suspend 停诊</li>
 *   <li>PATCH  /api/b/schedules/{id}/resume  恢复</li>
 *   <li>PATCH  /api/b/schedules/{id}/slots   手动调整余量</li>
 *   <li>POST   /api/b/schedules/copy-week    周复制</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/b/schedules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public Result<PageResponse<ScheduleVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(scheduleService.page(pageNum, pageSize, departmentId, doctorId, date));
    }

    @GetMapping("/{id}")
    public Result<ScheduleVO> getById(@PathVariable Long id) {
        return Result.success(scheduleService.getById(id));
    }

    @PostMapping
    public Result<ScheduleVO> create(@Valid @RequestBody ScheduleRequest req) {
        return Result.success(scheduleService.create(req));
    }

    @PutMapping("/{id}")
    public Result<ScheduleVO> update(@PathVariable Long id, @Valid @RequestBody ScheduleRequest req) {
        return Result.success(scheduleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success();
    }

    @PatchMapping("/{id}/suspend")
    public Result<ScheduleVO> suspend(@PathVariable Long id) {
        return Result.success(scheduleService.suspend(id));
    }

    @PatchMapping("/{id}/resume")
    public Result<ScheduleVO> resume(@PathVariable Long id) {
        return Result.success(scheduleService.resume(id));
    }

    @PatchMapping("/{id}/slots")
    public Result<ScheduleVO> adjustSlots(@PathVariable Long id, @Valid @RequestBody SlotAdjustRequest req) {
        return Result.success(scheduleService.adjustSlots(id, req.getDelta()));
    }

    @PostMapping("/copy-week")
    public Result<CopyWeekResult> copyWeek(@Valid @RequestBody CopyWeekRequest req) {
        return Result.success(scheduleService.copyWeek(req));
    }
}
