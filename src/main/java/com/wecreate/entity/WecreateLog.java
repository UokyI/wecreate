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

@Data
@TableName("WECREATE_CLI_LOG")
public class WecreateLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal id;

    @TableField(value = "CREATE_USER", fill = FieldFill.INSERT)
    private String createUser;

    @TableField(value = "CREATE_TIME", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField("TRACE_ID")
    private String traceId;

    @TableField("type")
    private String type;

    @TableField("CONTENT")
    private String content;

    /**
     * 最后更新人
     */
    @TableField(value = "LM_USER", fill = FieldFill.INSERT_UPDATE)
    private String lmUser;

    /**
     * 最后更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "LM_TIME", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lmTime;
}