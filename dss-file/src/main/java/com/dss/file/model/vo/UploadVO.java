package com.dss.file.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上传结果")
public class UploadVO {

    @Schema(description = "文件标识，存进库里、写进请求用它", example = "group1/M00/00/00/wKgAAmbzZ1uAcUTnAAHUH3XGd6s512.jpg")
    private String fileId;

    @Schema(description = "完整访问地址", example = "http://img.example.com/group1/M00/00/00/wKgAAmbzZ1uAcUTnAAHUH3XGd6s512.jpg")
    private String url;
}
