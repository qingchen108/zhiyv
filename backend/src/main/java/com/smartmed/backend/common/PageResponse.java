package com.smartmed.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 通用分页响应（03 引入，对齐 ticket Q12）。
 * <p>
 * 仅暴露 {@code records}/{@code total}/{@code page}/{@code size} 四字段，
 * 不泄漏 MyBatis-Plus IPage 内部字段（searchCount/orders 等）。
 *
 * @param records 当前页数据
 * @param total   总记录数
 * @param page    当前页码
 * @param size    每页大小
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;

    /** 从 MyBatis-Plus IPage 构造（仅取四字段）。 */
    public static <T> PageResponse<T> of(com.baomidou.mybatisplus.core.metadata.IPage<T> page) {
        return new PageResponse<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
