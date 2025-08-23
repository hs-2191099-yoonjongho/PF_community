package com.example.community.repository;

import com.example.community.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByTitleContainingIgnoreCase(String q, Pageable pageable);
    boolean existsByIdAndAuthor_Username(Long id, String username);
    // ★ username 말고 id 기준으로
    boolean existsByIdAndAuthor_Id(Long postId, Long authorId);
}
