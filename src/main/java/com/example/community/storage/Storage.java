package com.example.community.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 인터페이스
 * 로컬, S3 등 다양한 스토리지 구현체를 지원합니다.
 * 
 * 파일 키는 '{directory}/{memberId}/{uuid.ext}' 형식의 전체 경로를 사용합니다.
 * 이는 다음과 같은 이점이 있습니다:
 * 1. 회원별 파일 분리로 접근 제어 강화
 * 2. 고유 식별자로 파일명 충돌 방지
 * 3. 원본 확장자 유지로 파일 유형 식별 용이
 */
public interface Storage {
    /**
     * 파일 저장 (자동 파일명 생성)
     * @param file 업로드된 파일
     * @param directory 저장 디렉토리 (상대 경로, 예: 'posts')
     * @return 저장된 파일 정보 (자동 생성된 고유 키 포함)
     */
    StoredFile store(MultipartFile file, String directory) throws StorageException;
    
    /**
     * 파일 저장 (지정된 키 사용)
     * @param file 업로드된 파일
     * @param directory 저장 디렉토리 (key가 전체 경로를 포함하므로 이 매개변수는 무시될 수 있음)
     * @param key 저장 파일 키 (전체 경로를 포함한 고유 식별자, 예: 'posts/1234/file.jpg')
     * @return 저장된 파일 정보
     */
    StoredFile store(MultipartFile file, String directory, String key) throws StorageException;
    
    /**
     * 파일 삭제
     * @param key 삭제할 파일 키 (전체 경로를 포함한 고유 식별자)
     */
    void delete(String key) throws StorageException;
    
    /**
     * 파일 URL 생성
     * @param key 파일 키 (전체 경로를 포함한 고유 식별자)
     * @return 접근 가능한 URL
     */
    String url(String key);
    
    /**
     * 파일 존재 여부 확인
     * @param key 파일 키 (전체 경로를 포함한 고유 식별자)
     * @return 존재 여부
     */
    boolean exists(String key);

    /**
     * 저장된 파일 정보
     * 
     * @param key 파일의 고유 식별자 (전체 경로 포함, 예: 'posts/1234/uuid.jpg')
     * @param originalName 원본 파일명
     * @param contentType 파일 MIME 타입
     * @param size 파일 크기 (바이트)
     * @param url 파일 접근 URL
     */
    record StoredFile(String key, String originalName, String contentType, long size, String url) {}
}
