package com.smartmed.backend.registration.scheduler;

import com.smartmed.backend.registration.mapper.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 挂号状态自动流转定时任务（05 ticket，CONTEXT §7）。
 * <p>
 * 每 10 分钟扫描：schedule_date + end_time < now() 且 status=REGISTERED → 自动标记 VISITED。
 * 兜底机制，医生手动标记为主（ticket 06 医生工作台）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationScheduler {

    private final RegistrationMapper registrationMapper;

    @Scheduled(cron = "0 */10 * * * *")
    public void autoMarkVisited() {
        int updated = registrationMapper.autoMarkVisited();
        if (updated > 0) {
            log.info("自动流转 REGISTERED→VISITED 完成，影响 {} 条记录", updated);
        }
    }
}
