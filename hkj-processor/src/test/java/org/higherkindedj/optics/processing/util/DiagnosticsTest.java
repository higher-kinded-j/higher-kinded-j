// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Diagnostics - the shared what/why/fix processor message format")
class DiagnosticsTest {

  private record Printed(Diagnostic.Kind kind, String message, Element element) {}

  private final List<Printed> printed = new ArrayList<>();

  private final Messager messager =
      (Messager)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {Messager.class},
              (proxy, method, args) -> {
                if ("printMessage".equals(method.getName()) && args.length == 3) {
                  printed.add(
                      new Printed(
                          (Diagnostic.Kind) args[0], args[1].toString(), (Element) args[2]));
                }
                return null;
              });

  private final Element element =
      (Element)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {Element.class},
              (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
              });

  @Test
  @DisplayName("format joins the annotation tag and the three sentences")
  void formatJoinsParts() {
    String message =
        Diagnostics.format(
            "@GenerateMapping",
            "target field 'UserDto.fullName' has no source.",
            "Found on User: [name, email, age].",
            "Add a @MapField(to=\"fullName\") method to the MappingSpec.");

    assertThat(message)
        .isEqualTo(
            "@GenerateMapping: target field 'UserDto.fullName' has no source."
                + " Found on User: [name, email, age]."
                + " Add a @MapField(to=\"fullName\") method to the MappingSpec.");
  }

  @Test
  @DisplayName("error and warning print the formatted message against the element")
  void errorAndWarningPrint() {
    Diagnostics.error(messager, element, "@GenerateFocus", "what.", "why.", "fix.");
    Diagnostics.warning(messager, element, "@GenerateFocus", "what.", "why.", "fix.");

    assertThat(printed)
        .containsExactly(
            new Printed(Diagnostic.Kind.ERROR, "@GenerateFocus: what. why. fix.", element),
            new Printed(Diagnostic.Kind.WARNING, "@GenerateFocus: what. why. fix.", element));
  }

  @Test
  @DisplayName("format trims and guarantees sentence-ending punctuation")
  void formatNormalisesSloppyInput() {
    assertThat(Diagnostics.format("@A", "  what without stop  ", "why?", "fix!"))
        .isEqualTo("@A: what without stop. why? fix!");
    assertThat(Diagnostics.format("@A", "what.", "", "  ")).isEqualTo("@A: what.  ");
  }

  @Test
  @DisplayName("all arguments are eagerly guarded")
  void argumentsAreGuarded() {
    assertThatNullPointerException()
        .isThrownBy(() -> Diagnostics.error(null, element, "@A", "w.", "y.", "f."))
        .withMessage("messager must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> Diagnostics.warning(messager, null, "@A", "w.", "y.", "f."))
        .withMessage("element must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> Diagnostics.format(null, "w.", "y.", "f."))
        .withMessage("annotation must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> Diagnostics.format("@A", null, "y.", "f."))
        .withMessage("what must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> Diagnostics.format("@A", "w.", null, "f."))
        .withMessage("why must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> Diagnostics.format("@A", "w.", "y.", null))
        .withMessage("fix must not be null");
  }
}
