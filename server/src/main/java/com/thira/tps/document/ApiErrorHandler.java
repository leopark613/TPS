package com.thira.tps.document;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiErrorHandler.class);
    @ExceptionHandler(ApiException.class) ResponseEntity<ApiDtos.ErrorResponse> known(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiDtos.ErrorResponse(ex.getCode(),ex.getMessage(),ex.getDiagnostics(),ex.getCurrentHeadEtag()));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class}) ResponseEntity<ApiDtos.ErrorResponse> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiDtos.ErrorResponse("INVALID_REQUEST","요청 항목을 확인하세요.",List.of(),null));
    }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<ApiDtos.ErrorResponse> database(DataAccessException ex) {
        // Do not log the exception message/SQL parameters or an entire document.
        log.warn("Document database operation failed ({})",ex.getClass().getSimpleName());
        return ResponseEntity.status(503).body(new ApiDtos.ErrorResponse("SAVE_RETRY_REQUIRED","저장소 연결을 확인하세요. 저장 요청은 결과 확인 후 같은 요청으로 다시 시도하세요.",List.of(),null));
    }
    @ExceptionHandler(Exception.class) ResponseEntity<ApiDtos.ErrorResponse> unexpected(Exception ex) {
        log.error("Document operation failed ({})",ex.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiDtos.ErrorResponse("INTERNAL_ERROR","요청을 완료하지 못했습니다. 저장 결과를 확인하세요.",List.of(),null));
    }
}
