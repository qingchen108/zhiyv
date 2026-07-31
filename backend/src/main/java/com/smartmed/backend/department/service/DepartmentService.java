package com.smartmed.backend.department.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.department.dto.DepartmentRequest;
import com.smartmed.backend.department.dto.DepartmentVO;
import com.smartmed.backend.department.entity.Department;
import com.smartmed.backend.department.mapper.DepartmentMapper;
import com.smartmed.backend.doctor.entity.Doctor;
import com.smartmed.backend.doctor.mapper.DoctorMapper;
import com.smartmed.backend.schedule.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 科室服务（仅 ADMIN 操作，权限由 Controller @PreAuthorize 控制）。
 * <p>
 * hospital_id 硬编码为 1（CONTEXT 术语表"唯一医院"）；
 * 删除走物理删 + 前置引用检查（ADR-0006）。
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    /** 唯一医院 ID（种子预设，B 端不提供医院管理页面）。 */
    private static final long HOSPITAL_ID = 1L;

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;

    /** 分页查询，支持 name 模糊筛选，固定 id ASC（Q12）。 */
    public PageResponse<DepartmentVO> page(long pageNum, long pageSize, String name) {
        Page<Department> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Department> qw = new LambdaQueryWrapper<Department>()
                .like(name != null && !name.isBlank(), Department::getName, name)
                .orderByAsc(Department::getId);
        departmentMapper.selectPage(page, qw);
        return PageResponse.of(page.convert(this::toVO));
    }

    public DepartmentVO getById(Long id) {
        Department d = departmentMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "科室不存在");
        }
        return toVO(d);
    }

    @Transactional
    public DepartmentVO create(DepartmentRequest req) {
        Department d = new Department();
        d.setHospitalId(HOSPITAL_ID);
        d.setName(req.getName());
        d.setDescription(req.getDescription());
        d.setLocation(req.getLocation());
        departmentMapper.insert(d);
        return toVO(d);
    }

    @Transactional
    public DepartmentVO update(Long id, DepartmentRequest req) {
        Department d = departmentMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "科室不存在");
        }
        d.setName(req.getName());
        d.setDescription(req.getDescription());
        d.setLocation(req.getLocation());
        departmentMapper.updateById(d);
        return toVO(d);
    }

    /**
     * 删除科室：前置引用检查（ADR-0006）。
     * 有 doctor / schedule 引用则 409 拒绝。
     */
    @Transactional
    public void delete(Long id) {
        if (departmentMapper.selectById(id) == null) {
            throw new BusinessException(404, "科室不存在");
        }
        long doctorCount = doctorMapper.selectCount(
                new LambdaQueryWrapper<Doctor>().eq(Doctor::getDepartmentId, id));
        if (doctorCount > 0) {
            throw new BusinessException(409, "该科室下存在医生，无法删除");
        }
        if (scheduleMapper.countByDepartmentId(id) > 0) {
            throw new BusinessException(409, "该科室存在排班记录，无法删除");
        }
        departmentMapper.deleteById(id);
    }

    private DepartmentVO toVO(Department d) {
        return DepartmentVO.builder()
                .id(d.getId())
                .hospitalId(d.getHospitalId())
                .name(d.getName())
                .description(d.getDescription())
                .location(d.getLocation())
                .build();
    }
}
