package com.example.community.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * 페이징 및 정렬 관련 유틸리티 클래스
 * 정렬 필드 화이트리스트 검증 및 안전한 페이지 요청 생성
 */
public class PageableUtil {
    // 댓글에 허용되는 정렬 필드
    private static final Set<String> ALLOWED_COMMENT_SORT_FIELDS = Set.of(
            "createdAt", "id");

    // 게시글에 허용되는 정렬 필드
    private static final Set<String> ALLOWED_POST_SORT_FIELDS = Set.of(
            "createdAt", "id", "likeCount", "viewCount");

    /**
     * 댓글 정렬을 위한 안전한 Pageable 객체 생성
     * 
     * @param pageable 원본 Pageable 객체
     * @return 화이트리스트 기반으로 검증된 Pageable 객체
     */
    public static Pageable getSafeCommentPageable(Pageable pageable) {
        return getSafePageable(pageable, ALLOWED_COMMENT_SORT_FIELDS);
    }

    /**
     * 게시글 정렬을 위한 안전한 Pageable 객체 생성
     * 
     * @param pageable 원본 Pageable 객체
     * @return 화이트리스트 기반으로 검증된 Pageable 객체
     */
    public static Pageable getSafePostPageable(Pageable pageable) {
        return getSafePageable(pageable, ALLOWED_POST_SORT_FIELDS);
    }

    /**
     * 화이트리스트 기반으로 안전한 Pageable 객체 생성
     * 
     * @param pageable      원본 Pageable 객체
     * @param allowedFields 허용된 정렬 필드 목록
     * @return 화이트리스트 기반으로 검증된 Pageable 객체
     */
    private static Pageable getSafePageable(Pageable pageable, Set<String> allowedFields) {
        Sort sort = pageable.getSort();

        // 정렬이 완전히 없는 경우: createdAt DESC, id DESC 기본 정렬 적용
        if (sort.isUnsorted()) {
            Sort defaultSort = Sort.by(Sort.Direction.DESC, "createdAt")
                    .and(Sort.by(Sort.Direction.DESC, "id"));
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }

        // 정렬이 안전한 경우 그대로 반환하되, id가 없을 때만 DESC 추가
        if (isValidSort(sort, allowedFields)) {
            Sort resultSort = sort;
            boolean hasId = sort.stream().anyMatch(order -> order.getProperty().equals("id"));
            if (!hasId) {
                resultSort = resultSort.and(Sort.by(Sort.Direction.DESC, "id"));
            }
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resultSort);
        }

        // 안전하지 않은 정렬이 있는 경우 화이트리스트 기반으로 필터링
        Sort filteredSort = getFilteredSort(sort, allowedFields);

        // 기본 정렬이 없는 경우 createdAt 내림차순 추가
        if (filteredSort.isUnsorted()) {
            filteredSort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        // id가 정렬에 없을 때만 보조키로 추가
        boolean hasId = filteredSort.stream().anyMatch(order -> order.getProperty().equals("id"));
        if (!hasId) {
            filteredSort = filteredSort.and(Sort.by(Sort.Direction.DESC, "id"));
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), filteredSort);
    }

    /**
     * 주어진 정렬이 허용된 필드만 사용하는지 검증
     */
    private static boolean isValidSort(Sort sort, Set<String> allowedFields) {
        return sort.stream()
                .allMatch(order -> allowedFields.contains(order.getProperty()));
    }

    /**
     * 허용된 필드만 포함하는 정렬 객체 생성
     */
    private static Sort getFilteredSort(Sort sort, Set<String> allowedFields) {
        var filtered = sort.stream()
                .filter(order -> allowedFields.contains(order.getProperty()))
                .toList();
        if (filtered.isEmpty()) {
            return Sort.unsorted();
        }
        return Sort.by(filtered);
    }
}
