package com.dss.file.model.enums;

import java.util.Locale;

/**
 * 允许上传的图片类型。后缀用于生成 ObjectKey，Content-Type 用作 OSS 对象的元数据，
 * 魔数用于校验文件真实内容（接口文档 5.2.1 只接受 jpg / png / webp）。
 */
public enum ImageType {

    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String contentType;

    ImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    /** 按原始文件名后缀识别；jpg 和 jpeg 都算 JPEG。不认识的后缀返回 null。 */
    public static ImageType ofExtension(String extension) {
        if (extension == null) {
            return null;
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> JPEG;
            case "png" -> PNG;
            case "webp" -> WEBP;
            default -> null;
        };
    }

    /**
     * 按文件头魔数识别真实类型，不能只信客户端给的 Content-Type 或后缀。
     * 传入的字节数不足时返回 null。
     *
     * @param head 文件开头的前若干个字节（取前 12 个即可覆盖这三种格式）
     */
    public static ImageType sniff(byte[] head) {
        if (head == null) {
            return null;
        }
        if (head.length >= 3
                && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (head.length >= 8
                && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A
                && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A) {
            return PNG;
        }
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return WEBP;
        }
        return null;
    }
}
