// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.boundary.EffectBoundary;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("FreePathReturnValueHandler interpretation")
class FreePathReturnValueHandlerTest {

  @Mock private HttpServletResponse response;

  @Mock private NativeWebRequest webRequest;

  @Mock private MethodParameter returnType;

  @Mock private ModelAndViewContainer mavContainer;

  @Mock private ApplicationContext applicationContext;

  private FreePathReturnValueHandler handler;
  private StringWriter stringWriter;
  private PrintWriter printWriter;

  /** A program of one IO step, interpreted by the identity boundary. */
  private static <A> FreePath<IOKind.Witness, A> program(Supplier<A> step) {
    return FreePath.liftF(IO_OP.widen(IO.delay(step)), IOMonad.INSTANCE);
  }

  @BeforeEach
  void setUp() throws Exception {
    handler =
        new FreePathReturnValueHandler(
            JsonMapper.builder().build(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            false,
            applicationContext);
    stringWriter = new StringWriter();
    printWriter = new PrintWriter(stringWriter);

    when(applicationContext.getBean(EffectBoundary.class))
        .thenReturn(EffectBoundary.of(Natural.<IOKind.Witness>identity()));
    lenient().when(webRequest.getNativeResponse(HttpServletResponse.class)).thenReturn(response);
    lenient().when(response.getWriter()).thenReturn(printWriter);
  }

  @Test
  @DisplayName("a program's value is written as the body")
  void writesTheValue() throws Exception {
    handler.handleReturnValue(program(() -> "done"), returnType, mavContainer, webRequest);

    verify(response).setStatus(HttpStatus.OK.value());
    verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    printWriter.flush();
    assertThat(stringWriter.toString()).isEqualTo("\"done\"");
  }

  @Test
  @DisplayName("a program that returns null writes no body at the resolved status, as void does")
  void writesNoBodyForANullValue() throws Exception {
    handler.handleReturnValue(program(() -> null), returnType, mavContainer, webRequest);

    verify(response).setStatus(HttpStatus.OK.value());
    verify(response, never()).setContentType(anyString());
    printWriter.flush();
    assertThat(stringWriter.toString()).isEmpty();
  }

  @Test
  @DisplayName("a program that throws writes the failure status")
  void writesTheFailure() throws Exception {
    FreePath<IOKind.Witness, String> failing =
        program(
            () -> {
              throw new IllegalStateException("boom");
            });

    handler.handleReturnValue(failing, returnType, mavContainer, webRequest);

    verify(response).setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
    printWriter.flush();
    assertThat(stringWriter.toString()).contains("\"success\":false");
  }
}
