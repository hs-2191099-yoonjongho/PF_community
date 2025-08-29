package com.example.community.domain;

import com.example.community.domain.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "posts", indexes = @Index(name = "idx_post_author", columnList = "author_id"))
public class Post extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private Member author;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BoardType boardType = BoardType.FREE; // 기본값은 자유게시판

    @Column(nullable = false)
    private long viewCount;

    @Builder.Default  // Builder 패턴에서 기본값 설정
    @Column(nullable = false)
    private long likeCount = 0L;  // 추천수 필드 추가

    @Version // 낙관적 락으로 동시성 제어
    private Long version;
    
    @Builder.Default
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostImage> images = new ArrayList<>();
    
    // 비즈니스 메서드: 조회수 증가
    public void incrementViewCount() {
        this.viewCount++;
    }
    
    // 비즈니스 메서드: 추천수 증가
    public void incrementLikeCount() {
        this.likeCount++;
    }
    
    // 비즈니스 메서드: 추천수 감소 (추천 취소)
    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
    
    // 비즈니스 메서드: 게시글 내용 수정
    public void updateContent(String title, String content) {
        validateTitle(title);
        validateContent(content);
        this.title = title.trim();
        this.content = content.trim();
    }
    
    // private 검증 메서드들
    private void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("제목은 필수입니다");
        }
        if (title.trim().length() > 200) {
            throw new IllegalArgumentException("제목은 200자를 초과할 수 없습니다");
        }
    }
    
    private void validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("내용은 필수입니다");
        }
    }
    
    // 이미지 관련 메서드
    public void addImage(PostImage image) {
        this.images.add(image);
        if (image.getPost() != this) {
            image.setPost(this);
        }
    }
    
    public void removeImage(PostImage image) {
        this.images.remove(image);
        image.setPost(null); // 양방향 관계 끊기 → orphanRemoval 확실히 동작
    }
    
    public void clearImages() {
        images.forEach(image -> image.setPost(null));
        this.images.clear();
    }
}
