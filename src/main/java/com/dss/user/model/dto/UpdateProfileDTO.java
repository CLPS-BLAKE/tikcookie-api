package com.dss.user.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "修改资料：传哪个字段就改哪个")
public class UpdateProfileDTO {

    @Size(min = 1, max = 20, message = "昵称 1–20 个字")
    @Schema(description = "昵称", example = "爱吃面的小王")
    private String nickname;

    @Size(max = 200, message = "头像 fileId 太长")
    @Schema(description = "头像 fileId（先调上传接口）", example = "group1/M00/00/00/wKgAAmbzZ1uAcUTnAAHUH3XGd6s512.jpg")
    private String avatar;
}
