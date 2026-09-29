package com.dss.favorite.controller;

import com.dss.common.config.OpenApiConfig;
import com.dss.common.context.UserContext;
import com.dss.common.result.PageResult;
import com.dss.common.result.Result;
import com.dss.favorite.model.dto.FavoriteDTO;
import com.dss.favorite.model.dto.FavoriteQuery;
import com.dss.favorite.model.enums.TargetType;
import com.dss.favorite.model.vo.FavoriteStatusVO;
import com.dss.favorite.model.vo.FavoriteVO;
import com.dss.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏（全部需要登录）。接口文档 5.7。
 */
@Tag(name = "收藏")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏", description = "同一目标只能收藏一次；下架的商品也能收藏")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody FavoriteDTO dto) {
        favoriteService.add(UserContext.requireUserId(), dto);
        return Result.ok();
    }

    @Operation(summary = "取消收藏", description = "没收藏过也返回成功")
    @DeleteMapping("/{targetType}/{targetId}")
    public Result<Void> remove(@PathVariable TargetType targetType, @PathVariable Long targetId) {
        favoriteService.remove(UserContext.requireUserId(), targetType, targetId);
        return Result.ok();
    }

    @Operation(summary = "收藏列表", description = "按类型分页，按收藏时间倒序；商品下架了也照常显示并标明")
    @GetMapping
    public Result<PageResult<FavoriteVO>> list(@Valid @ParameterObject FavoriteQuery query) {
        return Result.ok(favoriteService.list(UserContext.requireUserId(), query));
    }

    @Operation(summary = "是否已收藏")
    @GetMapping("/status")
    public Result<FavoriteStatusVO> status(@RequestParam TargetType targetType, @RequestParam Long targetId) {
        return Result.ok(favoriteService.status(UserContext.requireUserId(), targetType, targetId));
    }
}
