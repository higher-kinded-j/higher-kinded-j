// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Return value handler for {@link EitherT} with {@link CompletableFuture} async support.
 *
 * <p>This handler enables controllers to return {@code EitherT<CompletableFuture.Witness, E, A>}
 * for asynchronous operations with functional error handling.
 *
 * <p>The handler:
 *
 * <ul>
 *   <li>Unwraps the EitherT to get {@code CompletableFuture<Either<E, A>>}
 *   <li>Creates a {@link DeferredResult} for Spring's async processing
 *   <li>Maps the async result to HTTP responses based on Either value
 * </ul>
 *
 * <p>Example controller method:
 *
 * <pre>{@code
 * @GetMapping("/users/{id}")
 * public EitherT<CompletableFuture.Witness, DomainError, User> getUserAsync(@PathVariable String id) {
 *     return asyncUserService.findByIdAsync(id);
 *     // Left(error) → HTTP 404/400/etc (async)
 *     // Right(user) → HTTP 200 with user JSON (async)
 * }
 * }</pre>
 *
 * <p>Benefits of EitherT over plain Either:
 *
 * <ul>
 *   <li>Non-blocking I/O - frees up request threads
 *   <li>Composable async operations with flatMap
 *   <li>Consistent error handling across async boundaries
 *   <li>Type-safe error propagation in async chains
 * </ul>
 */
public class EitherTReturnValueHandler implements AsyncHandlerMethodReturnValueHandler {

  private final int defaultErrorStatus;

  /** Creates a new EitherTReturnValueHandler with default settings. */
  public EitherTReturnValueHandler() {
    this(HttpStatus.BAD_REQUEST.value());
  }

  /**
   * Creates a new EitherTReturnValueHandler with default error status.
   *
   * @param defaultErrorStatus the default HTTP status code for errors
   */
  public EitherTReturnValueHandler(int defaultErrorStatus) {
    this.defaultErrorStatus = defaultErrorStatus;
  }

  @Override
  public boolean supportsReturnType(MethodParameter returnType) {
    return EitherT.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public boolean isAsyncReturnValue(Object returnValue, MethodParameter returnType) {
    // EitherT is always async when wrapping CompletableFuture
    return returnValue instanceof EitherT;
  }

  @Override
  public void handleReturnValue(
      Object returnValue,
      MethodParameter returnType,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest)
      throws Exception {

    if (returnValue instanceof EitherT<?, ?, ?> eitherT) {
      // Extract the CompletableFuture<Either<E, A>>
      CompletableFuture<Either<?, ?>> future = unwrapEitherT(eitherT);

      // Create DeferredResult with ResponseEntity to include status and headers
      DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>();

      // Handle the async result
      future.whenComplete(
          (either, throwable) -> {
            try {
              if (throwable != null) {
                // Exception occurred during async execution
                Map<String, Object> errorBody =
                    Map.of(
                        "success",
                        false,
                        "error",
                        Map.of(
                            "type",
                            throwable.getClass().getSimpleName(),
                            "message",
                            throwable.getMessage() != null
                                ? throwable.getMessage()
                                : "Internal server error"));
                deferredResult.setResult(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody));
              } else if (either != null) {
                // Either value received - fold to determine response
                either.fold(
                    error -> {
                      int statusCode =
                          ErrorStatusCodeMapper.determineStatusCode(error, defaultErrorStatus);
                      Map<String, Object> errorBody = Map.of("success", false, "error", error);
                      deferredResult.setResult(ResponseEntity.status(statusCode).body(errorBody));
                      return null;
                    },
                    value -> {
                      deferredResult.setResult(ResponseEntity.ok(value));
                      return null;
                    });
              } else {
                // Null result
                deferredResult.setResult(ResponseEntity.noContent().build());
              }
            } catch (Exception e) {
              deferredResult.setErrorResult(e);
            }
          });

      // Start async processing with Spring
      WebAsyncUtils.getAsyncManager(webRequest)
          .startDeferredResultProcessing(deferredResult, mavContainer);
    }
  }

  /** Unwraps EitherT to get the underlying CompletableFuture<Either>. */
  @SuppressWarnings("unchecked")
  private CompletableFuture<Either<?, ?>> unwrapEitherT(EitherT<?, ?, ?> eitherT) {
    // Get the Kind<CompletableFuture.Witness, Either<E, A>> from EitherT
    Kind<?, ?> kind = eitherT.value();
    // Narrow to CompletableFuture using CompletableFutureKindHelper
    return (CompletableFuture<Either<?, ?>>)
        CompletableFutureKindHelper.FUTURE.narrow((Kind<CompletableFutureKind.Witness, ?>) kind);
  }
}
