package com.smartmed.backend.department.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** 科室视图对象（响应）。 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DepartmentVO {

    private Long id;
    private Long hospitalId;
    private String name;
    private String description;
}
