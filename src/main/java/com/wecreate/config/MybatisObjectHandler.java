package com.wecreate.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * 解决：mybatisplus中的@TableField(fill = FieldFill.INSERT)不起作用（不能自动填充数据）
 */
@Configuration
public class MybatisObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 获取当前的 createTime 和 createUser
        Object createTime = getFieldValByName("createTime", metaObject);
        Object createUser = getFieldValByName("createUser", metaObject);
        // 获取当前的 lmTime 和 lmUser
        Object lmTime = getFieldValByName("lmTime", metaObject);
        Object lmUser = getFieldValByName("lmUser", metaObject);
        setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        if (createUser == null) {
            setFieldValByName("createUser", "System", metaObject);
        }
        setFieldValByName("lmTime", LocalDateTime.now(), metaObject);
        if (lmUser == null) {
            setFieldValByName("lmUser", "System", metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("lmTime", LocalDateTime.now(), metaObject);
        setFieldValByName("lmUser", "System", metaObject);
    }
}