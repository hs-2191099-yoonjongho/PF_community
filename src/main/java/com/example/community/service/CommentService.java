package com.example.community.service;

import com.example.community.domain.Comment;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.CommentRepository;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import com.example.community.service.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository comments;
    private final PostRepository posts;
    private final MemberRepository members;

    @Transactional
    public Comment add(Long postId, Long authorId, String content) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        Member author = members.findById(authorId)
                .orElseThrow(() -> new EntityNotFoundException("작성자", authorId));
        Comment c = Comment.builder().post(post).author(author).content(content).build();
        return comments.save(c);
    }

    @Transactional(readOnly = true)
    public List<Comment> getByPost(Long postId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        return comments.findByPost(post);
    }

    @Transactional
    public void delete(Long commentId) { 
        if (!comments.existsById(commentId)) {
            throw new EntityNotFoundException("삭제할 댓글", commentId);
        }
        comments.deleteById(commentId); 
    }
}
