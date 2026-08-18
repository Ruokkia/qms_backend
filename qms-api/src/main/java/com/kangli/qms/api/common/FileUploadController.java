package com.kangli.qms.api.common;

import com.kangli.qms.common.R;
import com.kangli.qms.service.common.FileStorageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

/**
 * 通用文件上传（现场照片等）。返回可访问的相对 URL，由 WebMvcConfig 的 /files/** 映射提供访问。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@Api(tags = "通用-文件上传")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    @ApiOperation(value = "上传单个文件，返回可访问的相对 URL")
    public R<String> upload(
            @ApiParam(value = "文件", required = true) @RequestParam("file") MultipartFile file,
            @ApiParam(value = "子目录，默认 supplier-audit") @RequestParam(defaultValue = "supplier-audit") String subDir) {
        String url = fileStorageService.store(file, subDir);
        return R.ok(url);
    }

    @PostMapping("/upload-batch")
    @ApiOperation(value = "批量上传，返回相对 URL 列表")
    public R<List<String>> uploadBatch(
            @ApiParam(value = "文件列表", required = true) @RequestParam("files") List<MultipartFile> files,
            @ApiParam(value = "子目录，默认 supplier-audit") @RequestParam(defaultValue = "supplier-audit") String subDir) {
        List<String> urls = files.stream().map(f -> fileStorageService.store(f, subDir)).collect(java.util.stream.Collectors.toList());
        return R.ok(urls);
    }
}
