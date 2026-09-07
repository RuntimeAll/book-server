package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.QuestionBasketBo;
import org.dromara.book.domain.vo.BasketKeysVo;
import org.dromara.book.domain.vo.BasketPageVo;
import org.dromara.book.service.paper.QuestionBasketEntryService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SaCheckLogin
@RestController
@RequestMapping("/teacher/question/basket")
@RequiredArgsConstructor
public class QuestionBasketEntryController {
    private final QuestionBasketEntryService basketService;

    @GetMapping("/keys")
    public BasketKeysVo keys(@RequestParam(defaultValue = "default") String namespace) {
        return basketService.keys(namespace);
    }

    @GetMapping("/entries")
    public BasketPageVo entries(@RequestParam(defaultValue = "default") String namespace,
                               @RequestParam(defaultValue = "1") int pageIndex,
                               @RequestParam(defaultValue = "50") int pageSize) {
        return basketService.page(namespace, pageIndex, pageSize);
    }

    @PostMapping("/entries")
    public AddedCountVo add(@RequestBody QuestionBasketBo bo) {
        return new AddedCountVo(basketService.add(bo));
    }

    @PostMapping("/remove")
    public R<Void> remove(@RequestBody QuestionBasketBo bo) {
        basketService.remove(bo);
        return R.ok();
    }

    @PostMapping("/empty")
    public R<Void> empty(@RequestBody QuestionBasketBo bo) {
        basketService.empty(bo);
        return R.ok();
    }

    @PostMapping("/remove-entries")
    public R<Void> removeEntries(@RequestBody QuestionBasketBo bo) {
        basketService.removeEntries(bo);
        return R.ok();
    }

    public record AddedCountVo(int addedCount) {
    }
}
