package com.wecreate.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("WECREATE_CLI_PROMPT")
public class WecreatePrompt implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(value = "ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal id;

    @TableField(value = "CREATE_USER", fill = FieldFill.INSERT)
    private String createUser;

    @TableField(value = "CREATE_TIME", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField("PROMPT_CONTENT")
    private String promptContent;

    @TableField("EXECUTION_TIME")
    private Integer executionTime;

    @TableField("WORK_DIRECTORY")
    private String workDirectory;

    @TableField("TRACE_ID")
    private String traceId;

    @TableField("REQUEST_IP")
    private String requestIp;

    @TableField("SCORE")
    private BigDecimal score;

    @TableField("SCORE_USER")
    private String scoreUser;
    
    // Git变更信息字段
    @TableField("GIT_CHANGED_FILES")
    private Integer gitChangedFiles;
    
    @TableField("GIT_INSERTIONS")
    private Integer gitInsertions;
    
    @TableField("GIT_DELETIONS")
    private Integer gitDeletions;
    
    @TableField("GIT_TOTAL_CHANGES")
    private Integer gitTotalChanges;

    // 新增的描述字段，用于存储Git统计信息、提交哈希码和GitLab查看链接
    @TableField("DESCRIPTION")
    private String description;

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
    
    /**
     * 项目ID
     */
    @TableField("PROJECT_ID")
    private String projectId;

    // Token统计相关字段
    @TableField("INPUT_TOKENS")
    private Integer inputTokens;

    @TableField("OUTPUT_TOKENS")
    private Integer outputTokens;

    @TableField("TOTAL_TOKENS")
    private Integer totalTokens;

    @TableField("CACHED_TOKENS")
    private Integer cachedTokens;

    // Git提交哈希值，用于避免重复记录同一提交
    @TableField("GIT_COMMIT_HASH")
    private String gitCommitHash;
}