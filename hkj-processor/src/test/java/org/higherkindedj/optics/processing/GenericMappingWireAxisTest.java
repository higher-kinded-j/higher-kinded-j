// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One case per wire shape {@code @GenerateMapping} accepts, each with its type parameters supplied
 * three ways: none at all, declared by the spec, and inherited from a generic base.
 *
 * <p>The inherited column is the one that hid a defect. A wire that declares no parameters of its
 * own still reaches members that do — {@code class UserDto extends BaseDto<String>} answers {@code
 * T getId()} — and a gate that asks only whether the wire itself is generic lets those through
 * unread. Every other column agrees whether members are read as declared or under the wire, so the
 * fault is invisible without this one.
 */
@DisplayName("Every mapping wire shape, generic and not")
class GenericMappingWireAxisTest {

  private static final JavaFileObject DOMAIN =
      JavaFileObjects.forSourceString(
          "com.example.Dom",
          """
          package com.example;

          public record Dom(String id, String name) {}
          """);

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new MappingProcessor()).compile(sources);
  }

  @Test
  @DisplayName("a record wire, with no type parameters")
  void recordWire() {
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.RecWire",
            """
            package com.example;

            public record RecWire(String id, String name) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.RecMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface RecMapping extends MappingSpec<Dom, RecWire> {}
            """);

    var compilation = compile(DOMAIN, wire, spec);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.RecMappingImpl");
  }

  @Test
  @DisplayName("a record wire whose parameters the spec declares")
  void genericRecordWire() {
    var domain =
        JavaFileObjects.forSourceString(
            "com.example.Page",
            """
            package com.example;

            import java.util.List;

            public record Page<T>(List<T> items, String cursor) {}
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.PageDto",
            """
            package com.example;

            import java.util.List;

            public record PageDto<T>(List<T> items, String cursor) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.PageMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {}
            """);

    var compilation = compile(domain, wire, spec);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.PageMappingImpl");
  }

  @Test
  @DisplayName("a bean wire, with no type parameters")
  void beanWire() {
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.BeanWire",
            """
            package com.example;

            public class BeanWire {
                private String id;
                private String name;
                public BeanWire() {}
                public String getId() { return id; }
                public void setId(String id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.BeanMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface BeanMapping extends MappingSpec<Dom, BeanWire> {}
            """);

    var compilation = compile(DOMAIN, wire, spec);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.BeanMappingImpl");
  }

  @Test
  @DisplayName("a bean wire inheriting a property from a generic base")
  void beanWireInheritingFromAGenericBase() {
    var base =
        JavaFileObjects.forSourceString(
            "com.example.GenBase",
            """
            package com.example;

            public class GenBase<T> {
                private T id;
                public T getId() { return id; }
                public void setId(T id) { this.id = id; }
            }
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.InhWire",
            """
            package com.example;

            public class InhWire extends GenBase<String> {
                private String name;
                public InhWire() {}
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.InhMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface InhMapping extends MappingSpec<Dom, InhWire> {}
            """);

    // InhWire declares no parameters, so a gate reading only its own would pass it; getId() is
    // still 'T getId()' until it is read under InhWire.
    var compilation = compile(DOMAIN, base, wire, spec);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.InhMappingImpl");
  }

  @Test
  @DisplayName("a mix-in carrying a generic ancestor's members is rejected by name")
  void mixinWithAGenericAncestor() {
    var base =
        JavaFileObjects.forSourceString(
            "com.example.BaseVocab",
            """
            package com.example;

            public interface BaseVocab<T> {
                @org.higherkindedj.optics.annotations.MapField(to = "name")
                T shared();
            }
            """);

    var mixin =
        JavaFileObjects.forSourceString(
            "com.example.Vocab",
            """
            package com.example;

            public interface Vocab extends BaseVocab<String> {}
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.MixWire",
            """
            package com.example;

            public record MixWire(String id, String name) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.MixMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface MixMapping extends MappingSpec<Dom, MixWire>, Vocab {}
            """);

    // Members are collected across the whole ancestry, so the gate is too, and it names the
    // ancestor that is actually generic rather than the one the spec lists.
    var compilation = compile(DOMAIN, base, mixin, wire, spec);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("mix-in 'BaseVocab' is generic");
  }

  @Test
  @DisplayName("a generic ancestor that carries no vocabulary does not refuse the spec")
  void genericAncestorCarryingNoVocabulary() {
    var marker =
        JavaFileObjects.forSourceString(
            "com.example.Ordered",
            """
            package com.example;

            public interface Ordered extends Comparable<Ordered> {
                @Override
                default int compareTo(Ordered other) { return 0; }
            }
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.OrderedWire",
            """
            package com.example;

            public record OrderedWire(String id, String name) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.OrderedMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface OrderedMapping extends MappingSpec<Dom, OrderedWire>, Ordered {}
            """);

    // Comparable is generic and two levels up, but contributes no rename and no leaf, so nothing
    // it declares can carry an unsubstituted variable into the mapping - and a remedy naming a JDK
    // type could not be followed anyway.
    var compilation = compile(DOMAIN, marker, wire, spec);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.OrderedMappingImpl");
  }
}
