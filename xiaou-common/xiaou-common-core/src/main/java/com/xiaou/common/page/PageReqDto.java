package com.xiaou.common.page;

import lombok.Data;

/**
 * 分页请求参数
 */
@Data
public class PageReqDto {

    /**
     * 每页最大条数限制
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 请求页码，默认第 1 页
     */
    private int pageNum = 1;

    /**
     * 每页大小，默认每页 10 条
     */
    private int pageSize = 10;

    /**
     * 是否查询所有，默认不查所有。为 true 时，pageNum 和 pageSize 无效
     */
    private boolean fetchAll = false;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 是否降序，默认 true（倒序）
     */
    private Boolean desc = true;

    public void setPageNum(int pageNum) {
        this.pageNum = Math.max(1, pageNum);
    }

    public void setPageSize(int pageSize) {
        if (pageSize <= 0) {
            this.pageSize = 10;
        } else {
            this.pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        }
    }
}
