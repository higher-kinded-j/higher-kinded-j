// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The environment the registry classifies specs in: the compiler's own, with its diagnostics
 * dropped and its writes refused as the compiler refuses one.
 */
@DisplayName("SilentEnvironment - the compiler's environment, silent and writing nothing")
class SilentEnvironmentTest {

  /** Calls made on the compiler's environment, by method name. */
  private final List<String> calls = new ArrayList<>();

  private final Elements elements = proxy(Elements.class);
  private final Types types = proxy(Types.class);
  private final FileObject resource = proxy(FileObject.class);

  private final Filer filer =
      (Filer)
          Proxy.newProxyInstance(
              Filer.class.getClassLoader(),
              new Class<?>[] {Filer.class},
              (target, method, args) -> {
                calls.add(method.getName());
                return resource;
              });

  private final ProcessingEnvironment compiler =
      (ProcessingEnvironment)
          Proxy.newProxyInstance(
              ProcessingEnvironment.class.getClassLoader(),
              new Class<?>[] {ProcessingEnvironment.class},
              (target, method, args) -> {
                calls.add(method.getName());
                return switch (method.getName()) {
                  case "getOptions" -> Map.of("hkj.mapping.index", "false");
                  case "getMessager" -> throw new AssertionError("the compiler's messager is used");
                  case "getFiler" -> filer;
                  case "getElementUtils" -> elements;
                  case "getTypeUtils" -> types;
                  case "getSourceVersion" -> SourceVersion.RELEASE_25;
                  case "getLocale" -> Locale.UK;
                  case "isPreviewEnabled" -> true;
                  default -> throw new AssertionError(method.getName());
                };
              });

  private static <T> T proxy(Class<T> type) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (target, method, args) -> {
              throw new AssertionError(method.getName());
            }));
  }

  @Test
  @DisplayName("answers every question the compiler would")
  void delegates() throws Exception {
    SilentEnvironment silent = new SilentEnvironment(compiler);

    assertThat(silent.getOptions()).containsEntry("hkj.mapping.index", "false");
    assertThat(silent.getElementUtils()).isSameAs(elements);
    assertThat(silent.getTypeUtils()).isSameAs(types);
    assertThat(silent.getSourceVersion()).isEqualTo(SourceVersion.RELEASE_25);
    assertThat(silent.getLocale()).isEqualTo(Locale.UK);
    assertThat(silent.isPreviewEnabled()).isTrue();
    assertThat(silent.getFiler().getResource(StandardLocation.CLASS_PATH, "p", "r.txt"))
        .isSameAs(resource);
    assertThat(calls)
        .containsExactly(
            "getFiler",
            "getOptions",
            "getElementUtils",
            "getTypeUtils",
            "getSourceVersion",
            "getLocale",
            "isPreviewEnabled",
            "getResource");
  }

  @Test
  @DisplayName("drops every diagnostic")
  void dropsDiagnostics() {
    Messager messager = new SilentEnvironment(compiler).getMessager();

    messager.printMessage(Diagnostic.Kind.ERROR, "one");
    messager.printMessage(Diagnostic.Kind.ERROR, "two", null);
    messager.printMessage(Diagnostic.Kind.WARNING, "three", null, null);
    messager.printMessage(Diagnostic.Kind.NOTE, "four", null, null, null);
    messager.printError("five");

    assertThat(calls).containsExactly("getFiler");
  }

  @Test
  @DisplayName("refuses every write, as the compiler refuses one")
  void refusesWrites() {
    Filer silent = new SilentEnvironment(compiler).getFiler();

    assertThatThrownBy(() -> silent.createSourceFile("p.Impl"))
        .isInstanceOf(FilerException.class)
        .hasMessage("a silent classification writes nothing: p.Impl");
    assertThatThrownBy(() -> silent.createClassFile("p.Impl"))
        .isInstanceOf(FilerException.class)
        .hasMessage("a silent classification writes nothing: p.Impl");
    assertThatThrownBy(() -> silent.createResource(StandardLocation.CLASS_OUTPUT, "p", "r.txt"))
        .isInstanceOf(FilerException.class)
        .hasMessage("a silent classification writes nothing: r.txt");
    assertThat(calls).containsExactly("getFiler");
  }
}
