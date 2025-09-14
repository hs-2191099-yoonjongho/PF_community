package com.example.community.domain;

import com.example.community.domain.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "post_images", indexes = @Index(name = "idx_post_images_post", columnList = "post_id"))
public class PostImage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false)
    private String fileKey; // 저장소의 파일 키

    @Column(nullable = false)
    private String originalName; // 원본 파일명

    @Column(nullable = false)
    private String contentType; // 파일 타입 (MIME)

    @Column(nullable = false)
    private long size; // 파일 크기

    @Column(nullable = false)
    private String url; // 접근 가능한 URL

    // 포스트 연결
    public void setPost(Post post) {
        this.post = post;
    }
}
