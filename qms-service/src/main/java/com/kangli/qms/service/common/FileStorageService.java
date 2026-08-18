package com.kangli.qms.service.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 通用文件存储服务：将上传文件保存到本地磁盘，返回可访问的相对 URL。
 * <p>上传目录由配置项 {@code qms.file-storage.base-dir} 指定（默认 ./uploads）。
 * 通过 WebMvcConfig 的静态资源映射以 /files/** 对外提供访问。</p>
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${qms.file-storage.base-dir:./uploads}")
    private String baseDir;

    private Path root;

    @PostConstruct
    public void init() {
        root = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件存储目录: " + root, e);
        }
    }

    /**
     * 保存文件，返回相对 URL（如 /files/2026-08-17/uuid.jpg）。
     */
    public String store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String dateDir = LocalDate.now().toString();
        Path targetDir = root.resolve(subDir).resolve(dateDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建子目录: " + targetDir, e);
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = targetDir.resolve(filename);
        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException e) {
            throw new IllegalStateException("文件写入失败: " + target, e);
        }
        String relative = "/" + subDir + "/" + dateDir + "/" + filename;
        return "/files" + relative;
    }

    public Path resolve(String relativeUrl) {
        // relativeUrl 形如 /files/xxx → 取 /files 之后的部分
        String suffix = relativeUrl.startsWith("/files") ? relativeUrl.substring("/files".length()) : relativeUrl;
        return root.resolve(suffix.replace("/", File.separator));
    }
}
