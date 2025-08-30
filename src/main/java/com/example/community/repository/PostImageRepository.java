package com.example.community.repository;

import com.example.community.domain.Post;
import com.example.community.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 이미지 리포지토리
 */
public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    List<PostImage> findByPost(Post post);
    
    @Query("SELECT pi FROM PostImage pi WHERE pi.post.id = :postId")
    List<PostImage> findByPostId(@Param("postId") Long postId);
    
    /**
     * 파일 키로 이미지 조회
     * @param fileKey 파일 키
     * @return 이미지 엔티티 (Optional)
     */
    @Query("SELECT pi FROM PostImage pi WHERE pi.fileKey = :fileKey")
    Optional<PostImage> findByFileKey(@Param("fileKey") String fileKey);
    
    @Modifying
    @Query("DELETE FROM PostImage pi WHERE pi.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
    
    @Modifying
    @Query("DELETE FROM PostImage pi WHERE pi.id = :id AND pi.post.id = :postId")
    void deleteByIdAndPostId(@Param("id") Long id, @Param("postId") Long postId);
}
