package com.tgaws.common.result;

import java.util.List;

/**
 * 统一分页出参（《4》4.2：{"total":100,"list":[...]}；入参 pageNum≥1/pageSize 1~200）。
 */
public record PageResult<T>(long total, List<T> list) {
}
