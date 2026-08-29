package tech.liganex.studio.common;

import java.util.List;

/**
 * 分页结果（跨模块共用，前端接口与内部接口复用同一份契约）。
 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }
}
