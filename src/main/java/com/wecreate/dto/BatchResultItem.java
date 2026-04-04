package com.wecreate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Project：WeCreate
 * Date：2026/1/8
 * Time：10:54
 * Description：TODO
 *
 * @author UokyI
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchResultItem {
    private String project;
    private String result;
    private String msg;
}
