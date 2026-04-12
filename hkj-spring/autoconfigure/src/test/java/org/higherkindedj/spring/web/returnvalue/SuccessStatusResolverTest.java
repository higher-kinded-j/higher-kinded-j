// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Tests for {@link SuccessStatusResolver}. */
@DisplayName("SuccessStatusResolver Tests")
class SuccessStatusResolverTest {

  private static MethodParameter returnTypeOf(Class<?> controller, String methodName)
      throws NoSuchMethodException {
    Method method = controller.getDeclaredMethod(methodName);
    return new MethodParameter(method, -1);
  }

  @Nested
  @DisplayName("Method-level @ResponseStatus")
  class MethodLevel {

    @Test
    @DisplayName("Should return default status when no annotation is present")
    void noAnnotation() throws Exception {
      MethodParameter rt = returnTypeOf(SampleController.class, "plainGet");
      int status = SuccessStatusResolver.resolveSuccessStatus(rt, HttpStatus.OK.value());
      assertThat(status).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("Should honor @ResponseStatus(CREATED) on POST handler")
    void createdOnPost() throws Exception {
      MethodParameter rt = returnTypeOf(SampleController.class, "createUser");
      int status = SuccessStatusResolver.resolveSuccessStatus(rt, HttpStatus.OK.value());
      assertThat(status).isEqualTo(HttpStatus.CREATED.value());
    }

    @Test
    @DisplayName("Should honor @ResponseStatus(NO_CONTENT) on DELETE handler")
    void noContentOnDelete() throws Exception {
      MethodParameter rt = returnTypeOf(SampleController.class, "deleteUser");
      int status = SuccessStatusResolver.resolveSuccessStatus(rt, HttpStatus.OK.value());
      assertThat(status).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    @Test
    @DisplayName("Should honor @ResponseStatus(code = ACCEPTED) via code alias")
    void acceptedViaCodeAlias() throws Exception {
      MethodParameter rt = returnTypeOf(SampleController.class, "acceptAsync");
      int status = SuccessStatusResolver.resolveSuccessStatus(rt, HttpStatus.OK.value());
      assertThat(status).isEqualTo(HttpStatus.ACCEPTED.value());
    }
  }

  @Nested
  @DisplayName("Class-level @ResponseStatus")
  class ClassLevel {

    @Test
    @DisplayName("Should honor @ResponseStatus on controller class when method has none")
    void classLevelApplied() throws Exception {
      MethodParameter rt = returnTypeOf(ClassLevelController.class, "anything");
      int status = SuccessStatusResolver.resolveSuccessStatus(rt, HttpStatus.OK.value());
      assertThat(status).isEqualTo(HttpStatus.I_AM_A_TEAPOT.value());
    }
  }

  @Nested
  @DisplayName("Meta-annotation support")
  class MetaAnnotation {

    @Test
    @DisplayName("Should honor @ResponseStatus when composed via meta-annotation")
    void metaAnnotated() throws Exception {
      MethodParameter rt = returnTypeOf(SampleController.class, "metaAnnotated");
      int status = SuccessStatusResolver.resolveSuccessStatus(rt, HttpStatus.OK.value());
      assertThat(status).isEqualTo(HttpStatus.CREATED.value());
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Should return default status when MethodParameter is null")
    void nullMethodParameter() {
      int status = SuccessStatusResolver.resolveSuccessStatus(null, HttpStatus.OK.value());
      assertThat(status).isEqualTo(HttpStatus.OK.value());
    }
  }

  // ---- Test fixtures ----

  @SuppressWarnings("unused")
  static class SampleController {
    public String plainGet() {
      return "ok";
    }

    @ResponseStatus(HttpStatus.CREATED)
    public String createUser() {
      return "created";
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser() {}

    @ResponseStatus(code = HttpStatus.ACCEPTED)
    public String acceptAsync() {
      return "accepted";
    }

    @Created
    public String metaAnnotated() {
      return "created";
    }
  }

  @SuppressWarnings("unused")
  @ResponseStatus(HttpStatus.I_AM_A_TEAPOT)
  static class ClassLevelController {
    public String anything() {
      return "ok";
    }
  }

  @ResponseStatus(HttpStatus.CREATED)
  @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD})
  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
  @interface Created {}
}
