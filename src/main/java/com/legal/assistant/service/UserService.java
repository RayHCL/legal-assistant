package com.legal.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.legal.assistant.dto.request.ChangePhoneRequest;
import com.legal.assistant.dto.request.UpdateUserRequest;
import com.legal.assistant.dto.response.UserInfoResponse;
import com.legal.assistant.entity.*;
import com.legal.assistant.enums.FileType;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.*;
import com.legal.assistant.utils.FileUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private ConversationMapper conversationMapper;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private DocumentFileMapper documentFileMapper;
    
    @Autowired
    private ShareMapper shareMapper;
    
    @Autowired
    private ReportMapper reportMapper;
    
    @Autowired
    private MinioClient minioClient;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private FileService fileService;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    @Value("${business.user.avatar-max-size:5242880}")
    private Long avatarMaxSize;
    

    
    /**
     * 获取用户信息
     */
    public UserInfoResponse getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        
        UserInfoResponse response = new UserInfoResponse();
        response.setId(user.getId().intValue());
        response.setPhoneNumber(maskPhone(user.getPhone()));  // 脱敏
        response.setNickName(user.getNickname());
        response.setBio(user.getBio());
        response.setAvatarUrl(user.getAvatar());
        
        return response;
    }
    
    /**
     * 更新用户信息
     */
    @Transactional
    public void updateUserInfo(Long userId, UpdateUserRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        
        if (request.getNickName() != null && !request.getNickName().trim().isEmpty()) {
            user.setNickname(request.getNickName().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatar(request.getAvatarUrl());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        log.info("更新用户信息: userId={}", userId);
    }
    
    
    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 上传用户头像
     */
    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        // 1. 验证用户存在
        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        
        // 2. 验证文件类型（必须是图片）
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED.getCode(), "文件名不能为空");
        }
        
        FileType fileType = FileUtils.getFileType(originalFilename);
        if (fileType == null || !fileType.isImage()) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED.getCode(), 
                    "不支持的文件类型，仅支持jpg、jpeg、png、gif、bmp、tiff、webp格式的图片");
        }
        
        // 3. 验证文件大小
        if (!FileUtils.validateFileSize(file, avatarMaxSize)) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE.getCode(), 
                    String.format("文件大小超出限制，最大允许%dMB", avatarMaxSize / 1024 / 1024));
        }
        
        // 4. 上传文件到MinIO
        String minioPath;
        try {
            String extension = FileUtils.getFileExtension(originalFilename);
            String objectName = "avatars/" + userId + "_" + System.currentTimeMillis() + "_" +
                    UUID.randomUUID().toString().replace("-", "") + "." + extension;
            
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            
            minioPath = objectName;
            log.info("头像上传到MinIO成功: userId={}, objectName={}", userId, objectName);
        } catch (Exception e) {
            log.error("上传头像到MinIO失败: userId={}, filename={}", userId, originalFilename, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED.getCode(), "上传头像失败: " + e.getMessage());
        }
        
        // 5. 构建头像访问URL
        String avatarUrl = fileService.buildDownloadUrl(minioPath);
        
        // 6. 更新用户头像
        user.setAvatar(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        log.info("用户头像更新成功: userId={}, avatarUrl={}", userId, avatarUrl);
        return avatarUrl;
    }

    /**
     * 修改手机号（修改成功后需用新手机号重新登录）
     */
    @Transactional
    public void changePhoneNumber(Long userId, ChangePhoneRequest request, String token) {
        // 1. 验证用户存在
        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        
        // 2. 验证新手机号不能与当前手机号相同
        if (request.getNewPhoneNumber().equals(user.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_INVALID.getCode(), "新手机号不能与当前手机号相同");
        }
        
        // 3. 检查新手机号是否已被使用
        User existingUser = userMapper.selectByPhone(request.getNewPhoneNumber());
        if (existingUser != null && !existingUser.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PHONE_INVALID.getCode(), "该手机号已被其他用户使用");
        }
        
        // 4. 验证验证码
        String codeKey = "sms:code:" + request.getNewPhoneNumber();
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.equals(request.getVerificationCode())) {
            throw new BusinessException(ErrorCode.CODE_INVALID.getCode(), ErrorCode.CODE_INVALID.getMessage());
        }
        
        // 5. 验证成功后删除验证码
        redisTemplate.delete(codeKey);
        
        // 6. 更新用户手机号
        String oldPhone = user.getPhone();
        user.setPhone(request.getNewPhoneNumber());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        // 7. 使当前及所有旧 token 失效，要求用新手机号重新登录
        if (token != null && !token.isEmpty()) {
            try {
                redisTemplate.opsForValue().set("token:blacklist:" + token, "1", 7, TimeUnit.DAYS);
                log.info("修改手机号后将当前token加入黑名单: userId={}", userId);
            } catch (Exception e) {
                log.warn("修改手机号时加入token黑名单失败: userId={}", userId, e);
            }
        }
        redisTemplate.opsForValue().set("user:relogin:" + userId, "1", 7, TimeUnit.DAYS);
        log.info("标记用户需重新登录: userId={}", userId);
        
        log.info("用户手机号修改成功: userId={}, oldPhone={}, newPhone={}", 
                userId, oldPhone, request.getNewPhoneNumber());
    }

    /**
     * 注销账户（逻辑删除所有相关数据）
     */
    @Transactional
    public void deactivateAccount(Long userId, String token) {
        // 1. 验证用户存在
        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }

        log.info("开始注销用户账户: userId={}, phone={}", userId, user.getPhone());

        // 2. 先获取用户的所有会话ID（在删除前获取）
        LambdaQueryWrapper<Conversation> conversationQueryWrapper = new LambdaQueryWrapper<>();
        conversationQueryWrapper.eq(Conversation::getUserId, userId)
                .eq(Conversation::getIsDeleted, false)
                .select(Conversation::getId);
        List<Conversation> conversations = conversationMapper.selectList(conversationQueryWrapper);
        List<Long> conversationIds = conversations.stream()
                .map(Conversation::getId)
                .collect(Collectors.toList());

        // 3. 逻辑删除所有消息（通过会话ID）
        int messageCount = 0;
        if (!conversationIds.isEmpty()) {
            LambdaUpdateWrapper<Message> messageWrapper = new LambdaUpdateWrapper<>();
            messageWrapper.in(Message::getConversationId, conversationIds)
                    .eq(Message::getIsDeleted, false)
                    .set(Message::getIsDeleted, true)
                    .set(Message::getUpdatedAt, LocalDateTime.now());
            messageCount = messageMapper.update(null, messageWrapper);
            log.info("逻辑删除消息数量: userId={}, count={}", userId, messageCount);
        }

        // 4. 逻辑删除所有会话
        LambdaUpdateWrapper<Conversation> conversationWrapper = new LambdaUpdateWrapper<>();
        conversationWrapper.eq(Conversation::getUserId, userId)
                .eq(Conversation::getIsDeleted, false)
                .set(Conversation::getIsDeleted, true)
                .set(Conversation::getDeletedAt, LocalDateTime.now())
                .set(Conversation::getUpdatedAt, LocalDateTime.now());
        int conversationCount = conversationMapper.update(null, conversationWrapper);
        log.info("逻辑删除会话数量: userId={}, count={}", userId, conversationCount);

        // 5. 逻辑删除所有上传的文件
        LambdaUpdateWrapper<DocumentFile> fileWrapper = new LambdaUpdateWrapper<>();
        fileWrapper.eq(DocumentFile::getUserId, userId)
                .eq(DocumentFile::getIsDeleted, false)
                .set(DocumentFile::getIsDeleted, true);
        int fileCount = documentFileMapper.update(null, fileWrapper);
        log.info("逻辑删除文件数量: userId={}, count={}", fileCount);

        // 6. 逻辑删除所有分享
        LambdaUpdateWrapper<Share> shareWrapper = new LambdaUpdateWrapper<>();
        shareWrapper.eq(Share::getUserId, userId)
                .eq(Share::getIsDeleted, false)
                .set(Share::getIsDeleted, true);
        int shareCount = shareMapper.update(null, shareWrapper);
        log.info("逻辑删除分享数量: userId={}, count={}", shareCount);

        // 7. 逻辑删除所有报告
        LambdaUpdateWrapper<Report> reportWrapper = new LambdaUpdateWrapper<>();
        reportWrapper.eq(Report::getUserId, userId)
                .eq(Report::getIsDeleted, false)
                .set(Report::getIsDeleted, true)
                .set(Report::getUpdatedAt, LocalDateTime.now());
        int reportCount = reportMapper.update(null, reportWrapper);
        log.info("逻辑删除报告数量: userId={}, count={}", reportCount);

        // 8. 将当前token加入黑名单
        if (token != null && !token.isEmpty()) {
            try {
                redisTemplate.opsForValue().set("token:blacklist:" + token, "1", 7, TimeUnit.DAYS);
                log.info("注销账户时将token加入黑名单: userId={}, token={}", userId, token.substring(0, Math.min(20, token.length())) + "...");
            } catch (Exception e) {
                log.warn("注销账户时加入token黑名单失败: userId={}", userId, e);
            }
        }

        // 9. 标记用户的所有token失效（使用userId作为key）
        redisTemplate.opsForValue().set("user:deleted:" + userId, "1", 30, TimeUnit.DAYS);
        log.info("标记用户所有token失效: userId={}", userId);

        // 10. 最后逻辑删除用户
        user.setIsDeleted(true);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户账户注销完成: userId={}, phone={}, 删除会话={}, 删除消息={}, 删除文件={}, 删除分享={}, 删除报告={}",
                userId, user.getPhone(), conversationCount, messageCount, fileCount, shareCount, reportCount);
    }
    
}
