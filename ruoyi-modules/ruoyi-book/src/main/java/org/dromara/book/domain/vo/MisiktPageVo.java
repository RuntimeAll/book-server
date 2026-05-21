package org.dromara.book.domain.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * misikt 风格分页响应 VO（对齐 misikt PageHelper 真实抓包字段命名）。
 *
 * <p>跟 RuoYi-Vue-Plus 自带的 {@code TableDataInfo<T>} 不同：本 VO 给 misikt 端点专用，
 * 字段命名严格按 misikt 抓包（{@code list / pageNum / pageSize / pages / isFirstPage /
 * isLastPage / hasPreviousPage / hasNextPage / navigatePages / navigatepageNums / ...}）。
 *
 * <p>由 MyBatis-Plus {@code IPage} 转换构造（{@link #of}）。
 *
 * @param <T> 列表元素类型
 * @author backend-dev
 */
@Data
@JsonPropertyOrder({
    "total", "list", "pageNum", "pageSize", "size", "startRow", "endRow", "pages",
    "prePage", "nextPage", "isFirstPage", "isLastPage", "hasPreviousPage", "hasNextPage",
    "navigatePages", "navigatepageNums", "navigateFirstPage", "navigateLastPage"
})
public class MisiktPageVo<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 字段声明顺序 = 序列化输出顺序（按 misikt 真响应 A6-paper-page.json 字节级对齐）
    private long total;
    private List<T> list;
    private long pageNum;
    private long pageSize;
    private long size;
    private long startRow;
    private long endRow;
    private long pages;
    private long prePage;
    private long nextPage;
    /** boolean 字段命名 firstPage (no `is` prefix) — Lombok 给 boolean 加 `is` 前缀 getter；
     *  Jackson 默认会 strip `is` 序列化为 firstPage（misikt 期望 isFirstPage）。
     *  方案：字段名 firstPage + @JsonProperty 显式重命名输出 → 单一 key `isFirstPage`。 */
    @JsonProperty("isFirstPage")
    private boolean firstPage;
    @JsonProperty("isLastPage")
    private boolean lastPage;
    private boolean hasPreviousPage;
    private boolean hasNextPage;
    private int navigatePages;
    private List<Long> navigatepageNums;
    private long navigateFirstPage;
    private long navigateLastPage;

    // 字段顺序锁定（防 reflection 乱序 + 字节级对齐 misikt 真响应顺序）

    /**
     * 从 MyBatis-Plus IPage 转 misikt 风格分页 VO。
     *
     * @param page MyBatis-Plus 分页结果（含 records / total / current / size / pages）
     * @param <T>  列表元素类型
     * @return misikt 风格分页 VO
     */
    public static <T> MisiktPageVo<T> of(IPage<T> page) {
        MisiktPageVo<T> vo = new MisiktPageVo<>();
        long current = page.getCurrent();
        long pageSize = page.getSize();
        long total = page.getTotal();
        long pages = page.getPages();
        List<T> records = page.getRecords();

        vo.setTotal(total);
        vo.setList(records);
        vo.setPageNum(current);
        vo.setPageSize(pageSize);
        vo.setSize(records == null ? 0 : records.size());
        vo.setStartRow(records == null || records.isEmpty() ? 0 : (current - 1) * pageSize + 1);
        vo.setEndRow(records == null || records.isEmpty() ? 0 : (current - 1) * pageSize + records.size());
        vo.setPages(pages);
        vo.setPrePage(current > 1 ? current - 1 : 0);
        vo.setNextPage(current < pages ? current + 1 : 0);
        vo.setFirstPage(current == 1);
        vo.setLastPage(current >= pages || pages == 0);
        vo.setHasPreviousPage(current > 1);
        vo.setHasNextPage(current < pages);

        int navPages = 8;
        vo.setNavigatePages(navPages);
        // navigatepageNums 按 misikt 风格：当前页前后取 navigatePages 个连续页码
        List<Long> nums = buildNavigateNums(current, pages, navPages);
        vo.setNavigatepageNums(nums);
        vo.setNavigateFirstPage(nums.isEmpty() ? 0 : nums.get(0));
        vo.setNavigateLastPage(nums.isEmpty() ? 0 : nums.get(nums.size() - 1));

        return vo;
    }

    /**
     * 构造 navigatepageNums — 居中当前页的连续页码窗口（misikt PageHelper 默认实现）。
     */
    private static List<Long> buildNavigateNums(long current, long pages, int window) {
        List<Long> out = new ArrayList<>();
        if (pages <= 0) {
            return out;
        }
        long half = window / 2;
        long start = Math.max(1, current - half);
        long end = Math.min(pages, start + window - 1);
        // end 撞顶时回退 start 凑满 window
        if (end - start + 1 < window) {
            start = Math.max(1, end - window + 1);
        }
        for (long p = start; p <= end; p++) {
            out.add(p);
        }
        return out;
    }
}
