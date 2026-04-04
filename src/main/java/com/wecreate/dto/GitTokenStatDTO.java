package com.wecreate.dto;

import lombok.Data;

@Data
public class GitTokenStatDTO {
    private String weekCode;
    private String weekStartDate;
    private String weekEndDate;
    private Integer weekNum;
    private Integer totalCount;
    private Integer acceptedCount;
    private Double avgScore;
    private Integer totalChangedFiles;
    private Integer totalInsertions;
    private Integer totalDeletions;
    private Integer totalChanges;
    private Integer totalInputTokens;
    private Integer totalOutputTokens;
    private Integer sumTotalTokens;
    private Integer sumCachedTokens;
    private Integer avgInputTokens;
    private Integer avgOutputTokens;
    private Integer avgTotalTokens;
}