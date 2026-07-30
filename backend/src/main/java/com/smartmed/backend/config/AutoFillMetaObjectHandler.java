package com.smartmed.backend.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * MyBatis-Plus 自动填充处理器（03 引入）。
 * <p>
 * 类名避开 MyBatis-Plus 自带的 {@code MetaObjectHandler} 接口（同名会"已在编译单元定义"冲突）。
 * INSERT 填 createdAt + updatedAt，UPDATE 填 updatedAt。
 * 用 {@link OffsetDateTime} 对齐 PG {@code TIMESTAMPTZ}；DB {@code DEFAULT now()} 作为兜底。
 */
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now();
        strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class, OffsetDateTime.now());
    }
}
