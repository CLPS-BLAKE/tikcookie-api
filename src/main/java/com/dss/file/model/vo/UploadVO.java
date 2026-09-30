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

    @Schema(description = "文件标识，存进库里、写进请求用它", example = "group1/M00/00/00/3f2b9c4e7a1d4f0e8b6c5a2d1e0f9a8b.jpg")
    private String fileId;

    @Schema(description = "完整访问地址（后端读图接口，可直接放进 img src）", example = "https://demo.example.com/api/v1/images/group1/M00/00/00/3f2b9c4e7a1d4f0e8b6c5a2d1e0f9a8b.jpg")
    private String url;
}
