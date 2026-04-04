package com.wecreate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Project：WeCreate
 * Date：2026/1/8
 * Time：13:35
 * Description：TODO
 *
 * @author UokyI
 * @version 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestsDTO {
    List<BatchRequestItem> requests;
}
