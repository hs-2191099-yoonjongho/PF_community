package com.example.community.web.exception;

import com.example.community.service.exception.ForbiddenOperationException;
import com.example.community.service.exception.BusinessException;
import com.example.community.service.exception.EntityNotFoundException;
import com.example.community.service.exception.TokenReuseDetectedException;
import com.example.community.service.exception.WithdrawalException;
import com.example.community.storage.StorageException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 처리하여 일관된 응답 형식을 제공합니다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * DB 락/데드락/타임아웃 등 동시성 예외를 409(CONFLICT)로 매핑 (JPA + Spring DataAccessException)
     */

    @ExceptionHandler({
            // JPA (jakarta) 쪽
            jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.LockTimeoutException.class,
            jakarta.persistence.QueryTimeoutException.class,

            // Spring DataAccessException 쪽
            org.springframework.dao.CannotAcquireLockException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.QueryTimeoutException.class
    })
    public ResponseEntity<Map<String, Object>> dbConflict(RuntimeException e) {
        log.warn("DB 동시성 예외: {}", e.getMessage());
        Map<String, Object> body = base(HttpStatus.CONFLICT, "DB_CONFLICT");
        body.put("message", "일시적 충돌/타임아웃입니다. 잠시 후 다시 시도해주세요.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * 기본 응답 형식 생성
     */
    private Map<String, Object> base(HttpStatus status, String code) {
        Map<String, Object> m = new HashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status.value());
        m.put("code", code);
        return m;
    }

    /**
     * 비즈니스 로직 위반 예외 처리
     * 클라이언트에게 400 Bad Request와 커스텀 에러코드를 반환
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, e.getErrorCode());
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 접근 권한 거부 예외 응답 생성 (공통 메서드)
     */
    private ResponseEntity<Map<String, Object>> createAccessDeniedResponse(String message) {
        Map<String, Object> body = base(HttpStatus.FORBIDDEN, "access_denied");
        body.put("message", message != null ? message : "해당 리소스에 대한 권한이 없습니다");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Spring Security의 메서드 보안(PreAuthorize) 거부 예외 처리
     * 기본적으로 403 Forbidden을 반환한다.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorizationDenied(AuthorizationDeniedException e) {
        return createAccessDeniedResponse(null);
    }

    /**
     * Spring Security의 AccessDeniedException 처리 (필터/인터셉터 단계)
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleSpringAccessDenied(
            org.springframework.security.access.AccessDeniedException e) {
        return createAccessDeniedResponse(null);
    }

    /**
     * Bean Validation 예외 처리 (JSR-380)
     * 요청 객체의 필드 유효성 검증 실패 시 발생
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "validation_failed");
        body.put("errors", e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Bean Validation 예외 처리 (제약 조건 위반)
     * 메서드 파라미터나 반환값의 유효성 검증 실패 시 발생
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "constraint_violation");
        body.put("errors", e.getConstraintViolations().stream()
                .map(cv -> Map.of("property", cv.getPropertyPath().toString(), "message", cv.getMessage()))
                .toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 잘못된 인자 예외 처리
     * 메서드에 잘못된 인자가 전달되었을 때 발생
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "invalid_argument");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 엔티티를 찾을 수 없는 예외 처리 (커스텀)
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException e) {
        Map<String, Object> body = base(HttpStatus.NOT_FOUND, "entity_not_found");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * JPA의 EntityNotFoundException도 404로 매핑 (getReferenceById 등에서 발생)
     */
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleJpaEntityNotFound(jakarta.persistence.EntityNotFoundException e) {
        Map<String, Object> body = base(HttpStatus.NOT_FOUND, "entity_not_found");
        body.put("message", "요청한 엔티티를 찾을 수 없습니다.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * 접근 권한 없음 예외 처리 (서비스 계층)
     */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(ForbiddenOperationException e) {
        return createAccessDeniedResponse(e.getMessage());
    }

    /**
     * 스토리지 관련 예외 처리
     * 내부 구현 정보 노출을 방지하기 위해 일반화된 메시지만 클라이언트에 전달
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorageException(StorageException e) {
        log.error("Storage error", e);
        Map<String, Object> body = base(HttpStatus.INTERNAL_SERVER_ERROR, "storage_error");
        body.put("message", "파일 처리 중 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 파일 업로드 크기 제한 초과 예외 처리
     * 클라이언트에게 413 Payload Too Large 상태 코드를 반환합니다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        Map<String, Object> body = base(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large");
        body.put("message", "업로드 파일 크기가 제한을 초과했습니다. 최대 허용 크기를 확인하세요.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    /**
     * 토큰 재사용 감지 예외 처리
     */
    @ExceptionHandler(TokenReuseDetectedException.class)
    public ResponseEntity<Map<String, Object>> handleTokenReuse(TokenReuseDetectedException e) {
        Map<String, Object> body = base(HttpStatus.UNAUTHORIZED, "token_reused");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * 회원 탈퇴 관련 예외 처리
     */
    @ExceptionHandler(WithdrawalException.class)
    public ResponseEntity<Map<String, Object>> handleWithdrawal(WithdrawalException e) {
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, e.getErrorCode());
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * JSON 파싱 오류 및 타입 불일치 처리
     * 클라이언트가 보낸 JSON이 문법적으로 잘못되었거나
     * 타입이 요청 모델과 일치하지 않을 때 발생
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            org.springframework.validation.BindException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadPayload(Exception e) {
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "bad_request");
        body.put("message", "요청 본문을 해석할 수 없습니다");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 데이터 무결성 위반 예외 처리
     * 보통 고유 제약 조건 위반이나 외래 키 제약 조건 위반 시 발생
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        Map<String, Object> body = base(HttpStatus.CONFLICT, "data_integrity_violation");
        body.put("message", "중복된 데이터이거나 제약 조건을 위반했습니다");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * 지원하지 않는 HTTP 메서드 사용 시 예외 처리 (405 Method Not Allowed)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        Map<String, Object> body = base(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed");
        String[] supported = e.getSupportedMethods();
        String allowed = (supported != null) ? String.join(", ", supported) : "";
        body.put("message", String.format("지원하지 않는 HTTP 메서드입니다. 허용된 메서드: %s", allowed));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    /**
     * 요청한 리소스를 찾을 수 없는 경우 예외 처리 (404 Not Found)
     * 이 핸들러가 작동하려면 다음 설정이 필요합니다:
     * spring.mvc.throw-exception-if-no-handler-found=true
     * spring.web.resources.add-mappings=false
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException e) {
        Map<String, Object> body = base(HttpStatus.NOT_FOUND, "not_found");
        body.put("message", String.format("요청한 리소스를 찾을 수 없습니다: %s %s",
                e.getHttpMethod(), e.getRequestURL()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * 인증 관련 예외 응답 생성 (공통 메서드)
     */
    private ResponseEntity<Map<String, Object>> createAuthenticationErrorResponse(String code, String message) {
        Map<String, Object> body = base(HttpStatus.UNAUTHORIZED, code);
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * 인증 관련 예외 처리
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException e) {
        // BadCredentialsException은 별도 처리
        if (e instanceof BadCredentialsException) {
            return createAuthenticationErrorResponse(
                    "bad_credentials",
                    "사용자명 또는 비밀번호가 올바르지 않습니다");
        }

        return createAuthenticationErrorResponse(
                "authentication_failed",
                "인증에 실패했습니다");
    }

    /**
     * 이미지 처리 예외(InvalidImageException) 처리 (구체 타입)
     * 400 Bad Request + 안전 메시지 반환
     */
    @ExceptionHandler(com.example.community.service.exception.InvalidImageException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidImageException(
            com.example.community.service.exception.InvalidImageException e) {
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "invalid_image");
        body.put("message", "이미지 처리 중 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // 보안 난수 생성기를 static으로 선언하여 재사용
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    /**
     * 기타 모든 예외 처리
     * 예상치 못한 예외가 발생했을 때의 마지막 안전망
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        // 암호학적으로 안전한 난수 생성
        byte[] randomBytes = new byte[16];
        SECURE_RANDOM.nextBytes(randomBytes);
        String errorId = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        log.error("Unexpected error occurred (ID: {})", errorId, e);

        Map<String, Object> body = base(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error");
        // 내부 에러 메시지는 노출하지 않고, 식별용 errorId만 제공(서버 로그와 매칭 용도)
        body.put("message", "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        body.put("errorId", errorId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
