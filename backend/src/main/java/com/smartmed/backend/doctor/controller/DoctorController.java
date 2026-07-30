package com.smartmed.backend.doctor.controller;

import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.doctor.dto.DoctorProfileUpdateRequest;
import com.smartmed.backend.doctor.dto.DoctorRequest;
import com.smartmed.backend.doctor.dto.DoctorVO;
import com.smartmed.backend.doctor.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生管理接口（03 ticket 接口契约）。
 * <ul>
 *   <li>GET    /api/b/doctors           分页（departmentId? + name?，ADMIN|DOCTOR）</li>
 *   <li>GET    /api/b/doctors/me        DOCTOR 查本人（/me 须在 /{id} 前声明）</li>
 *   <li>GET    /api/b/doctors/{id}      详情（ADMIN|DOCTOR）</li>
 *   <li>POST   /api/b/doctors           新建（ADMIN，同事务建 sys_user）</li>
 *   <li>PUT    /api/b/doctors/me        DOCTOR 编辑本人（仅 specialty/avatarUrl/intro）</li>
 *   <li>PUT    /api/b/doctors/{id}      编辑（ADMIN，全字段）</li>
 *   <li>DELETE /api/b/doctors/{id}      删除（ADMIN，同事务删 sys_user）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/b/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResponse<DoctorVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String name) {
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(doctorService.page(pageNum, pageSize, departmentId, name));
    }

    /** DOCTOR 查本人（须在 /{id} 前声明，避免 "me" 被当 id）。 */
    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public Result<DoctorVO> getMe() {
        return Result.success(doctorService.getMe());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<DoctorVO> getById(@PathVariable Long id) {
        return Result.success(doctorService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DoctorVO> create(@Valid @RequestBody DoctorRequest req) {
        return Result.success(doctorService.create(req));
    }

    /** DOCTOR 编辑本人（仅 specialty/avatarUrl/intro，Q11）。 */
    @PutMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public Result<DoctorVO> updateMe(@Valid @RequestBody DoctorProfileUpdateRequest req) {
        return Result.success(doctorService.updateMe(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DoctorVO> update(@PathVariable Long id, @Valid @RequestBody DoctorRequest req) {
        return Result.success(doctorService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        doctorService.delete(id);
        return Result.success();
    }
}
