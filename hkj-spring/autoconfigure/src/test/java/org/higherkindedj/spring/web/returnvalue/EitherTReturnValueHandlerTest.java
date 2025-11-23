// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.StandardServletAsyncWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
@DisplayName("EitherTReturnValueHandler Tests")
class EitherTReturnValueHandlerTest {

  @Mock private MethodParameter returnType;

  private ModelAndViewContainer mavContainer;
  private EitherTReturnValueHandler handler;
  private ServletWebRequest webRequest;
  private MockHttpServletRequest servletRequest;
  private MockHttpServletResponse servletResponse;

  @BeforeEach
  void setUp() {
    handler = new EitherTReturnValueHandler(HttpStatus.BAD_REQUEST.value());
    mavContainer = new ModelAndViewContainer();

    // Set up proper async infrastructure
    servletRequest = new MockHttpServletRequest();
    servletRequest.setAsyncSupported(true);
    servletResponse = new MockHttpServletResponse();

    webRequest = new ServletWebRequest(servletRequest, servletResponse);

    // Initialize the async manager for the request
    WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
    StandardServletAsyncWebRequest asyncWebRequest =
        new StandardServletAsyncWebRequest(servletRequest, servletResponse);
    asyncManager.setAsyncWebRequest(asyncWebRequest);
  }

  @Nested
  @DisplayName("supportsReturnType Tests")
  class SupportsReturnTypeTests {

    @Test
    @DisplayName("Should support EitherT return type")
    void shouldSupportEitherTReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) EitherT.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should not support non-EitherT return type")
    void shouldNotSupportNonEitherTReturnType() {
      when(returnType.getParameterType()).thenReturn((Class) String.class);

      boolean result = handler.supportsReturnType(returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("isAsyncReturnValue Tests")
  class IsAsyncReturnValueTests {

    @Test
    @DisplayName("Should return true for EitherT value")
    void shouldReturnTrueForEitherT() {
      CompletableFuture<Either<String, String>> future =
          CompletableFuture.completedFuture(Either.right("test"));
      Kind<CompletableFutureKind.Witness, Either<String, String>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, String, String> eitherT = EitherT.fromKind(kind);

      boolean result = handler.isAsyncReturnValue(eitherT, returnType);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-EitherT value")
    void shouldReturnFalseForNonEitherT() {
      boolean result = handler.isAsyncReturnValue("not an EitherT", returnType);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Right (Success) Tests")
  class RightValueTests {

    @Test
    @DisplayName("Should handle async Right value with HTTP 200")
    void shouldHandleAsyncRightValueWith200() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      CompletableFuture<Either<String, TestUser>> future =
          CompletableFuture.completedFuture(Either.right(user));
      Kind<CompletableFutureKind.Witness, Either<String, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, String, TestUser> eitherT = EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }

    @Test
    @DisplayName("Should handle Right value with complex object")
    void shouldHandleRightValueWithComplexObject() throws Exception {
      TestUser user = new TestUser("1", "alice@example.com");
      CompletableFuture<Either<TestError, TestUser>> future =
          CompletableFuture.completedFuture(Either.right(user));
      Kind<CompletableFutureKind.Witness, Either<TestError, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, TestError, TestUser> eitherT = EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Left (Error) Tests")
  class LeftValueTests {

    @Test
    @DisplayName("Should handle async Left value with NotFound error")
    void shouldHandleAsyncLeftValueWithNotFoundError() throws Exception {
      UserNotFoundError error = new UserNotFoundError("123");
      CompletableFuture<Either<UserNotFoundError, TestUser>> future =
          CompletableFuture.completedFuture(Either.left(error));
      Kind<CompletableFutureKind.Witness, Either<UserNotFoundError, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, UserNotFoundError, TestUser> eitherT =
          EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }

    @Test
    @DisplayName("Should handle async Left value with Validation error")
    void shouldHandleAsyncLeftValueWithValidationError() throws Exception {
      ValidationError error = new ValidationError("field", "Invalid value");
      CompletableFuture<Either<ValidationError, TestUser>> future =
          CompletableFuture.completedFuture(Either.left(error));
      Kind<CompletableFutureKind.Witness, Either<ValidationError, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, ValidationError, TestUser> eitherT =
          EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }

    @Test
    @DisplayName("Should handle async Left value with Authorization error")
    void shouldHandleAsyncLeftValueWithAuthorizationError() throws Exception {
      AuthorizationError error = new AuthorizationError("User", "123");
      CompletableFuture<Either<AuthorizationError, TestUser>> future =
          CompletableFuture.completedFuture(Either.left(error));
      Kind<CompletableFutureKind.Witness, Either<AuthorizationError, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, AuthorizationError, TestUser> eitherT =
          EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }

    @Test
    @DisplayName("Should handle async Left value with Authentication error")
    void shouldHandleAsyncLeftValueWithAuthenticationError() throws Exception {
      AuthenticationError error = new AuthenticationError("Invalid token");
      CompletableFuture<Either<AuthenticationError, TestUser>> future =
          CompletableFuture.completedFuture(Either.left(error));
      Kind<CompletableFutureKind.Witness, Either<AuthenticationError, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, AuthenticationError, TestUser> eitherT =
          EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }

    @Test
    @DisplayName("Should handle async Left value with generic error")
    void shouldHandleAsyncLeftValueWithGenericError() throws Exception {
      GenericError error = new GenericError("Something went wrong");
      CompletableFuture<Either<GenericError, TestUser>> future =
          CompletableFuture.completedFuture(Either.left(error));
      Kind<CompletableFutureKind.Witness, Either<GenericError, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, GenericError, TestUser> eitherT =
          EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Exception Tests")
  class ExceptionTests {

    @Test
    @DisplayName("Should handle CompletableFuture exception")
    void shouldHandleCompletableFutureException() throws Exception {
      CompletableFuture<Either<String, TestUser>> future = new CompletableFuture<>();
      future.completeExceptionally(new RuntimeException("Async operation failed"));

      Kind<CompletableFutureKind.Witness, Either<String, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, String, TestUser> eitherT = EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started (exception handling happens in DeferredResult)
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }
  }

  @Nested
  @DisplayName("handleReturnValue - Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle delayed async completion")
    void shouldHandleDelayedAsyncCompletion() throws Exception {
      CompletableFuture<Either<String, TestUser>> future =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  Thread.sleep(50); // Simulate delay
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return Either.right(new TestUser("1", "delayed@example.com"));
              });

      Kind<CompletableFutureKind.Witness, Either<String, TestUser>> kind =
          CompletableFutureKindHelper.FUTURE.widen(future);
      EitherT<CompletableFutureKind.Witness, String, TestUser> eitherT = EitherT.fromKind(kind);

      handler.handleReturnValue(eitherT, returnType, mavContainer, webRequest);

      // Verify async processing was started
      WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(webRequest);
      assertThat(asyncManager.isConcurrentHandlingStarted()).isTrue();
    }
  }

  // Test helper classes
  record TestUser(String id, String email) {}

  record TestError(String message) {}

  record UserNotFoundError(String userId) {}

  record ValidationError(String field, String message) {}

  record AuthorizationError(String resource, String resourceId) {}

  record AuthenticationError(String message) {}

  record GenericError(String message) {}
}
