package com.example.community.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.model.PutObjectRequest;
// import software.amazon.awssdk.core.sync.RequestBody;

import java.util.UUID;

@Service
@Profile("s3")
@RequiredArgsConstructor
public class S3StorageService implements Storage {
    // private final S3Client s3;
    @Value("${app.storage.s3.bucket}") String bucket;
    @Value("${app.storage.s3.base-url}") String baseUrl;

    @Override
    public StoredFile store(MultipartFile file, String directory) throws Exception {
        String safeName = sanitize(file.getOriginalFilename());
        String ext = getExt(safeName);
        String key = (directory != null && !directory.isBlank() ? directory + "/" : "")
                + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        // PutObjectRequest req = PutObjectRequest.builder()
        //     .bucket(bucket).key(key)
        //     .contentType(file.getContentType())
        //     .build();
        // s3.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // 데모용(나중에 S3Client 주입/주석 해제)
        String url = baseUrl + "/" + key;
        return new StoredFile(key, safeName, file.getSize(),
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(), url);
    }

    @Override
    public void delete(String key) {
        // s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    @Override
    public String url(String key) {
        return baseUrl + "/" + key;
    }

    private String sanitize(String name) {
        if (name == null) return "unnamed";
        String n = name.replace("\\", "/");
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.replaceAll("[\\r\\n]", "").trim();
    }

    private String getExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > -1 && i < name.length() - 1) ? name.substring(i + 1).toLowerCase() : "";
    }
}
