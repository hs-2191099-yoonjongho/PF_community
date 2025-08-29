package com.example.community.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
@Profile({"default","local","prod"}) // prod에서도 local storage 사용
@RequiredArgsConstructor
public class LocalStorageService implements Storage {

    @Value("${app.storage.local.base-path}")
    private String basePath;
    @Value("${app.storage.public-base-url}")
    private String publicBaseUrl;

    @Override
    public StoredFile store(MultipartFile file, String directory) throws Exception {
        String safeName = sanitize(file.getOriginalFilename());
        String ext = getExt(safeName);
        String key = (directory != null && !directory.isBlank() ? directory + "/" : "")
                + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        Path target = Path.of(basePath).resolve(key).normalize();
        Files.createDirectories(target.getParent());

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String url = publicBaseUrl + "/" + key.replace("\\", "/");
        return new StoredFile(key, safeName, file.getSize(), contentType(file), url);
    }

    @Override
    public void delete(String key) throws Exception {
        Path p = Path.of(basePath).resolve(key).normalize();
        Files.deleteIfExists(p);
    }

    @Override
    public String url(String key) {
        return publicBaseUrl + "/" + key;
    }

    private String contentType(MultipartFile f) {
        return f.getContentType() == null ? "application/octet-stream" : f.getContentType();
    }

    private String sanitize(String name) {
        if (name == null) return "unnamed";
        String n = name.replace("\\", "/");
        n = n.substring(n.lastIndexOf('/') + 1);   // 경로 제거
        return n.replaceAll("[\\r\\n]", "").trim();
    }

    private String getExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > -1 && i < name.length() - 1) ? name.substring(i + 1).toLowerCase() : "";
    }
}
