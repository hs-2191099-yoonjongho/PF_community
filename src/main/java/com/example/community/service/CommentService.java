package com.example.community.service;

import com.example.community.domain.Comment;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.CommentRepository;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository comments;
    private final PostRepository posts;
    private final MemberRepository members;

    @Transactional
    public Comment add(Long postId, Long authorId, String content) {
        Post post = posts.findById(postId).orElseThrow();
        Member author = members.findById(authorId).orElseThrow();
        Comment c = Comment.builder().post(post).author(author).content(content).build();
        return comments.save(c);
    }

    @Transactional
    public void delete(Long commentId) { comments.deleteById(commentId); }
}
