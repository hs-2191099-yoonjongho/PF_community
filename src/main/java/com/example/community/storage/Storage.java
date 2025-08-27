package com.example.community.storage;

import org.springframework.web.multipart.MultipartFile;

public interface Storage {
    StoredFile store(MultipartFile file, String directory) throws Exception;
    void delete(String key) throws Exception;
    String url(String key);

    record StoredFile(String key, String originalName, long size, String contentType, String url) {}
}
