package uz.xitlar.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uz.xitlar.dto.common.ResponseApi;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataNotFoundException.class)
    public ResponseEntity<ResponseApi<Void>> handleDataNotFound(DataNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ResponseApi<Void>> handleDuplicateEntity(DuplicateEntityException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ResponseApi<Void>> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Database integrity violation: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, "Database constraint violation or duplicate entity");
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ResponseApi<Void>> handlePasswordMismatch(PasswordMismatchException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(SelfRoleChangeException.class)
    public ResponseEntity<ResponseApi<Void>> handleSelfRoleChange(SelfRoleChangeException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseApi<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("Malformed request body: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ResponseApi<Void>> handleBadCredentials(BadCredentialsException e) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(OAuthAuthenticationException.class)
    public ResponseEntity<ResponseApi<Void>> handleOAuthAuthentication(OAuthAuthenticationException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseApi<Void>> handleAccessDenied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseApi<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApi<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ResponseApi<Void>> handleFileStorage(FileStorageException e) {
        log.error("File storage error: {}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "File storage operation failed");
    }

    @ExceptionHandler(InvalidAudioFileException.class)
    public ResponseEntity<ResponseApi<Void>> handleInvalidAudioFile(InvalidAudioFileException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ResponseApi<Void>> handleUnsupportedFileType(UnsupportedFileTypeException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseApi<Void>> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds the maximum allowed limit");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseApi<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResponseApi<Void>> handleMethodArgumentTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        String message = String.format("Parameter '%s' must be of type '%s'", e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "valid type");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<ResponseApi<Void>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ResponseApi.<Void>builder()
                        .success(false)
                        .message(message)
                        .build());
    }
}
