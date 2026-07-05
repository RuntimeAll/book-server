package org.dromara.book.domain.bo;

import lombok.Data;

/** 备课包改段入参（PRD-C-213 PUT /teacher/schedule/prep-pack/{id}/segs）。 */
@Data
public class PrepPackSegsBo {
    private Object segs;
}
