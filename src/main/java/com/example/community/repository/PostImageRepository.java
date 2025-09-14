package com.example.community.repository;

import com.example.community.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;



/**
 * 게시글 이미지 엔티티에 대한 데이터 접근 인터페이스
 * 이미지 조회 및 관리 기능을 제공합니다.
 */
public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    /**
     * 파일 키와 게시글 작성자 ID가 모두 일치할 때만 삭제 (TOCTOU 방지)
     * 
     * @param fileKey  파일 키
     * @param authorId 작성자 ID
     * @return 삭제된 row 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    int deleteByFileKeyAndPost_Author_Id(String fileKey, Long authorId);

}
