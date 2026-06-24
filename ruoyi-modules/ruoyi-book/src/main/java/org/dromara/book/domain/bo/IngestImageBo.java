package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 本地路径直传图入参（POST /teacher/ingest/image，{localPath} 分支）—— PRD-C-204 B1。
 *
 * <p>multipart file 分支不走本 BO（直接 @RequestPart）；本 BO 仅本地脚本直传磁盘文件用。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
public class IngestImageBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本地磁盘绝对路径（脚本直传） */
    private String localPath;

    /** 资产种类 stem/answer/figure */
    private String assetKind;
}
