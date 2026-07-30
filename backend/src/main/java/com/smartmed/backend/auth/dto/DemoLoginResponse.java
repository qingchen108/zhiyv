package com.smartmed.backend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** C 端 demo-login 响应（02 ticket 接口契约）。 */
@Data
@Builder
@AllArgsConstructor
public class DemoLoginResponse {

    /** JWT token（typ=C）。 */
    private String token;
    /** 患者ID（固定为预设 patient.id=1）。 */
    private Long patientId;
    /** 患者姓名（省前端二次查询）。 */
    private String patientName;
    /** 过期秒数（7d = 604800）。 */
    private long expiresIn;
}
