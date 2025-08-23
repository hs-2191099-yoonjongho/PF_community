package com.example.community.repository;

import com.example.community.domain.Comment;
import com.example.community.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPost(Post post);
    
    @Query("select c.author.id from Comment c where c.id = :id")
    Optional<Long> findAuthorIdById(@Param("id") Long id);
}
