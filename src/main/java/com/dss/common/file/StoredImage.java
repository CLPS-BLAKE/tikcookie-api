package com.dss.common.file;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 从 OSS 读到的图片内容，供后端代理读图接口写给浏览器。
 * {@link #content()} 是 OSS 的响应流，必须关闭（用 try-with-resources），
 * 否则底层 HTTP 连接不会归还连接池，读图多了会把连接耗光。
 *
 * @param content       图片字节流
 * @param contentType   OSS 里存的 Content-Type（上传时按识别结果写入）
 * @param contentLength 字节数
 * @param etag          OSS 的 ETag，用作浏览器缓存校验；可能为 null
 */
public record StoredImage(InputStream content, String contentType, long contentLength, String etag)
        implements Closeable {

    @Override
    public void close() throws IOException {
        content.close();
    }
}
