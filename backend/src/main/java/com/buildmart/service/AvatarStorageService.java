package com.buildmart.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores mini-program user avatars on local disk. Files land in
 * {@code <upload-dir>/avatars} and are served back through the
 * {@code /files/**} resource handler registered in WebConfig, so the value
 * returned here is a context-relative URL path rather than a file path.
 */
@Service
public class AvatarStorageService {
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private final Path avatarDir;

    public AvatarStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.avatarDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("avatars");
    }

    /**
     * Persist the uploaded avatar and return its public URL path
     * (relative to the server context path, e.g. {@code /files/avatars/1-abc.jpg}).
     */
    public String save(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new IllegalStateException("用户未登录");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("头像文件不能超过 5MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String filename = userId + "-" + UUID.randomUUID().toString().replace("-", "") + extension;
        try {
            Files.createDirectories(avatarDir);
            Path target = avatarDir.resolve(filename).normalize();
            if (!target.startsWith(avatarDir)) {
                throw new IllegalArgumentException("头像文件名不合法");
            }
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/files/avatars/" + filename;
        } catch (IOException ex) {
            throw new IllegalStateException("头像保存失败，请稍后重试", ex);
        }
    }

    private String extensionOf(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.trim().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot) : "";
        // wx.chooseAvatar temp files are always jpeg/png; fall back to .jpg
        // when the client supplies no usable extension.
        return ALLOWED_EXTENSIONS.contains(extension) ? extension : ".jpg";
    }
}
