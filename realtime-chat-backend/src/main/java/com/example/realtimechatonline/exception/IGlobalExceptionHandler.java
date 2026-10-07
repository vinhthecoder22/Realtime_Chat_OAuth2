package com.example.realtimechatonline.exception;

import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import com.example.realtimechatonline.exception.extended.OAuth2EmailConflictException;
import com.example.realtimechatonline.exception.extended.PasswordMismatchException;
import com.example.realtimechatonline.exception.extended.ResourceAlreadyExistsException;
import com.example.realtimechatonline.exception.extended.ResourceNotFoundException;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

public interface IGlobalExceptionHandler {

    ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e);

    ResponseEntity<ErrorResponse> handleResourceAlreadyExists(ResourceAlreadyExistsException e);

    ResponseEntity<ErrorResponse> handlePasswordMismatch(PasswordMismatchException e);

    ResponseEntity<ErrorResponse> handleOAuth2EmailConflict(OAuth2EmailConflictException e);

    ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e);

    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e);

    ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e);

    ResponseEntity<ErrorResponse> handleNumberFormat(NumberFormatException e);

    ResponseEntity<ErrorResponse> handleJwtException(JwtException e);

    ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e);

    ResponseEntity<ErrorResponse> handleGenericException(Exception e);

    void handleClientAbortException(ClientAbortException e);

}
