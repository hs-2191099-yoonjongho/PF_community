package com.example.community.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 인터페이스
 * 파일 키는 '{directory}/{memberId}/{uuid.ext}' 형식의 전체 경로를 사용합니다.
 * 모든 메서드는 경로 안전성을 검증하며, 안전하지 않은 경로 접근 시 StorageException을 발생시킵니다.
 */
public interface Storage {
    /**
     * 파일 저장 (자동 파일명 생성)
     * 
     * @param file      업로드된 파일
     * @param directory 저장 디렉토리 (상대 경로, 예: 'posts')
     * @return 저장된 파일 정보 (자동 생성된 고유 키 포함)
     */
    StoredFile store(MultipartFile file, String directory) throws StorageException;

    /**
     * 파일 저장 (지정된 키 사용)
     * 
     * @param file 업로드된 파일
     * @param key  저장 파일 키 (전체 경로를 포함한 고유 식별자, 예: 'posts/1234/file.jpg')
     * @return 저장된 파일 정보
     */
    StoredFile storeWithKey(MultipartFile file, String key) throws StorageException;

    /**
     * 파일 삭제
     * 
     * @param key 삭제할 파일 키 (전체 경로를 포함한 고유 식별자)
     */
    void delete(String key) throws StorageException;

    /**
     * 파일 URL 생성
     * 
     * @param key 파일 키 (전체 경로를 포함한 고유 식별자)
     * @return 접근 가능한 URL
     * @throws StorageException 경로가 안전하지 않거나 처리 중 오류 발생 시
     */
    String url(String key) throws StorageException;

    /**
     * 파일 존재 여부 확인
     * 
     * @param key 파일 키 (전체 경로를 포함한 고유 식별자)
     * @return 존재 여부
     * @throws StorageException 경로가 안전하지 않거나 처리 중 오류 발생 시
     */
    boolean exists(String key) throws StorageException;

    /**
     * 저장된 파일 정보
     * 
     * @param key          파일의 고유 식별자 (전체 경로 포함, 예: 'posts/1234/uuid.jpg')
     * @param originalName 원본 파일명
     * @param contentType  파일 MIME 타입
     * @param size         파일 크기 (바이트)
     * @param url          파일 접근 URL
     */
    record StoredFile(String key, String originalName, String contentType, long size, String url) {
    }
}
