package com.smartmed.backend.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 家庭成员新增/编辑请求（C 端，07 ticket）。
 * <p>
 * relationship 取值：配偶/父亲/母亲/儿子/女儿/兄弟/姐妹/其他。
 */
@Data
public class FamilyMemberRequest {

    @NotBlank(message = "成员姓名不能为空")
    @Size(max = 64, message = "姓名不超过64个字符")
    private String name;

    @NotBlank(message = "关系不能为空")
    @Size(max = 32, message = "关系描述不超过32个字符")
    private String relationship;

    @Size(max = 32, message = "手机号不超过32个字符")
    private String phone;

    private String gender;

    private LocalDate birthDate;

    @Size(max = 256, message = "过敏史不超过256个字符")
    private String allergyHistory;
}