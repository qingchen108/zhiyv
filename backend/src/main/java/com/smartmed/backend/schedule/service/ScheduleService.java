package com.smartmed.backend.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.department.entity.Department;
import com.smartmed.backend.department.mapper.DepartmentMapper;
import com.smartmed.backend.doctor.entity.Doctor;
import com.smartmed.backend.doctor.mapper.DoctorMapper;
import com.smartmed.backend.schedule.dto.CopyWeekRequest;
import com.smartmed.backend.schedule.dto.CopyWeekResult;
import com.smartmed.backend.schedule.dto.ScheduleRequest;
import com.smartmed.backend.schedule.dto.ScheduleVO;
import com.smartmed.backend.schedule.entity.Schedule;
import com.smartmed.backend.schedule.entity.TimePeriod;
import com.smartmed.backend.schedule.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 排班服务（04 ticket，ADR-0009）。
 * <p>
 * 核心规则：
 * <ul>
 *   <li>创建即发布即写 Redis</li>
 *   <li>UNIQUE(doctor_id, schedule_date, time_period) 冲突校验</li>
 *   <li>日期窗口 [today, today+14]</li>
 *   <li>有挂号引用不可删除，仅可停诊</li>
 *   <li>修改 total 不得少于已用数</li>
 *   <li>周复制跳过已有，remaining = total</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final int MAX_ADVANCE_DAYS = 14;

    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleRedisService redisService;

    // ==================== CRUD ====================

    /** 分页查询（支持 departmentId / doctorId / date 筛选）。 */
    public PageResponse<ScheduleVO> page(long pageNum, long pageSize,
                                         Long departmentId, Long doctorId, LocalDate date) {
        Page<Schedule> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Schedule> qw = new LambdaQueryWrapper<Schedule>()
                .eq(departmentId != null, Schedule::getDepartmentId, departmentId)
                .eq(doctorId != null, Schedule::getDoctorId, doctorId)
                .eq(date != null, Schedule::getScheduleDate, date)
                .orderByAsc(Schedule::getScheduleDate)
                .orderByAsc(Schedule::getStartTime);
        scheduleMapper.selectPage(page, qw);
        return PageResponse.of(page.convert(this::toVO));
    }

    public ScheduleVO getById(Long id) {
        Schedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "排班不存在");
        }
        return toVO(s);
    }

    @Transactional
    public ScheduleVO create(ScheduleRequest req) {
        TimePeriod period = parseTimePeriod(req.getTimePeriod());
        validateDateWindow(req.getScheduleDate());
        validateDoctorExists(req.getDoctorId());
        validateDepartmentExists(req.getDepartmentId());
        validateConflict(req.getDoctorId(), req.getScheduleDate(), period, null);

        Schedule s = new Schedule();
        s.setDoctorId(req.getDoctorId());
        s.setDepartmentId(req.getDepartmentId());
        s.setScheduleDate(req.getScheduleDate());
        s.setTimePeriod(period.name());
        s.setStartTime(period.getStartTime());
        s.setEndTime(period.getEndTime());
        s.setTotalSlots(req.getTotalSlots());
        s.setRemainingSlots(req.getTotalSlots());
        s.setStatus("PUBLISHED");
        scheduleMapper.insert(s);

        // 创建即发布即写 Redis
        redisService.syncSlots(s.getId(), s.getRemainingSlots());
        return toVO(s);
    }

    @Transactional
    public ScheduleVO update(Long id, ScheduleRequest req) {
        Schedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "排班不存在");
        }
        TimePeriod period = parseTimePeriod(req.getTimePeriod());
        validateDateWindow(req.getScheduleDate());
        validateDoctorExists(req.getDoctorId());
        validateDepartmentExists(req.getDepartmentId());
        validateConflict(req.getDoctorId(), req.getScheduleDate(), period, id);

        // 修改 total 不得少于已用数
        int used = s.getTotalSlots() - s.getRemainingSlots();
        if (used < 0) used = 0; // remaining > total 时（加号场景）已用视为 0
        if (req.getTotalSlots() < used) {
            throw new BusinessException(400, "号源总数不得少于已用数（" + used + "）");
        }

        s.setDoctorId(req.getDoctorId());
        s.setDepartmentId(req.getDepartmentId());
        s.setScheduleDate(req.getScheduleDate());
        s.setTimePeriod(period.name());
        s.setStartTime(period.getStartTime());
        s.setEndTime(period.getEndTime());
        // 调整 total 时 remaining 同步变化（保持已用数不变）
        int newRemaining = req.getTotalSlots() - used;
        s.setTotalSlots(req.getTotalSlots());
        s.setRemainingSlots(newRemaining);
        scheduleMapper.updateById(s);

        // PUBLISHED 状态同步 Redis
        if ("PUBLISHED".equals(s.getStatus())) {
            redisService.syncSlots(s.getId(), s.getRemainingSlots());
        }
        return toVO(s);
    }

    @Transactional
    public void delete(Long id) {
        Schedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "排班不存在");
        }
        // 前置引用检查（ADR-0006 / ADR-0009）
        if (scheduleMapper.countRegistrationByScheduleId(id) > 0) {
            throw new BusinessException(409, "该排班已有挂号记录，无法删除，仅可停诊");
        }
        scheduleMapper.deleteById(id);
        redisService.removeSlots(id);
    }

    // ==================== 停诊 / 恢复 ====================

    @Transactional
    public ScheduleVO suspend(Long id) {
        Schedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "排班不存在");
        }
        if ("SUSPENDED".equals(s.getStatus())) {
            throw new BusinessException(400, "该排班已停诊");
        }
        s.setStatus("SUSPENDED");
        scheduleMapper.updateById(s);
        redisService.removeSlots(id);
        return toVO(s);
    }

    @Transactional
    public ScheduleVO resume(Long id) {
        Schedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "排班不存在");
        }
        if ("PUBLISHED".equals(s.getStatus())) {
            throw new BusinessException(400, "该排班未停诊");
        }
        s.setStatus("PUBLISHED");
        scheduleMapper.updateById(s);
        // 恢复时用 DB 中的 remaining（不重置）
        redisService.syncSlots(id, s.getRemainingSlots());
        return toVO(s);
    }

    // ==================== 手动调整号源 ====================

    @Transactional
    public ScheduleVO adjustSlots(Long id, int delta) {
        Schedule s = scheduleMapper.selectById(id);
        if (s == null) {
            throw new BusinessException(404, "排班不存在");
        }
        if (delta == 0) {
            throw new BusinessException(400, "调整量不能为0");
        }
        int newRemaining = s.getRemainingSlots() + delta;
        if (newRemaining < 0) {
            throw new BusinessException(400, "余量不足，当前剩余 " + s.getRemainingSlots());
        }
        s.setRemainingSlots(newRemaining);
        scheduleMapper.updateById(s);

        if ("PUBLISHED".equals(s.getStatus())) {
            redisService.syncSlots(id, newRemaining);
        }
        return toVO(s);
    }

    // ==================== 周复制 ====================

    @Transactional
    public CopyWeekResult copyWeek(CopyWeekRequest req) {
        LocalDate srcStart = req.getSourceWeekStart();
        LocalDate tgtStart = req.getTargetWeekStart();

        // 校验周一
        if (srcStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(400, "源周起始日必须为周一");
        }
        if (tgtStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(400, "目标周起始日必须为周一");
        }
        // 目标周日期窗口校验（目标周最后一天 <= today+14）
        LocalDate tgtEnd = tgtStart.plusDays(6);
        LocalDate maxDate = LocalDate.now().plusDays(MAX_ADVANCE_DAYS);
        if (tgtEnd.isAfter(maxDate)) {
            throw new BusinessException(400, "目标周超出排班窗口（最多" + MAX_ADVANCE_DAYS + "天）");
        }

        // 查源周排班
        LocalDate srcEnd = srcStart.plusDays(6);
        List<Schedule> sourceList = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>()
                        .ge(Schedule::getScheduleDate, srcStart)
                        .le(Schedule::getScheduleDate, srcEnd)
                        .eq(Schedule::getStatus, "PUBLISHED"));

        int skipped = 0;
        int created = 0;
        for (Schedule src : sourceList) {
            LocalDate targetDate = tgtStart.plusDays(
                    src.getScheduleDate().toEpochDay() - srcStart.toEpochDay());
            // 检查目标组合是否已存在
            Long count = scheduleMapper.selectCount(
                    new LambdaQueryWrapper<Schedule>()
                            .eq(Schedule::getDoctorId, src.getDoctorId())
                            .eq(Schedule::getScheduleDate, targetDate)
                            .eq(Schedule::getTimePeriod, src.getTimePeriod()));
            if (count > 0) {
                skipped++;
                continue;
            }
            Schedule ns = new Schedule();
            ns.setDoctorId(src.getDoctorId());
            ns.setDepartmentId(src.getDepartmentId());
            ns.setScheduleDate(targetDate);
            ns.setTimePeriod(src.getTimePeriod());
            ns.setStartTime(src.getStartTime());
            ns.setEndTime(src.getEndTime());
            ns.setTotalSlots(src.getTotalSlots());
            ns.setRemainingSlots(src.getTotalSlots()); // 全新放号
            ns.setStatus("PUBLISHED");
            scheduleMapper.insert(ns);
            redisService.syncSlots(ns.getId(), ns.getRemainingSlots());
            created++;
        }
        return new CopyWeekResult(skipped, created);
    }

    // ==================== 内部校验 ====================

    private TimePeriod parseTimePeriod(String value) {
        try {
            return TimePeriod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "无效班次：" + value + "，可选 MORNING/AFTERNOON/EVENING");
        }
    }

    private void validateDateWindow(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw new BusinessException(400, "排班日期不能早于今天");
        }
        if (date.isAfter(today.plusDays(MAX_ADVANCE_DAYS))) {
            throw new BusinessException(400, "排班日期最多提前" + MAX_ADVANCE_DAYS + "天");
        }
    }

    private void validateDoctorExists(Long doctorId) {
        if (doctorMapper.selectById(doctorId) == null) {
            throw new BusinessException(400, "医生不存在");
        }
    }

    private void validateDepartmentExists(Long departmentId) {
        if (departmentMapper.selectById(departmentId) == null) {
            throw new BusinessException(400, "科室不存在");
        }
    }

    private void validateConflict(Long doctorId, LocalDate date, TimePeriod period, Long excludeId) {
        LambdaQueryWrapper<Schedule> qw = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctorId)
                .eq(Schedule::getScheduleDate, date)
                .eq(Schedule::getTimePeriod, period.name())
                .ne(excludeId != null, Schedule::getId, excludeId);
        if (scheduleMapper.selectCount(qw) > 0) {
            throw new BusinessException(409, "该医生在此日期此时段已有排班");
        }
    }

    private ScheduleVO toVO(Schedule s) {
        String doctorName = null;
        String departmentName = null;
        Doctor doc = doctorMapper.selectById(s.getDoctorId());
        if (doc != null) doctorName = doc.getName();
        Department dept = departmentMapper.selectById(s.getDepartmentId());
        if (dept != null) departmentName = dept.getName();

        return ScheduleVO.builder()
                .id(s.getId())
                .doctorId(s.getDoctorId())
                .doctorName(doctorName)
                .departmentId(s.getDepartmentId())
                .departmentName(departmentName)
                .scheduleDate(s.getScheduleDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .timePeriod(s.getTimePeriod())
                .totalSlots(s.getTotalSlots())
                .remainingSlots(s.getRemainingSlots())
                .status(s.getStatus())
                .build();
    }

    /**
     * Agent 排班查询（ticket 12）。
     * <p>
     * 返回扁平列表，过滤 SUSPENDED 和余量=0，按日期升序。
     * 支持按 doctorId / departmentId / date 筛选（均为可选）。
     */
    public List<Map<String, Object>> queryForAgent(Long doctorId, Long departmentId, LocalDate date) {
        LambdaQueryWrapper<Schedule> qw = new LambdaQueryWrapper<Schedule>()
                .eq(doctorId != null, Schedule::getDoctorId, doctorId)
                .eq(departmentId != null, Schedule::getDepartmentId, departmentId)
                .eq(date != null, Schedule::getScheduleDate, date)
                .ne(Schedule::getStatus, "SUSPENDED")
                .gt(Schedule::getRemainingSlots, 0)
                .orderByAsc(Schedule::getScheduleDate)
                .orderByAsc(Schedule::getStartTime);

        List<Schedule> schedules = scheduleMapper.selectList(qw);
        return schedules.stream().map(s -> {
            Doctor doc = doctorMapper.selectById(s.getDoctorId());
            Department dept = departmentMapper.selectById(s.getDepartmentId());
            TimePeriod tp = TimePeriod.valueOf(s.getTimePeriod());
            String timeRange = tp.getStartTime() + "-" + tp.getEndTime();

            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("scheduleId", s.getId());
            map.put("doctorId", s.getDoctorId());
            map.put("doctorName", doc != null ? doc.getName() : "");
            map.put("departmentId", s.getDepartmentId());
            map.put("departmentName", dept != null ? dept.getName() : "");
            map.put("scheduleDate", s.getScheduleDate().toString());
            map.put("timePeriod", s.getTimePeriod());
            map.put("timeRange", timeRange);
            map.put("remainingSlots", s.getRemainingSlots());
            map.put("status", s.getStatus());
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }
}
