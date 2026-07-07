// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code @GenerateForComprehensions} is {@code @Target(PACKAGE)}, so javac cannot hand the
 * processor a non-package element or a package it cannot read the annotation from — but another
 * processor generating annotated sources in a later round could. These tests drive those guards
 * directly (the same proxy pattern as the MappingProcessor filer-fallback test).
 */
@DisplayName("ForComprehensionProcessor guards against elements javac cannot produce")
class ForComprehensionProcessorGuardTest {

  private final List<String> errors = new ArrayList<>();

  private <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> impl) {
    return type.cast(
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {type},
            (p, method, args) ->
                switch (method.getName()) {
                  case "equals" -> p == args[0];
                  case "hashCode" -> System.identityHashCode(p);
                  default -> impl.apply(method.getName(), args);
                }));
  }

  private Element element(ElementKind kind, String name, Object annotation) {
    return proxy(
        Element.class,
        (m, args) ->
            switch (m) {
              case "getKind" -> kind;
              case "toString" -> name;
              case "getAnnotation" -> annotation;
              default -> null;
            });
  }

  @Test
  @DisplayName("non-package elements, duplicate packages and unreadable annotations are skipped")
  void guardsSkipUnprocessableElements() {
    ForComprehensionProcessor processor = new ForComprehensionProcessor();
    Messager messager =
        proxy(
            Messager.class,
            (m, args) -> {
              if ("printMessage".equals(m)) {
                errors.add(String.valueOf(args[1]));
              }
              return null;
            });
    processor.init(
        proxy(ProcessingEnvironment.class, (m, args) -> "getMessager".equals(m) ? messager : null));

    TypeElement annotationType = proxy(TypeElement.class, (m, args) -> null);
    Element nonPackage = element(ElementKind.CLASS, "com.example.NotAPackage", null);
    Element unreadable = element(ElementKind.PACKAGE, "com.example.unreadable", null);
    RoundEnvironment roundEnv =
        proxy(
            RoundEnvironment.class,
            (m, args) ->
                "getElementsAnnotatedWith".equals(m) ? Set.of(nonPackage, unreadable) : null);

    boolean claimed = processor.process(Set.of(annotationType), roundEnv);

    assertThat(claimed).isTrue();
    assertThat(errors)
        .anySatisfy(msg -> assertThat(msg).contains("can only be applied to packages"))
        .anySatisfy(msg -> assertThat(msg).contains("Could not read @GenerateForComprehensions"));

    // A second round presenting the same unreadable package name is skipped silently.
    errors.clear();
    Element duplicate = element(ElementKind.PACKAGE, "com.example.unreadable", null);
    RoundEnvironment secondRound =
        proxy(
            RoundEnvironment.class,
            (m, args) -> "getElementsAnnotatedWith".equals(m) ? Set.of(duplicate) : null);
    processor.process(Set.of(annotationType), secondRound);
    assertThat(errors).isEmpty();
  }
}
