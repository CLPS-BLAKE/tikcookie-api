package com.dss.user.service;

import com.dss.user.model.dto.UpdateProfileDTO;
import com.dss.user.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料。规则见接口文档 5.1.4–5.1.6、需求文档第 2 节（账号）。
 */
public interface UserService {

    /**
     * 当前用户资料；用户不存在时返回 101005。
     */
    UserVO getCurrentUser(Long userId);

    /**
     * 修改昵称 / 头像：传哪个改哪个，都不传不改；改完同步更新 Redis 登录态里的 nickname、avatar
     * （同一用户可能有多个 token，只更新当前请求的 token 即可，其他 token 下次登录时刷新）。
     * 用户不存在时返回 101005。
     * <p>
     * 这里的 avatar 是 fileId（前端先调上传接口拿到），适合"先传图、再连昵称一起提交"的场景；
     * 只想换头像、且不想让前端调两次接口时用 {@link #updateAvatar(Long, MultipartFile)}。
     */
    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 更新头像（一步到位）：接收上传的图片，存进 OSS 拿到 fileId，再当作头像写进用户资料，
     * 返回最新资料（含 avatarUrl，前端拿到即可回填头像框）。
     * <p>
     * 上传走 {@code com.dss.common.file.FileStorageService}，商品图、店铺图、评价图用的是同一套；
     * 校验失败（106001–106004）或上传失败时数据库不动。旧头像对象不删除（教学版允许存在）。
     * 用户不存在时返回 101005。
     */
    UserVO updateAvatar(Long userId, MultipartFile file);
}
