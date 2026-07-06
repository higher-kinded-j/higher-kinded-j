// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import javax.lang.model.element.TypeElement;
import org.higherkindedj.hkt.effect.annotation.Handles;
import org.higherkindedj.hkt.effect.annotation.PathSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The annotation-Class accessors are only ever called inside javac, where {@code
 * MirroredTypeException} is contractually thrown; these tests pin the loud failure if that contract
 * is ever violated (a plain annotation instance returning normally).
 */
@DisplayName("MirroredTypeException guards fail loudly outside javac")
class MirroredTypeGuardTest {

  private static final PathSource PLAIN_PATH_SOURCE =
      new PathSource() {
        @Override
        public Class<? extends Annotation> annotationType() {
          return PathSource.class;
        }

        @Override
        public Class<?> witness() {
          return String.class;
        }

        @Override
        public Class<?> errorType() {
          return Void.class;
        }

        @Override
        public Capability capability() {
          return Capability.CHAINABLE;
        }

        @Override
        public String targetPackage() {
          return "";
        }

        @Override
        public String suffix() {
          return "Path";
        }
      };

  @Test
  @DisplayName("PathSource witness/errorType accessors reject a non-mirrored annotation")
  void pathSourceAccessorsRejectPlainAnnotation() {
    assertThatThrownBy(() -> PathSourceProcessor.getWitnessType(PLAIN_PATH_SOURCE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("MirroredTypeException");
    assertThatThrownBy(() -> PathSourceProcessor.getErrorType(PLAIN_PATH_SOURCE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("MirroredTypeException");
  }

  @Test
  @DisplayName("@Handles resolution returns null without the annotation and rejects a plain one")
  void handlesResolutionGuards() {
    Handles plainHandles =
        new Handles() {
          @Override
          public Class<? extends Annotation> annotationType() {
            return Handles.class;
          }

          @Override
          public Class<?> value() {
            return String.class;
          }
        };
    TypeElement unannotated = typeElement(annotationClass -> null);
    TypeElement plainAnnotated =
        typeElement(annotationClass -> annotationClass == Handles.class ? plainHandles : null);

    ComposeEffectsProcessor processor = new ComposeEffectsProcessor();
    assertThat(processor.getHandlesAlgebraType(unannotated)).isNull();
    assertThatThrownBy(() -> processor.getHandlesAlgebraType(plainAnnotated))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("MirroredTypeException");
  }

  private interface AnnotationLookup {
    Object lookup(Class<?> annotationClass);
  }

  private static TypeElement typeElement(AnnotationLookup lookup) {
    return (TypeElement)
        Proxy.newProxyInstance(
            MirroredTypeGuardTest.class.getClassLoader(),
            new Class<?>[] {TypeElement.class},
            (proxy, method, args) ->
                "getAnnotation".equals(method.getName())
                    ? lookup.lookup((Class<?>) args[0])
                    : null);
  }
}
