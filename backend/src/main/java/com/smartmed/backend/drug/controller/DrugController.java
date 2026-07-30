package com.smartmed.backend.drug.controller;

import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.drug.dto.DrugRequest;
import com.smartmed.backend.drug.dto.DrugVO;
import com.smartmed.backend.drug.service.DrugService;
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
 * 药品管理接口（仅 ADMIN，03 ticket 接口契约）。
 * <ul>
 *   <li>GET    /api/b/drugs          分页（name? 模糊）</li>
 *   <li>GET    /api/b/drugs/{id}     详情</li>
 *   <li>POST   /api/b/drugs          新增</li>
 *   <li>PUT    /api/b/drugs/{id}     编辑</li>
 *   <li>DELETE /api/b/drugs/{id}     删除（物理删+前置检查，ADR-0006）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/b/drugs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DrugController {

    private final DrugService drugService;

    @GetMapping
    public Result<PageResponse<DrugVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String name) {
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(drugService.page(pageNum, pageSize, name));
    }

    @GetMapping("/{id}")
    public Result<DrugVO> getById(@PathVariable Long id) {
        return Result.success(drugService.getById(id));
    }

    @PostMapping
    public Result<DrugVO> create(@Valid @RequestBody DrugRequest req) {
        return Result.success(drugService.create(req));
    }

    @PutMapping("/{id}")
    public Result<DrugVO> update(@PathVariable Long id, @Valid @RequestBody DrugRequest req) {
        return Result.success(drugService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        drugService.delete(id);
        return Result.success();
    }
}
