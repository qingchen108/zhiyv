package com.smartmed.backend.department.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 科室新增/编辑请求（hospital_id 后端硬编码，DTO 不含）。 */
@Data
public class DepartmentRequest {

    @NotBlank(message = "科室名称不能为空")
    private String name;

    private String description;
}
