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
    // The consuming build's own flags, as GenericsAcceptanceAxisTest and
    // SpecInterfaceProcessingTest
    // use: an erased member emits a raw type into the Impl, which compiles clean on javac's
    // defaults and passes succeeded() while failing the build that consumes it.
    return javac()
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .withProcessors(new MappingProcessor())
        .compile(sources);
  }

  @Test
  @DisplayName("two mix-ins agreeing on a rename with covariant returns get the narrowest stub")
  void covariantRenameStub() {
    // Override-equivalent abstracts may legally coexist (JLS 9.4.1.3); the stub has to satisfy
    // both declarations, so it returns String. The pair is mirrored across two specs because
    // getAllMembers order is a javac internal: whichever member is seen first, one spec meets
    // the wider declaration first (the narrower must replace it) and the other the narrower
    // first (it must be kept), so no first-found stub satisfies both specs. The third spec
    // supplies the returns through generic mix-ins at different instantiations, so the chooser
    // reads substituted types, not declarations.
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.AWire",
            """
            package com.example;
            public record AWire(String label, String name) {}
            """);
    var v1 =
        JavaFileObjects.forSourceString(
            "com.example.AV1",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.MapField;
            public interface AV1 { @MapField(to = "label") CharSequence id(); }
            """);
    var v2 =
        JavaFileObjects.forSourceString(
            "com.example.AV2",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.MapField;
            public interface AV2 { @MapField(to = "label") String id(); }
            """);
    var w1 =
        JavaFileObjects.forSourceString(
            "com.example.BW1",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.MapField;
            public interface BW1 { @MapField(to = "label") String id(); }
            """);
    var w2 =
        JavaFileObjects.forSourceString(
            "com.example.BW2",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.MapField;
            public interface BW2 { @MapField(to = "label") CharSequence id(); }
            """);
    var g =
        JavaFileObjects.forSourceString(
            "com.example.GV",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.MapField;
            public interface GV<T> { @MapField(to = "label") T id(); }
            """);
    var h =
        JavaFileObjects.forSourceString(
            "com.example.HV",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.MapField;
            public interface HV<U> { @MapField(to = "label") U id(); }
            """);
    var specA =
        JavaFileObjects.forSourceString(
            "com.example.ASpec",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface ASpec extends MappingSpec<Dom, AWire>, AV1, AV2 {}
            """);
    var specB =
        JavaFileObjects.forSourceString(
            "com.example.BSpec",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface BSpec extends MappingSpec<Dom, AWire>, BW1, BW2 {}
            """);
    var specC =
        JavaFileObjects.forSourceString(
            "com.example.CSub",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface CSub extends MappingSpec<Dom, AWire>, GV<CharSequence>, HV<String> {}
            """);
    var compilation = compile(DOMAIN, wire, v1, v2, w1, w2, g, h, specA, specB, specC);
    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.ASpecImpl", "public String id()");
    assertGeneratedCodeContains(compilation, "com.example.BSpecImpl", "public String id()");
    assertGeneratedCodeContains(compilation, "com.example.CSubImpl", "public String id()");
  }

  @Test
  @DisplayName("a rename group with no narrowest return is refused, naming every declaration")
  void noNarrowestReturn() {
    // A raw return is substitutable for both parameterised ones by unchecked conversion, so
    // javac accepts the spec, but the two parameterised returns are incomparable: no stub
    // satisfies every declaration, and emitting one would fail inside the generated file.
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.RWire",
            """
            package com.example;
            public record RWire(String label, String name) {}
            """);
    var r1 =
        JavaFileObjects.forSourceString(
            "com.example.RV1",
            """
            package com.example;
            import java.util.List;
            import org.higherkindedj.optics.annotations.MapField;
            @SuppressWarnings("rawtypes")
            public interface RV1 { @MapField(to = "label") List id(); }
            """);
    var r2 =
        JavaFileObjects.forSourceString(
            "com.example.RV2",
            """
            package com.example;
            import java.util.List;
            import org.higherkindedj.optics.annotations.MapField;
            public interface RV2 { @MapField(to = "label") List<String> id(); }
            """);
    var r3 =
        JavaFileObjects.forSourceString(
            "com.example.RV3",
            """
            package com.example;
            import java.util.List;
            import org.higherkindedj.optics.annotations.MapField;
            public interface RV3 { @MapField(to = "label") List<Integer> id(); }
            """);
    var spec =
        JavaFileObjects.forSourceString(
            "com.example.RSpec",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface RSpec extends MappingSpec<Dom, RWire>, RV1, RV2, RV3 {}
            """);
    var compilation = compile(DOMAIN, wire, r1, r2, r3, spec);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@GenerateMapping: same-named members 'id' declare returns none of which satisfies the"
                + " rest ('List' from 'RV1', 'List<Integer>' from 'RV3', 'List<String>' from"
                + " 'RV2'). The generated Impl emits one member for the group, and its return type"
                + " has to be a subtype of every declaration. Align the returns, or give the"
                + " methods different names.");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("a private nested type of the spec's own enclosing class is refused too")
  void privateNestedMemberType() {
    // The Impl is flattened to a top-level class, so a private type the nested spec can name is
    // one the generated file cannot.
    var outer =
        JavaFileObjects.forSourceString(
            "com.example.Outer",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MapField;
            import org.higherkindedj.optics.annotations.MappingSpec;
            public class Outer {
                private record Secret(String value) {}

                public record OWire(String label, String name) {}

                @GenerateMapping
                public interface Spec extends MappingSpec<Dom, OWire> {
                    @MapField(to = "label") Secret id();
                }
            }
            """);
    var compilation = compile(DOMAIN, outer);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@MapField method 'id' names 'Secret', which cannot be reached from 'com.example'.");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("wildcard-differing leaf declarations fold to the narrowest prism type")
  void wildcardCovariantLeaves() {
    // ValidatedPrism<TDto, T> is return-type-substitutable for
    // ValidatedPrism<TDto, ? extends T>, so the pair may legally coexist (JLS 9.4.1.3); the
    // of(...) field and its accessor take the narrower one, which overrides both.
    var l1 =
        JavaFileObjects.forSourceString(
            "com.example.LeafA",
            """
            package com.example;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            public interface LeafA<TDto, T> { ValidatedPrism<TDto, ? extends T> items(); }
            """);
    var l2 =
        JavaFileObjects.forSourceString(
            "com.example.LeafB",
            """
            package com.example;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            public interface LeafB<TDto, T> { ValidatedPrism<TDto, T> items(); }
            """);
    var domain =
        JavaFileObjects.forSourceString(
            "com.example.WPage",
            """
            package com.example;
            import java.util.List;
            public record WPage<T>(List<T> items) {}
            """);
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.WDto",
            """
            package com.example;
            import java.util.List;
            public record WDto<TDto>(List<TDto> items) {}
            """);
    var spec =
        JavaFileObjects.forSourceString(
            "com.example.WSpec",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface WSpec<T, TDto>
                extends MappingSpec<WPage<T>, WDto<TDto>>, LeafA<TDto, T>, LeafB<TDto, T> {}
            """);
    var compilation = compile(l1, l2, domain, wire, spec);
    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(
        compilation, "com.example.WSpecImpl", "ValidatedPrism<TDto, T> items");
  }

  @Test
  @DisplayName("a member type the spec's package cannot reach is refused at the declaration")
  void unreachableMemberType() {
    var secret =
        JavaFileObjects.forSourceString(
            "other.Secret",
            """
            package other;
            record Secret(String value) {}
            """);
    var vocab =
        JavaFileObjects.forSourceString(
            "other.BVocab",
            """
            package other;
            import org.higherkindedj.optics.annotations.MapField;
            public interface BVocab { @MapField(to = "label") Secret id(); }
            """);
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.BWire",
            """
            package com.example;
            public record BWire(String label, String name) {}
            """);
    var spec =
        JavaFileObjects.forSourceString(
            "com.example.BSpec",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface BSpec extends MappingSpec<Dom, BWire>, other.BVocab {}
            """);
    var compilation = compile(DOMAIN, secret, vocab, wire, spec);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@GenerateMapping: @MapField method 'id' (inherited from 'BVocab') names 'Secret',"
                + " which cannot be reached from 'com.example'. The generated Impl writes the"
                + " member's type out in full, so every type named inside it has to be visible in"
                + " the spec's package, where the Impl is declared. Make 'Secret' and the types"
                + " enclosing it public, or declare the spec in the package they are already"
                + " visible from.");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("a leaf whose prism type the spec's package cannot reach is refused too")
  void unreachableLeafType() {
    // The leaf becomes a constructor-supplied field of the Impl, generated in the spec's
    // package, so the reachability question is the same one the rename stub asks.
    var hidden =
        JavaFileObjects.forSourceString(
            "other.Hidden",
            """
            package other;
            record Hidden(String value) {}
            """);
    var vocab =
        JavaFileObjects.forSourceString(
            "other.EVocab",
            """
            package other;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            public interface EVocab { ValidatedPrism<String, Hidden> secret(); }
            """);
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.EDto",
            """
            package com.example;
            import java.util.List;
            public record EDto<T>(List<T> items) {}
            """);
    var domain =
        JavaFileObjects.forSourceString(
            "com.example.EPage",
            """
            package com.example;
            import java.util.List;
            public record EPage<T>(List<T> items) {}
            """);
    var spec =
        JavaFileObjects.forSourceString(
            "com.example.ESpec",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface ESpec<T> extends MappingSpec<EPage<T>, EDto<T>>, other.EVocab {}
            """);
    var compilation = compile(hidden, vocab, domain, wire, spec);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@GenerateMapping: abstract leaf 'secret' (inherited from 'EVocab') names 'Hidden',"
                + " which cannot be reached from 'com.example'.");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("a leaf declaring its own type parameters is refused at the declaration")
  void genericMethodLeaf() {
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.CDto",
            """
            package com.example;
            import java.util.List;
            public record CDto<T>(List<T> items) {}
            """);
    var domain =
        JavaFileObjects.forSourceString(
            "com.example.CPage",
            """
            package com.example;
            import java.util.List;
            public record CPage<T>(List<T> items) {}
            """);
    var spec =
        JavaFileObjects.forSourceString(
            "com.example.CSpec",
            """
            package com.example;
            import org.higherkindedj.optics.validated.ValidatedPrism;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface CSpec<T> extends MappingSpec<CPage<T>, CDto<T>> {
                <R> ValidatedPrism<R, R> items();
            }
            """);
    var compilation = compile(domain, wire, spec);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@GenerateMapping: abstract method 'items' declares type parameters of its own. The"
                + " generated Impl carries a leaf as a constructor-supplied field and a rename as"
                + " a stub, and neither has anywhere to declare the method's own type parameters,"
                + " so the generated file would name a variable nothing brings into scope."
                + " Declare the element types among the type parameters of 'CSpec', where the spec"
                + " can thread them, or give the method a body.");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("a rename declaring its own type parameters is refused the same way")
  void genericMethodRename() {
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.DWire",
            """
            package com.example;
            public record DWire(String label, String name) {}
            """);
    var spec =
        JavaFileObjects.forSourceString(
            "com.example.DSpec",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MapField;
            import org.higherkindedj.optics.annotations.MappingSpec;
            @GenerateMapping
            public interface DSpec extends MappingSpec<Dom, DWire> {
                @MapField(to = "label") <R> R id();
            }
            """);
    var compilation = compile(DOMAIN, wire, spec);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "abstract method 'id' declares type parameters of its own. The generated Impl carries"
                + " a leaf as a constructor-supplied field and a rename as a stub, and neither has"
                + " anywhere to declare the method's own type parameters, so the generated file"
                + " would name a variable nothing brings into scope. Give 'id' a concrete return"
                + " type; a rename is a marker method and the generated stub only has to name"
                + " one.");
    assertThat(compilation).hadErrorCount(1);
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
  @DisplayName(
      "a sealed wire, whose variant spec reaches its vocabulary through a generic ancestor")
  void sealedWireWithAGenericAncestor() {
    // The sealed column reads members in two places at once: the variant spec is an ordinary
    // record pair, and the sum spec reads the pairs to build its dispatch. A member left as
    // declared in either would put a free variable into one of the two Impls.
    var model =
        JavaFileObjects.forSourceString(
            "com.example.Sealed",
            """
            package com.example;

            import org.higherkindedj.optics.validated.ValidatedPrism;

            public final class Sealed {
              public record Pan(String digits) {}

              public sealed interface Pay permits Card {}

              public record Card(Pan pan) implements Pay {}

              public sealed interface PayDto permits CardDto {}

              public record CardDto(String pan) implements PayDto {}

              public interface BasePans<T> {
                default ValidatedPrism<String, T> pan() {
                  throw new UnsupportedOperationException();
                }
              }

              public interface Pans extends BasePans<Pan> {}
            }
            """);

    var variantSpec =
        JavaFileObjects.forSourceString(
            "com.example.CardMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface CardMapping
                extends MappingSpec<Sealed.Card, Sealed.CardDto>, Sealed.Pans {}
            """);

    var sumSpec =
        JavaFileObjects.forSourceString(
            "com.example.PayMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface PayMapping extends MappingSpec<Sealed.Pay, Sealed.PayDto> {}
            """);

    var compilation = compile(model, variantSpec, sumSpec);

    assertThat(compilation).succeeded();
    // The variant's leaf arrives at Pan, the argument Pans gives BasePans, not at its T.
    assertGeneratedCodeContains(
        compilation,
        "com.example.CardMappingImpl",
        "return new Sealed.CardDto(pan().build(domain.pan()))");
    // And the sum dispatches to it.
    assertGeneratedCodeContains(
        compilation,
        "com.example.PayMappingImpl",
        "case Sealed.Card v -> CardMappingImpl.INSTANCE.build(v)");
  }

  @Test
  @DisplayName("a sealed wire whose sum type extends a generic interface")
  void sealedWireWhoseSumExtendsAGenericInterface() {
    // The sum itself is non-generic, but it inherits an accessor from a generic supertype. That
    // is the sealed form of the bean's inherited column: nothing on the type names a parameter,
    // and a member does.
    var model =
        JavaFileObjects.forSourceString(
            "com.example.Tagged",
            """
            package com.example;

            public final class Tagged {
              public interface HasTag<T> {
                T tag();
              }

              public sealed interface Pay extends HasTag<String> permits Card {}

              public record Card(String number, String tag) implements Pay {}

              public sealed interface PayDto permits CardDto {}

              public record CardDto(String number, String tag) implements PayDto {}
            }
            """);

    var variantSpec =
        JavaFileObjects.forSourceString(
            "com.example.TaggedCardMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface TaggedCardMapping extends MappingSpec<Tagged.Card, Tagged.CardDto> {}
            """);

    var sumSpec =
        JavaFileObjects.forSourceString(
            "com.example.TaggedPayMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface TaggedPayMapping extends MappingSpec<Tagged.Pay, Tagged.PayDto> {}
            """);

    var compilation = compile(model, variantSpec, sumSpec);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(
        compilation,
        "com.example.TaggedPayMappingImpl",
        "case Tagged.Card v -> TaggedCardMappingImpl.INSTANCE.build(v)");
  }

  @Test
  @DisplayName("a mix-in reached through a generic ancestor is read under the spec")
  void mixinWithAGenericAncestor() {
    var base =
        JavaFileObjects.forSourceString(
            "com.example.BaseVocab",
            """
            package com.example;

            public interface BaseVocab<T> {
                @org.higherkindedj.optics.annotations.MapField(to = "label")
                T name();
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

            public record MixWire(String id, String label) {}
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

    // 'T name()' is declared in BaseVocab's vocabulary and instantiated by the interface below
    // it, so the spec has it at String. Read as declared it was compared with the wire's own
    // String and matched only by coincidence; refusing it was the answer before #752.
    var compilation = compile(DOMAIN, base, mixin, wire, spec);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.MixMappingImpl", "public String name()");
  }

  @Test
  @DisplayName("a generic mix-in extended directly is read under the spec")
  void genericMixinExtendedDirectly() {
    var base =
        JavaFileObjects.forSourceString(
            "com.example.DirectVocab",
            """
            package com.example;

            public interface DirectVocab<T> {
                @org.higherkindedj.optics.annotations.MapField(to = "label")
                T name();
            }
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.DirectWire",
            """
            package com.example;

            public record DirectWire(String id, String label) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.DirectMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface DirectMapping
                extends MappingSpec<Dom, DirectWire>, DirectVocab<String> {}
            """);

    var compilation = compile(DOMAIN, base, wire, spec);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(
        compilation, "com.example.DirectMappingImpl", "public String name()");
  }

  @Test
  @DisplayName("a generic mix-in's leaf arrives at the type the spec gives it")
  void genericMixinCarryingALeaf() {
    var email =
        JavaFileObjects.forSourceString(
            "com.example.Email",
            """
            package com.example;

            public record Email(String value) {}
            """);

    var domain =
        JavaFileObjects.forSourceString(
            "com.example.LeafDom",
            """
            package com.example;

            public record LeafDom(String id, Email name) {}
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.LeafWire",
            """
            package com.example;

            public record LeafWire(String id, String name) {}
            """);

    var leaves =
        JavaFileObjects.forSourceString(
            "com.example.Leaves",
            """
            package com.example;

            import org.higherkindedj.optics.validated.ValidatedPrism;

            public interface Leaves<T> {
                default ValidatedPrism<String, T> name() {
                    return ValidatedPrism.of(w -> null, d -> null);
                }
            }
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.LeafMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface LeafMapping extends MappingSpec<LeafDom, LeafWire>, Leaves<Email> {}
            """);

    // The sharper half: a leaf is matched by its two type arguments against the wire and domain
    // components, so a leaf read as 'ValidatedPrism<String, T>' matches nothing and the author is
    // told to declare what they already wrote one interface along.
    var compilation = compile(email, domain, wire, leaves, spec);

    assertThat(compilation).succeeded();
    // The leaf binds by its two arguments, so this is the assertion that the substitution ran.
    assertGeneratedCodeContains(compilation, "com.example.LeafMappingImpl", "name()::parse");
  }

  @Test
  @DisplayName("an inherited member colliding with a generated one is caught under the spec")
  void inheritedCollisionIsCaughtUnderTheSpec() {
    var auditable =
        JavaFileObjects.forSourceString(
            "com.example.Auditable",
            """
            package com.example;

            public interface Auditable<D> {
                default String build(D value) {
                    return "";
                }
            }
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.AuditWire",
            """
            package com.example;

            public record AuditWire(String id, String name) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.AuditMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface AuditMapping extends MappingSpec<Dom, AuditWire>, Auditable<Dom> {}
            """);

    // The collision check compares a spec member's parameters against the members the Impl is
    // about to emit, which are instantiated. Read as declared, 'build(D)' erases to
    // 'build(Object)', matches nothing, and the Impl then declares a second 'build' that javac
    // rejects in a file the author never wrote. The reachable shape only exists because a generic
    // mix-in is accepted at all.
    var compilation = compile(DOMAIN, auditable, wire, spec);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("'build(Dom)'");
    assertThat(compilation).hadErrorContaining("collides with the 'build' member");
  }

  @Test
  @DisplayName("a generic mix-in's derived field is read under the spec")
  void genericMixinDerivedField() {
    var vocab =
        JavaFileObjects.forSourceString(
            "com.example.TagVocab",
            """
            package com.example;

            import org.higherkindedj.optics.Getter;

            public interface TagVocab<D> {
                default Getter<D, String> tag() {
                    return d -> "tag";
                }
            }
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.TagWire",
            """
            package com.example;

            public record TagWire(String id, String name, String tag) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.TagMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface TagMapping extends MappingSpec<Dom, TagWire>, TagVocab<Dom> {}
            """);

    // The derived field's Getter is matched against the domain record and the wire component,
    // both instantiated. Read as declared it is Getter<D, String> and matches neither, so the
    // spec is refused with a remedy naming a variable the author never wrote.
    var compilation = compile(DOMAIN, vocab, wire, spec);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(compilation, "com.example.TagMappingImpl", "tag().get(domain)");
  }

  @Test
  @DisplayName("a hand-written mapper inherited from a generic mix-in gets the targeted answer")
  void genericMixinHandMapper() {
    var vocab =
        JavaFileObjects.forSourceString(
            "com.example.Converts",
            """
            package com.example;

            public interface Converts<D, W> {
                W toWire(D domain);
            }
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.ConvWire",
            """
            package com.example;

            public record ConvWire(String id, String name) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.ConvMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface ConvMapping
                extends MappingSpec<Dom, ConvWire>, Converts<Dom, ConvWire> {}
            """);

    // The hand-written-mapper reflex deserves its targeted answer even when the method arrives
    // through a mix-in: read as declared the signature is (D) -> W, which matches no pair.
    var compilation = compile(DOMAIN, vocab, wire, spec);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("redeclares the mapping itself");
  }

  @Test
  @DisplayName("a non-generic ancestor behind a raw link keeps its members")
  void nonGenericAncestorBehindARawLink() {
    var vocab =
        JavaFileObjects.forSourceString(
            "com.example.PlainVocab",
            """
            package com.example;

            public interface PlainVocab {
                @org.higherkindedj.optics.annotations.MapField(to = "label")
                String name();
            }
            """);

    var mid =
        JavaFileObjects.forSourceString(
            "com.example.PlainMid",
            """
            package com.example;

            public interface PlainMid<T> extends PlainVocab {}
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.PlainWire",
            """
            package com.example;

            public record PlainWire(String id, String label) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.PlainMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            @SuppressWarnings("rawtypes")
            public interface PlainMapping extends MappingSpec<Dom, PlainWire>, PlainMid {}
            """);

    // Erasure through a raw supertype only reaches members whose own declaring interface is
    // generic; javac substitutes nothing for a non-generic one. Refusing this would name a clause
    // the author may not own, for a loss that did not happen.
    var compilation = compile(DOMAIN, vocab, mid, wire, spec);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.PlainMappingImpl");
  }

  @Test
  @DisplayName("a raw argument on the spec's own mix-in clause is refused")
  void rawArgumentOnTheMixinClause() {
    var vocab =
        JavaFileObjects.forSourceString(
            "com.example.ArgVocab",
            """
            package com.example;

            public interface ArgVocab<T> {
                @org.higherkindedj.optics.annotations.MapField(to = "label")
                T name();
            }
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.ArgWire",
            """
            package com.example;

            import java.util.List;

            @SuppressWarnings("rawtypes")
            public record ArgWire(String id, List label) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.ArgMapping",
            """
            package com.example;

            import java.util.List;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            @SuppressWarnings("rawtypes")
            public interface ArgMapping extends MappingSpec<Dom, ArgWire>, ArgVocab<List> {}
            """);

    // The member is emitted at the argument written here, so a raw one lands in the Impl as a
    // [rawtypes] warning the author's own suppression does not reach. The MappingSpec clause has
    // always been checked this way; the mix-in clause was not.
    var compilation = compile(DOMAIN, vocab, wire, spec);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("mix-in 'ArgVocab' is used at an unsupported instantiation");
  }

  @Test
  @DisplayName("a raw clause one interface up names the file that holds it")
  void rawClauseOneLevelUp() {
    var vocab =
        JavaFileObjects.forSourceString(
            "com.example.UpVocab",
            """
            package com.example;

            public interface UpVocab<T> {
                @org.higherkindedj.optics.annotations.MapField(to = "label")
                T name();
            }
            """);

    var mid =
        JavaFileObjects.forSourceString(
            "com.example.UpMid",
            """
            package com.example;

            @SuppressWarnings("rawtypes")
            public interface UpMid extends UpVocab {}
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.UpWire",
            """
            package com.example;

            public record UpWire(String id, String label) {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.UpMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface UpMapping extends MappingSpec<Dom, UpWire>, UpMid {}
            """);

    // The spec's own clause is ordinary; the raw one is in UpMid. A remedy naming a clause the
    // spec does not have is one the author cannot act on, so the message names the file it is in.
    var compilation = compile(DOMAIN, vocab, mid, wire, spec);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("mix-in 'UpVocab' is extended raw by 'UpMid'");
    assertThat(compilation)
        .hadErrorContaining("Name the type arguments where 'UpMid' extends 'UpVocab'");
  }

  @Test
  @DisplayName("a mix-in reached raw is refused, directly and through a child")
  void rawMixinIsRefused() {
    var base =
        JavaFileObjects.forSourceString(
            "com.example.RawVocab",
            """
            package com.example;

            public interface RawVocab<T> {
                @org.higherkindedj.optics.annotations.MapField(to = "label")
                T name();
            }
            """);

    var wire =
        JavaFileObjects.forSourceString(
            "com.example.RawWire",
            """
            package com.example;

            public record RawWire(String id, String label) {}
            """);

    var direct =
        JavaFileObjects.forSourceString(
            "com.example.RawMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            @SuppressWarnings("rawtypes")
            public interface RawMapping extends MappingSpec<Dom, RawWire>, RawVocab {}
            """);

    // Substitution has nothing to work from: a raw supertype erases every member, whatever it
    // declares, so the spec would inherit Object where it was written with String.
    var directly = compile(DOMAIN, base, wire, direct);

    assertThat(directly).failed();
    assertThat(directly).hadErrorContaining("mix-in 'RawVocab' is extended raw by the spec");
    assertThat(directly)
        .hadErrorContaining("Name the type arguments where the spec extends 'RawVocab'");

    // Generic, and extended raw by the spec below: the link the spec lists is what erases, and
    // the vocabulary beneath it is written with its argument intact. Its contribution is a
    // default method, the third of the three shapes a mix-in can carry.
    var routeVocab =
        JavaFileObjects.forSourceString(
            "com.example.RawRouteVocab",
            """
            package com.example;

            import org.higherkindedj.optics.validated.ValidatedPrism;

            public interface RawRouteVocab<T> {
                default ValidatedPrism<String, T> label() {
                    return ValidatedPrism.of(raw -> null, value -> "");
                }
            }
            """);

    var mid =
        JavaFileObjects.forSourceString(
            "com.example.RawMid",
            """
            package com.example;

            public interface RawMid<T> extends RawRouteVocab<T> {}
            """);

    var throughChild =
        JavaFileObjects.forSourceString(
            "com.example.RawRouteMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            @SuppressWarnings("rawtypes")
            public interface RawRouteMapping extends MappingSpec<Dom, RawWire>, RawMid {}
            """);

    // Rawness erases downwards, so the link the spec lists can be perfectly ordinary while the
    // one above it is not. The route is named, because that is the clause to correct.
    var throughAChild = compile(DOMAIN, routeVocab, wire, mid, throughChild);

    assertThat(throughAChild).failed();
    // The raw clause is RawMid, not the vocabulary beneath it, so that is what the remedy names:
    // 'extends RawRouteVocab<...>' is not a line the author's spec has.
    assertThat(throughAChild)
        .hadErrorContaining(
            "mix-in 'RawRouteVocab' is reached through 'RawMid', which the spec extends raw");
    assertThat(throughAChild)
        .hadErrorContaining("Name the type arguments where the spec extends 'RawMid'");

    // The vocabulary test reads the three things a mix-in can contribute, and a rename is not a
    // default method: an ancestor carrying only an abstract leaf is as much a reason to refuse a
    // raw route as one carrying a rename.
    var leafBase =
        JavaFileObjects.forSourceString(
            "com.example.RawLeafVocab",
            """
            package com.example;

            import org.higherkindedj.optics.validated.ValidatedPrism;

            public interface RawLeafVocab<T> {
                ValidatedPrism<String, T> label();
            }
            """);

    var leafSpec =
        JavaFileObjects.forSourceString(
            "com.example.RawLeafMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            @SuppressWarnings("rawtypes")
            public interface RawLeafMapping extends MappingSpec<Dom, RawWire>, RawLeafVocab {}
            """);

    var carryingALeaf = compile(DOMAIN, leafBase, wire, leafSpec);

    assertThat(carryingALeaf).failed();
    assertThat(carryingALeaf)
        .hadErrorContaining("mix-in 'RawLeafVocab' is extended raw by the spec");

    // A raw ancestor that contributes nothing carries nothing in, so it is no reason to refuse.
    // An interface static is the shape that reads as a method and is not inherited (JLS 8.4.8),
    // so it exercises the vocabulary test's negative answer without leaving an unimplemented
    // member behind.
    var inert =
        JavaFileObjects.forSourceString(
            "com.example.RawInert",
            """
            package com.example;

            public interface RawInert<T> {
                static String describe() {
                    return "inert";
                }
            }
            """);

    var inertSpec =
        JavaFileObjects.forSourceString(
            "com.example.RawInertMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            @SuppressWarnings("rawtypes")
            public interface RawInertMapping extends MappingSpec<Dom, InertWire>, RawInert {}
            """);

    var inertWire =
        JavaFileObjects.forSourceString(
            "com.example.InertWire",
            """
            package com.example;

            public record InertWire(String id, String name) {}
            """);

    var carryingNothing = compile(DOMAIN, inert, inertWire, inertSpec);

    assertThat(carryingNothing).succeeded();
    assertThat(carryingNothing).generatedSourceFile("com.example.RawInertMappingImpl");
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

  @Test
  @DisplayName("a bean wire built through a builder the factory instantiates")
  void beanWireBuiltThroughAnInstantiatedGenericBuilder() {
    var wire =
        JavaFileObjects.forSourceString(
            "com.example.BuiltWire",
            """
            package com.example;

            public class BuiltWire {
                private final String id;
                private final String name;
                private BuiltWire(String id, String name) {
                    this.id = id;
                    this.name = name;
                }
                public String getId() { return id; }
                public String getName() { return name; }
                public static Builder<String> builder() { return new Builder<>(); }

                public static final class Builder<T> {
                    private T id;
                    private String name;
                    public Builder<T> setId(T id) { this.id = id; return this; }
                    public Builder<T> setName(String name) { this.name = name; return this; }
                    public BuiltWire build() { return new BuiltWire(String.valueOf(id), name); }
                }
            }
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.BuiltMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface BuiltMapping extends MappingSpec<Dom, BuiltWire> {}
            """);

    // The factory says Builder<String>; rebuilding the builder from its element says Builder<T>,
    // and setId is then read at a variable this wire's author never wrote.
    var compilation = compile(DOMAIN, wire, spec);

    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("com.example.BuiltMappingImpl");
  }

  @Test
  @DisplayName("a generic ancestor contributing only an abstract leaf is judged on it")
  void genericAncestorCarryingOnlyAnAbstractLeaf() {
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

    var base =
        JavaFileObjects.forSourceString(
            "com.example.LeafBase",
            """
            package com.example;

            import org.higherkindedj.optics.validated.ValidatedPrism;

            public interface LeafBase<X> {
                ValidatedPrism<X, String> items();
            }
            """);

    var mixin =
        JavaFileObjects.forSourceString(
            "com.example.LeafVocab",
            """
            package com.example;

            public interface LeafVocab extends LeafBase<String> {}
            """);

    var spec =
        JavaFileObjects.forSourceString(
            "com.example.LeafPageMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface LeafPageMapping<T> extends MappingSpec<Page<T>, PageDto<T>>, LeafVocab {}
            """);

    // The leaf is declared 'ValidatedPrism<X, String>' in LeafBase's vocabulary, and the interface
    // below it says X is String. The Impl declares T, not X, so the leaf field is only nameable
    // there once it is read under the spec.
    var compilation = compile(domain, wire, base, mixin, spec);

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(
        compilation, "com.example.LeafPageMappingImpl", "ValidatedPrism<String, String> items");
  }
}
