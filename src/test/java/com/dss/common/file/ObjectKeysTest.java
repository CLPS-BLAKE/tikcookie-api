package com.dss.common.file;

import com.dss.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ObjectKey 校验约定：user / shop / product 保存图片字段前用它挡住非法值和外部 URL。
 * 生成规则 group1/M00/00/00/{uuid}.{后缀} 只有一处实现（ObjectKeys），这里锁住格式。
 */
class ObjectKeysTest {

    @Test
    @DisplayName("生成：group1/M00/00/00/{32 位十六进制}.{后缀}，且每次都不一样")
    void generate() {
        String first = ObjectKeys.generate("jpg");
        String second = ObjectKeys.generate("jpg");

        assertThat(first).matches("group1/M00/00/00/[0-9a-f]{32}\\.jpg");
        assertThat(first).doesNotContain("//");
        assertThat(second).isNotEqualTo(first);
        assertThat(ObjectKeys.isValid(first)).isTrue();
    }

    @Test
    @DisplayName("生成：后缀大小写不敏感，统一转小写")
    void generateNormalizesExtension() {
        assertThat(ObjectKeys.generate("PNG")).endsWith(".png").doesNotContain("//");
        assertThat(ObjectKeys.generate("WebP")).endsWith(".webp").doesNotContain("//");
    }

    @Test
    @DisplayName("生成：不支持的后缀直接抛 IllegalArgumentException（属于编码错误，不是客户端错误）")
    void generateRejectsUnsupportedExtension() {
        assertThatThrownBy(() -> ObjectKeys.generate("gif")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObjectKeys.generate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObjectKeys.generate("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("校验：本服务生成的 key 通过")
    void acceptsGeneratedKey() {
        assertThat(ObjectKeys.isValid(ObjectKeys.generate("png"))).isTrue();
        assertThat(ObjectKeys.isValid("group1/M00/00/00/0123456789abcdef0123456789abcdef.webp")).isTrue();
    }

    @Test
    @DisplayName("校验：外部 URL、本地路径、路径穿越、错误后缀一律拒绝")
    void rejectsForeignValues() {
        assertThat(ObjectKeys.isValid(null)).isFalse();
        assertThat(ObjectKeys.isValid("")).isFalse();
        assertThat(ObjectKeys.isValid("   ")).isFalse();
        // 外部地址：业务方只保存本服务的 ObjectKey，不能存任意外链
        assertThat(ObjectKeys.isValid("https://evil.example.com/a.jpg")).isFalse();
        assertThat(ObjectKeys.isValid("http://img.example.com/group1/M00/00/00/0123456789abcdef0123456789abcdef.jpg")).isFalse();
        assertThat(ObjectKeys.isValid("//evil.example.com/a.jpg")).isFalse();
        // 本地路径和穿越
        assertThat(ObjectKeys.isValid("/etc/passwd")).isFalse();
        assertThat(ObjectKeys.isValid("../../etc/passwd")).isFalse();
        assertThat(ObjectKeys.isValid("group1/M00/00/00/../../secret.jpg")).isFalse();
        // 目录或后缀不对
        assertThat(ObjectKeys.isValid("img/0123456789abcdef0123456789abcdef.jpg")).isFalse();
        assertThat(ObjectKeys.isValid("group1/M00/00/01/0123456789abcdef0123456789abcdef.jpg")).isFalse();
        assertThat(ObjectKeys.isValid("group1/M00/00/00/0123456789abcdef0123456789abcdef.gif")).isFalse();
        assertThat(ObjectKeys.isValid("group1/M00/00/00/short.jpg")).isFalse();
        // 原始文件名不能用（生成端就不接受用户文件名）
        assertThat(ObjectKeys.isValid("group1/M00/00/00/我的头像.jpg")).isFalse();
    }

    @Test
    @DisplayName("识别外部地址：给业务方区分提示用")
    void detectsExternalUrl() {
        assertThat(ObjectKeys.isExternalUrl("https://evil.example.com/a.jpg")).isTrue();
        assertThat(ObjectKeys.isExternalUrl("HTTP://evil.example.com/a.jpg")).isTrue();
        assertThat(ObjectKeys.isExternalUrl("//evil.example.com/a.jpg")).isTrue();
        assertThat(ObjectKeys.isExternalUrl(ObjectKeys.generate("jpg"))).isFalse();
        assertThat(ObjectKeys.isExternalUrl(null)).isFalse();
    }

    @Test
    @DisplayName("requireValid：合法值原样返回")
    void requireValidPassesThrough() {
        String key = ObjectKeys.generate("jpg");

        assertThat(ObjectKeys.requireValid(key)).isSameAs(key);
    }

    @Test
    @DisplayName("requireValid：非法值抛 400 / 40000，且不回显完整入参")
    void requireValidRejects() {
        assertThatThrownBy(() -> ObjectKeys.requireValid("group1/M00/00/00/short.jpg"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40000))
                .hasMessageContaining("图片标识不合法")
                .hasMessageNotContaining("short.jpg");
    }

    @Test
    @DisplayName("前缀常量与接口文档示例一致：group1/M00/00/00，且不带结尾的 /（带了会生成 //）")
    void directoryHasNoTrailingSlash() {
        assertThat(ObjectKeys.DIRECTORY).isEqualTo("group1/M00/00/00");
        assertThat(ObjectKeys.DIRECTORY).doesNotEndWith("/");
    }
}
