// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.optics.processing.effect.ComposeEffectsProcessor;
import org.higherkindedj.optics.processing.effect.EffectAlgebraProcessor;
import org.higherkindedj.optics.processing.effect.PathProcessor;
import org.higherkindedj.optics.processing.effect.PathSourceProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A generated companion is a top-level class in its annotated type's package, or the one its
 * annotation names, so every type it reads or writes has to be visible from there: the annotated
 * type, the bounds of its type parameters, and each component, subtype, variant or operation the
 * companion generates for. A private type nested beside the annotated type, or one another package
 * cannot see, is refused at the annotation with a what/why/fix message, never left to fail inside
 * the generated file. A type the companion never names stays free to be private.
 */
@DisplayName("Companion generators - every type a companion names is visible from its package")
class CompanionReachabilityTest {

  private static final String IMPORTS =
      """
      package com.example;

      import java.util.ArrayList;
      import java.util.List;
      import java.util.Optional;
      import org.higherkindedj.hkt.TypeArity;
      import org.higherkindedj.hkt.WitnessArity;
      import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
      import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
      import org.higherkindedj.hkt.effect.annotation.PathSource;
      import org.higherkindedj.hkt.effect.annotation.PathVia;
      import org.higherkindedj.hkt.error.ErrorEnvelope;
      import org.higherkindedj.hkt.maybe.MaybeKind;
      import org.higherkindedj.optics.Iso;
      import org.higherkindedj.optics.Lens;
      import org.higherkindedj.optics.annotations.GenerateAssembly;
      import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
      import org.higherkindedj.optics.annotations.GenerateFocus;
      import org.higherkindedj.optics.annotations.GenerateFolds;
      import org.higherkindedj.optics.annotations.GenerateGetters;
      import org.higherkindedj.optics.annotations.GenerateIsos;
      import org.higherkindedj.optics.annotations.GenerateLenses;
      import org.higherkindedj.optics.annotations.GeneratePrisms;
      import org.higherkindedj.optics.annotations.GenerateSetters;
      import org.higherkindedj.optics.annotations.GenerateTraversals;
      import org.higherkindedj.optics.annotations.ImportOptics;
      import org.higherkindedj.optics.annotations.OpticsSpec;
      import org.higherkindedj.optics.annotations.ViaConstructor;

      """;

  private static final String WHY =
      " The generated companion is a top-level class in that package, where it names every type it"
          + " reads or writes, so each has to be visible from there. ";

  /** A package-private holder class in {@code com.example}, its body nested inside it. */
  private static JavaFileObject holder(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name, IMPORTS + "final class " + name + " {\n" + body + "}\n");
  }

  /** A source file in its own package, written whole. */
  private static JavaFileObject source(String qualifiedName, String text) {
    return JavaFileObjects.forSourceString(qualifiedName, text);
  }

  private static Compilation compile(List<String> options, JavaFileObject... sources) {
    return javac()
        .withProcessors(
            List.<Processor>of(
                new LensProcessor(),
                new FocusProcessor(),
                new PrismProcessor(),
                new TraversalProcessor(),
                new GetterProcessor(),
                new SetterProcessor(),
                new FoldProcessor(),
                new IsoProcessor(),
                new ImportOpticsProcessor(),
                new AssemblyProcessor(),
                new ErrorEnvelopeProcessor(),
                new EffectAlgebraProcessor(),
                new ComposeEffectsProcessor(),
                new PathSourceProcessor(),
                new PathProcessor()))
        .withOptions(options)
        .compile(sources);
  }

  @Nested
  @DisplayName("a type the companion's package cannot reach is refused at the annotation")
  class Refused {

    /**
     * One compilation for every route: each holder declares one annotated type whose companion
     * would name a type nested {@code private} beside it, and each processor refuses it where the
     * annotation sits.
     */
    private static final class Routes {
      static final JavaFileObject LENSES =
          holder(
              "Lenses",
              """
              private record Sku(String value) {}
              @GenerateLenses record Stock(Sku sku, String name) {}
              """);

      static final JavaFileObject ENVELOPES =
          holder(
              "Envelopes",
              """
              private record Ctx(String id) {}
              @GenerateErrorEnvelope sealed interface OrderError {
                record NotFound(String id, ErrorEnvelope<Ctx> envelope) implements OrderError {}
              }
              """);

      static final Compilation COMPILATION =
          compile(
              List.of("-Xlint:all,-processing"),
              LENSES,
              ENVELOPES,
              holder(
                  "HiddenLenses",
                  """
                  @GenerateLenses private record Item(String name) {}
                  """),
              holder(
                  "BoundLenses",
                  """
                  private interface Tag {}
                  @GenerateLenses record Tagged<T extends Tag>(T value) {}
                  """),
              holder(
                  "Focus",
                  """
                  private record Sku(String value) {}
                  @GenerateFocus record Line(Sku sku) {}
                  """),
              holder(
                  "Getters",
                  """
                  private record Sku(String value) {}
                  @GenerateGetters record Bin(Sku sku) {}
                  """),
              holder(
                  "Setters",
                  """
                  private record Sku(String value) {}
                  @GenerateSetters record Tray(Sku sku) {}
                  """),
              holder(
                  "Folds",
                  """
                  private record Sku(String value) {}
                  @GenerateFolds record Box(List<Sku> skus) {}
                  """),
              holder(
                  "Traversals",
                  """
                  private record Sku(String value) {}
                  @GenerateTraversals record Rack(List<Sku> skus) {}
                  """),
              holder(
                  "Prisms",
                  """
                  @GeneratePrisms sealed interface Shape permits Circle, Square {}
                  record Circle(double radius) implements Shape {}
                  private record Square(double side) implements Shape {}
                  """),
              holder(
                  "PrismClause",
                  """
                  private record Unit() {}
                  @GeneratePrisms sealed interface Measure<T> permits Metric, Other {}
                  record Metric() implements Measure<Unit> {}
                  record Other<T>() implements Measure<T> {}
                  """),
              holder(
                  "HiddenPrisms",
                  """
                  @GeneratePrisms private enum Level { LOW, HIGH }
                  """),
              holder(
                  "Imports",
                  """
                  private record Sku(String value) {}
                  record Item(Sku sku) {}
                  @ImportOptics(Item.class) interface ItemOptics {}
                  """),
              holder(
                  "ImportedSum",
                  """
                  sealed interface Shape permits Circle, Square {}
                  record Circle(double radius) implements Shape {}
                  private record Square(double side) implements Shape {}
                  @ImportOptics(Shape.class) interface ShapeOptics {}
                  """),
              holder(
                  "ImportedEnum",
                  """
                  private enum Level { LOW, HIGH }
                  @ImportOptics(Level.class) interface LevelOptics {}
                  """),
              holder(
                  "ImportedWither",
                  """
                  private record Sku(String value) {}
                  static final class Account {
                    private final Sku sku;
                    Account(Sku sku) { this.sku = sku; }
                    public Sku sku() { return sku; }
                    public Account withSku(Sku sku) { return new Account(sku); }
                  }
                  @ImportOptics(Account.class) interface AccountOptics {}
                  """),
              holder(
                  "SpecFocus",
                  """
                  private record Sku(String value) {}
                  record Item(Sku sku, String name) {}
                  @ImportOptics interface ItemOptics extends OpticsSpec<Item> {
                    @ViaConstructor Lens<Item, Sku> sku();
                    @ViaConstructor Lens<Item, String> name();
                  }
                  """),
              holder(
                  "SpecSource",
                  """
                  private record Item(String name) {}
                  @ImportOptics interface ItemOptics extends OpticsSpec<Item> {
                    @ViaConstructor Lens<Item, String> name();
                  }
                  """),
              holder(
                  "Assembly",
                  """
                  private record Sku(String value) {}
                  @GenerateAssembly record Order(Sku sku, String note) {}
                  """),
              holder(
                  "HiddenAssembly",
                  """
                  @GenerateAssembly private record Order(String note) {}
                  """),
              holder(
                  "EnvelopeComponent",
                  """
                  private record Sku(String value) {}
                  record Ctx(String id) {}
                  @GenerateErrorEnvelope sealed interface StockError {
                    record Missing(Sku sku, ErrorEnvelope<Ctx> envelope) implements StockError {}
                  }
                  """),
              holder(
                  "HiddenEnvelope",
                  """
                  record Ctx(String id) {}
                  @GenerateErrorEnvelope private sealed interface PayError {
                    record Declined(String id, ErrorEnvelope<Ctx> envelope) implements PayError {}
                  }
                  """),
              holder(
                  "Retargeted",
                  """
                  @GenerateLenses(targetPackage = "com.example.optics") record Label(String text) {}
                  """),
              holder(
                  "Enclosed",
                  """
                  private static final class Codes {
                    record Sku(String value) {}
                  }
                  @GenerateLenses record Crate(Codes.Sku sku) {}
                  """));
    }

    private static void refuses(String what) {
      assertThat(Routes.COMPILATION).hadErrorContaining(what);
    }

    @Test
    @DisplayName("each annotated type is refused once, at its annotation")
    void refusedAtEveryAnnotationOnly() {
      assertThat(Routes.COMPILATION).failed();
      assertThat(Routes.COMPILATION).hadErrorCount(24);
      Assertions.assertThat(Routes.COMPILATION.errors())
          .allSatisfy(
              error -> Assertions.assertThat(error.getMessage(null)).contains("cannot be reached"));
      assertThat(Routes.COMPILATION)
          .hadErrorContaining("record component 'sku' of 'Stock'")
          .inFile(Routes.LENSES)
          .onLineContaining("@GenerateLenses record Stock");
      assertThat(Routes.COMPILATION)
          .hadErrorContaining("context record 'Envelopes.Ctx'")
          .inFile(Routes.ENVELOPES)
          .onLineContaining("@GenerateErrorEnvelope sealed interface OrderError");
    }

    @Test
    @DisplayName("a lens companion's component, naming the type, the package and the fix")
    void lenses() {
      refuses(
          "@GenerateLenses: record component 'sku' of 'Stock' names 'Sku', which cannot be reached"
              + " from 'com.example'."
              + WHY
              + "Remove 'private' from 'Sku'.");
      refuses(
          "@GenerateLenses: record 'HiddenLenses.Item' cannot be reached from 'com.example'."
              + WHY
              + "Remove 'private' from 'Item'.");
      refuses(
          "@GenerateLenses: type parameter 'T' of 'Tagged' names 'Tag', which cannot be reached");
    }

    @Test
    @DisplayName("a type hidden only by the class enclosing it names that class")
    void enclosingClass() {
      refuses(
          "@GenerateLenses: record component 'sku' of 'Crate' names 'Sku', which cannot be reached"
              + " from 'com.example'."
              + WHY
              + "Remove 'private' from 'Codes', which encloses 'Sku'.");
    }

    @Test
    @DisplayName("every record companion: focus, getters, setters, folds and traversals")
    void recordCompanions() {
      refuses("@GenerateFocus: record component 'sku' of 'Line' names 'Sku'");
      refuses("@GenerateGetters: record component 'sku' of 'Bin' names 'Sku'");
      refuses("@GenerateSetters: record component 'sku' of 'Tray' names 'Sku'");
      refuses("@GenerateFolds: record component 'skus' of 'Box' names 'Sku'");
      refuses("@GenerateTraversals: record component 'skus' of 'Rack' names 'Sku'");
    }

    @Test
    @DisplayName("a prism companion's subtype, the sum type as a subtype names it, or the enum")
    void prisms() {
      refuses(
          "@GeneratePrisms: permitted subtype 'Prisms.Square' of 'Shape' cannot be reached from"
              + " 'com.example'.");
      refuses(
          "@GeneratePrisms: the supertype clause of 'Metric' names 'Unit', which cannot be reached"
              + " from 'com.example'.");
      refuses("@GeneratePrisms: enum 'HiddenPrisms.Level' cannot be reached from 'com.example'.");
    }

    @Test
    @DisplayName("an imported record, sum type, enum, wither class or spec interface")
    void importOptics() {
      refuses("@ImportOptics: record component 'sku' of 'Item' names 'Sku'");
      refuses("@ImportOptics: permitted subtype 'ImportedSum.Square' of 'Shape' cannot be reached");
      refuses("@ImportOptics: enum 'ImportedEnum.Level' cannot be reached");
      refuses("@ImportOptics: field 'sku' of 'Account' names 'Sku'");
      refuses(
          "@ImportOptics: optic 'sku' names 'Sku', which cannot be reached from 'com.example'.");
      refuses("@ImportOptics: source type 'SpecSource.Item' cannot be reached from 'com.example'.");
    }

    @Test
    @DisplayName("an assembly's component, or the record itself")
    void assembly() {
      refuses("@GenerateAssembly: record component 'sku' of 'Order' names 'Sku'");
      refuses(
          "@GenerateAssembly: record 'HiddenAssembly.Order' cannot be reached from 'com.example'.");
    }

    @Test
    @DisplayName("an error envelope's context, a variant's component, or the hierarchy itself")
    void errorEnvelope() {
      refuses(
          "@GenerateErrorEnvelope: context record 'Envelopes.Ctx' cannot be reached from"
              + " 'com.example'."
              + WHY
              + "Remove 'private' from 'Ctx'.");
      refuses("@GenerateErrorEnvelope: record component 'sku' of 'Missing' names 'Sku'");
      // The variant nested in the private hierarchy is hidden by the same keyword: one refusal.
      refuses(
          "@GenerateErrorEnvelope: interface 'HiddenEnvelope.PayError' cannot be reached from"
              + " 'com.example'.");
    }

    @Test
    @DisplayName("a companion written to another package offers to write it beside the type")
    void targetPackage() {
      refuses(
          "@GenerateLenses: record 'Retargeted.Label' cannot be reached from"
              + " 'com.example.optics'."
              + WHY
              + "Make 'Retargeted' and 'Label' public, or remove targetPackage.");
    }
  }

  @Nested
  @DisplayName("a protected type inherited from another package")
  class Inherited {

    @Test
    @DisplayName("is refused with no move to offer, beside the record or under targetPackage")
    void protectedType() {
      Compilation compilation =
          compile(
              List.of("-Xlint:all,-processing"),
              source(
                  "com.example.base.Catalogue",
                  """
                  package com.example.base;

                  public class Catalogue {
                    protected record Code(String value) {}
                  }
                  """),
              source(
                  "com.example.Shop",
                  """
                  package com.example;

                  import com.example.base.Catalogue;
                  import org.higherkindedj.optics.annotations.GenerateLenses;

                  final class Shop extends Catalogue {
                    @GenerateLenses record Item(Code code) {}

                    @GenerateLenses(targetPackage = "com.example.optics")
                    record Label(Code code) {}
                  }
                  """));
      // Moving either companion does not reach a type only a subclass of Catalogue can see.
      assertThat(compilation)
          .hadErrorContaining(
              "@GenerateLenses: record component 'code' of 'Item' names 'Code', which cannot be"
                  + " reached from 'com.example'."
                  + WHY
                  + "Make 'Code' public.");
      assertThat(compilation)
          .hadErrorContaining(
              "@GenerateLenses: record component 'code' of 'Label' names 'Code', which cannot be"
                  + " reached from 'com.example.optics'.");
    }
  }

  @Nested
  @DisplayName("an effect algebra and what composes or wraps it")
  class Effects {

    private static final Compilation COMPILATION =
        compile(
            List.of("-Xlint:all,-processing"),
            holder(
                "Console",
                """
                private record Msg(String text) {}
                @EffectAlgebra sealed interface ConsoleOp<A> {
                  record ReadLine<A>() implements ConsoleOp<A> {}
                  record PrintLine<A>(Msg message) implements ConsoleOp<A> {}
                }
                """),
            holder(
                "Store",
                """
                @EffectAlgebra sealed interface LogOp<A> {
                  record Write<A>(String line) implements LogOp<A> {}
                }
                @EffectAlgebra private sealed interface DbOp<A> {
                  record Load<A>(String id) implements DbOp<A> {}
                }
                @ComposeEffects record AppEffects(Class<LogOp<?>> log, Class<DbOp<?>> db) {}
                """),
            holder(
                "Paths",
                """
                private record Problem(String reason) {}
                @PathSource(
                    witness = MaybeKind.Witness.class,
                    errorType = Problem.class,
                    capability = PathSource.Capability.RECOVERABLE)
                interface Lookup<A> {}
                """),
            holder(
                "Witnesses",
                """
                private static final class Box implements WitnessArity<TypeArity.Unary> {}
                @PathSource(witness = Box.class) interface Boxed<A> {}
                """));

    @Test
    @DisplayName("each is refused once")
    void refusedOnce() {
      // The algebra's own refusal, the composition declining it, and one for each other route.
      assertThat(COMPILATION).hadErrorCount(5);
    }

    @Test
    @DisplayName("an operation's component, or the algebra itself, once")
    void algebra() {
      assertThat(COMPILATION)
          .hadErrorContaining(
              "@EffectAlgebra: record component 'message' of 'PrintLine' names 'Msg', which cannot"
                  + " be reached from 'com.example'."
                  + WHY
                  + "Remove 'private' from 'Msg'.");
      // The operation nested in the private algebra is hidden by the same keyword: one refusal.
      assertThat(COMPILATION)
          .hadErrorContaining(
              "@EffectAlgebra: interface 'Store.DbOp' cannot be reached from 'com.example'.");
    }

    @Test
    @DisplayName("a composition declines an algebra @EffectAlgebra refuses, naming why")
    void composition() {
      assertThat(COMPILATION)
          .hadErrorContaining(
              "@ComposeEffects field 'db' names com.example.Store.DbOp, which @EffectAlgebra"
                  + " rejects because 'DbOp' cannot be reached from 'com.example', where its"
                  + " generated classes are written, so its Kind and Ops are not generated.");
    }

    @Test
    @DisplayName("a path source's witness, and its error type where the path recovers from it")
    void pathSource() {
      assertThat(COMPILATION)
          .hadErrorContaining(
              "@PathSource: error type 'Paths.Problem' cannot be reached from 'com.example'.");
      assertThat(COMPILATION)
          .hadErrorContaining(
              "@PathSource: witness 'Witnesses.Box' cannot be reached from 'com.example'.");
    }
  }

  @Nested
  @DisplayName("the older checks give the same fix")
  class OlderChecks {

    @Test
    @DisplayName("an iso and a path bridge name the class to change")
    void isoAndBridge() {
      Compilation compilation =
          compile(
              List.of("-Xlint:all,-processing"),
              holder(
                  "Isos",
                  """
                  private record Sku(String value) {}
                  static final class Codecs {
                    @GenerateIsos static Iso<Sku, String> sku() {
                      return Iso.of(Sku::value, Sku::new);
                    }
                  }
                  """),
              source(
                  "com.example.Vault",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  class Outer {
                    public static class Secret {}
                  }

                  @GeneratePathBridge(targetPackage = "com.example.paths")
                  public interface Vault {
                    @PathVia
                    Optional<Outer.Secret> find(String id);
                  }
                  """));
      assertThat(compilation)
          .hadErrorContaining(
              "@GenerateIsos: the iso returned by 'sku' names 'Sku', which cannot be reached from"
                  + " 'com.example'. The generated field writes its own type out in full, so every"
                  + " type named inside it has to be visible where the field is declared. Remove"
                  + " 'private' from 'Sku'.");
      assertThat(compilation)
          .hadErrorContaining(
              "@GeneratePathBridge: on 'Vault', the signature names 'Secret', which cannot be"
                  + " reached from 'com.example.paths'. The bridge writes it down as it stands, and"
                  + " it is not visible there. Make 'Outer', which encloses 'Secret', public, or"
                  + " remove targetPackage.");
    }
  }

  @Nested
  @DisplayName("a type the companion never names, or one it can reach, compiles")
  class Reached {

    @Test
    @DisplayName("what a companion passes over, and each fix followed")
    void compiles() {
      Compilation compilation =
          compile(
              List.of("-Xlint:all,-processing", "-Werror"),
              holder(
                  "PassedOver",
                  """
                  private record Sku(String value) {}
                  private interface Tag {}
                  record Unit() implements Tag {}
                  @SuppressWarnings("serial") // never serialised
                  static final class Grid extends ArrayList<Sku> {}
                  @GenerateTraversals record Rack(Sku first, List<String> tags) {}
                  @GenerateTraversals record Tagged<T extends Tag>(T value, String name) {}
                  @GenerateFolds record Cells(Grid cells, String name) {}
                  @GenerateLenses private record Empty() {}
                  @GeneratePrisms sealed interface Measure<T extends Tag> permits Metric, Imperial {}
                  record Metric() implements Measure<Unit> {}
                  record Imperial() implements Measure<Unit> {}
                  private record Problem(String reason) {}
                  @PathSource(witness = MaybeKind.Witness.class, errorType = Problem.class)
                  private interface Lookup<A> {}
                  """),
              holder(
                  "Followed",
                  """
                  record Sku(String value) {}
                  @GenerateLenses @GenerateFocus @GenerateGetters @GenerateSetters
                  record Stock(Sku sku, String name) {}
                  @GenerateFolds @GenerateTraversals record Box(List<Sku> skus) {}
                  @GeneratePrisms sealed interface Shape permits Circle, Square {}
                  record Circle(double radius) implements Shape {}
                  record Square(double side) implements Shape {}
                  record Item(Sku sku) {}
                  @ImportOptics(Item.class) interface ItemOptics {}
                  @GenerateAssembly record Order(Sku sku, String note) {}
                  record Ctx(String id) {}
                  @GenerateErrorEnvelope sealed interface OrderError {
                    record NotFound(Sku sku, ErrorEnvelope<Ctx> envelope) implements OrderError {}
                  }
                  record Msg(String text) {}
                  @EffectAlgebra sealed interface ConsoleOp<A> {
                    record PrintLine<A>(Msg message) implements ConsoleOp<A> {}
                  }
                  """),
              source(
                  "com.example.Retargeted",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateLenses;

                  public final class Retargeted {
                    @GenerateLenses(targetPackage = "com.example.optics")
                    public record Label(String text) {}
                  }
                  """));
      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a navigator into a record whose component its package cannot see is left out")
    void navigatorFallsBack() {
      Compilation compilation =
          compile(
              List.of("-Xlint:all,-processing", "-Werror"),
              source(
                  "com.example.stock.Item",
                  """
                  package com.example.stock;

                  import org.higherkindedj.optics.annotations.GenerateFocus;

                  @GenerateFocus(generateNavigators = true)
                  public record Item(Sku sku, String name) {}
                  """),
              source(
                  "com.example.stock.Sku",
                  """
                  package com.example.stock;

                  record Sku(String value) {}
                  """),
              source(
                  "com.example.Order",
                  """
                  package com.example;

                  import com.example.stock.Item;
                  import org.higherkindedj.optics.annotations.GenerateFocus;

                  @GenerateFocus(generateNavigators = true)
                  public record Order(Item item, String id) {}
                  """));
      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(compilation.diagnostics())
          .anySatisfy(
              diagnostic -> {
                Assertions.assertThat(diagnostic.getKind()).isEqualTo(Diagnostic.Kind.NOTE);
                Assertions.assertThat(diagnostic.getMessage(null))
                    .isEqualTo(
                        "Navigator for field 'item' is not generated: a navigator into Item names"
                            + " 'Sku', which cannot be reached from 'com.example'. Make 'Sku'"
                            + " public. Until then OrderFocus.item() keeps its plain path.");
              });
    }
  }
}
