package com.smartmed.backend.doctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmed.backend.auth.entity.SysUser;
import com.smartmed.backend.auth.mapper.SysUserMapper;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.department.entity.Department;
import com.smartmed.backend.department.mapper.DepartmentMapper;
import com.smartmed.backend.doctor.dto.DoctorProfileUpdateRequest;
import com.smartmed.backend.doctor.dto.DoctorRequest;
import com.smartmed.backend.doctor.dto.DoctorTriageVO;
import com.smartmed.backend.doctor.dto.DoctorVO;
import com.smartmed.backend.doctor.entity.Doctor;
import com.smartmed.backend.doctor.mapper.DoctorMapper;
import com.smartmed.backend.schedule.entity.Schedule;
import com.smartmed.backend.schedule.mapper.ScheduleMapper;
import com.smartmed.backend.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmed.backend.schedule.entity.Schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 医生服务。
 * <p>
 * 新建/删除 doctor 同事务建/删 sys_user（ADR-0005 账号联动）；
 * DoctorVO 的 phone 来自 sys_user（单源镜像 JOIN）；
 * age 由 birthDate 派生计算；
 * DOCTOR 仅可改 specialty/avatarUrl/intro（Q11）。
 */
@Service
@RequiredArgsConstructor
public class DoctorService {

    /** 新建医生默认密码（ADR-0005 首登改密）。 */
    private static final String DEFAULT_PASSWORD = "123456";

    private final DoctorMapper doctorMapper;
    private final SysUserMapper sysUserMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleMapper scheduleMapper;
    private final PasswordEncoder passwordEncoder;

    /** 分页查询，支持 departmentId + name 模糊，固定 id ASC（Q12）。 */
    public PageResponse<DoctorVO> page(long pageNum, long pageSize, Long departmentId, String name) {
        Page<Doctor> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Doctor> qw = new LambdaQueryWrapper<Doctor>()
                .eq(departmentId != null, Doctor::getDepartmentId, departmentId)
                .like(name != null && !name.isBlank(), Doctor::getName, name)
                .orderByAsc(Doctor::getId);
        doctorMapper.selectPage(page, qw);
        // 批量取 phone（按 doctor_id 查 sys_user），避免 N+1
        Map<Long, String> phoneByDoctorId = loadPhones(page.getRecords());
        // 批量取科室名称
        Map<Long, String> deptNameById = loadDeptNames(page.getRecords());
        return PageResponse.of(page.convert(d -> toVO(d, phoneByDoctorId.get(d.getId()), deptNameById.get(d.getDepartmentId()))));
    }

    public DoctorVO getById(Long id) {
        Doctor d = doctorMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "医生不存在");
        }
        return toVO(d, loadPhone(d.getId()), loadDeptName(d.getDepartmentId()));
    }

    /** DOCTOR 查本人（Q11，doctorId 从 token 取）。 */
    public DoctorVO getMe() {
        Long doctorId = SecurityUtil.current().getDoctorId();
        if (doctorId == null) {
            throw new BusinessException(403, "当前账号未关联医生");
        }
        return getById(doctorId);
    }

    /**
     * 导诊场景按科室查询医生推荐（ticket 11）。
     * <p>
     * 返回医生基本信息 + 可约号源列表，按好评率降序 + 余量降序排列。
     * 最多返回 3 位医生。
     */
    public List<DoctorTriageVO> queryForTriage(Long departmentId) {
        // 查该科室所有医生
        List<Doctor> doctors = doctorMapper.selectList(
                new LambdaQueryWrapper<Doctor>()
                        .eq(Doctor::getDepartmentId, departmentId)
                        .orderByDesc(Doctor::getGoodRate));
        if (doctors.isEmpty()) {
            return List.of();
        }

        String deptName = loadDeptName(departmentId);
        Map<Long, String> phoneByDoctorId = loadPhones(doctors);

        // 查每个医生的可约号源（today ~ today+14，PUBLISHED，remaining>0）
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(14);
        List<Long> doctorIds = doctors.stream().map(Doctor::getId).toList();
        List<Schedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>()
                        .in(Schedule::getDoctorId, doctorIds)
                        .ge(Schedule::getScheduleDate, today)
                        .le(Schedule::getScheduleDate, maxDate)
                        .eq(Schedule::getStatus, "PUBLISHED")
                        .gt(Schedule::getRemainingSlots, 0)
                        .orderByAsc(Schedule::getScheduleDate)
                        .orderByAsc(Schedule::getStartTime));

        // 按 doctor_id 分组
        Map<Long, List<DoctorTriageVO.AvailableSlot>> slotsByDoctor = schedules.stream()
                .collect(Collectors.groupingBy(
                        Schedule::getDoctorId,
                        Collectors.mapping(this::toAvailableSlot, Collectors.toList())));

        // 构建结果，按余量+好评率排序
        List<DoctorTriageVO> result = new ArrayList<>();
        for (Doctor d : doctors) {
            List<DoctorTriageVO.AvailableSlot> slots = slotsByDoctor.getOrDefault(d.getId(), List.of());
            int totalRemaining = slots.stream().mapToInt(DoctorTriageVO.AvailableSlot::getRemainingSlots).sum();
            result.add(DoctorTriageVO.builder()
                    .id(d.getId())
                    .departmentId(d.getDepartmentId())
                    .departmentName(deptName)
                    .name(d.getName())
                    .title(d.getTitle())
                    .specialty(d.getSpecialty())
                    .avatarUrl(d.getAvatarUrl())
                    .intro(d.getIntro())
                    .goodRate(d.getGoodRate())
                    .availableSlots(slots)
                    .build());
        }

        // 排序：有号 > 无号，好评率降序，余量降序
        result.sort(Comparator
                .<DoctorTriageVO>comparingInt(d -> d.getAvailableSlots().isEmpty() ? 1 : 0)
                .thenComparing(d -> d.getGoodRate() != null ? d.getGoodRate().negate() : BigDecimal.ZERO)
                .thenComparingInt(d -> -d.getAvailableSlots().stream()
                        .mapToInt(DoctorTriageVO.AvailableSlot::getRemainingSlots).sum()));

        return result.stream().limit(3).toList();
    }

    private DoctorTriageVO.AvailableSlot toAvailableSlot(Schedule s) {
        return DoctorTriageVO.AvailableSlot.builder()
                .scheduleId(s.getId())
                .date(s.getScheduleDate().toString())
                .timePeriod(s.getTimePeriod())
                .startTime(s.getStartTime().toString())
                .endTime(s.getEndTime().toString())
                .remainingSlots(s.getRemainingSlots())
                .build();
    }

    /**
     * 新建医生（ADMIN）：同事务建 doctor + sys_user（ADR-0005）。
     * phone 重复（sys_user.phone UNIQUE）由 DB 约束抛出，GlobalExceptionHandler 兜底转 500，
     * 此处先主动查一次返回友好的 409。
     */
    @Transactional
    public DoctorVO create(DoctorRequest req) {
        // 校验科室存在
        if (departmentMapper.selectById(req.getDepartmentId()) == null) {
            throw new BusinessException(404, "科室不存在");
        }
        // phone 唯一性预检
        Long exist = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, req.getPhone()));
        if (exist != null && exist > 0) {
            throw new BusinessException(409, "手机号已被使用");
        }

        Doctor d = new Doctor();
        d.setDepartmentId(req.getDepartmentId());
        d.setName(req.getName());
        d.setGender(req.getGender());
        d.setBirthDate(req.getBirthDate());
        d.setTitle(req.getTitle());
        d.setSpecialty(req.getSpecialty());
        d.setAvatarUrl(req.getAvatarUrl());
        d.setIntro(req.getIntro());
        d.setGoodRate(req.getGoodRate());
        doctorMapper.insert(d);

        // 同事务建 sys_user：role=DOCTOR, doctor_id 关联, username=姓名, phone=登录键, 密码默认 123456
        SysUser user = new SysUser();
        user.setUsername(req.getName());
        user.setPhone(req.getPhone());
        String pwd = (req.getPassword() == null || req.getPassword().isBlank()) ? DEFAULT_PASSWORD : req.getPassword();
        user.setPasswordHash(passwordEncoder.encode(pwd));
        user.setRole("DOCTOR");
        user.setDoctorId(d.getId());
        user.setMustChangePassword(true);
        user.setStatus(1);
        sysUserMapper.insert(user);

        return toVO(d, req.getPhone(), loadDeptName(d.getDepartmentId()));
    }

    /** ADMIN 编辑医生（全字段，但 phone 改写到 sys_user，name 同步 sys_user.username）。 */
    @Transactional
    public DoctorVO update(Long id, DoctorRequest req) {
        Doctor d = doctorMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "医生不存在");
        }
        if (departmentMapper.selectById(req.getDepartmentId()) == null) {
            throw new BusinessException(404, "科室不存在");
        }
        d.setDepartmentId(req.getDepartmentId());
        d.setName(req.getName());
        d.setGender(req.getGender());
        d.setBirthDate(req.getBirthDate());
        d.setTitle(req.getTitle());
        d.setSpecialty(req.getSpecialty());
        d.setAvatarUrl(req.getAvatarUrl());
        d.setIntro(req.getIntro());
        d.setGoodRate(req.getGoodRate());
        doctorMapper.updateById(d);

        // 同步 sys_user: username=姓名, phone=登录手机号
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDoctorId, id));
        if (user != null) {
            // phone 变更要校验唯一（排除自身）
            Long dup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, req.getPhone())
                    .ne(SysUser::getId, user.getId()));
            if (dup != null && dup > 0) {
                throw new BusinessException(409, "手机号已被使用");
            }
            user.setUsername(req.getName());
            user.setPhone(req.getPhone());
            sysUserMapper.updateById(user);
        }
        return toVO(d, req.getPhone(), loadDeptName(d.getDepartmentId()));
    }

    /** DOCTOR 编辑本人（仅 specialty/avatarUrl/intro，Q11）。 */
    @Transactional
    public DoctorVO updateMe(DoctorProfileUpdateRequest req) {
        Long doctorId = SecurityUtil.current().getDoctorId();
        if (doctorId == null) {
            throw new BusinessException(403, "当前账号未关联医生");
        }
        Doctor d = doctorMapper.selectById(doctorId);
        if (d == null) {
            throw new BusinessException(404, "医生不存在");
        }
        // 仅这三字段，其余不动
        if (req.getSpecialty() != null) {
            d.setSpecialty(req.getSpecialty());
        }
        if (req.getAvatarUrl() != null) {
            d.setAvatarUrl(req.getAvatarUrl());
        }
        if (req.getIntro() != null) {
            d.setIntro(req.getIntro());
        }
        doctorMapper.updateById(d);
        return toVO(d, loadPhone(doctorId), loadDeptName(d.getDepartmentId()));
    }

    /**
     * 删除医生（ADMIN）：前置引用检查 + 同事务删 sys_user（ADR-0005/0006）。
     * 有 schedule 引用则 409 拒绝；registration/consultation 等表 05+ 才有，检查留待后续。
     */
    @Transactional
    public void delete(Long id) {
        Doctor d = doctorMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "医生不存在");
        }
        // 前置检查：schedule 引用（04 表，03 阶段无数据，检查代码就绪为 04 防护）
        if (scheduleMapper.countByDoctorId(id) > 0) {
            throw new BusinessException(409, "该医生存在排班记录，无法删除");
        }
        // 先删 sys_user（子表无引用），再删 doctor
        sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDoctorId, id));
        doctorMapper.deleteById(id);
    }

    // ---------- 私有辅助 ----------

    /** 批量取 phone（按 doctor_id 查 sys_user），避免列表 N+1。 */
    private Map<Long, String> loadPhones(List<Doctor> doctors) {
        if (doctors.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = doctors.stream().map(Doctor::getId).toList();
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().in(SysUser::getDoctorId, ids));
        Map<Long, String> map = new HashMap<>();
        for (SysUser u : users) {
            map.put(u.getDoctorId(), u.getPhone());
        }
        return map;
    }

    /** 批量取科室名称，避免列表 N+1。 */
    private Map<Long, String> loadDeptNames(List<Doctor> doctors) {
        if (doctors.isEmpty()) {
            return Map.of();
        }
        List<Long> deptIds = doctors.stream().map(Doctor::getDepartmentId).distinct().toList();
        List<Department> depts = departmentMapper.selectBatchIds(deptIds);
        Map<Long, String> map = new HashMap<>();
        for (Department dept : depts) {
            map.put(dept.getId(), dept.getName());
        }
        return map;
    }

    private String loadDeptName(Long departmentId) {
        Department dept = departmentMapper.selectById(departmentId);
        return dept == null ? null : dept.getName();
    }

    private String loadPhone(Long doctorId) {
        SysUser u = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDoctorId, doctorId));
        return u == null ? null : u.getPhone();
    }

    private DoctorVO toVO(Doctor d, String phone, String departmentName) {
        return DoctorVO.builder()
                .id(d.getId())
                .departmentId(d.getDepartmentId())
                .departmentName(departmentName)
                .name(d.getName())
                .gender(d.getGender())
                .birthDate(d.getBirthDate())
                .age(computeAge(d.getBirthDate()))
                .title(d.getTitle())
                .specialty(d.getSpecialty())
                .avatarUrl(d.getAvatarUrl())
                .intro(d.getIntro())
                .goodRate(d.getGoodRate())
                .phone(phone)
                .build();
    }

    /** 由出生日期派生年龄（birthDate 为 null 返回 null）。 */
    private Integer computeAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
