// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import javax.lang.model.util.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GenerationContext")
class GenerationContextTest {

  /** Element utilities nothing here reads: the context is refused before it could. */
  private static final Elements UNREAD =
      (Elements)
          Proxy.newProxyInstance(
              Elements.class.getClassLoader(),
              new Class<?>[] {Elements.class},
              (proxy, method, args) -> {
                throw new AssertionError("nothing here reads elements: " + method.getName());
              });

  @Test
  @DisplayName("hands back the package and element utilities it was made with")
  void handsBackWhatItWasMadeWith() {
    GenerationContext context = new GenerationContext("com.example", UNREAD);

    assertThat(context.targetPackage()).isEqualTo("com.example");
    assertThat(context.elements()).isSameAs(UNREAD);
  }

  @Test
  @DisplayName("is a final class, not a record, so it can grow without breaking a plugin")
  void isAFinalClassNotARecord() {
    assertThat(GenerationContext.class.isRecord()).isFalse();
    assertThat(Modifier.isFinal(GenerationContext.class.getModifiers())).isTrue();
  }

  @Test
  @DisplayName("refuses a null package, naming it")
  void refusesANullPackage() {
    assertThatNullPointerException()
        .isThrownBy(() -> new GenerationContext(null, UNREAD))
        .withMessage("targetPackage");
  }

  @Test
  @DisplayName("refuses null element utilities, naming them")
  void refusesNullElements() {
    assertThatNullPointerException()
        .isThrownBy(() -> new GenerationContext("com.example", null))
        .withMessage("elements");
  }
}
