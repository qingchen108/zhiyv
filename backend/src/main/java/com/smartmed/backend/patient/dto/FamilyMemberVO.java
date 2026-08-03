package com.smartmed.backend.patient.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 家庭成员 VO（C 端，07 ticket）。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FamilyMemberVO {

    private Long id;
    private String name;
    private String relationship;
    private String phone;
    private String gender;
    private LocalDate birthDate;
    /** 派生的年龄（由 birthDate 计算，不存 DB）。 */
    private Integer age;
    private String allergyHistory;
    private OffsetDateTime createdAt;
}