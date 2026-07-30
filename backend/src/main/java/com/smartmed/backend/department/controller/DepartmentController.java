package com.smartmed.backend.department.controller;

import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.department.dto.DepartmentRequest;
import com.smartmed.backend.department.dto.DepartmentVO;
import com.smartmed.backend.department.service.DepartmentService;
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
 * 科室管理接口（仅 ADMIN，03 ticket 接口契约）。
 * <ul>
 *   <li>GET    /api/b/departments          分页（name? 模糊）</li>
 *   <li>GET    /api/b/departments/{id}     详情</li>
 *   <li>POST   /api/b/departments          新增（hospital_id 硬编码 1）</li>
 *   <li>PUT    /api/b/departments/{id}     编辑</li>
 *   <li>DELETE /api/b/departments/{id}     删除（物理删+前置检查，ADR-0006）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/b/departments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public Result<PageResponse<DepartmentVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String name) {
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(departmentService.page(pageNum, pageSize, name));
    }

    @GetMapping("/{id}")
    public Result<DepartmentVO> getById(@PathVariable Long id) {
        return Result.success(departmentService.getById(id));
    }

    @PostMapping
    public Result<DepartmentVO> create(@Valid @RequestBody DepartmentRequest req) {
        return Result.success(departmentService.create(req));
    }

    @PutMapping("/{id}")
    public Result<DepartmentVO> update(@PathVariable Long id, @Valid @RequestBody DepartmentRequest req) {
        return Result.success(departmentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.success();
    }
}
