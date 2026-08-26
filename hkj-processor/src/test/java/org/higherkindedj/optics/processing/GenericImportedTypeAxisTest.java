// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One case per shape {@code @ImportOptics({Class...})} auto-detects, each in a non-generic and a
 * generic form.
 *
 * <p>The generic half is where a generator that names the imported type <em>raw</em> and one that
 * reads a component's own type arguments disagree. The two agree for every non-generic shape, so
 * the fault is invisible without this half — which is how a generic record's traversal named a type
 * parameter it never declared, in a class whose lens methods declared theirs correctly.
 *
 * <p>Where the generic form is not yet generated as it should be, the case pins what <em>is</em>
 * emitted and names the gap, so that closing it shows up here as a test to update rather than as a
 * cell nobody was looking at.
 */
@DisplayName("Every imported shape, generic and not")
class GenericImportedTypeAxisTest {

  /** Imports the named classes into {@code com.myapp.optics} and compiles. */
  private Compilation compile(String importedClasses, JavaFileObject... sources) {
    var packageInfo =
        JavaFileObjects.forSourceString(
            "com.myapp.optics.package-info",
            """
            @ImportOptics({%s})
            package com.myapp.optics;

            import org.higherkindedj.optics.annotations.ImportOptics;
            """
                .formatted(importedClasses));

    var all = new JavaFileObject[sources.length + 1];
    System.arraycopy(sources, 0, all, 0, sources.length);
    all[sources.length] = packageInfo;
    return javac().withProcessors(new ImportOpticsProcessor()).compile(all);
  }

  @Test
  @DisplayName("a record, and a generic record whose parameters the methods declare")
  void records() {
    var plain =
        JavaFileObjects.forSourceString(
            "com.external.PlainRec",
            """
            package com.external;

            import java.util.List;

            public record PlainRec(String name, List<String> items) {}
            """);

    var generic =
        JavaFileObjects.forSourceString(
            "com.external.GenRec",
            """
            package com.external;

            import java.util.List;

            public record GenRec<T>(String name, List<T> items) {}
            """);

    var plainCompilation = compile("com.external.PlainRec.class", plain);
    assertThat(plainCompilation).succeeded();
    assertGeneratedCodeContains(
        plainCompilation,
        "com.myapp.optics.PlainRecLenses",
        "public static Lens<PlainRec, String> name()");
    assertGeneratedCodeContains(
        plainCompilation,
        "com.myapp.optics.PlainRecLenses",
        "public static Traversal<PlainRec, String> itemsTraversal()");

    var genericCompilation = compile("com.external.GenRec.class", generic);
    assertThat(genericCompilation).succeeded();
    assertGeneratedCodeContains(
        genericCompilation,
        "com.myapp.optics.GenRecLenses",
        "public static <T> Lens<GenRec<T>, String> name()");
    // The traversal is the method that named the record raw while its neighbours did not.
    assertGeneratedCodeContains(
        genericCompilation,
        "com.myapp.optics.GenRecLenses",
        "public static <T> Traversal<GenRec<T>, T> itemsTraversal()");
  }

  @Test
  @DisplayName("a wither class, and a generic wither class")
  void witherClasses() {
    var plain =
        JavaFileObjects.forSourceString(
            "com.external.PlainBox",
            """
            package com.external;

            import java.util.List;

            public class PlainBox {
                private String name;
                private List<String> items;
                public String name() { return name; }
                public List<String> items() { return items; }
                public PlainBox withName(String name) { return this; }
                public PlainBox withItems(List<String> items) { return this; }
            }
            """);

    var generic =
        JavaFileObjects.forSourceString(
            "com.external.GenBox",
            """
            package com.external;

            import java.util.List;

            public class GenBox<T> {
                private String name;
                private List<T> items;
                public String name() { return name; }
                public List<T> items() { return items; }
                public GenBox<T> withName(String name) { return this; }
                public GenBox<T> withItems(List<T> items) { return this; }
            }
            """);

    var plainCompilation = compile("com.external.PlainBox.class", plain);
    assertThat(plainCompilation).succeeded();
    assertGeneratedCodeContains(
        plainCompilation,
        "com.myapp.optics.PlainBoxLenses",
        "public static Lens<PlainBox, String> name()");

    var genericCompilation = compile("com.external.GenBox.class", generic);
    assertThat(genericCompilation).succeeded();
    assertGeneratedCodeContains(
        genericCompilation,
        "com.myapp.optics.GenBoxLenses",
        "public static <T> Lens<GenBox<T>, List<T>> items()");
  }

  @Test
  @DisplayName("a sealed interface, and a generic one, whose prisms are still named raw")
  void sealedInterfaces() {
    var plainShape =
        JavaFileObjects.forSourceString(
            "com.external.PlainShape",
            """
            package com.external;

            public sealed interface PlainShape permits PlainCircle {}
            """);
    var plainCircle =
        JavaFileObjects.forSourceString(
            "com.external.PlainCircle",
            """
            package com.external;

            public record PlainCircle(double radius) implements PlainShape {}
            """);

    var genShape =
        JavaFileObjects.forSourceString(
            "com.external.GenShape",
            """
            package com.external;

            public sealed interface GenShape<T> permits GenCircle {}
            """);
    var genCircle =
        JavaFileObjects.forSourceString(
            "com.external.GenCircle",
            """
            package com.external;

            public record GenCircle<T>(T tag) implements GenShape<T> {}
            """);

    var plainCompilation = compile("com.external.PlainShape.class", plainShape, plainCircle);
    assertThat(plainCompilation).succeeded();
    assertGeneratedCodeContains(
        plainCompilation,
        "com.myapp.optics.PlainShapePrisms",
        "public static Prism<PlainShape, PlainCircle> plainCircle()");

    var genericCompilation = compile("com.external.GenShape.class", genShape, genCircle);
    assertThat(genericCompilation).succeeded();
    // Both sides are named raw, so the prism does not compose with an optic that carries the
    // hierarchy's argument. Generating Prism<GenShape<T>, GenCircle<T>> is issue #742; this pins
    // what is emitted until then, rather than leaving the cell unexercised.
    assertGeneratedCodeContains(
        genericCompilation,
        "com.myapp.optics.GenShapePrisms",
        "public static Prism<GenShape, GenCircle> genCircle()");
  }

  @Test
  @DisplayName("an enum, which has no generic form")
  void enums() {
    var status =
        JavaFileObjects.forSourceString(
            "com.external.Status",
            """
            package com.external;

            public enum Status { PENDING, ACTIVE }
            """);

    // An enum cannot declare type parameters, so this row has one cell by construction.
    var compilation = compile("com.external.Status.class", status);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(
        compilation,
        "com.myapp.optics.StatusPrisms",
        "public static Prism<Status, Status> pending()");
  }
}
