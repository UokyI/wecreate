package com.wecreate.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkflowScoreDTO {
    private Double score;
    private String scoreUser;
    private String projectId;
    private String judgeInput;
    private String requestsInput;
    private String traceId;
    private Long excTime;
}