package com.dss.common.file;

import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * OSS ObjectKey 的生成与校验（唯一来源）。fileId 就是 ObjectKey，规则见中间件配置第 6 节。
 *     写库前用 {@link #requireValid(String)} 校验，只接受本服务生成的 ObjectKey；
 *     前端传的外部 URL http://、https:// 开头）、本地路径、带 {@code ..} 的相对路径一律拒绝，
 *         避免把任意外链存进库里；需要判断而不抛异常时用 {@link #isValid(String)}；
 *     展示时用 {@link FileUrlResolver#toUrl(String)} 把 ObjectKey 拼成完整 URL。
 * "对象来源/存在性"的业务校验（比如这张图是不是真的存在）按需用 OSS HeadObject 判断，
 * 不在本类的范围内：本类只保证格式合法、且一定由本服务生成。
 */
public final class ObjectKeys {

    /**
     * ObjectKey 的固定前缀，形如 group1/M00/00/00/{32 位十六进制 uuid}.{后缀}。
     * 约定：这里不带结尾的 "/"，斜杠由 {@link #generate(String)} 和 PATTERN 统一补。
     * 写成 "group1/M00/00/00/" 会生成 group1/M00/00/00//xxx.png（多一个空路径段），
     * URL 被 CDN/代理规范化成单斜杠后取不到图。
     */
    public static final String DIRECTORY = "group1/M00/00/00";

    /** 允许的图片后缀；小写比较。 */
    public static final String EXTENSION_JPG = "jpg";
    public static final String EXTENSION_PNG = "png";
    public static final String EXTENSION_WEBP = "webp";

    /**
     * 合法 ObjectKey：固定目录 + 后端生成的随机文件名（32 位十六进制 UUID，无连字符）+ 允许的后缀。
     * 用和生成端一致的严格规则，前端自造的路径（子目录、其它后缀、长文件名）都会被拒。
     */
    private static final Pattern PATTERN =
            Pattern.compile("^" + Pattern.quote(DIRECTORY) + "/[0-9a-f]{32}\\.(jpg|png|webp)$");

    private ObjectKeys() {
    }

    /**
     * 生成一个新的 ObjectKey，如 img/3f2b...9c.jpg。
     * 文件名用 UUID，不使用用户上传的原始文件名（避免路径穿越和重名覆盖）。
     *
     * @param extension 允许的后缀之一：jpg / png / webp（大小写不敏感）
     */
    public static String generate(String extension) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!EXTENSION_JPG.equals(ext) && !EXTENSION_PNG.equals(ext) && !EXTENSION_WEBP.equals(ext)) {
            throw new IllegalArgumentException("不支持的后缀：" + extension);
        }
        return DIRECTORY + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
    }

    /** 是否是本服务生成的合法 ObjectKey。null、空串、外部 URL、本地路径都返回 false。 */
    public static boolean isValid(String fileId) {
        return fileId != null && PATTERN.matcher(fileId).matches();
    }

    /** 是不是前端直接塞过来的外部地址；给业务方做更明确的错误提示用。 */
    public static boolean isExternalUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//");
    }

    /**
     * 业务写库前校验 fileId：不合法就抛 400 / 40000。
     *
     * @return 原样返回入参，方便链式使用
     */
    public static String requireValid(String fileId) {
        if (isValid(fileId)) {
            return fileId;
        }
        // 不回显完整入参，避免把外部地址原样写进日志和响应
        if (isExternalUrl(fileId)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "图片地址不合法：请先调用上传接口，只保存返回的 fileId");
        }
        throw new BizException(CommonErrorCode.BAD_REQUEST, "图片标识不合法：请先调用上传接口，只保存返回的 fileId");
    }
}
