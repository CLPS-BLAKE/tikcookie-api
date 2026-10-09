package com.dss.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dss.common.config.DssProperties;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileStorageService;
import com.dss.common.file.FileUrlResolver;
import com.dss.file.model.enums.FileErrorCode;
import com.dss.user.mapper.UserMapper;
import com.dss.user.model.entity.User;
import com.dss.user.model.enums.UserErrorCode;
import com.dss.user.model.vo.UserVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 头像一步更新（PUT /api/v1/users/me/avatar）：收图 → 存 OSS → fileId 写库 → 返回带 avatarUrl 的资料。
 * 上传能力在这里是 mock，真实 OSS 行为由 OssFileStorageTest / OssFileStorageLiveTest 覆盖。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplAvatarTest {

    private static final String FILE_ID = "group1/M00/00/00/3f2b9c4e7a1d4f0e8b6c5a2d1e0f9a8b.jpg";
    private static final String BASE_URL = "https://images.example.com";

    /**
     * LambdaUpdateWrapper 要按实体类的 lambda 缓存把 User::getAvatar 翻成列名，这份缓存在
     * 正常启动时由 MyBatis-Plus 建好；单测没有 Spring 容器，手动初始化一次。
     */
    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @Mock private UserMapper userMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private FileStorageService fileStorageService;
    @Spy private FileUrlResolver fileUrlResolver = resolver();
    @InjectMocks private UserServiceImpl userService;

    private final MockMultipartFile image =
            new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});

    @Test
    void uploadsImageThenStoresFileIdAndReturnsAvatarUrl() {
        when(userMapper.selectById(7L)).thenReturn(user());
        when(fileStorageService.uploadImage(image)).thenReturn(FILE_ID);

        UserVO vo = userService.updateAvatar(7L, image);

        verify(fileStorageService).uploadImage(image);
        assertThat(vo.getAvatar()).isEqualTo(FILE_ID);
        assertThat(vo.getAvatarUrl()).isEqualTo(BASE_URL + "/" + FILE_ID);
        // 真正落库的就是刚上传的 fileId（按 wrapper 里的参数值断言，不看生成的 SQL 片段）
        assertThat(writtenValues()).contains(FILE_ID);
    }

    @Test
    void uploadFailureLeavesDatabaseUntouched() {
        when(userMapper.selectById(7L)).thenReturn(user());
        when(fileStorageService.uploadImage(image))
                .thenThrow(new BizException(FileErrorCode.FILE_UPLOAD_FAILED));

        assertThatThrownBy(() -> userService.updateAvatar(7L, image))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", FileErrorCode.FILE_UPLOAD_FAILED);
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void unknownUserFailsBeforeUploadingAnything() {
        when(userMapper.selectById(7L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateAvatar(7L, image))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(fileStorageService);
    }

    /** 抓取 updateProfile 写库用的 wrapper，取出其中的参数值。 */
    @SuppressWarnings("unchecked")
    private Collection<Object> writtenValues() {
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        return captor.getValue().getParamNameValuePairs().values();
    }

    private static User user() {
        User user = new User();
        user.setId(7L);
        user.setPhone("13800138000");
        user.setNickname("用户8000");
        user.setCreatedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        return user;
    }

    private static FileUrlResolver resolver() {
        DssProperties properties = new DssProperties();
        properties.getFile().setBaseUrl(BASE_URL);
        return new FileUrlResolver(properties);
    }
}
