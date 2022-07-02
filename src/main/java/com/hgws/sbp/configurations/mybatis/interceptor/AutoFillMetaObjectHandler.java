package com.hgws.sbp.configurations.mybatis.interceptor;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author zhouhonggang
 * @version 1.0.0
 * @project spring-boot-pro
 * @datetime 2022-06-30 15:38
 * @description: 公共字段内容自动填充
 */
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    /**
     * 添加自动填充
     * @param metaObject 添加对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Integer userId = 1;
        this.fillStrategy(metaObject, "revision", 1);
        this.fillStrategy(metaObject, "createdBy", userId);
        this.strictInsertFill(metaObject, "createdTime", LocalDateTime::now, LocalDateTime.class);
    }

    /**
     * 修改自动填充
     * @param metaObject 修改对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Integer userId = 1;
        this.fillStrategy(metaObject, "updatedBy", userId);
        this.strictInsertFill(metaObject, "updatedTime", LocalDateTime::now, LocalDateTime.class);
    }

}
