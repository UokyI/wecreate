package com.wecreate.dto;

import lombok.Data;

@Data
public class AcceptanceRateStatDTO {
    private String weekCode;
    private String weekStartDate;
    private String weekEndDate;
    private Integer weekNum;
    private Double saAcceptanceRate;
    private Double devAcceptanceRate;
    private Integer saTotalUsage;
    private Integer devTotalUsage;
    private Integer totalUsage;
    private Integer saUserCount;
    private Integer devUserCount;
}