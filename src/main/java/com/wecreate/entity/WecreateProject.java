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
 * 项目管理实体类
 */
@Data
@TableName("WECREATE_PROJECT")
public class WecreateProject implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(value = "ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal id;

    /**
     * 项目名称
     */
    @TableField("PROJECT_NAME")
    private String projectName;

    /**
     * Git 仓库地址
     */
    @TableField("GIT_URL")
    private String gitUrl;

    /**
     * 本地绝对路径
     */
    @TableField("LOCAL_PATH")
    private String localPath;

    /**
     * 项目描述
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

    /**
     * 项目状态：0-未克隆，1-已克隆，2-克隆失败
     */
    @TableField("STATUS")
    private Integer status;

    /**
     * 备注信息
     */
    @TableField("REMARK")
    private String remark;
}
