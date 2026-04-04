package com.wecreate.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PromptScoreDTO {
    private String traceId;
    private BigDecimal score;
    private String userId;
}