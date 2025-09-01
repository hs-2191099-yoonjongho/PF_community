package com.example.community.storage;

import com.example.community.common.FilePolicy;
import com.example.community.util.FileTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.*;

@Service
@Profile({"default","local","prod","test"}) // test 프로필 추가
@RequiredArgsConstructor
public class LocalStorageService implements Storage {

    @Value("${app.storage.local.base-path:uploads}")
    private String basePath;
    @Value("${app.public-base-url}")
    private String publicBaseUrl;
    
    private Path base;
    
    @PostConstruct
    public void init() {
        base = Path.of(basePath).toAbsolutePath().normalize();
        try {
            // 기본 디렉토리 존재 확인 및 생성
            if (!Files.exists(base)) {
                Files.createDirectories(base);
                System.out.println("스토리지 기본 경로 생성됨: " + base);
            } else {
                System.out.println("스토리지 기본 경로 이미 존재함: " + base);
            }
            
            // 디렉토리 쓰기 권한 확인 (권한 변경은 런타임에서 시도하지 않음)
            if (!Files.isWritable(base)) {
                System.err.println("경고: 스토리지 기본 경로에 쓰기 권한이 없습니다: " + base + " (컨테이너/호스트의 소유자 및 권한 설정 확인 필요)");
            }
            
            // posts 서브디렉토리 확인 및 생성
            Path postsDir = base.resolve("posts");
            if (!Files.exists(postsDir)) {
                Files.createDirectories(postsDir);
                System.out.println("posts 디렉토리 생성됨: " + postsDir);
            }
            
            // 확인된 경로 및 권한 정보 출력
            System.out.println("스토리지 설정 완료:");
            System.out.println("- 기본 경로: " + base);
            System.out.println("- 쓰기 가능: " + Files.isWritable(base));
            System.out.println("- 읽기 가능: " + Files.isReadable(base));
            System.out.println("- 실행 가능: " + Files.isExecutable(base));
            
        } catch (Exception e) {
            String message = "스토리지 기본 경로를 생성할 수 없습니다: " + e.getMessage();
            System.err.println(message);
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
            throw new StorageException("파일 키가 null입니다");
        }
        
        if (!FilePolicy.isPathSafe(key)) {
            throw new StorageException("잘못된 경로입니다: " + key);
        }
        
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new StorageException("저장소 외부 접근은 금지됩니다: " + key);
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String directory) throws StorageException {
        try {
            // 업로드 타입 사전 검증 (선택 적용 지점)
            try {
                if (!FileTypeValidator.isAllowed(file, FilePolicy.ALLOWED_IMAGE_TYPES)) {
                    throw new StorageException("허용되지 않은 파일 유형입니다.");
                }
            } catch (Exception e) {
                throw new StorageException("파일 유형 검사 중 오류: " + e.getMessage(), e);
            }

            String safeName = sanitize(file.getOriginalFilename());
            String ext = getExt(safeName);
            String key = (directory != null && !directory.isBlank() ? directory + "/" : "")
                    + java.util.UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
            
            // 안전성 검증
            assertSafeKey(key);
            
            Path target = base.resolve(key).normalize();
            Files.createDirectories(target.getParent());

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String url = publicBaseUrl.replaceAll("/+$", "") + "/" + key.replace("\\", "/");
            return new StoredFile(key, safeName, contentType(file), file.getSize(), url);
        } catch (Exception e) {
            throw new StorageException("파일 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    @Override
    public StoredFile store(MultipartFile file, String directory, String key) throws StorageException {
        try {
            // 업로드 타입 사전 검증 (선택 적용 지점)
            try {
                if (!FileTypeValidator.isAllowed(file, FilePolicy.ALLOWED_IMAGE_TYPES)) {
                    throw new StorageException("허용되지 않은 파일 유형입니다.");
                }
            } catch (Exception e) {
                throw new StorageException("파일 유형 검사 중 오류: " + e.getMessage(), e);
            }

            // 안전성 검증
            assertSafeKey(key);
            
            String safeName = sanitize(file.getOriginalFilename());
            Path target = base.resolve(key).normalize();
            
            // 디렉토리 생성
            Files.createDirectories(target.getParent());

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String url = publicBaseUrl.replaceAll("/+$", "") + "/" + key.replace("\\", "/");
            return new StoredFile(key, safeName, contentType(file), file.getSize(), url);
        } catch (Exception e) {
            throw new StorageException("파일 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) throws StorageException {
        try {
            // 안전성 검증
            assertSafeKey(key);
            
            Path p = base.resolve(key).normalize();
            Files.deleteIfExists(p);
        } catch (Exception e) {
            throw new StorageException("파일 삭제 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public String url(String key) {
        // 키가 null이면 빈 문자열 반환
        if (key == null) {
            return "";
        }
        
        try {
            // 최소한의 안전성 검증 (예외 발생은 허용)
            if (!FilePolicy.isPathSafe(key)) {
                return "";
            }
            
            return publicBaseUrl.replaceAll("/+$", "") + "/" + key.replace("\\", "/");
        } catch (Exception e) {
            return "";
        }
    }
    
    @Override
    public boolean exists(String key) {
        try {
            // 안전성 검증
            assertSafeKey(key);
            
            Path p = base.resolve(key).normalize();
            return Files.exists(p);
        } catch (Exception e) {
            return false;
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
        if (name == null) return "unnamed";
        String n = name.replace("\\", "/");
        n = n.substring(n.lastIndexOf('/') + 1);   // 경로 제거
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
