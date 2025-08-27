package com.example.community.web;

import com.example.community.storage.Storage;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final Storage storage;

    // 업로드 허용 MIME (샘플—필요시 추가/수정)
    private static final Set<String> ALLOWED = Set.of(
        "image/png","image/jpeg","image/webp","application/pdf"
    );
    
    private static final Set<String> DIR_WHITELIST = Set.of("posts","avatars","docs","misc");

    private String safeDir(String dir) {
        if (dir == null || dir.isBlank()) return "misc";
        if (!DIR_WHITELIST.contains(dir)) throw new IllegalArgumentException("허용되지 않는 디렉터리");
        return dir;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<FileRes> upload(
            @RequestPart("file") @NotNull MultipartFile file,
            @RequestParam(value="dir", required = false) String dir // 예: posts, avatars ...
    ) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("파일이 비어있습니다");
        if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("10MB 이하만 업로드 가능합니다");

        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED.contains(ct)) throw new IllegalArgumentException("허용되지 않는 파일 형식: " + ct);

        Storage.StoredFile stored = storage.store(file, safeDir(dir));
        return ResponseEntity.created(URI.create(stored.url()))
                .body(new FileRes(stored.key(), stored.originalName(), stored.size(), stored.contentType(), stored.url()));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam("key") String key) throws Exception {
        storage.delete(key);
        return ResponseEntity.noContent().build();
    }

    public record FileRes(String key, String name, long size, String contentType, String url) {}
}
