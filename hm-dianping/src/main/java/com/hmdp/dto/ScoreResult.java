package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * @program: hm-dianping
 * @description:
 * @author: 作者
 * @create: 2023-01-29 19:51
 */
@Data
public class ScoreResult {
    private List<?> list;
    private Long minTime;
    private Integer integerId;
}
