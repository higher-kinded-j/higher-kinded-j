// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContainsRaw;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link PathProcessor}.
 *
 * <p>Tests verify that the processor correctly generates Path bridge classes for service interfaces
 * annotated with {@code @GeneratePathBridge} and {@code @PathVia}.
 */
@DisplayName("PathProcessor Integration Tests")
class PathProcessorIntegrationTest {

  /**
   * Compiles under the consuming build's own flags rather than javac's defaults.
   *
   * <p>Generated source is the claim here, and on defaults a bridge that converts unchecked or
   * names a raw type looks exactly like success - the warnings only become a failure in the build
   * that consumes the file, which is also the one place a suppression cannot be written.
   */
  private static Compilation compile(JavaFileObject... sources) {
    return javac()
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .withProcessors(new PathProcessor())
        .compile(sources);
  }

  @Nested
  @DisplayName("Basic Code Generation")
  class BasicCodeGeneration {

    @Test
    @DisplayName("generates bridge class for interface with Optional method")
    void shouldGenerateBridgeForOptionalMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.UserService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface UserService {

                  @PathVia
                  Optional<String> findById(Long id);
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.UserServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public final class UserServicePaths");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "private final UserService delegate");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public UserServicePaths(UserService delegate)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public OptionalPath<String> findById(Long id)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "Path.optional(this.delegate.findById(id))");
    }

    @Test
    @DisplayName("generates bridge class for interface with Either method")
    void shouldGenerateBridgeForEitherMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.OrderService",
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface OrderService {

                  @PathVia
                  Either<String, Integer> processOrder(String orderId);
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.OrderServicePaths";
      assertGeneratedCodeContains(
          compilation,
          generatedClassName,
          "public EitherPath<String, Integer> processOrder(String orderId)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "Path.either(this.delegate.processOrder(orderId))");
    }

    @Test
    @DisplayName("generates bridge class for interface with Maybe method")
    void shouldGenerateBridgeForMaybeMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.CacheService",
              """
              package com.example;

              import org.higherkindedj.hkt.maybe.Maybe;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface CacheService {

                  @PathVia
                  Maybe<String> get(String key);
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.CacheServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public MaybePath<String> get(String key)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "Path.maybe(this.delegate.get(key))");
    }

    @Test
    @DisplayName("generates bridge class for interface with Try method")
    void shouldGenerateBridgeForTryMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.FileService",
              """
              package com.example;

              import org.higherkindedj.hkt.trymonad.Try;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface FileService {

                  @PathVia
                  Try<String> readFile(String path);
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.FileServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public TryPath<String> readFile(String path)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "Path.tryPath(this.delegate.readFile(path))");
    }

    @Test
    @DisplayName("generates bridge class for interface with IO method")
    void shouldGenerateBridgeForIoMethod() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.ConfigService",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.io.IO;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface ConfigService {

                      @PathVia
                      IO<String> load(String key);
                  }
                  """));

      // Path.io takes a Supplier and IO is not one, so every bridge written against that factory
      // was source javac refused - in a file whose author had no way to correct it.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigServicePaths", "public IOPath<String> load(String key)");
      assertGeneratedCodeContains(
          compilation, "com.example.ConfigServicePaths", "Path.ioPath(this.delegate.load(key))");
    }

    @Test
    @DisplayName(
        "generates bridge class for interface with Validated method (adds Semigroup param)")
    void shouldGenerateBridgeForValidatedMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ValidationService",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;
              import java.util.List;

              @GeneratePathBridge
              public interface ValidationService {

                  @PathVia
                  Validated<List<String>, String> validate(String input);
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.ValidationServicePaths";
      // Validated methods should get an additional Semigroup parameter
      assertGeneratedCodeContains(
          compilation,
          generatedClassName,
          "ValidationPath<List<String>, String> validate(String input, Semigroup<List<String>> semigroup)");
      assertGeneratedCodeContains(
          compilation,
          generatedClassName,
          "Path.validated(this.delegate.validate(input), semigroup)");
    }
  }

  @Nested
  @DisplayName("Method type parameters")
  class MethodTypeParameters {

    @Test
    @DisplayName("declares both arms of an intersection bound")
    void declaresBothArmsOfAnIntersectionBound() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Sorter",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Sorter {
                      @PathVia
                      <R extends CharSequence & Comparable<R>> Optional<R> least(R first, R second);
                  }
                  """));

      // A bound is read as one upper bound or as the arms of an intersection; taking the first
      // arm for the whole would drop the half the delegate's own signature relies on.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.SorterPaths",
          "public <R extends CharSequence & Comparable<R>> OptionalPath<R> least(R first,"
              + " R second)");
    }
  }

  @Nested
  @DisplayName("Inherited @PathVia methods")
  class InheritedMethods {

    @Test
    @DisplayName("bridges a @PathVia the interface inherits rather than declares")
    void bridgesAnInheritedPathVia() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Store",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  public interface Store<T> {
                      @PathVia
                      Optional<String> byId(T id);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.StringStore",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

                  @GeneratePathBridge
                  public interface StringStore extends Store<String> {}
                  """));

      // Enclosed elements would have found nothing here, and said nothing about it either.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.StringStorePaths",
          "public OptionalPath<String> byId(String id)");
      assertGeneratedCodeContains(
          compilation, "com.example.StringStorePaths", "Path.optional(this.delegate.byId(id))");
    }

    @Test
    @DisplayName("reads an inherited method under the instantiation, bounds included")
    void readsAnInheritedMethodUnderTheInstantiation() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.other.Picker",
                  """
                  package com.other;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  public interface Picker<T> {
                      @PathVia
                      <R extends T> Optional<R> pick(R candidate, T from);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.TextPicker",
                  """
                  package com.example;

                  import com.other.Picker;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface TextPicker extends Picker<CharSequence> {
                      @PathVia
                      Optional<Integer> own(int n);
                  }
                  """));

      // 'R extends T' is 'R extends CharSequence' here. Copying the declaration would put
      // Picker's own parameter into a bridge that never declares it.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.TextPickerPaths",
          "public <R extends CharSequence> OptionalPath<R> pick(R candidate, CharSequence from)");
      // Own methods before inherited ones, so a supertype gaining a member does not reshuffle
      // the bridge's existing methods.
      assertGeneratedCodeInOrder(
          compilation, "com.example.TextPickerPaths", "own(int n)", "pick(R candidate");
    }

    @Test
    @DisplayName("bridges an overridden method once, as the override declares it")
    void bridgesAnOverriddenMethodOnce() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Reader",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  public interface Reader {
                      @PathVia
                      Optional<String> byId(String id);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.CachingReader",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface CachingReader extends Reader {
                      @Override
                      @PathVia
                      Optional<String> byId(String id);
                  }
                  """));

      // Java's own precedence: the override hides what it overrides, so the bridge declares one
      // byId rather than two the class cannot hold at once.
      assertThat(compilation).succeeded();
      assertGeneratedCodeOccursOnce(
          compilation, "com.example.CachingReaderPaths", "OptionalPath<String> byId(String id)");
    }

    @Test
    @DisplayName("reads an inherited return type under the instantiation")
    void readsAnInheritedReturnTypeUnderTheInstantiation() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Ledger",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  public interface Ledger<T, E> {
                      @PathVia
                      Optional<T> byId(String id);

                      @PathVia
                      Validated<E, T> check(String id);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.StringLedger",
                  """
                  package com.example;

                  import java.util.List;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

                  @GeneratePathBridge
                  public interface StringLedger extends Ledger<String, List<String>> {}
                  """));

      // The return type has to be read under the instantiation as much as the parameters do:
      // copying the declaration would name Ledger's T and E in a bridge that declares neither,
      // and for Validated it would name E twice - in the ValidationPath and in the Semigroup.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.StringLedgerPaths",
          "public OptionalPath<String> byId(String id)");
      assertGeneratedCodeContains(
          compilation,
          "com.example.StringLedgerPaths",
          "public ValidationPath<List<String>, String> check(String id,"
              + " Semigroup<List<String>> semigroup)");
    }

    @Test
    @DisplayName("bridges one method when two unrelated superinterfaces declare it")
    void bridgesOneMethodWhenTwoSuperinterfacesDeclareIt() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Readable",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  public interface Readable {
                      @PathVia
                      Optional<String> load(String key);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.Cacheable",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  public interface Cacheable {
                      @PathVia
                      Optional<String> load(String key);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.Both",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

                  @GeneratePathBridge
                  public interface Both extends Readable, Cacheable {}
                  """));

      // Neither declaration overrides the other, so Java's own precedence collapses nothing and
      // both members arrive. A bridge declaring load twice is a generated file that does not
      // compile, from a declaration javac itself accepts.
      assertThat(compilation).succeeded();
      assertGeneratedCodeOccursOnce(
          compilation, "com.example.BothPaths", "OptionalPath<String> load(String key)");
    }

    @Test
    @DisplayName("refuses a method type parameter that hides one the signature needs")
    void refusesAMethodTypeParameterThatHidesOneTheSignatureNeeds() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Narrower",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  public interface Narrower<U> {
                      @PathVia
                      <T extends U> Optional<T> narrow(T candidate);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.TextNarrower",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

                  @GeneratePathBridge
                  public interface TextNarrower<T> extends Narrower<T> {}
                  """));

      // The bridge declares the interface's parameters and the method's in one scope, which the
      // delegate never does. Emitted as written this is '<T extends T>', a cycle.
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the type parameter 'T' on 'narrow', inherited from 'Narrower', has the same name as"
                  + " 'TextNarrower's");
      assertThat(compilation).hadErrorContaining("Rename the method's type parameter.");
    }

    @Test
    @DisplayName("keeps a method type parameter that hides one the signature never names")
    void keepsAMethodTypeParameterThatHidesOneTheSignatureNeverNames() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Echo",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  public interface Echo<U> {
                      @PathVia
                      <T> Optional<T> echo(T value);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.TextEcho",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

                  @GeneratePathBridge
                  public interface TextEcho<T> extends Echo<T> {}
                  """));

      // Shadowing is only a problem where the signature depends on what is hidden. Here it names
      // the method's T throughout, and writes down exactly as the delegate declares it.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.TextEchoPaths", "public <T> OptionalPath<T> echo(T value)");
    }

    @Test
    @DisplayName("says so when nothing is annotated @PathVia")
    void saysSoWhenNothingIsAnnotated() {
      // Javac's defaults, deliberately: -Werror turns this warning into the build failure it
      // deserves to be, and the point of the case is the bridge that is written regardless.
      var compilation =
          javac()
              .withProcessors(new PathProcessor())
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.Untagged",
                      """
                      package com.example;

                      import java.util.Optional;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

                      @GeneratePathBridge
                      public interface Untagged {
                          Optional<String> byId(String id);
                      }
                      """));

      assertThat(compilation).succeeded();
      assertThat(compilation).hadWarningContaining("no @PathVia method was found");
      assertGeneratedCodeDoesNotContain(compilation, "com.example.UntaggedPaths", "byId");
    }
  }

  @Nested
  @DisplayName("What the bridge copies across")
  class CopiedAcross {

    @Test
    @DisplayName("carries the delegate's throws clause")
    void carriesTheDelegatesThrowsClause() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Loader",
                  """
                  package com.example;

                  import java.io.IOException;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Loader {
                      @PathVia
                      Optional<String> read(String key) throws IOException;
                  }
                  """));

      // The bridge only passes the call on. Dropping the clause left every caller with an
      // unreported checked exception, in a file they could not add it to.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.LoaderPaths",
          "public OptionalPath<String> read(String key) throws IOException");
    }

    @Test
    @DisplayName("calls through this.delegate, so a parameter cannot shadow the field")
    void callsThroughThisDelegate() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Shadowed",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Shadowed {
                      @PathVia
                      Optional<String> find(String delegate);
                  }
                  """));

      // The body is written as text, so a parameter of this name had turned the call into one
      // on itself - which compiles or not depending only on what the parameter's type declares.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.ShadowedPaths", "Path.optional(this.delegate.find(delegate))");
    }

    @Test
    @DisplayName("names the Semigroup around a parameter that has taken the name")
    void namesTheSemigroupAroundAParameterThatTookIt() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Namer",
                  """
                  package com.example;

                  import java.util.List;
                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Namer {
                      @PathVia
                      Validated<List<String>, Integer> parse(String semigroup);
                  }
                  """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.NamerPaths",
          "public ValidationPath<List<String>, Integer> parse(String semigroup,"
              + " Semigroup<List<String>> semigroup2)");
      assertGeneratedCodeContains(
          compilation,
          "com.example.NamerPaths",
          "Path.validated(this.delegate.parse(semigroup), semigroup2)");
    }

    @Test
    @DisplayName("pins the delegate's own inference with a type witness")
    void pinsTheDelegatesOwnInferenceWithATypeWitness() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Ranker",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Ranker {
                      @PathVia
                      <R extends Enum<R>> Optional<R> best();
                  }
                  """));

      // A self-referential bound leaves nothing for the Path factory's argument position to
      // infer R from, and javac reports "inferred type does not conform to equality constraints".
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.RankerPaths", "Path.optional(this.delegate.<R>best())");
    }

    @Test
    @DisplayName("links a declaring type the bridge's package cannot see through the interface")
    void linksAnUnreachableDeclarerThroughTheInterface() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.HiddenSource",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  interface HiddenSource {
                      @PathVia
                      Optional<String> fetch(String key);
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.PublicFacade",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

                  @GeneratePathBridge(targetPackage = "com.other")
                  public interface PublicFacade extends HiddenSource {}
                  """));

      // A {@link} to a type javapoet has to import is a real import. Naming the package-private
      // declarer would have put one in the file that javac then rejects, for a doc link.
      assertThat(compilation).succeeded();
      // Raw, not normalised: the claim is about the javadoc, which normalising strips.
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.other.PublicFacadePaths",
          "@return Path-wrapped result from {@link PublicFacade#fetch}");
      assertGeneratedCodeDoesNotContainRaw(
          compilation, "com.other.PublicFacadePaths", "import com.example.HiddenSource;");
    }
  }

  @Nested
  @DisplayName("Varargs")
  class Varargs {

    @Test
    @DisplayName("keeps a varargs delegate varargs")
    void keepsAVarargsDelegateVarargs() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Chooser",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Chooser {
                      @PathVia
                      Optional<String> first(String... candidates);
                  }
                  """));

      // The bridge mirrors the delegate, so call sites keep their shape rather than growing an
      // array literal the delegate never asked for.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.ChooserPaths",
          "public OptionalPath<String> first(String... candidates)");
    }

    @Test
    @DisplayName("takes the array when a Semigroup follows the varargs")
    void takesTheArrayWhenASemigroupFollows() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Checker",
                  """
                  package com.example;

                  import java.util.List;
                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Checker {
                      @PathVia
                      Validated<List<String>, String> check(String... parts);
                  }
                  """));

      // The Semigroup is appended after the caller's own arguments, which leaves the array no
      // longer last - the one position the language reserves for a varargs parameter.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.CheckerPaths",
          "public ValidationPath<List<String>, String> check(String[] parts,"
              + " Semigroup<List<String>> semigroup)");
    }

    @Test
    @DisplayName("takes the array when the element type is not reifiable")
    void takesTheArrayWhenTheElementTypeIsNotReifiable() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Bag",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Bag<T> {
                      @PathVia
                      @SuppressWarnings("unchecked")
                      Optional<T> first(T... candidates);
                  }
                  """));

      // A T[] parameter is warning-free; repeating the varargs would be a second "possible heap
      // pollution", in a file where the author cannot write the suppression they put on theirs.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.BagPaths", "public OptionalPath<T> first(T[] candidates)");
    }
  }

  @Nested
  @DisplayName("Annotation Options")
  class AnnotationOptions {

    @Test
    @DisplayName("uses custom method name from @PathVia")
    void shouldUseCustomMethodName() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.DataService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface DataService {

                  @PathVia(name = "fetchDataPath")
                  Optional<String> fetchData(String id);
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.DataServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public OptionalPath<String> fetchDataPath(String id)");
    }

    @Test
    @DisplayName("uses custom suffix from @GeneratePathBridge")
    void shouldUseCustomSuffix() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ApiService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge(suffix = "PathBridge")
              public interface ApiService {

                  @PathVia
                  Optional<String> getData();
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      // Should use custom suffix
      assertTrue(
          compilation.generatedSourceFile("com.example.ApiServicePathBridge").isPresent(),
          "Expected generated file ApiServicePathBridge");
    }

    @Test
    @DisplayName("includes documentation from @PathVia")
    void shouldIncludeDocumentation() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.DocService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface DocService {

                  @PathVia(doc = "Fetches user by ID with Path semantics")
                  Optional<String> fetchUser(Long id);
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.DocServicePaths";
      assertGeneratedCodeContainsRaw(
          compilation, generatedClassName, "Fetches user by ID with Path semantics");
    }
  }

  @Nested
  @DisplayName("Multiple Methods")
  class MultipleMethods {

    @Test
    @DisplayName("generates bridge methods for multiple @PathVia methods")
    void shouldGenerateMultipleBridgeMethods() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.CompleteService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.maybe.Maybe;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface CompleteService {

                  @PathVia
                  Optional<String> findOptional(String id);

                  @PathVia
                  Either<String, Integer> processEither(int value);

                  @PathVia
                  Maybe<Double> getMaybe();

                  // Method without @PathVia should NOT be included
                  void doSomething();
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.CompleteServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "OptionalPath<String> findOptional(String id)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "EitherPath<String, Integer> processEither(int value)");
      assertGeneratedCodeContains(compilation, generatedClassName, "MaybePath<Double> getMaybe()");
      // doSomething should NOT be in the generated code
      assertGeneratedCodeDoesNotContain(compilation, generatedClassName, "doSomething");
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("refuses a bound the target package cannot name")
    void refusesABoundTheTargetPackageCannotName() {
      var hidden =
          JavaFileObjects.forSourceString(
              "com.example.Hidden",
              """
              package com.example;

              class Hidden {}
              """);

      var onTheInterface =
          compile(
              hidden,
              JavaFileObjects.forSourceString(
                  "com.example.Repo",
                  """
                      package com.example;

                      import java.util.Optional;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                      import org.higherkindedj.hkt.effect.annotation.PathVia;

                      @GeneratePathBridge(targetPackage = "com.other")
                      public interface Repo<T extends Hidden> {
                          @PathVia
                          Optional<String> byId(T id);
                      }
                      """));

      assertThat(onTheInterface).failed();
      assertThat(onTheInterface)
          .hadErrorContaining("the bound on 'T' names 'Hidden', which cannot be reached");

      var onTheMethod =
          compile(
              hidden,
              JavaFileObjects.forSourceString(
                  "com.example.MethodRepo",
                  """
                      package com.example;

                      import java.util.Optional;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                      import org.higherkindedj.hkt.effect.annotation.PathVia;

                      @GeneratePathBridge(targetPackage = "com.other")
                      public interface MethodRepo {
                          @PathVia
                          <T extends Hidden> Optional<String> byId(T id);
                      }
                      """));

      assertThat(onTheMethod).failed();
      assertThat(onTheMethod)
          .hadErrorContaining("the bound on 'T' names 'Hidden', which cannot be reached");

      // Beside the interface, the same bound is nameable and nothing is refused.
      var beside =
          compile(
              hidden,
              JavaFileObjects.forSourceString(
                  "com.example.NearRepo",
                  """
                      package com.example;

                      import java.util.Optional;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                      import org.higherkindedj.hkt.effect.annotation.PathVia;

                      @GeneratePathBridge
                      public interface NearRepo<T extends Hidden> {
                          @PathVia
                          Optional<String> byId(T id);
                      }
                      """));

      assertThat(beside).succeeded();
      assertGeneratedCodeContains(
          beside,
          "com.example.NearRepoPaths",
          "public final class NearRepoPaths<T extends Hidden>");
    }

    @Test
    @DisplayName("writes the description before the block tags it belongs above")
    void writesTheDescriptionBeforeTheBlockTags() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.DocSvc",
                  """
                      package com.example;

                      import java.util.Optional;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                      import org.higherkindedj.hkt.effect.annotation.PathVia;

                      @GeneratePathBridge
                      public interface DocSvc {
                          @PathVia(doc = "Finds a thing by its type.")
                          <T> Optional<T> find(Class<T> type);
                      }
                      """));

      // A tag written before the description swallows it: javadoc reads everything after a block
      // tag as that tag's own text, so the sentence became part of the @param.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.DocSvcPaths",
          "   * Finds a thing by its type.\n"
              + "   *\n"
              + "   * @param <T> as declared by the delegate method\n"
              + "   * @return Path-wrapped result from {@link DocSvc#find}");
    }

    @Test
    @DisplayName("refuses a @PathVia method the delegate cannot be asked for")
    void refusesStaticAndPrivatePathViaMethods() {
      var statics =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.StaticSvc",
                  """
                      package com.example;

                      import java.util.Optional;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                      import org.higherkindedj.hkt.effect.annotation.PathVia;

                      @GeneratePathBridge
                      public interface StaticSvc {
                          @PathVia
                          static Optional<String> lookup(String k) { return Optional.of(k); }
                      }
                      """));

      assertThat(statics).failed();
      assertThat(statics).hadErrorContaining("a static interface method cannot be called that way");

      var privates =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.PrivateSvc",
                  """
                      package com.example;

                      import java.util.Optional;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                      import org.higherkindedj.hkt.effect.annotation.PathVia;

                      @GeneratePathBridge
                      public interface PrivateSvc {
                          @PathVia
                          private Optional<String> hidden(String k) { return Optional.of(k); }
                      }
                      """));

      assertThat(privates).failed();
      assertThat(privates)
          .hadErrorContaining("a private interface method cannot be called that way");
    }

    @Test
    @DisplayName("refuses a raw return type rather than converting unchecked")
    void refusesARawReturnType() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.RawRepo",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  @SuppressWarnings("rawtypes")
                  public interface RawRepo {
                      @PathVia
                      Optional byId(Long id);
                  }
                  """));

      // Substituting Object had made the delegate call an unchecked conversion three times over,
      // in a file whose only place for a suppression is generated too.
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("the signature of 'byId' names the raw type 'Optional'");
      assertThat(compilation).hadErrorContaining("Name 'Optional's type arguments");
    }

    @Test
    @DisplayName("refuses a wildcard error type on Validated, which the bridge names twice")
    void refusesAWildcardErrorTypeOnValidated() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.WildValidator",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface WildValidator {
                      @PathVia
                      Validated<? extends CharSequence, String> check(String in);
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the error type of the 'Validated' returned by 'check' is the wildcard '? extends"
                  + " CharSequence'");
      assertThat(compilation).hadErrorContaining("Name the error type.");
    }

    @Test
    @DisplayName("accepts the wildcards that do not have to be named twice")
    void acceptsTheWildcardsThatNeedNamingOnce() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.WildRepo",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.either.Either;
                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface WildRepo {
                      @PathVia
                      Optional<? extends CharSequence> name();

                      @PathVia
                      Either<? extends CharSequence, ? extends Number> reading();

                      @PathVia
                      Validated<String, ? extends Number> value();

                      @PathVia
                      Validated<List<? extends CharSequence>, String> nested();
                  }
                  """));

      // Only a wildcard the Semigroup has to name a second time is refused. Everywhere else the
      // capture happens once and javac unifies it, so the bridge mirrors what was written.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.WildRepoPaths",
          "public OptionalPath<? extends CharSequence> name()");
      assertGeneratedCodeContains(
          compilation,
          "com.example.WildRepoPaths",
          "public ValidationPath<String, ? extends Number> value(Semigroup<String> semigroup)");
      assertGeneratedCodeContains(
          compilation,
          "com.example.WildRepoPaths",
          "public EitherPath<? extends CharSequence, ? extends Number> reading()");
      // The near miss the error-type guard must not over-refuse: the wildcard is nested inside
      // the error type, so it is one type at both mentions and javac unifies it.
      assertGeneratedCodeContains(
          compilation,
          "com.example.WildRepoPaths",
          "public ValidationPath<List<? extends CharSequence>, String> nested("
              + "Semigroup<List<? extends CharSequence>> semigroup)");
    }

    @Test
    @DisplayName("refuses a raw type anywhere the bridge writes it down")
    void refusesARawTypeAnywhereTheBridgeWritesItDown() {
      var argument =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.RawArg",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  @SuppressWarnings("rawtypes")
                  public interface RawArg {
                      @PathVia
                      Optional<List> all();
                  }
                  """));

      // The author's own suppression covers their own file, not the generated one. The head of
      // the return type was never the only place a raw type could be written.
      assertThat(argument).failed();
      assertThat(argument).hadErrorContaining("the signature of 'all' names the raw type 'List'");

      var parameter =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.RawParam",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  @SuppressWarnings("rawtypes")
                  public interface RawParam {
                      @PathVia
                      Optional<String> find(List keys);
                  }
                  """));

      assertThat(parameter).failed();
      assertThat(parameter).hadErrorContaining("the signature of 'find' names the raw type 'List'");

      // A wildcard's bound is written out with the wildcard that carries it, so it is as much a
      // position as the argument beside it.
      var wildcardBound =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.RawWild",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  @SuppressWarnings("rawtypes")
                  public interface RawWild {
                      @PathVia
                      Optional<? extends List> all();
                  }
                  """));

      assertThat(wildcardBound).failed();
      assertThat(wildcardBound)
          .hadErrorContaining("the signature of 'all' names the raw type 'List'");

      // And the interface's own bounds, which the bridge repeats in its own declaration.
      var interfaceBound =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.RawBound",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  @SuppressWarnings("rawtypes")
                  public interface RawBound<T extends List> {
                      @PathVia
                      Optional<String> byId(T id);
                  }
                  """));

      assertThat(interfaceBound).failed();
      assertThat(interfaceBound).hadErrorContaining("the bound on 'T' names the raw type 'List'");
    }

    @Test
    @DisplayName("refuses a signature the target package cannot name")
    void refusesASignatureTheTargetPackageCannotName() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Secret",
                  """
                  package com.example;

                  class Secret {}
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.SecretRepo",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge(targetPackage = "com.other")
                  public interface SecretRepo {
                      @PathVia
                      Optional<String> find(Secret token);
                  }
                  """));

      // A bound was never the only thing the bridge writes down: the parameter and return types
      // land in the same declaration and have to be nameable from the same place.
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the signature names 'Secret', which cannot be reached from" + " 'com.other'");
    }

    @Test
    @DisplayName("refuses two @PathVia methods that bridge to one signature")
    void refusesTwoMethodsThatBridgeToOneSignature() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Renamer",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Renamer {
                      @PathVia
                      Optional<String> lookup(String key);

                      @PathVia(name = "lookup")
                      Optional<String> search(String key);
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("the bridge signature for 'search' is already taken");
    }

    @Test
    @DisplayName("refuses a @PathVia name that is not an identifier")
    void refusesAPathViaNameThatIsNotAnIdentifier() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.OddName",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface OddName {
                      @PathVia(name = "find by id")
                      Optional<String> byId(String key);
                  }
                  """));

      // Left ungated this reached javapoet, which threw, and the author was shown an annotation
      // processor stack trace rather than the attribute they mistyped.
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@PathVia(name = \"find by id\") is not a method name");
    }

    @Test
    @DisplayName("refuses a @PathVia name that is a keyword")
    void refusesAPathViaNameThatIsAKeyword() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Keyworded",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Keyworded {
                      @PathVia(name = "class")
                      Optional<String> type(String key);
                  }
                  """));

      // An identifier by shape, but not one javac will accept as a method name.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@PathVia(name = \"class\") is not a method name");
    }

    @Test
    @DisplayName("refuses a delegate the target package cannot name")
    void refusesADelegateTheTargetPackageCannotName() {
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Internal",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge(targetPackage = "com.other")
                  interface Internal {
                      @PathVia
                      Optional<String> find(String key);
                  }
                  """));

      // The delegate field and the constructor both name the interface itself, so it is as much
      // a type the bridge has to be able to see as anything in a signature.
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the delegate names 'Internal', which cannot be reached from 'com.other'");
    }

    @Test
    @DisplayName("fails when @GeneratePathBridge is applied to a class")
    void shouldFailForClass() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NotAnInterface",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

              @GeneratePathBridge
              public class NotAnInterface {}
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'NotAnInterface' is not an interface");
    }

    @Test
    @DisplayName("fails when @PathVia method has unsupported return type")
    void shouldFailForUnsupportedReturnType() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadService",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface BadService {

                  @PathVia
                  String notAnEffectType();
              }
              """);

      var compilation = compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the return type of 'notAnEffectType' is 'String', which no Path wraps");
    }
  }

  // Helper methods

  private static void assertGeneratedCodeContains(
      Compilation compilation, String generatedFileName, String expectedCode) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();
      String normalisedActual = normaliseCode(actualGeneratedCode);
      String normalisedExpected = normaliseCode(expectedCode);

      assertTrue(
          normalisedActual.contains(normalisedExpected),
          String.format(
              "Expected generated code to contain:%n---%n%s%n---%nBut was:%n---%n%s%n---",
              normalisedExpected, normalisedActual));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  private static void assertGeneratedCodeContainsRaw(
      Compilation compilation, String generatedFileName, String expectedText) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();

      assertTrue(
          actualGeneratedCode.contains(expectedText),
          String.format(
              "Expected generated code to contain (raw):%n---%n%s%n---%nBut was:%n---%n%s%n---",
              expectedText, actualGeneratedCode));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  /** Asserts the fragments appear in the generated file in the order given. */
  private static void assertGeneratedCodeInOrder(
      Compilation compilation, String generatedFileName, String... expectedInOrder) {
    String generated = normaliseCode(readGenerated(compilation, generatedFileName));
    int cursor = 0;
    for (String fragment : expectedInOrder) {
      String normalised = normaliseCode(fragment);
      int found = generated.indexOf(normalised, cursor);
      assertTrue(
          found >= 0,
          String.format(
              "Expected generated code to contain '%s' after index %d:%n---%n%s%n---",
              fragment, cursor, generated));
      // Past the match, not at it: overlapping fragments would otherwise satisfy an ordering
      // that the generated file does not actually have.
      cursor = found + normalised.length();
    }
  }

  /** Asserts the fragment appears exactly once in the generated file. */
  private static void assertGeneratedCodeOccursOnce(
      Compilation compilation, String generatedFileName, String expectedCode) {
    String generated = normaliseCode(readGenerated(compilation, generatedFileName));
    String expected = normaliseCode(expectedCode);
    int occurrences = 0;
    for (int at = generated.indexOf(expected); at >= 0; at = generated.indexOf(expected, at + 1)) {
      occurrences++;
    }
    assertTrue(
        occurrences == 1,
        String.format(
            "Expected exactly one '%s' in the generated code, found %d:%n---%n%s%n---",
            expected, occurrences, generated));
  }

  private static String readGenerated(Compilation compilation, String generatedFileName) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
    }

    try {
      return generatedSourceFile.orElseThrow().getCharContent(true).toString();
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
      throw new AssertionError(e);
    }
  }

  private static void assertGeneratedCodeDoesNotContainRaw(
      Compilation compilation, String generatedFileName, String unexpectedText) {
    assertTrue(
        !readGenerated(compilation, generatedFileName).contains(unexpectedText),
        "Expected generated code NOT to contain (raw): " + unexpectedText);
  }

  private static void assertGeneratedCodeDoesNotContain(
      Compilation compilation, String generatedFileName, String unexpectedCode) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();
      String normalisedActual = normaliseCode(actualGeneratedCode);
      String normalisedUnexpected = normaliseCode(unexpectedCode);

      assertTrue(
          !normalisedActual.contains(normalisedUnexpected),
          String.format(
              "Expected generated code NOT to contain:%n---%n%s%n---%nBut it did:%n---%n%s%n---",
              normalisedUnexpected, normalisedActual));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  private static String normaliseCode(String code) {
    String normalised =
        code.replaceAll("package [\\w.]+;\\s*", "")
            .replaceAll("import [\\w.]+;\\s*", "")
            .replaceAll("/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/", "")
            .replaceAll("//.*", "");

    // Normalise fully qualified class names to simple names
    normalised =
        normalised
            .replaceAll("java\\.util\\.Optional", "Optional")
            .replaceAll("java\\.util\\.List", "List")
            .replaceAll("java\\.util\\.Objects", "Objects")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.Path", "Path")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.MaybePath", "MaybePath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.EitherPath", "EitherPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.TryPath", "TryPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.ValidationPath", "ValidationPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.OptionalPath", "OptionalPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.IOPath", "IOPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.Semigroup", "Semigroup")
            .replaceAll("org\\.higherkindedj\\.hkt\\.either\\.Either", "Either")
            .replaceAll("org\\.higherkindedj\\.hkt\\.maybe\\.Maybe", "Maybe")
            .replaceAll("org\\.higherkindedj\\.hkt\\.trymonad\\.Try", "Try")
            .replaceAll("org\\.higherkindedj\\.hkt\\.validated\\.Validated", "Validated")
            // Test-specific types
            .replaceAll("com\\.example\\.UserService", "UserService")
            .replaceAll("com\\.example\\.OrderService", "OrderService")
            .replaceAll("com\\.example\\.CacheService", "CacheService")
            .replaceAll("com\\.example\\.FileService", "FileService")
            .replaceAll("com\\.example\\.ValidationService", "ValidationService")
            .replaceAll("com\\.example\\.DataService", "DataService")
            .replaceAll("com\\.example\\.ApiService", "ApiService")
            .replaceAll("com\\.example\\.DocService", "DocService")
            .replaceAll("com\\.example\\.CompleteService", "CompleteService");

    return normalised.replaceAll("\\s+", "").trim();
  }
}
