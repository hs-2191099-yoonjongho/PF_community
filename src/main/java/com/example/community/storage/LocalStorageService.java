package com.example.community.storage;

import com.example.community.common.FilePolicy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@Service
@Profile({ "default", "local", "prod", "test" }) // test 프로필 추가
@RequiredArgsConstructor
public class LocalStorageService implements Storage {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${app.storage.local.base-path:uploads}")
    private String basePath;
    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    private Path base;

    // 안전 임시파일 디렉터리(.tmp) 경로
    private Path tempDir;

    @PostConstruct
    public void init() {
        base = Path.of(basePath).toAbsolutePath().normalize();
        try {
            // 기본 디렉토리 존재 확인 및 생성
            if (!Files.exists(base)) {
                Files.createDirectories(base);
                log.info("스토리지 기본 경로 생성됨: {}", base);
            } else {
                log.info("스토리지 기본 경로 이미 존재함: {}", base);
            }

            // 디렉토리 쓰기 권한 확인 (권한 변경은 런타임에서 시도하지 않음)
            if (!Files.isWritable(base)) {
                log.warn("경고: 스토리지 기본 경로에 쓰기 권한이 없습니다: {} (컨테이너/호스트의 소유자 및 권한 설정 확인 필요)", base);
            }

            // posts 서브디렉토리 확인 및 생성
            Path postsDir = base.resolve("posts");
            if (!Files.exists(postsDir)) {
                Files.createDirectories(postsDir);
                log.info("posts 디렉토리 생성됨: {}", postsDir);
            }

            // 안전 임시파일 디렉터리(.tmp) 확인 및 생성
            tempDir = base.resolve(".tmp");
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
                log.info("임시파일 디렉토리 생성됨: {}", tempDir);
            }
            // .tmp 디렉터리 symlink 여부 검사
            if (Files.isSymbolicLink(tempDir)) {
                throw new StorageException("임시 디렉토리가 심볼릭 링크입니다: " + tempDir);
            }
            // publicBaseUrl 필수 검사
            if (publicBaseUrl == null || publicBaseUrl.trim().isEmpty()) {
                throw new StorageException("publicBaseUrl 설정이 비어 있습니다. app.public-base-url 을 설정하세요.");
            }
            log.info("스토리지 설정 완료:");
            log.info("- 기본 경로: {}", base);
            log.info("- 쓰기 가능: {}", Files.isWritable(base));
            log.info("- 읽기 가능: {}", Files.isReadable(base));
            log.info("- 실행 가능: {}", Files.isExecutable(base));
        } catch (Exception e) {
            String message = "스토리지 기본 경로를 생성할 수 없습니다: " + e.getMessage();
            log.error(message, e);
            throw new StorageException(message, e);
        }
    }

    /**
     * 키 경로 안전성 검증
     * 1. 상위 디렉터리 접근, 백슬래시 등 위험한 문자 검증 (FilePolicy.isPathSafe)
     * 2. 대상 경로가 base 경로 내에 있는지 확인 (경로 탈출 방지)
     * 
     * @param key 검증할 파일 키
     * @throws StorageException 안전하지 않은 경로인 경우 발생
     */
    private void assertSafeKey(String key) {
        if (key == null) {
            log.warn("파일 키가 null입니다");
            throw new StorageException("파일 키가 null입니다");
        }

        if (!FilePolicy.isPathSafe(key)) {
            log.warn("잘못된 경로 접근 시도: {}", key);
            throw new StorageException("잘못된 경로입니다: " + key);
        }

        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            log.warn("저장소 외부 접근 시도: {}", key);
            throw new StorageException("저장소 외부 접근은 금지됩니다: " + key);
        }

        // TOCTOU 공격 방지를 위한 canonical path 검증
        if (Files.exists(target)) {
            try {
                Path realTarget = target.toRealPath();
                Path realBase = base.toRealPath();
                if (!realTarget.startsWith(realBase)) {
                    log.warn("실제 경로가 저장소 외부입니다: {}", realTarget);
                    throw new StorageException("실제 파일 경로가 저장소 외부입니다: " + key);
                }
            } catch (IOException e) {
                log.error("실제 경로 확인 중 오류 발생: {}", e.getMessage(), e);
                throw new StorageException("경로 검증 중 오류 발생: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 경로 내 심볼릭 링크 확인
     * 대상 경로부터 base 경로까지 모든 상위 디렉토리를 확인하여 심볼릭 링크가 있는지 검사
     * 
     * @param target 검사할 경로
     * @throws StorageException 경로 내에 심볼릭 링크가 있는 경우 발생
     * @throws IOException      파일 시스템 접근 중 오류 발생 시
     */
    private void assertNoSymlinkInPath(Path target) throws IOException {
        Path p = target;
        while (p != null && !p.equals(base)) {
            if (Files.isSymbolicLink(p)) {
                log.warn("심볼릭 링크 경로 접근 시도: {}", p);
                throw new StorageException("심볼릭 링크 경로는 허용되지 않습니다: " + p);
            }
            p = p.getParent();
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String directory) throws StorageException {
        // 암호학적으로 안전한 랜덤 파일명 생성
        byte[] randomBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(randomBytes);
        String randomId = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String safeName = sanitize(file.getOriginalFilename());
        String ext = getExt(safeName);

        String key = (directory != null && !directory.isBlank() ? directory + "/" : "")
                + randomId + (ext.isEmpty() ? "" : "." + ext);

        return storeInternal(file, key);
    }

    @Override
    public StoredFile storeWithKey(MultipartFile file, String key) throws StorageException {
        return storeInternal(file, key);
    }

    /**
     * 내부 파일 저장 로직 
     * 
     * @param file 저장할 파일
     * @param key  저장 경로와 파일명
     * @return 저장된 파일 정보
     * @throws StorageException 저장 중 오류 발생 시
     */
    private StoredFile storeInternal(MultipartFile file, String key) throws StorageException {
        try {
            if (!FilePolicy.isAllowed(file, FilePolicy.ALLOWED_IMAGE_TYPES)) {
                throw new StorageException("허용되지 않은 파일 유형입니다.");
            }
            assertSafeKey(key);
            Path target = base.resolve(key).normalize();
            assertNoSymlinkInPath(target);
            String safeName = sanitize(file.getOriginalFilename());
            Files.createDirectories(target.getParent());
            String tempPrefix = "upload-";
            String tempSuffix = ".tmp";
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile(tempDir, tempPrefix, tempSuffix);
            } catch (IOException e) {
                log.error("임시파일 생성 실패: {}", e.getMessage(), e);
                throw new StorageException("임시파일 생성 실패: " + e.getMessage(), e);
            }
            try {
                if (Files.isSymbolicLink(tempFile) || !Files.isRegularFile(tempFile, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(tempFile);
                    throw new StorageException("임시파일이 심볼릭 링크이거나 regular file이 아닙니다: " + tempFile);
                }
            } catch (IOException e) {
                Files.deleteIfExists(tempFile);
                throw new StorageException("임시파일 검증 실패: " + e.getMessage(), e);
            }
            // 2. 임시파일에 먼저 저장 (REPLACE_EXISTING 적용)
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            // 3. 타깃 파일이 이미 존재하면 예외(덮어쓰기 금지)
            if (Files.exists(target)) {
                Files.deleteIfExists(tempFile);
                throw new StorageException("동일한 파일이 이미 존재합니다: " + key);
            }
            // 4. 이동 전 symlink/regular file/부모 toRealPath() 재확인
            assertNoSymlinkInPath(target);
            if (Files.isSymbolicLink(target)
                    || (Files.exists(target) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
                Files.deleteIfExists(tempFile);
                throw new StorageException("타깃 경로가 심볼릭 링크이거나 regular file이 아닙니다: " + target);
            }
            assertNoSymlinkInPath(target.getParent());
            Path realBase = base.toRealPath();
            Path parentReal = target.getParent().toRealPath();
            if (!parentReal.startsWith(realBase)) {
                Files.deleteIfExists(tempFile);
                throw new StorageException("타깃 부모 디렉토리가 저장소 외부입니다: " + parentReal);
            }
            // 타깃 자체 regular file + symlink 여부 최종 검사(쓰기 직전)
            if (Files.exists(target)
                    && (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
                Files.deleteIfExists(tempFile);
                throw new StorageException("타깃 경로가 심볼릭 링크이거나 regular file이 아닙니다(최종): " + target);
            }
            boolean moved = false;
            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
                moved = true;
            } catch (AtomicMoveNotSupportedException e) {
                log.warn("ATOMIC_MOVE 미지원: 일반 MOVE로 대체 (경쟁 조건 위험 있음): {}", e.getMessage());
                try {
                    Files.move(tempFile, target);
                    moved = true;
                } catch (Exception moveEx) {
                    Files.deleteIfExists(tempFile);
                    throw new StorageException("파일 이동 실패: " + moveEx.getMessage(), moveEx);
                }
            } catch (Exception e) {
                Files.deleteIfExists(tempFile);
                throw new StorageException("파일 이동 실패: " + e.getMessage(), e);
            }
            // 6. 이동 후 최종 경로 symlink/regular file 검사 (NOFOLLOW)
            assertNoSymlinkInPath(target);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
                if (!moved)
                    Files.deleteIfExists(tempFile);
                throw new StorageException("저장된 파일이 regular file이 아니거나 symlink입니다: " + target);
            }
            String url = publicBaseUrl.replaceAll("/+$", "") + "/" + key.replace("\\", "/");
            return new StoredFile(key, safeName, contentType(file), file.getSize(), url);
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("파일 저장 중 오류 발생: {}", e.getMessage(), e);
            throw new StorageException("파일 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) throws StorageException {
        try {
            assertSafeKey(key);
            Path p = base.resolve(key).normalize();
            assertNoSymlinkInPath(p);
            // 타깃 자체 regular file + symlink 여부 최종 검사
            if (Files.exists(p) && (Files.isSymbolicLink(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))) {
                throw new StorageException("지원하지 않는 파일 유형입니다(regular file만 허용): " + p);
            }
            boolean deleted = Files.deleteIfExists(p);
            if (deleted) {
                log.debug("파일 삭제됨: {}", key);
            } else {
                log.debug("삭제할 파일이 없음: {}", key);
            }
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("파일 삭제 중 오류: {}", e.getMessage(), e);
            throw new StorageException("파일 삭제 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public String url(String key) throws StorageException {
        if (key == null) {
            throw new StorageException("파일 키가 null입니다");
        }
        try {
            assertSafeKey(key);
            Path p = base.resolve(key).normalize();
            assertNoSymlinkInPath(p);
            // 타깃 자체 regular file + symlink 여부 최종 검사
            if (Files.exists(p) && (Files.isSymbolicLink(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))) {
                throw new StorageException("지원하지 않는 파일 유형입니다(regular file만 허용): " + p);
            }
            return publicBaseUrl.replaceAll("/+$", "") + "/" + key.replace("\\", "/");
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("URL 생성 중 오류: {}", e.getMessage());
            throw new StorageException("URL 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String key) throws StorageException {
        try {
            if (key == null) {
                throw new StorageException("파일 키가 null입니다");
            }
            assertSafeKey(key);
            Path p = base.resolve(key).normalize();
            assertNoSymlinkInPath(p);
            // 타깃 자체 regular file + symlink 여부 최종 검사
            if (Files.exists(p) && (Files.isSymbolicLink(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))) {
                throw new StorageException("지원하지 않는 파일 유형입니다(regular file만 허용): " + p);
            }
            return Files.exists(p);
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("파일 존재 확인 중 오류: {}", e.getMessage());
            throw new StorageException("파일 존재 확인 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private String contentType(MultipartFile f) {
        return f.getContentType() == null ? "application/octet-stream" : f.getContentType();
    }

    /**
     * 파일 이름 정제 (안전성 처리)
     * 1. 경로 구분자 처리 (백슬래시를 슬래시로 변환)
     * 2. 상대 경로 제거 (마지막 슬래시 이후 부분만 사용)
     * 3. 줄바꿈 문자 제거 및 앞뒤 공백 제거
     * 
     * @param name 원본 파일명
     * @return 정제된 파일명
     */
    private String sanitize(String name) {
        if (name == null)
            return "unnamed";
        String n = name.replace("\\", "/");
        n = n.substring(n.lastIndexOf('/') + 1); // 경로 제거
        return n.replaceAll("[\\r\\n]", "").trim();
    }

    /**
     * 파일 확장자 추출
     * 
     * @param name 파일명
     * @return 소문자로 변환된 확장자 (확장자가 없는 경우 빈 문자열)
     */
    private String getExt(String name) {
        int i = name.lastIndexOf('.');
        return (i > -1 && i < name.length() - 1) ? name.substring(i + 1).toLowerCase() : "";
    }
}
