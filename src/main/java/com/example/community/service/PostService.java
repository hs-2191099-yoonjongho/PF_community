package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import com.example.community.service.dto.PostDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository posts;
    private final MemberRepository members;

    @Transactional
    public Post create(Long authorId, PostDtos.Create req) {
        Member author = members.findById(authorId).orElseThrow();
        Post p = Post.builder()
                .author(author)
                .title(req.title())
                .content(req.content())
                .viewCount(0)
                .build();
        return posts.save(p);
    }

    @Transactional(readOnly = true)
    public Page<Post> search(String q, Pageable pageable) {
        return (q == null || q.isBlank())
                ? posts.findAll(pageable)
                : posts.findByTitleContainingIgnoreCase(q, pageable);
    }

    @Transactional(readOnly = true)
    public Post get(Long id) { return posts.findById(id).orElseThrow(); }

    @Transactional
    public Post update(Long id, PostDtos.Update req) {
        Post p = posts.findById(id).orElseThrow();
        p.setTitle(req.title());
        p.setContent(req.content());
        return p;
    }

    @Transactional
    public void delete(Long id) { posts.deleteById(id); }
}
