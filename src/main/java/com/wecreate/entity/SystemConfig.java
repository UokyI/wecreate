package com.wecreate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 系统配置实体类
 */
@Data
@TableName("SYSTEM_CONFIG")
public class SystemConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(value = "ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal id;

    /**
     * 配置键
     */
    @TableField("CONFIG_KEY")
    private String configKey;

    /**
     * 配置值
     */
    @TableField("CONFIG_VALUE")
    private String configValue;

    /**
     * 配置描述
     */
    @TableField("DESCRIPTION")
    private String description;

    /**
     * 创建人
     */
    @TableField(value = "CREATE_USER", fill = FieldFill.INSERT)
    private String createUser;

    /**
     * 创建时间
     */
    @TableField(value = "CREATE_TIME", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 最后更新人
     */
    @TableField(value = "LM_USER", fill = FieldFill.INSERT_UPDATE)
    private String lmUser;

    /**
     * 最后更新时间
     */
    @TableField(value = "LM_TIME", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lmTime;
}
