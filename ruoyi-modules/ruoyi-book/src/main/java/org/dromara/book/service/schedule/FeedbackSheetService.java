package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.FeedbackSheetBo;
import org.dromara.book.domain.entity.BizFeedbackSheet;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.mapper.BizFeedbackSheetMapper;
import org.dromara.book.mapper.BizStudentMapper;
import org.dromara.book.util.ScheduleRenderUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课后反馈单 Service（PRD-004 最小版）：单表 CRUD + PNG 导出。
 *
 * <p>owner 硬隔离：create_by 建单自动填（MetaObjectHandler），查/改/删/导出一律
 * {@code eq(create_by, LoginHelper.getUserId())} 过滤，防水平越权。
 *
 * <p>PNG = 复用 {@link ScheduleRenderUtil#renderToPng}（HTML→openhtmltopdf→pdfbox 光栅化，
 * 纯 Java 进程内，BUG-010 去浏览器管线）；样式对齐实料截图 P8：黄标题横条 + 分色表头 + 五列表格。
 * 🔴 卷面无任何内部词（层/★/素材/薄弱…），家长直读。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class FeedbackSheetService {

    private final BizFeedbackSheetMapper sheetMapper;
    private final BizStudentMapper studentMapper;
    private final ScheduleRenderUtil renderUtil;

    // ─────────────────────────── CRUD ───────────────────────────

    /** 建/改反馈单，返回 id。 */
    public Long upsert(FeedbackSheetBo bo) {
        // 反馈单以学生为主体，targetId 必填。缺失时返 400 而非落库触 NOT NULL 抛裸 500（BUG-D4-01/P7-BUG-1）。
        if (bo.getTargetId() == null) {
            throw new ServiceException("请选择学生（targetId 必填）", 400);
        }
        BizFeedbackSheet e;
        if (bo.getId() != null) {
            e = requireOwned(bo.getId());
        } else {
            e = new BizFeedbackSheet();
        }
        e.setTargetId(bo.getTargetId());
        // PRD-010 批次字段（可空=散单；update 走 NOT_NULL 策略，不传保持原值）
        e.setBatchKey(bo.getBatchKey());
        e.setLessonSeq(bo.getLessonSeq());
        e.setTitle(bo.getTitle());
        e.setLessonDate(parseDate(bo.getLessonDate()));
        e.setRowsJson(bo.getRows() == null ? "[]" : JsonUtils.toJsonString(bo.getRows()));
        if (e.getId() == null) {
            sheetMapper.insert(e);
        } else {
            sheetMapper.updateById(e);
        }
        return e.getId();
    }

    /** 列表（FE PageResult = {rows,total}）。owner 过滤 + 可选 targetId / keyword / batchKey（PRD-010）。 */
    public Map<String, Object> page(Long targetId, String keyword, String batchKey) {
        Long uid = LoginHelper.getUserId();
        LambdaQueryWrapper<BizFeedbackSheet> w = new LambdaQueryWrapper<BizFeedbackSheet>()
            .eq(BizFeedbackSheet::getCreateBy, uid)
            .eq(targetId != null, BizFeedbackSheet::getTargetId, targetId)
            .eq(batchKey != null && !batchKey.isBlank(), BizFeedbackSheet::getBatchKey, batchKey)
            .like(keyword != null && !keyword.isBlank(), BizFeedbackSheet::getTitle, keyword)
            .orderByDesc(BizFeedbackSheet::getId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizFeedbackSheet e : sheetMapper.selectList(w)) {
            out.add(brief(e));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rows", out);
        r.put("total", out.size());
        return r;
    }

    /** 详情（含 rows 数组）。 */
    public Map<String, Object> detail(Long id) {
        BizFeedbackSheet e = requireOwned(id);
        Map<String, Object> m = brief(e);
        m.put("rows", parseRows(e.getRowsJson()));
        return m;
    }

    /** 删除（owner 校验）。 */
    public void delete(Long id) {
        requireOwned(id);
        sheetMapper.deleteById(id);
    }

    // ─────────────────────────── PNG 导出 ───────────────────────────

    /** 导出家长版 PNG → {file,url}（下载复用 /teacher/schedule/artifact）。 */
    public Map<String, Object> exportPng(Long id) {
        BizFeedbackSheet e = requireOwned(id);
        List<Map<String, Object>> rows = parseRows(e.getRowsJson());
        String html = buildHtml(e.getTitle(), rows);
        // 🔴 BUG-014 收边（PRD-004 修复轮）：旧式 130+rows*40 高估致底部 ~36% 白条。
        //   改按**实际内容**估高：标题条+表头+内边距 ≈ 88px；每行按最长列（学习内容≈13字/行、
        //   不足点≈11字/行）估换行数 * 24px + 6px 行距，长文本自动多算一行不裁切、短行不留白。
        int height = 88 + estimateContentH(rows);
        String file = renderUtil.renderToPng(html, "feedback_" + id, 640, height);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", file);
        m.put("url", "/teacher/schedule/artifact?path=" + file);
        return m;
    }

    /**
     * 批次全量导出（PRD-010）：该学生一个批次 1~N 节全部反馈单，按课次序拼一张长 PNG
     * （用户实发形态：每节一条黄标题横条 + 五列表，垂直堆叠，一次性发家长）。
     *
     * @param targetId 学生 id（必填）
     * @param batchKey 批次键；空 = 该生**最新批次**（max create_time 的非空 batch_key，D4 无状态列约定）
     */
    public Map<String, Object> exportBatchPng(Long targetId, String batchKey) {
        Long uid = LoginHelper.getUserId();
        if (targetId == null) {
            throw new ServiceException("请选择学生（targetId 必填）", 400);
        }
        if (batchKey == null || batchKey.isBlank()) {
            BizFeedbackSheet latest = sheetMapper.selectList(new LambdaQueryWrapper<BizFeedbackSheet>()
                .eq(BizFeedbackSheet::getCreateBy, uid)
                .eq(BizFeedbackSheet::getTargetId, targetId)
                .isNotNull(BizFeedbackSheet::getBatchKey)
                .orderByDesc(BizFeedbackSheet::getCreateTime)
                .orderByDesc(BizFeedbackSheet::getId)
                .last("LIMIT 1")).stream().findFirst().orElse(null);
            if (latest == null) {
                throw new ServiceException("该学生还没有任何批次反馈单", 400);
            }
            batchKey = latest.getBatchKey();
        }
        List<BizFeedbackSheet> sheets = sheetMapper.selectList(new LambdaQueryWrapper<BizFeedbackSheet>()
            .eq(BizFeedbackSheet::getCreateBy, uid)
            .eq(BizFeedbackSheet::getTargetId, targetId)
            .eq(BizFeedbackSheet::getBatchKey, batchKey)
            .orderByAsc(BizFeedbackSheet::getLessonSeq)
            .orderByAsc(BizFeedbackSheet::getId));
        if (sheets.isEmpty()) {
            throw new ServiceException("批次「" + batchKey + "」下没有反馈单", 400);
        }
        StringBuilder body = new StringBuilder();
        int height = 14; // 顶部留白（.wrap padding-top）
        for (BizFeedbackSheet e : sheets) {
            List<Map<String, Object>> rows = parseRows(e.getRowsJson());
            body.append(sectionHtml(e.getTitle(), rows));
            height += 88 + estimateContentH(rows) + 16; // 每段自身高 + 段间距
        }
        String html = wrapDoc(body.toString());
        String file = renderUtil.renderToPng(html, "feedback_batch_" + targetId, 640, height);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", file);
        m.put("url", "/teacher/schedule/artifact?path=" + file);
        m.put("batchKey", batchKey);
        m.put("sheetCount", sheets.size());
        return m;
    }

    /** 内容高度估算（BUG-014 收边算法，单张/批次共用）。 */
    private int estimateContentH(List<Map<String, Object>> rows) {
        int contentH = 0;
        for (Map<String, Object> row : (rows.isEmpty() ? List.of(Map.<String, Object>of()) : rows)) {
            int cLines = wrapLines(str(row.get("content")), 13);
            int wLines = wrapLines(str(row.get("weakness")), 11);
            contentH += Math.max(cLines, wLines) * 24 + 6;
        }
        return contentH;
    }

    // ─────────────────────────── 家长版 PNG HTML（P8） ───────────────────────────

    /**
     * 反馈单 PNG HTML（对照设计稿 P8 / 实料截图）：黄标题横条 + 分色表头（蓝/蓝/蓝/绿/橙）
     * + 五列表格。⚠️ openhtmltopdf 不支持 flex/grid/CSS 变量 → 纯 table 布局；
     * 字体族用 renderUtil 注册的 'cjkhei'（标题）/ 'cjk'（正文）。🔴 无内部词。
     */
    @SuppressWarnings("unchecked")
    private String buildHtml(String title, List<Map<String, Object>> rows) {
        return wrapDoc(sectionHtml(title, rows));
    }

    /** 文档外壳（样式 + wrap 容器）。单张/批次导出共用，样式与 PRD-004 定版零漂移。 */
    private String wrapDoc(String bodySections) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:'cjk','cjkhei';color:#333;background:#fff;width:640px;font-size:12.5px}");
        sb.append(".wrap{padding:14px 16px 16px}");
        sb.append(".sec{margin-bottom:16px}");
        sb.append(".bar{background:#ffd94d;color:#3a3000;text-align:center;font-family:'cjkhei';")
            .append("font-weight:bold;font-size:15px;padding:9px 8px;border-radius:4px 4px 0 0}");
        sb.append("table{border-collapse:collapse;width:100%;table-layout:fixed}");
        sb.append("th{color:#fff;font-family:'cjkhei';font-weight:bold;font-size:12px;padding:7px 8px;")
            .append("text-align:center;border:1px solid #fff}");
        sb.append("th.h1,th.h2,th.h3{background:#5b8ff9}");
        sb.append("th.h4{background:#61c28e}");
        sb.append("th.h5{background:#f2933e}");
        sb.append("td{border:1px solid #e8e8e8;padding:6px 8px;font-size:12px;color:#333;")
            .append("vertical-align:middle;word-wrap:break-word}");
        sb.append("td.c{text-align:center}");
        sb.append("tr.alt td{background:#fafbfc}");
        sb.append("</style></head><body><div class=\"wrap\">");
        sb.append(bodySections);
        sb.append("</div></body></html>");
        return sb.toString();
    }

    /** 单节课的段落（黄标题条 + 五列表）。批次导出=N 段垂直堆叠。 */
    private String sectionHtml(String title, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"sec\">");
        sb.append("<div class=\"bar\">").append(esc(title == null ? "上课反馈" : title)).append("</div>");
        sb.append("<table>");
        sb.append("<tr>")
            .append("<th class=\"h1\" style=\"width:44px\">序号</th>")
            .append("<th class=\"h2\" style=\"width:96px\">所属模块</th>")
            .append("<th class=\"h3\">学习内容</th>")
            .append("<th class=\"h4\" style=\"width:96px\">掌握情况</th>")
            .append("<th class=\"h5\" style=\"width:132px\">不足点</th>")
            .append("</tr>");
        int i = 0;
        for (Map<String, Object> row : rows) {
            i++;
            String seq = str(row.get("seq"));
            if (seq.isBlank()) seq = String.valueOf(i);
            sb.append("<tr").append(i % 2 == 0 ? " class=\"alt\"" : "").append(">");
            sb.append("<td class=\"c\">").append(esc(seq)).append("</td>");
            sb.append("<td class=\"c\">").append(esc(str(row.get("module")))).append("</td>");
            sb.append("<td>").append(esc(str(row.get("content")))).append("</td>");
            sb.append("<td class=\"c\">").append(esc(str(row.get("mastery")))).append("</td>");
            sb.append("<td>").append(esc(str(row.get("weakness")))).append("</td>");
            sb.append("</tr>");
        }
        if (rows.isEmpty()) {
            sb.append("<tr><td class=\"c\" colspan=\"5\" style=\"color:#999;padding:14px\">（暂无内容）</td></tr>");
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private BizFeedbackSheet requireOwned(Long id) {
        BizFeedbackSheet e = sheetMapper.selectById(id);
        if (e == null || !LoginHelper.getUserId().equals(e.getCreateBy())) {
            // 归属拦截统一返 4xx（403），而非默认裸 500（BUG-D4-02 一致性）。不区分不存在/无权，防存在性探测。
            throw new ServiceException("反馈单不存在或无权访问", 403);
        }
        return e;
    }

    private Map<String, Object> brief(BizFeedbackSheet e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(e.getId()));
        m.put("targetId", e.getTargetId() == null ? null : String.valueOf(e.getTargetId()));
        m.put("targetName", targetName(e.getTargetId()));
        m.put("batchKey", e.getBatchKey());
        m.put("lessonSeq", e.getLessonSeq());
        m.put("title", e.getTitle());
        m.put("lessonDate", e.getLessonDate() == null ? null : e.getLessonDate().toString());
        m.put("createTime", e.getCreateTime());
        m.put("updateTime", e.getUpdateTime());
        return m;
    }

    private String targetName(Long targetId) {
        if (targetId == null) return null;
        BizStudent s = studentMapper.selectById(targetId);
        return s == null ? null : s.getName();
    }

    private static final TypeReference<List<Map<String, Object>>> ROWS_TYPE =
        new TypeReference<>() {
        };

    private List<Map<String, Object>> parseRows(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<Map<String, Object>> arr = JsonUtils.parseObject(json, ROWS_TYPE);
            return arr == null ? new ArrayList<>() : arr;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            throw new ServiceException("日期格式非法（应 yyyy-MM-dd）：" + s, 400);
        }
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 估算一段文本在给定每行字数下的换行行数（≥1），用于 PNG 内容高度收边。 */
    private int wrapLines(String s, int perLine) {
        if (s == null || s.isBlank()) return 1;
        return Math.max(1, (int) Math.ceil(s.trim().length() / (double) perLine));
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
