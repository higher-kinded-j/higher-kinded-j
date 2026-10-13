// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a spec's copy strategy is held to: which method or constructor a generated call binds,
 * whether every method name the strategy carries is one the generated class can call, the order a
 * {@code @ViaConstructor} passes its arguments in, and when the focus is passed unboxed.
 *
 * <p>Kept apart from {@link SpecInterfaceProcessingTest}, which covers the rest of a spec's
 * reading, because these are the checks {@code CopyStrategyChecks} makes.
 */
@DisplayName("Spec Copy Strategy Checks")
class SpecCopyStrategyChecksTest {

  /** A class in {@code com.external}, the package every spec here imports from. */
  private static JavaFileObject external(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        "com.external." + simpleName, "package com.external;\n\n" + body);
  }

  /** A spec interface in {@code com.myapp}, with the external classes and annotations in scope. */
  private static JavaFileObject spec(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        "com.myapp." + simpleName,
        """
        package com.myapp;

        import com.external.*;
        import org.higherkindedj.optics.Lens;
        import org.higherkindedj.optics.annotations.*;

        """
            + body);
  }

  /** The lint a call bound to the wrong method would trip. */
  private static final String LINT_CHECKS = "-Xlint:unchecked,rawtypes,static";

  private static final List<String> LINT = List.of(LINT_CHECKS, "-Werror");

  /** Compiles under the lint a call bound to the wrong method would trip, as an error. */
  private static Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new ImportOpticsProcessor()).withOptions(LINT).compile(sources);
  }

  /** Compiles under the same lint with warnings left as warnings, so that one can be read. */
  private static Compilation compileReporting(JavaFileObject... sources) {
    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions(List.of(LINT_CHECKS))
        .compile(sources);
  }

  /**
   * Compiles {@code spec} against {@code source} read from a class file that names {@code
   * com.external.Gone}, which is missing from the classpath: a type the processor does not wait
   * for, since it would never come.
   */
  private static Compilation compileAgainstAClassFileNamingAMissingType(
      Path dir, JavaFileObject source, JavaFileObject spec) throws IOException {
    Compilation upstream = javac().compile(source, external("Gone", "public final class Gone {}"));
    assertThat(upstream).succeeded();
    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions(LINT)
        .withClasspath(
            GeneratorTestHelper.classpathWith(
                GeneratorTestHelper.classDirectoryWithout(upstream, dir, "com.external.Gone")))
        .compile(spec);
  }

  /** A two-int point whose constructor names its parameters after its accessors. */
  private static final String POINT =
      """
      public final class Point {
          private final int x;
          private final int y;
          public Point(int x, int y) { this.x = x; this.y = y; }
          public int x() { return x; }
          public int y() { return y; }
      }
      """;

  /** A label, a note and an array of parts, each named after the accessor that reads it. */
  private static final String TAG =
      """
      public final class Tag {
          private final String label;
          private final String note;
          private final String[] parts;
          public Tag(String label, String note, String... parts) {
              this.label = label;
              this.note = note;
              this.parts = parts.clone();
          }
          public String label() { return label; }
          public String note() { return note; }
          public String[] parts() { return parts.clone(); }
      }
      """;

  /**
   * Compiles {@code specs} against {@code source} read from a class file javac wrote with {@code
   * options}: with none it keeps no parameter names, with {@code -g} it keeps them.
   */
  private static Compilation compileAgainstClassFile(
      Path dir, List<String> options, JavaFileObject source, JavaFileObject... specs)
      throws IOException {
    Compilation upstream = javac().withOptions(options).compile(source);
    assertThat(upstream).succeeded();
    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions(LINT)
        .withClasspath(
            GeneratorTestHelper.classpathWith(
                GeneratorTestHelper.classDirectoryWithout(upstream, dir)))
        .compile(specs);
  }

  @Nested
  @DisplayName("@Wither Call Binding")
  class WitherCallBinding {

    @Test
    @DisplayName("each lens is checked against the overload its focus binds")
    void eachLensIsCheckedAgainstTheOverloadItsFocusBinds() {
      // In each class one overload returns the source type, and it is not the one the call binds:
      // a String binds withId(String), the most specific overload that takes one.
      var compilation =
          compile(
              external(
                  "RawDraft",
                  """
                  @SuppressWarnings("rawtypes")
                  public final class RawDraft<T> {
                      private final String id;
                      public RawDraft(String id) { this.id = id; }
                      public String id() { return id; }
                      public RawDraft withId(String id) { return new RawDraft<>(id); }
                      public RawDraft<T> withId(Object id) {
                          return new RawDraft<>(String.valueOf(id));
                      }
                  }
                  """),
              external(
                  "Tagged",
                  """
                  public final class Tagged<T> {
                      private final String id;
                      public Tagged(String id) { this.id = id; }
                      public String id() { return id; }
                      public Tagged<String> withId(String id) { return new Tagged<>(id); }
                      public Tagged<T> withId(Integer id) {
                          return new Tagged<>(String.valueOf(id));
                      }
                  }
                  """),
              spec(
                  "RawDraftOpticsSpec",
                  """
                  @ImportOptics
                  public interface RawDraftOpticsSpec<T> extends OpticsSpec<RawDraft<T>> {
                      @Wither(value = "withId", getter = "id")
                      Lens<RawDraft<T>, String> id();
                  }
                  """),
              spec(
                  "TaggedOpticsSpec",
                  """
                  @ImportOptics
                  public interface TaggedOpticsSpec<T> extends OpticsSpec<Tagged<T>> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Tagged<T>, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'RawDraft', not the source type 'RawDraft<T>'");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'Tagged<String>', not the source type"
                  + " 'Tagged<T>'");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName(
        "a focus the generated lens unboxes binds the primitive overload, and it is checked")
    void focusTheGeneratedLensUnboxesBindsThePrimitiveOverload() {
      // The lens passes an int, not an Integer, so withN(Integer) never takes the call, and its
      // return is not the one the check reads.
      var compilation =
          compile(
              external(
                  "Boxed",
                  """
                  public final class Boxed {
                      private final int n;
                      public Boxed(int n) { this.n = n; }
                      public int n() { return n; }
                      public Boxed withN(int n) { return new Boxed(n); }
                      public Object withN(Integer n) { return this; }
                  }
                  """),
              spec(
                  "BoxedOpticsSpec",
                  """
                  @ImportOptics
                  public interface BoxedOpticsSpec extends OpticsSpec<Boxed> {
                      @Wither(value = "withN", getter = "n")
                      Lens<Boxed, Integer> n();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.BoxedOptics", "source.withN((int) newValue)");
    }

    @Test
    @DisplayName("the most specific overload is the one checked")
    void theMostSpecificOverloadIsTheOneChecked() {
      // Counter's Integer reaches no reference overload, so it binds the most specific primitive
      // one, withN(int) over withN(long). Named's String binds withId(String) over the
      // CharSequence and Object overloads. Shape inherits withId(String) from Sized and from
      // Tinted, and the call takes the one with the most specific return.
      var compilation =
          compile(
              external(
                  "Counter",
                  """
                  public final class Counter {
                      private final int n;
                      public Counter(int n) { this.n = n; }
                      public int n() { return n; }
                      public Object withN(long n) { return this; }
                      public Counter withN(int n) { return new Counter(n); }
                  }
                  """),
              external(
                  "Named",
                  """
                  public final class Named {
                      private final String id;
                      public Named(String id) { this.id = id; }
                      public String id() { return id; }
                      public Object withId(Object id) { return this; }
                      public Object withId(CharSequence id) { return this; }
                      public Named withId(String id) { return new Named(id); }
                  }
                  """),
              external("Sized", "public interface Sized { Shape withId(String id); }\n"),
              external("Tinted", "public interface Tinted { Object withId(String id); }\n"),
              external("Shape", "public interface Shape extends Sized, Tinted { String id(); }\n"),
              spec(
                  "CounterOpticsSpec",
                  """
                  @ImportOptics
                  public interface CounterOpticsSpec extends OpticsSpec<Counter> {
                      @Wither(value = "withN", getter = "n")
                      Lens<Counter, Integer> n();
                  }
                  """),
              spec(
                  "NamedOpticsSpec",
                  """
                  @ImportOptics
                  public interface NamedOpticsSpec extends OpticsSpec<Named> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Named, String> id();
                  }
                  """),
              spec(
                  "ShapeOpticsSpec",
                  """
                  @ImportOptics
                  public interface ShapeOpticsSpec extends OpticsSpec<Shape> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Shape, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      // A primitive read under an overloaded name is passed unboxed, so the call binds withN(int)
      // rather than the wider withN(long).
      assertGeneratedCodeContains(
          compilation, "com.myapp.CounterOptics", "source.withN((int) newValue)");
      assertGeneratedCodeContains(compilation, "com.myapp.ShapeOptics", "source.withId(newValue)");
    }

    @Test
    @DisplayName("a call that rests on inference is left to javac")
    void callThatRestsOnInferenceIsLeftToJavac() {
      // Label's parameter is one of its own type variables, and Tags' String reaches withFirst
      // only as the element of its array. The one method each call might bind returns the source
      // type, so the check passes it, and javac settles the call.
      var compilation =
          compile(
              external(
                  "Label",
                  """
                  public final class Label {
                      private final String text;
                      public Label(String text) { this.text = text; }
                      public String text() { return text; }
                      public <V extends CharSequence> Label withText(V text) {
                          return new Label(text.toString());
                      }
                  }
                  """),
              external(
                  "Tags",
                  """
                  public final class Tags {
                      private final String first;
                      public Tags(String first) { this.first = first; }
                      public String first() { return first; }
                      public Tags withFirst(String... tags) { return new Tags(tags[0]); }
                  }
                  """),
              spec(
                  "LabelOpticsSpec",
                  """
                  @ImportOptics
                  public interface LabelOpticsSpec extends OpticsSpec<Label> {
                      @Wither(value = "withText", getter = "text")
                      Lens<Label, String> text();
                  }
                  """),
              spec(
                  "TagsOpticsSpec",
                  """
                  @ImportOptics
                  public interface TagsOpticsSpec extends OpticsSpec<Tags> {
                      @Wither(value = "withFirst", getter = "first")
                      Lens<Tags, String> first();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a call that rests on inference is refused when nothing it might bind fits")
    void callThatRestsOnInferenceIsRefusedWhenNothingItMightBindFits() {
      var compilation =
          compile(
              external(
                  "Note",
                  """
                  public final class Note {
                      private final String text;
                      public Note(String text) { this.text = text; }
                      public String text() { return text; }
                      public <V extends CharSequence> Object withText(V text) { return this; }
                  }
                  """),
              spec(
                  "NoteOpticsSpec",
                  """
                  @ImportOptics
                  public interface NoteOpticsSpec extends OpticsSpec<Note> {
                      @Wither(value = "withText", getter = "text")
                      Lens<Note, String> text();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withText(V)' returns 'Object', not the source type 'Note'. The generated"
                  + " lens sets through 'withText(V)' and hands its result back as the source"
                  + " type 'Note', which 'Object' is not.");
    }

    @Test
    @DisplayName("a wither the generated class shares a package with is called")
    void witherTheGeneratedClassSharesAPackageWithIsCalled() {
      // Declared without a modifier, and callable here because the optics are generated into the
      // package that declares it.
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.myapp.Parcel",
                  """
                  package com.myapp;

                  public final class Parcel {
                      private final String id;
                      public Parcel(String id) { this.id = id; }
                      public String id() { return id; }
                      Parcel withId(String id) { return new Parcel(id); }
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.myapp.ParcelOpticsSpec",
                  """
                  package com.myapp;

                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.annotations.ImportOptics;
                  import org.higherkindedj.optics.annotations.OpticsSpec;
                  import org.higherkindedj.optics.annotations.Wither;

                  @ImportOptics
                  public interface ParcelOpticsSpec extends OpticsSpec<Parcel> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Parcel, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a wildcard focus reaching no one-parameter method is left to javac")
    void wildcardFocusReachingNoOneParameterMethodIsLeftToJavac() {
      var compilation =
          compile(
              external(
                  "Pair2",
                  """
                  public final class Pair2 {
                      private final String id;
                      public Pair2(String id) { this.id = id; }
                      public String id() { return id; }
                      public Pair2 withId(String id, int copies) { return new Pair2(id); }
                  }
                  """),
              spec(
                  "Pair2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Pair2OpticsSpec extends OpticsSpec<Pair2> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Pair2, ?> id();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@Wither:"));
    }

    @Test
    @DisplayName("a static wither under a wildcard focus is refused, naming what it sets through")
    void staticWitherUnderAWildcardFocusIsRefused() {
      // Which method such a call binds is javac's to settle, so the refusal names the method
      // rather than the value that chose it.
      var compilation =
          compile(
              external(
                  "Seal",
                  """
                  public final class Seal {
                      private final String id;
                      public Seal(String id) { this.id = id; }
                      public String id() { return id; }
                      public static Seal withId(String id) { return new Seal(id); }
                  }
                  """),
              spec(
                  "SealOpticsSpec",
                  """
                  @ImportOptics
                  public interface SealOpticsSpec extends OpticsSpec<Seal> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Seal, ? extends CharSequence> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' is static, so the generated lens cannot rebuild a 'Seal'"
                  + " through it. The generated lens sets through 'withId(String)', and a static"
                  + " method never reads the 'Seal' it is called on.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a wildcard focus is left to javac, which infers it from the getter")
    void wildcardFocusIsLeftToJavac() {
      // A lens whose focus is a wildcard is inferred from its getter as much as from the
      // wildcard: Memo's is Lens<Memo, String>, not the CharSequence its bound names, so
      // withA(String) is the method the call binds. Reading the bound as the argument would
      // refuse every one of these, and each compiles.
      var compilation =
          compile(
              external(
                  "Memo",
                  """
                  public final class Memo {
                      private final String a;
                      private final String b;
                      private final String c;
                      public Memo(String a, String b, String c) {
                          this.a = a;
                          this.b = b;
                          this.c = c;
                      }
                      public String a() { return a; }
                      public String b() { return b; }
                      public String c() { return c; }
                      public Memo withA(String a) { return new Memo(a, b, c); }
                      public Object withA(CharSequence a) { return this; }
                      public Memo withB(String b) { return new Memo(a, b, c); }
                      public Memo withC(String c) { return new Memo(a, b, c); }
                      public Object withC(Object c) { return this; }
                  }
                  """),
              spec(
                  "MemoOpticsSpec",
                  """
                  @ImportOptics
                  public interface MemoOpticsSpec extends OpticsSpec<Memo> {
                      @Wither(value = "withA", getter = "a")
                      Lens<Memo, ? extends CharSequence> a();

                      @Wither(value = "withB", getter = "b")
                      Lens<Memo, ? super String> b();

                      @Wither(value = "withC", getter = "c")
                      Lens<Memo, ?> c();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a wither the source type inherits from a package-private class is called")
    void witherInheritedFromAPackagePrivateClassIsCalled() {
      // The call names no type but Ledger's own, so a public method it inherits is one it can
      // call, wherever that method was declared.
      var compilation =
          compile(
              external(
                  "Ledger",
                  """
                  public final class Ledger extends LedgerBase<String> {
                      public Ledger(String id) { super(id); }
                  }

                  class LedgerBase<T> {
                      private final String id;
                      LedgerBase(String id) { this.id = id; }
                      public String id() { return id; }
                      public Ledger withId(String id) { return new Ledger(id); }
                  }
                  """),
              spec(
                  "LedgerOpticsSpec",
                  """
                  @ImportOptics
                  public interface LedgerOpticsSpec extends OpticsSpec<Ledger> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Ledger, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("an overload that loses the choice does not answer for the call")
    void overloadThatLosesTheChoiceDoesNotAnswerForTheCall() {
      // withId(String) is more specific than the generic overload, so the call binds it and its
      // return is the one checked, though the generic one hands the source type back.
      var compilation =
          compile(
              external(
                  "Draft2",
                  """
                  public final class Draft2 {
                      private final String id;
                      public Draft2(String id) { this.id = id; }
                      public String id() { return id; }
                      public <V extends CharSequence> Draft2 withId(V id) {
                          return new Draft2(id.toString());
                      }
                      public Object withId(String id) { return this; }
                  }
                  """),
              spec(
                  "Draft2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Draft2OpticsSpec extends OpticsSpec<Draft2> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Draft2, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'Object', not the source type 'Draft2'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a static method the call might bind is refused too")
    void staticMethodTheCallMightBindIsRefusedToo() {
      // A variable-arity call is left to javac, but whatever it settles on has to read the value
      // it is called on, and a static method never does.
      var compilation =
          compile(
              external(
                  "Batch",
                  """
                  public final class Batch {
                      private final String id;
                      public Batch(String id) { this.id = id; }
                      public String id() { return id; }
                      public static Batch withId(String... ids) { return new Batch(ids[0]); }
                  }
                  """),
              spec(
                  "BatchOpticsSpec",
                  """
                  @ImportOptics
                  public interface BatchOpticsSpec extends OpticsSpec<Batch> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Batch, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String...)' is static, so the generated lens cannot rebuild a"
                  + " 'Batch' through it. The generated lens sets through"
                  + " 'source.withId(newValue)'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a source type that does not resolve is left to javac")
    void sourceTypeThatDoesNotResolveIsLeftToJavac() {
      var compilation =
          compile(
              spec(
                  "GhostOpticsSpec",
                  """
                  @ImportOptics
                  public interface GhostOpticsSpec extends OpticsSpec<com.external.Ghost> {
                      @Wither(value = "withId", getter = "id")
                      Lens<com.external.Ghost, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      // The spec waits for the type, and javac reports the missing package at the spec itself.
      assertThat(compilation).hadErrorContaining("package com.external does not exist");
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@Wither:"));
    }

    @Test
    @DisplayName("a focus no method of the name takes is refused, listing them")
    void focusNoMethodOfTheNameTakesIsRefusedListingThem() {
      // A three-parameter variable-arity method needs two arguments before its array, so it takes
      // no single one. Cell's parameter is its T, which the wildcard leaves unknown, so its remedy
      // is the source type; Account's T is the spec's own, and its remedy stays the focus.
      var compilation =
          compile(
              external(
                  "Account",
                  """
                  public final class Account<T> {
                      private final String id;
                      public Account(String id) { this.id = id; }
                      public String id() { return id; }
                      public Account<T> withId(Integer id) {
                          return new Account<>(String.valueOf(id));
                      }
                      public Account<T> withId(String id, int copies) { return new Account<>(id); }
                      public Account<T> withId(String first, String second, String... rest) {
                          return new Account<>(first);
                      }
                  }
                  """),
              external(
                  "Cell",
                  """
                  public final class Cell<T> {
                      private final T value;
                      public Cell(T value) { this.value = value; }
                      public T value() { return value; }
                      public Cell<T> withValue(T value) { return new Cell<>(value); }
                  }
                  """),
              spec(
                  "AccountOpticsSpec",
                  """
                  @ImportOptics
                  public interface AccountOpticsSpec<T> extends OpticsSpec<Account<T>> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Account<T>, String> id();
                  }
                  """),
              spec(
                  "CellOpticsSpec",
                  """
                  @ImportOptics
                  public interface CellOpticsSpec extends OpticsSpec<Cell<?>> {
                      @Wither(value = "withValue", getter = "value")
                      Lens<Cell<?>, Object> value();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: No method 'withId' of 'Account<T>' takes the lens's focus type 'String'."
                  + " The generated lens sets through 'source.withId(newValue)' with the new value"
                  + " typed 'String'.");
      assertThat(compilation).hadErrorContaining("withId(String, String, String...)");
      assertThat(compilation)
          .hadErrorContaining(
              "Name a wither that takes the value the getter reads, or point 'getter' at an"
                  + " accessor one of them takes and declare the focus as its type; otherwise"
                  + " rebuild 'Account<T>' with @ViaBuilder, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: No method 'withValue' of 'Cell<?>' takes the lens's focus type 'Object'."
                  + " The generated lens sets through 'source.withValue(newValue)' with the new"
                  + " value typed 'Object'. Found on 'Cell<?>': [withValue(?)]. A parameter a"
                  + " wildcard of 'Cell<?>' stands in takes no value at all, since the type it"
                  + " stands for is unknown. Declare the spec over the type each wildcard stands"
                  + " for, or rebuild 'Cell<?>' with @ViaBuilder, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a call javac cannot choose for is refused, and the focus that chooses compiles")
    void callJavacCannotChooseForIsRefusedAndTheFocusThatChoosesCompiles() {
      final var ident =
          external(
              "Ident",
              """
              public final class Ident {
                  private final String id;
                  public Ident(String id) { this.id = id; }
                  public String id() { return id; }
                  public Ident withId(java.io.Serializable id) { return new Ident(id.toString()); }
                  public Ident withId(CharSequence id) { return new Ident(id.toString()); }
              }
              """);

      var ambiguous =
          compile(
              ident,
              spec(
                  "IdentOpticsSpec",
                  """
                  @ImportOptics
                  public interface IdentOpticsSpec extends OpticsSpec<Ident> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Ident, String> id();
                  }
                  """));

      assertThat(ambiguous).failed();
      assertThat(ambiguous)
          .hadErrorContaining(
              "@Wither: The generated call to 'withId' cannot choose between"
                  + " 'withId(Serializable)' and 'withId(CharSequence)'. The generated lens sets"
                  + " through 'source.withId(newValue)' with the new value typed 'String', which"
                  + " each of them takes, with no parameter more specific than every other."
                  + " Declare the lens's focus as the parameter type of the one you mean");
      assertThat(ambiguous).hadErrorCount(1);

      // The fix line, followed: a CharSequence focus reaches only withId(CharSequence).
      var chosen =
          compile(
              ident,
              spec(
                  "IdentOpticsSpec",
                  """
                  @ImportOptics
                  public interface IdentOpticsSpec extends OpticsSpec<Ident> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Ident, CharSequence> id();
                  }
                  """));

      assertThat(chosen).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a static method the call binds is refused")
    void staticMethodTheCallBindsIsRefused() {
      // A String binds the static withId(CharSequence) ahead of the instance withId(Object).
      var compilation =
          compile(
              external(
                  "Stamp",
                  """
                  public final class Stamp {
                      private final String id;
                      public Stamp(String id) { this.id = id; }
                      public String id() { return id; }
                      public static Stamp withId(CharSequence id) { return new Stamp(id.toString()); }
                      public Object withId(Object id) { return this; }
                  }
                  """),
              spec(
                  "StampOpticsSpec",
                  """
                  @ImportOptics
                  public interface StampOpticsSpec extends OpticsSpec<Stamp> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Stamp, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(CharSequence)' is static, so the generated lens cannot rebuild a"
                  + " 'Stamp' through it. The generated lens sets through 'source.withId(newValue)'"
                  + " with the new value typed 'String', which binds 'withId(CharSequence)', and a"
                  + " static method never reads the 'Stamp' it is called on. Declare the lens's"
                  + " focus as the parameter type of an instance overload, name an instance"
                  + " wither, or rebuild 'Stamp' with @ViaBuilder, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a wither name the source type does not have is refused, offering its withers")
    void witherNameTheSourceTypeDoesNotHaveIsRefusedOfferingItsWithers() {
      // Only a one-parameter instance method that hands Plain back is offered, with the
      // parameter that says which value it takes: not the static one, the two-parameter one or
      // the one returning a String.
      var compilation =
          compile(
              external(
                  "Plain",
                  """
                  public final class Plain {
                      private final String id;
                      private final String name;
                      public Plain(String id, String name) {
                          this.id = id;
                          this.name = name;
                      }
                      public String id() { return id; }
                      public String name() { return name; }
                      public Plain withName(String name) { return new Plain(id, name); }
                      public Plain withId(String id) { return new Plain(id, name); }
                      public Plain withId(Integer id) { return new Plain(String.valueOf(id), name); }
                      public static Plain withDefaults(String id) { return new Plain(id, ""); }
                      public Plain withBoth(String id, String name) { return new Plain(id, name); }
                      public String withSuffix(String suffix) { return id + suffix; }
                  }
                  """),
              external(
                  "Bare",
                  """
                  public final class Bare {
                      private final String id;
                      public Bare(String id) { this.id = id; }
                      public String id() { return id; }
                  }
                  """),
              external(
                  "Hidden",
                  """
                  public final class Hidden {
                      private final String id;
                      public Hidden(String id) { this.id = id; }
                      public String id() { return id; }
                      Hidden withId(String id) { return new Hidden(id); }
                      public Hidden withName(String name) { return new Hidden(name); }
                  }
                  """),
              spec(
                  "PlainOpticsSpec",
                  """
                  @ImportOptics
                  public interface PlainOpticsSpec extends OpticsSpec<Plain> {
                      @Wither(value = "withIdd", getter = "id")
                      Lens<Plain, String> id();
                  }
                  """),
              spec(
                  "PlainNameOpticsSpec",
                  """
                  @ImportOptics
                  public interface PlainNameOpticsSpec extends OpticsSpec<Plain> {
                      @Wither(value = "withIdentifier", getter = "name")
                      Lens<Plain, String> name();
                  }
                  """),
              spec(
                  "BareOpticsSpec",
                  """
                  @ImportOptics
                  public interface BareOpticsSpec extends OpticsSpec<Bare> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Bare, String> id();
                  }
                  """),
              spec(
                  "HiddenOpticsSpec",
                  """
                  @ImportOptics
                  public interface HiddenOpticsSpec extends OpticsSpec<Hidden> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Hidden, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'Plain' has no method 'withIdd' for the generated lens to call. The"
                  + " generated lens sets through 'source.withIdd(newValue)', so it needs a method"
                  + " of that name on 'Plain', declared or inherited. Did you mean 'withId'?"
                  + " Withers found on 'Plain': [withId(Integer), withId(String),"
                  + " withName(String)]. Name one of the withers found on 'Plain' that takes the"
                  + " value 'String' the getter reads, or rebuild 'Plain' with @ViaBuilder,"
                  + " @ViaConstructor or @ViaCopyAndSet.");
      // Too far from any wither to be offered as a misspelling of one.
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains("'withIdentifier'"))
          .singleElement()
          .satisfies(
              error ->
                  Assertions.assertThat(error.getMessage(null))
                      .contains(
                          "Withers found on 'Plain': [withId(Integer), withId(String),"
                              + " withName(String)].")
                      .doesNotContain("Did you mean"));
      // Declared, but not where the generated class can call it, and the message says so.
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'Hidden' has no method 'withId' for the generated lens to call. The"
                  + " generated lens sets through 'source.withId(newValue)', so it needs a method"
                  + " of that name the generated class in 'com.myapp' can call, and 'withId' is"
                  + " declared where it cannot. Withers found on 'Hidden': [withName(String)].");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'Bare' has no method 'withId' for the generated lens to call. The"
                  + " generated lens sets through 'source.withId(newValue)', so it needs a method"
                  + " of that name on 'Bare', declared or inherited. No one-parameter instance"
                  + " method of 'Bare' hands it back. Rebuild 'Bare' with @ViaBuilder,"
                  + " @ViaConstructor or @ViaCopyAndSet.");
      // Reported at the spec, so javac never meets the call in a generated file.
      assertThat(compilation).hadErrorCount(4);
    }

    @Test
    @DisplayName("overloads are ordered by subtyping alone, as javac orders them")
    void overloadsAreOrderedBySubtypingAlone() {
      // A raw parameter is no subtype of a parameterised one, so neither withItems below is more
      // specific; an inherited pair is ordered the same way, and the parameterised one binds; and
      // two concrete overloads one instantiation makes alike are ambiguous to javac.
      var compilation =
          compile(
              external(
                  "Crate",
                  """
                  import java.util.ArrayList;
                  import java.util.List;
                  @SuppressWarnings("rawtypes")
                  public final class Crate {
                      private final ArrayList<String> items;
                      public Crate(ArrayList<String> items) { this.items = items; }
                      public ArrayList<String> items() { return items; }
                      public Crate withItems(ArrayList raw) { return this; }
                      public Crate withItems(List<String> items) {
                          return new Crate(new ArrayList<>(items));
                      }
                  }
                  """),
              external(
                  "Rack",
                  """
                  import java.util.List;
                  @SuppressWarnings("rawtypes")
                  public interface Rack extends RackA, RackB {
                      List<String> items();
                  }
                  """),
              external(
                  "RackA",
                  """
                  import java.util.List;
                  @SuppressWarnings("rawtypes")
                  public interface RackA { Rack withItems(List raw); }
                  """),
              external(
                  "RackB",
                  """
                  import java.util.List;
                  public interface RackB { Object withItems(List<String> items); }
                  """),
              external(
                  "Wrap",
                  """
                  public final class Wrap<T> {
                      private final Object x;
                      public Wrap(Object x) { this.x = x; }
                      public String x() { return String.valueOf(x); }
                      public Wrap<T> withX(T x) { return new Wrap<>(x); }
                      public Wrap<T> withX(String x) { return new Wrap<>(x); }
                  }
                  """),
              spec(
                  "CrateOpticsSpec",
                  """
                  @ImportOptics
                  public interface CrateOpticsSpec extends OpticsSpec<Crate> {
                      @Wither("withItems")
                      Lens<Crate, java.util.ArrayList<String>> items();
                  }
                  """),
              spec(
                  "RackOpticsSpec",
                  """
                  @ImportOptics
                  public interface RackOpticsSpec extends OpticsSpec<Rack> {
                      @Wither("withItems")
                      Lens<Rack, java.util.List<String>> items();
                  }
                  """),
              external(
                  "Tab",
                  """
                  public interface Tab extends TabA, TabB {
                      String x();
                  }
                  """),
              external("TabA", "public interface TabA { Tab withX(CharSequence x); }"),
              external("TabB", "public interface TabB { Tab withX(Comparable<String> x); }"),
              spec(
                  "TabOpticsSpec",
                  """
                  @ImportOptics
                  public interface TabOpticsSpec extends OpticsSpec<Tab> {
                      @Wither("withX")
                      Lens<Tab, String> x();
                  }
                  """),
              external(
                  "Slot",
                  """
                  public final class Slot<T> {
                      private String x;
                      public Slot(String x) { this.x = x; }
                      public Slot(Slot<T> other) { this.x = other.x; }
                      public String x() { return x; }
                      public void setX(T x) { this.x = String.valueOf(x); }
                      public void setX(String x) { this.x = x; }
                  }
                  """),
              spec(
                  "SlotOpticsSpec",
                  """
                  @ImportOptics
                  public interface SlotOpticsSpec extends OpticsSpec<Slot<String>> {
                      @ViaCopyAndSet(setter = "setX")
                      Lens<Slot<String>, String> x();
                  }
                  """),
              spec(
                  "WrapOpticsSpec",
                  """
                  @ImportOptics
                  public interface WrapOpticsSpec extends OpticsSpec<Wrap<String>> {
                      @Wither("withX")
                      Lens<Wrap<String>, String> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "cannot choose between 'withItems(ArrayList)' and 'withItems(List<String>)'");
      assertThat(compilation)
          .hadErrorContaining("'withItems(List<String>)' returns 'Object', not the source type");
      assertThat(compilation)
          .hadErrorContaining("cannot choose between 'withX(T)' and 'withX(String)'");
      // No focus can tell apart two setters one instantiation makes alike, so only another
      // strategy is offered.
      assertThat(compilation)
          .hadErrorContaining(
              "cannot choose between 'setX(T)' and 'setX(String)'. The generated lens sets through"
                  + " 'setX(newValue)' with the new value typed 'String', which each of them takes,"
                  + " with no parameter more specific than every other. Rebuild 'Slot<String>' with"
                  + " @Wither, @ViaBuilder or @ViaConstructor.");
      // Two abstract withers neither of which is more specific are inherited apart, not merged.
      Assertions.assertThat(compilation.errors())
          .anyMatch(
              error ->
                  error.getMessage(null).contains("@Wither: The generated call to 'withX' cannot")
                      && error.getMessage(null).contains("'withX(CharSequence)'")
                      && error.getMessage(null).contains("'withX(Comparable<String>)'"));
      assertThat(compilation).hadErrorCount(5);
    }

    @Test
    @DisplayName("a concrete method beside an interface's declaration of it is the one called")
    void concreteMethodBesideAnInterfacesDeclarationOfItIsTheOneCalled() {
      // getAllMembers hands over both the class's method and the interface's declaration of it;
      // the concrete one is what javac calls, whatever the interface declares.
      var compilation =
          compile(
              external(
                  "Draft3",
                  """
                  public final class Draft3 extends DraftBase implements Redraftable {
                      private final String x;
                      public Draft3(String x) { this.x = x; }
                      @Override public String x() { return x; }
                  }
                  """),
              external(
                  "DraftBase",
                  """
                  public abstract class DraftBase {
                      public abstract String x();
                      public Draft3 withX(String x) { return new Draft3(x); }
                  }
                  """),
              external("Redraftable", "public interface Redraftable { Draft3 withX(String x); }"),
              external(
                  "Sketch",
                  """
                  public final class Sketch extends SketchBase implements Resketchable {
                      private final String x;
                      public Sketch(String x) { this.x = x; }
                      @Override public String x() { return x; }
                  }
                  """),
              external(
                  "SketchBase",
                  """
                  public abstract class SketchBase {
                      public abstract String x();
                      public Sketch withX(String x) { return new Sketch(x); }
                  }
                  """),
              external(
                  "Resketchable",
                  "public interface Resketchable { default Sketch withX(String x) { return null; } }"),
              external(
                  "Member",
                  """
                  public final class Member extends Entity implements Named {
                      public Member() {}
                      public Member(Member other) { setName(other.getName()); }
                  }
                  """),
              external(
                  "Entity",
                  """
                  public abstract class Entity {
                      private String name;
                      public void setName(String name) { this.name = name; }
                      public String getName() { return name; }
                  }
                  """),
              external(
                  "Named",
                  "public interface Named { void setName(String name); String getName(); }"),
              external(
                  "Roster",
                  """
                  import java.util.List;
                  @SuppressWarnings({"rawtypes", "unchecked"})
                  public final class Roster extends RosterBase implements Rewritable {
                      private final List<String> x;
                      public Roster(List x) { this.x = x; }
                      public List<String> x() { return x; }
                  }
                  """),
              external(
                  "RosterBase",
                  """
                  import java.util.List;
                  @SuppressWarnings({"rawtypes", "unchecked"})
                  public abstract class RosterBase {
                      public Roster withX(List x) { return new Roster(x); }
                  }
                  """),
              external(
                  "Rewritable",
                  "public interface Rewritable { Object withX(java.util.List<String> x); }"),
              external(
                  "Stub",
                  """
                  public abstract class Stub extends StubBase implements Restubbable {
                      public abstract String x();
                  }
                  """),
              external(
                  "StubBase",
                  "public abstract class StubBase { public Stub withX(Object x) { return null; } }"),
              external("Restubbable", "public interface Restubbable { Stub withX(String x); }"),
              external(
                  "Ledger3",
                  "public abstract class Ledger3 extends Ledger3Base implements Ledger3J,"
                      + " Ledger3I { public abstract java.util.List<String> x(); }"),
              external(
                  "Ledger3Base",
                  """
                  @SuppressWarnings({"rawtypes", "unchecked"})
                  public abstract class Ledger3Base {
                      public Ledger3 withX(java.util.List x) { return (Ledger3) this; }
                  }
                  """),
              external(
                  "Ledger3J",
                  """
                  @SuppressWarnings("rawtypes")
                  public interface Ledger3J { default Object withX(java.util.List x) { return null; } }
                  """),
              external(
                  "Ledger3I",
                  "public interface Ledger3I { Object withX(java.util.List<String> x); }"),
              spec(
                  "Ledger3OpticsSpec",
                  """
                  @ImportOptics
                  public interface Ledger3OpticsSpec extends OpticsSpec<Ledger3> {
                      // A default never implements another method; the class's raw one runs.
                      @Wither("withX")
                      Lens<Ledger3, java.util.List<String>> x();
                  }
                  """),
              spec(
                  "RosterOpticsSpec",
                  """
                  @ImportOptics
                  public interface RosterOpticsSpec extends OpticsSpec<Roster> {
                      // The raw withX(List) the class inherits implements the interface's, and runs.
                      @Wither("withX")
                      Lens<Roster, java.util.List<String>> x();
                  }
                  """),
              spec(
                  "StubOpticsSpec",
                  """
                  @ImportOptics
                  public interface StubOpticsSpec extends OpticsSpec<Stub> {
                      // withX(Object) implements nothing withX(String) declares, so that one binds.
                      @Wither("withX")
                      Lens<Stub, String> x();
                  }
                  """),
              spec(
                  "Draft3OpticsSpec",
                  """
                  @ImportOptics
                  public interface Draft3OpticsSpec extends OpticsSpec<Draft3> {
                      @Wither("withX")
                      Lens<Draft3, String> x();
                  }
                  """),
              spec(
                  "SketchOpticsSpec",
                  """
                  @ImportOptics
                  public interface SketchOpticsSpec extends OpticsSpec<Sketch> {
                      @Wither("withX")
                      Lens<Sketch, String> x();
                  }
                  """),
              spec(
                  "MemberOpticsSpec",
                  """
                  @ImportOptics
                  public interface MemberOpticsSpec extends OpticsSpec<Member> {
                      @ViaCopyAndSet(setter = "setName")
                      Lens<Member, String> getName();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a default method is never the one an abstract method's call runs")
    void defaultMethodIsNeverTheOneAnAbstractMethodsCallRuns() {
      // The abstract withX(List<String>) the class declares is what javac binds, beside a raw
      // default an interface offers; it returns the source type in one and Object in the other.
      var compilation =
          compile(
              external(
                  "Reel",
                  "public abstract class Reel extends ReelBase implements Reeling {"
                      + " public abstract java.util.List<String> x(); }"),
              external(
                  "ReelBase",
                  "public abstract class ReelBase {"
                      + " public abstract Reel withX(java.util.List<String> x); }"),
              external(
                  "Reeling",
                  """
                  @SuppressWarnings("rawtypes")
                  public interface Reeling { default Object withX(java.util.List x) { return null; } }
                  """),
              external(
                  "Spool",
                  "public abstract class Spool extends SpoolBase implements Spooling {"
                      + " public abstract java.util.List<String> x(); }"),
              external(
                  "SpoolBase",
                  "public abstract class SpoolBase {"
                      + " public abstract Object withX(java.util.List<String> x); }"),
              external(
                  "Spooling",
                  """
                  @SuppressWarnings("rawtypes")
                  public interface Spooling { default Spool withX(java.util.List x) { return null; } }
                  """),
              spec(
                  "ReelOpticsSpec",
                  """
                  @ImportOptics
                  public interface ReelOpticsSpec extends OpticsSpec<Reel> {
                      @Wither("withX")
                      Lens<Reel, java.util.List<String>> x();
                  }
                  """),
              spec(
                  "SpoolOpticsSpec",
                  """
                  @ImportOptics
                  public interface SpoolOpticsSpec extends OpticsSpec<Spool> {
                      @Wither("withX")
                      Lens<Spool, java.util.List<String>> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withX(List<String>)' returns 'Object', not the source type" + " 'Spool'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("abstract methods whose erasures differ are two methods, however they instantiate")
    void abstractMethodsWhoseErasuresDifferAreTwoMethods() {
      var compilation =
          compile(
              external(
                  "Knot",
                  """
                  public interface Knot extends KnotA<String>, KnotB {
                      String x();
                  }
                  """),
              external("KnotA", "public interface KnotA<T> { Knot withX(T x); }"),
              external("KnotB", "public interface KnotB { Knot withX(String x); }"),
              spec(
                  "KnotOpticsSpec",
                  """
                  @ImportOptics
                  public interface KnotOpticsSpec extends OpticsSpec<Knot> {
                      @Wither("withX")
                      Lens<Knot, String> x();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .anyMatch(
              error ->
                  error.getMessage(null).contains("@Wither: The generated call to 'withX' cannot")
                      && error.getMessage(null).contains("'withX(T)'")
                      && error.getMessage(null).contains("'withX(String)'"));
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("Strategy Method Names")
  class StrategyMethodNames {

    /**
     * A class every strategy can be pointed at, with a builder and a setter that work, beside the
     * shapes the checks turn away: a static {@code stamp()}, a {@code label(int)} that takes an
     * argument, and a {@code size()} that reads another type.
     */
    private static JavaFileObject account() {
      return external(
          "Account",
          """
          public final class Account {
              private final String id;
              private String host;

              public Account(String id) { this.id = id; }
              public Account(Account other) { this.id = other.id; this.host = other.host; }

              public String id() { return id; }
              public String getId() { return id; }
              public int size() { return id.length(); }
              public static String stamp() { return ""; }
              public static void setDefault(String host) {}
              public String label(int index) { return id; }
              public void setHost(String host) { this.host = host; }
              public Account withId(String id) { return new Account(id); }
              public Builder toBuilder() { return new Builder(id); }

              public static final class Builder {
                  private String id;
                  Builder(String id) { this.id = id; }
                  public Builder id(String id) { this.id = id; return this; }
                  public Account build() { return new Account(id); }
              }
          }
          """);
    }

    @Test
    @DisplayName("a getter the source type does not have is refused, whichever strategy reads it")
    void getterTheSourceTypeDoesNotHaveIsRefused() {
      // @Wither and @ViaBuilder name their getter, and are told to point it somewhere real;
      // @ViaCopyAndSet and @ViaConstructor read through the lens method's own name, and carry no
      // attribute that could point anywhere else.
      var compilation =
          compile(
              account(),
              spec(
                  "WitherSpec",
                  """
                  @ImportOptics
                  public interface WitherSpec extends OpticsSpec<Account> {
                      @Wither(value = "withId", getter = "ident")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "BuilderSpec",
                  """
                  @ImportOptics
                  public interface BuilderSpec extends OpticsSpec<Account> {
                      @ViaBuilder(getter = "ident")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "CopySpec",
                  """
                  @ImportOptics
                  public interface CopySpec extends OpticsSpec<Account> {
                      @ViaCopyAndSet(setter = "setHost")
                      Lens<Account, String> ident();
                  }
                  """),
              spec(
                  "ConstructorSpec",
                  """
                  @ImportOptics
                  public interface ConstructorSpec extends OpticsSpec<Account> {
                      @ViaConstructor(parameterOrder = {"ident"})
                      Lens<Account, String> ident();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'Account' has no method 'ident()' for the generated lens to read the value"
                  + " it focuses. The generated lens calls 'ident()' on 'source', so it needs a"
                  + " zero-parameter instance method of that name that the generated class in"
                  + " 'com.myapp' can call. Set @Wither's 'getter' to a method 'Account'"
                  + " declares.");
      assertThat(compilation)
          .hadErrorContaining("@ViaBuilder: 'Account' has no method 'ident()' for the generated");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'Account' has no method 'ident()' for the generated lens to read the"
                  + " value it focuses.");
      assertThat(compilation)
          .hadErrorContaining(
              "Name the lens method after a zero-parameter method 'Account' declares, which is the"
                  + " accessor this strategy reads through.");
      assertThat(compilation).hadErrorCount(4);
    }

    @Test
    @DisplayName("a name spelt in another convention is offered the type's own, however far apart")
    void nameSpeltInAnotherConventionIsOfferedTheTypesOwn() {
      // A bean reads and writes through prefixed names, a record and its fluent builder through
      // bare ones; a prefix added, taken away or swapped is three or more edits, too many for a
      // near miss. A server reads through a bare name and sets through a fluent setter beside a
      // wither; a draft has only a wither and a bare method that copies, which @Wither is for
      // rather than a copy's setter; a site's builder steps through a wither; and a chain's only
      // one-argument method is a fluent setter, which a wither must not be. A box's value takes a
      // type its wildcard stands in, which no value is, and a site's title setter ends the builder
      // chain.
      var profile =
          external(
              "Profile",
              """
              public final class Profile {
                  private String name;
                  private boolean active;

                  public Profile() {}
                  public Profile(Profile other) { this.name = other.name; this.active = other.active; }

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }
                  public boolean isActive() { return active; }
                  public void setActive(boolean active) { this.active = active; }
              }
              """);
      var card =
          external(
              "Card",
              """
              public record Card(String title) {
                  public Card withTitle(String title) { return new Card(title); }
                  public Builder toBuilder() { return new Builder(); }

                  public static final class Builder {
                      private String title;
                      public Builder title(String title) { this.title = title; return this; }
                      public Card build() { return new Card(title); }
                  }
              }
              """);
      var server =
          external(
              "Server",
              """
              public final class Server {
                  private String host;

                  public Server() {}
                  public Server(Server other) { this.host = other.host; }

                  public String host() { return host; }
                  public Server setHost(String host) { this.host = host; return this; }
                  public Server withHost(String host) {
                      Server copy = new Server(this);
                      copy.host = host;
                      return copy;
                  }
              }
              """);
      var draft =
          external(
              "Draft",
              """
              public final class Draft {
                  private final String title;

                  public Draft(String title) { this.title = title; }
                  public Draft(Draft other) { this.title = other.title; }

                  public String title() { return title; }
                  public Draft title(String title) { return new Draft(title); }
                  public Draft withTitle(String title) { return new Draft(title); }
              }
              """);
      var site =
          external(
              "Site",
              """
              public final class Site {
                  private final String url;

                  public Site(String url) { this.url = url; }

                  public String getUrl() { return url; }
                  public String getTitle() { return url; }
                  public Builder toBuilder() { return new Builder(url); }

                  public static final class Builder {
                      private String url;
                      Builder(String url) { this.url = url; }
                      public Builder withUrl(String url) { this.url = url; return this; }
                      public void setTitle(String title) {}
                      public Site build() { return new Site(url); }
                  }
              }
              """);
      var chain =
          external(
              "Chain",
              """
              public final class Chain {
                  private String label;

                  public String getLabel() { return label; }
                  public Chain setLabel(String label) { this.label = label; return this; }
              }
              """);
      var prefixAdded =
          spec(
              "PrefixAddedSpec",
              """
              @ImportOptics
              public interface PrefixAddedSpec extends OpticsSpec<Profile> {
                  @ViaCopyAndSet(setter = "setName")
                  Lens<Profile, String> name();
              }
              """);
      var prefixSwapped =
          spec(
              "PrefixSwappedSpec",
              """
              @ImportOptics
              public interface PrefixSwappedSpec extends OpticsSpec<Profile> {
                  @ViaBuilder(getter = "getActive")
                  Lens<Profile, Boolean> active();
              }
              """);
      var prefixTakenAway =
          spec(
              "PrefixTakenAwaySpec",
              """
              @ImportOptics
              public interface PrefixTakenAwaySpec extends OpticsSpec<Card> {
                  @Wither(value = "withTitle", getter = "getTitle")
                  Lens<Card, String> title();
              }
              """);
      var builderSetter =
          spec(
              "BuilderSetterSpec",
              """
              @ImportOptics
              public interface BuilderSetterSpec extends OpticsSpec<Card> {
                  @ViaBuilder(setter = "setTitle")
                  Lens<Card, String> title();
              }
              """);
      var beanSetter =
          spec(
              "BeanSetterSpec",
              """
              @ImportOptics
              public interface BeanSetterSpec extends OpticsSpec<Profile> {
                  @ViaCopyAndSet(setter = "name")
                  Lens<Profile, String> getName();
              }
              """);
      var wither =
          spec(
              "WitherSpec",
              """
              @ImportOptics
              public interface WitherSpec extends OpticsSpec<Card> {
                  @Wither(value = "setTitle", getter = "title")
                  Lens<Card, String> title();
              }
              """);

      var accessorAsSetter =
          spec(
              "AccessorAsSetterSpec",
              """
              @ImportOptics
              public interface AccessorAsSetterSpec extends OpticsSpec<Server> {
                  @ViaCopyAndSet(setter = "host")
                  Lens<Server, String> host();
              }
              """);
      var accessorAsWildcardSetter =
          spec(
              "AccessorAsWildcardSetterSpec",
              """
              @ImportOptics
              public interface AccessorAsWildcardSetterSpec extends OpticsSpec<Server> {
                  @ViaCopyAndSet(setter = "host")
                  Lens<Server, ? extends CharSequence> host();
              }
              """);
      var accessorAsWither =
          spec(
              "AccessorAsWitherSpec",
              """
              @ImportOptics
              public interface AccessorAsWitherSpec extends OpticsSpec<Server> {
                  @Wither("host")
                  Lens<Server, String> host();
              }
              """);
      var witherForSetter =
          spec(
              "WitherForSetterSpec",
              """
              @ImportOptics
              public interface WitherForSetterSpec extends OpticsSpec<Draft> {
                  @ViaCopyAndSet(setter = "setTitle")
                  Lens<Draft, String> title();
              }
              """);

      var box =
          external(
              "Box",
              """
              public final class Box<T> {
                  private T value;

                  public T getValue() { return value; }
                  public Box<T> withValue(T value) {
                      Box<T> copy = new Box<>();
                      copy.value = value;
                      return copy;
                  }
                  public Builder<T> toBuilder() { return new Builder<>(); }

                  public static final class Builder<T> {
                      public Builder<T> setValue(T value) { return this; }
                      public Box<T> build() { return new Box<>(); }
                  }
              }
              """);
      var wildcardWither =
          spec(
              "WildcardWitherSpec",
              """
              @ImportOptics
              public interface WildcardWitherSpec extends OpticsSpec<Box<?>> {
                  @Wither(value = "value", getter = "getValue")
                  Lens<Box<?>, Object> value();
              }
              """);
      var concreteWither =
          spec(
              "ConcreteWitherSpec",
              """
              @ImportOptics
              public interface ConcreteWitherSpec extends OpticsSpec<Box<String>> {
                  @Wither(value = "value", getter = "getValue")
                  Lens<Box<String>, String> value();
              }
              """);
      var wildcardBuilderSetter =
          spec(
              "WildcardBuilderSetterSpec",
              """
              @ImportOptics
              public interface WildcardBuilderSetterSpec extends OpticsSpec<Box<?>> {
                  @ViaBuilder(getter = "getValue")
                  Lens<Box<?>, Object> value();
              }
              """);
      var voidBuilderSetter =
          spec(
              "VoidBuilderSetterSpec",
              """
              @ImportOptics
              public interface VoidBuilderSetterSpec extends OpticsSpec<Site> {
                  @ViaBuilder(getter = "getTitle")
                  Lens<Site, String> title();
              }
              """);
      var builderWither =
          spec(
              "BuilderWitherSpec",
              """
              @ImportOptics
              public interface BuilderWitherSpec extends OpticsSpec<Site> {
                  @ViaBuilder(getter = "getUrl", setter = "setUrl")
                  Lens<Site, String> url();
              }
              """);
      var accessorAsFluentWither =
          spec(
              "AccessorAsFluentWitherSpec",
              """
              @ImportOptics
              public interface AccessorAsFluentWitherSpec extends OpticsSpec<Chain> {
                  @Wither(value = "getLabel", getter = "getLabel")
                  Lens<Chain, String> label();
              }
              """);
      var nearWitherForSetter =
          spec(
              "NearWitherForSetterSpec",
              """
              @ImportOptics
              public interface NearWitherForSetterSpec extends OpticsSpec<Draft> {
                  @ViaCopyAndSet(setter = "withTitl")
                  Lens<Draft, String> title();
              }
              """);
      var nearSetterForWither =
          spec(
              "NearSetterForWitherSpec",
              """
              @ImportOptics
              public interface NearSetterForWitherSpec extends OpticsSpec<Chain> {
                  @Wither(value = "setLabl", getter = "getLabel")
                  Lens<Chain, String> label();
              }
              """);
      var recordObjectMethod =
          spec(
              "RecordObjectMethodSpec",
              """
              @ImportOptics
              public interface RecordObjectMethodSpec extends OpticsSpec<Card> {
                  @Wither(value = "withTitle", getter = "hashcode")
                  Lens<Card, Integer> hash();
              }
              """);
      var objectMethod =
          spec(
              "ObjectMethodSpec",
              """
              @ImportOptics
              public interface ObjectMethodSpec extends OpticsSpec<Profile> {
                  @ViaCopyAndSet(setter = "setName")
                  Lens<Profile, String> at();
              }
              """);

      var compilation =
          compile(
              profile,
              card,
              server,
              draft,
              site,
              chain,
              box,
              wildcardWither,
              concreteWither,
              wildcardBuilderSetter,
              voidBuilderSetter,
              builderWither,
              accessorAsFluentWither,
              nearWitherForSetter,
              nearSetterForWither,
              recordObjectMethod,
              objectMethod,
              prefixAdded,
              prefixSwapped,
              prefixTakenAway,
              builderSetter,
              beanSetter,
              wither,
              accessorAsSetter,
              accessorAsWildcardSetter,
              accessorAsWither,
              witherForSetter);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'Profile' has no method 'name()' for the generated lens to read the"
                  + " value it focuses. The generated lens calls 'name()' on 'source', so it needs a"
                  + " zero-parameter instance method of that name that the generated class in"
                  + " 'com.myapp' can call. Did you mean 'getName'? Name the lens method after a"
                  + " zero-parameter method 'Profile' declares, which is the accessor this strategy"
                  + " reads through.")
          .inFile(prefixAdded);
      assertThat(compilation).hadErrorContaining("Did you mean 'isActive'?").inFile(prefixSwapped);
      assertThat(compilation).hadErrorContaining("Did you mean 'title'?").inFile(prefixTakenAway);
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Card.Builder' has no method 'setTitle' for the generated lens to set"
                  + " through. The generated lens sets through 'setTitle(newValue)' on"
                  + " 'Card.Builder', so it needs a method of that name there that the generated"
                  + " class in 'com.myapp' can call. Did you mean 'title'?")
          .inFile(builderSetter);
      assertThat(compilation).hadErrorContaining("Did you mean 'setName'?").inFile(beanSetter);
      assertThat(compilation).hadErrorContaining("Did you mean 'withTitle'?").inFile(wither);
      // A name that only reads, written where a setter or a wither was meant, is offered the one
      // that spells the same value in the convention the call needs.
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: No method 'host' of 'Server' takes the lens's focus type 'String'."
                  + " The generated lens sets through 'host(newValue)' with the new value typed"
                  + " 'String'. Found on 'Server': [host()]. Did you mean 'setHost'?")
          .inFile(accessorAsSetter);
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: No method 'host' of 'Server' takes one argument. The generated lens"
                  + " sets through 'host(newValue)', passing the one value it sets. Found on"
                  + " 'Server': [host()]. Did you mean 'setHost'?")
          .inFile(accessorAsWildcardSetter);
      assertThat(compilation)
          .hadErrorContaining("Found on 'Server': [host()]. Did you mean 'withHost'?")
          .inFile(accessorAsWither);
      // A builder carries on with what its setter hands back, so a wither step is offered there.
      assertThat(compilation).hadErrorContaining("Did you mean 'withUrl'?").inFile(builderWither);
      assertThat(compilation)
          .hadErrorContaining("Did you mean 'withValue'?")
          .inFile(concreteWither);
      // A copy's setter is meant to set the copy, so neither the bare method nor the wither, each
      // of which copies, is offered for it, however near the name; a wither is offered no
      // fluent setter, which would change its source; neither Object's 'wait' nor a record's
      // 'hashCode' is ever what was meant; a method whose parameter a wildcard stands in takes no
      // value; and a builder setter that hands back nothing ends the chain.
      for (String refusal :
          List.of(
              "'Draft' has no method 'setTitle'",
              "'Draft' has no method 'withTitl'",
              "No method 'getLabel' of 'Chain'",
              "'Chain' has no method 'setLabl'",
              "'Card' has no method 'hashcode()'",
              "'Profile' has no method 'at()'",
              "'Box<?>' has no method 'value' for the generated lens to call",
              "'Box.Builder<",
              "'Site.Builder' has no method 'title'")) {
        Assertions.assertThat(compilation.errors())
            .filteredOn(error -> error.getMessage(null).contains(refusal))
            .singleElement()
            .satisfies(
                error ->
                    Assertions.assertThat(error.getMessage(null)).doesNotContain("Did you mean"));
      }
      assertThat(compilation).hadErrorCount(20);
    }

    @Test
    @DisplayName("a static method, and one that takes an argument, are not accessors")
    void staticMethodAndOneThatTakesAnArgumentAreNotAccessors() {
      var compilation =
          compile(
              account(),
              spec(
                  "StampSpec",
                  """
                  @ImportOptics
                  public interface StampSpec extends OpticsSpec<Account> {
                      @Wither(value = "withId", getter = "stamp")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "LabelSpec",
                  """
                  @ImportOptics
                  public interface LabelSpec extends OpticsSpec<Account> {
                      @Wither(value = "withId", getter = "label")
                      Lens<Account, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Wither: 'Account' has no method 'stamp()' for the generated lens");
      assertThat(compilation)
          .hadErrorContaining("@Wither: 'Account' has no method 'label()' for the generated lens");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a getter that reads another type is refused, with the focus it reads")
    void getterThatReadsAnotherTypeIsRefused() {
      // The pairing a spec exists to declare still has to typecheck: LocalDate's withMonth(int)
      // beside getMonth() is this shape.
      var compilation =
          compile(
              account(),
              spec(
                  "SizedSpec",
                  """
                  @ImportOptics
                  public interface SizedSpec extends OpticsSpec<Account> {
                      @Wither(value = "withId", getter = "size")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "SizedCopySpec",
                  """
                  @ImportOptics
                  public interface SizedCopySpec extends OpticsSpec<Account> {
                      @ViaCopyAndSet(setter = "setHost")
                      Lens<Account, String> size();
                  }
                  """),
              external(
                  "Dated",
                  """
                  public final class Dated {
                      private final int month;
                      public Dated(int month) { this.month = month; }
                      public String getMonth() { return String.valueOf(month); }
                      public Dated withMonth(int month) { return new Dated(month); }
                  }
                  """),
              spec(
                  "DatedOpticsSpec",
                  """
                  @ImportOptics
                  public interface DatedOpticsSpec extends OpticsSpec<Dated> {
                      @Wither(value = "withMonth", getter = "getMonth")
                      Lens<Dated, Integer> month();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'size()' reads 'int', not the lens's focus 'String'. The generated lens"
                  + " reads through 'source.size()' and hands what it reads back as its focus,"
                  + " which 'int' is not. Point @Wither's 'getter' at an accessor that reads"
                  + " 'String', or declare the lens over 'Integer' and rebuild it through a method"
                  + " that takes one.");
      // Read through the lens method's own name, there is no attribute to point anywhere else.
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'size()' reads 'int', not the lens's focus 'String'. The generated"
                  + " lens reads through 'source.size()' and hands what it reads back as its focus,"
                  + " which 'int' is not. Name the lens method after an accessor that reads"
                  + " 'String', or declare the lens over 'Integer' and rebuild it through a method"
                  + " that takes one.");
      // A reference read keeps its own name where the lens could be declared over it.
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'getMonth()' reads 'String', not the lens's focus 'Integer'. The generated"
                  + " lens reads through 'source.getMonth()' and hands what it reads back as its"
                  + " focus, which 'String' is not. Point @Wither's 'getter' at an accessor that"
                  + " reads 'Integer', or declare the lens over 'String' and rebuild it through a"
                  + " method that takes one.");
      assertThat(compilation).hadErrorCount(3);
    }

    @Test
    @DisplayName("each step of a builder chain is held to what the step before it hands back")
    void eachStepOfABuilderChainIsHeldToWhatTheStepBeforeItHandsBack() {
      var compilation =
          compile(
              account(),
              external(
                  "Flat",
                  """
                  public final class Flat {
                      private final String id;
                      public Flat(String id) { this.id = id; }
                      public String id() { return id; }
                      public int toBuilder() { return 0; }
                  }
                  """),
              external(
                  "Odd",
                  """
                  public final class Odd {
                      private final String id;
                      public Odd(String id) { this.id = id; }
                      public String id() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public void id(String id) {}
                          public String build() { return ""; }
                      }
                  }
                  """),
              external(
                  "Half",
                  """
                  public final class Half {
                      private final String id;
                      public Half(String id) { this.id = id; }
                      public String id() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public Builder id(String id) { return this; }
                          public String build() { return ""; }
                      }
                  }
                  """),
              spec(
                  "MissingBuilderSpec",
                  """
                  @ImportOptics
                  public interface MissingBuilderSpec extends OpticsSpec<Account> {
                      @ViaBuilder(getter = "id", toBuilder = "builder")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "MissingSetterSpec",
                  """
                  @ImportOptics
                  public interface MissingSetterSpec extends OpticsSpec<Account> {
                      @ViaBuilder(getter = "id", setter = "ident")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "MissingBuildSpec",
                  """
                  @ImportOptics
                  public interface MissingBuildSpec extends OpticsSpec<Account> {
                      @ViaBuilder(getter = "id", build = "create")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "FlatSpec",
                  """
                  @ImportOptics
                  public interface FlatSpec extends OpticsSpec<Flat> {
                      @ViaBuilder
                      Lens<Flat, String> id();
                  }
                  """),
              spec(
                  "OddSpec",
                  """
                  @ImportOptics
                  public interface OddSpec extends OpticsSpec<Odd> {
                      @ViaBuilder
                      Lens<Odd, String> id();
                  }
                  """),
              spec(
                  "HalfSpec",
                  """
                  @ImportOptics
                  public interface HalfSpec extends OpticsSpec<Half> {
                      @ViaBuilder
                      Lens<Half, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Account' has no method 'builder()' for the generated lens to rebuild"
                  + " through.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Account.Builder' has no method 'ident' for the generated lens to set"
                  + " through. The generated lens sets through 'ident(newValue)' on"
                  + " 'Account.Builder', so it needs a method of that name there that the generated"
                  + " class in 'com.myapp' can call. Set @ViaBuilder's 'setter' to a method"
                  + " 'Account.Builder' declares.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Account.Builder' has no method 'create()' for the generated lens to"
                  + " finish the value it rebuilds. The generated lens calls 'create()' on the"
                  + " 'Account.Builder' the setter hands back");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'toBuilder()' hands back 'int', which is not a builder to set"
                  + " through.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'id(String)' hands back 'void', which is not a builder to build"
                  + " from.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'build()' returns 'String', not the source type 'Half'. The generated"
                  + " lens finishes with 'build()' and hands its result back as the source type"
                  + " 'Half', which 'String' is not. Set @ViaBuilder's 'build' to the method that"
                  + " finishes a 'Half', or rebuild 'Half' with @Wither, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(6);
    }

    @Test
    @DisplayName("a setter the call cannot bind is refused, on a builder and on the source")
    void setterTheCallCannotBindIsRefused() {
      var compilation =
          compile(
              account(),
              external(
                  "Picky",
                  """
                  public final class Picky {
                      private final String id;
                      public Picky(String id) { this.id = id; }
                      public Picky(Picky other) { this.id = other.id; }
                      public String id() { return id; }
                      public void setId(Integer id) {}
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public Builder id(java.io.Serializable id) { return this; }
                          public Builder id(CharSequence id) { return this; }
                          public Picky build() { return new Picky(""); }
                      }
                  }
                  """),
              spec(
                  "PickyCopySpec",
                  """
                  @ImportOptics
                  public interface PickyCopySpec extends OpticsSpec<Picky> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Picky, String> id();
                  }
                  """),
              spec(
                  "PickyBuilderSpec",
                  """
                  @ImportOptics
                  public interface PickyBuilderSpec extends OpticsSpec<Picky> {
                      @ViaBuilder
                      Lens<Picky, String> id();
                  }
                  """),
              spec(
                  "MissingCopySetterSpec",
                  """
                  @ImportOptics
                  public interface MissingCopySetterSpec extends OpticsSpec<Account> {
                      @ViaCopyAndSet(setter = "setHots")
                      Lens<Account, String> getId();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: No method 'setId' of 'Picky' takes the lens's focus type 'String'."
                  + " The generated lens sets through 'setId(newValue)' with the new value typed"
                  + " 'String'. Found on 'Picky': [setId(Integer)]. Set @ViaCopyAndSet's 'setter'"
                  + " to a method that takes the value the getter reads; otherwise rebuild 'Picky'"
                  + " with @Wither, @ViaBuilder or @ViaConstructor.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: The generated call to 'id' on a 'Picky.Builder' cannot choose between"
                  + " 'id(Serializable)' and 'id(CharSequence)'.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'Account' has no method 'setHots' for the generated lens to set"
                  + " through.");
      assertThat(compilation).hadErrorContaining("Did you mean 'setHost'?");
      assertThat(compilation).hadErrorCount(3);
    }

    @Test
    @DisplayName("a static setter the call binds is refused")
    void staticSetterTheCallBindsIsRefused() {
      var compilation =
          compile(
              external(
                  "Fixed",
                  """
                  public final class Fixed {
                      private final String id;
                      public Fixed(String id) { this.id = id; }
                      public Fixed(Fixed other) { this.id = other.id; }
                      public String id() { return id; }
                      public static void setId(String id) {}
                  }
                  """),
              spec(
                  "FixedSpec",
                  """
                  @ImportOptics
                  public interface FixedSpec extends OpticsSpec<Fixed> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Fixed, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'setId(String)' is static, so the generated lens cannot set through"
                  + " it on a 'Fixed'. The generated lens sets through 'setId(newValue)' with the"
                  + " new value typed 'String', which binds that method, and a static method never"
                  + " reads the value it is called on. Set @ViaCopyAndSet's 'setter' to an instance"
                  + " method.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a parameterOrder name that reads nothing is refused")
    void parameterOrderNameThatReadsNothingIsRefused() {
      var compilation =
          compile(
              external(
                  "Point",
                  """
                  public final class Point {
                      private final int x;
                      private final int y;
                      public Point(int x, int y) { this.x = x; this.y = y; }
                      public int x() { return x; }
                      public int y() { return y; }
                  }
                  """),
              spec(
                  "PointSpec",
                  """
                  @ImportOptics
                  public interface PointSpec extends OpticsSpec<Point> {
                      @ViaConstructor(parameterOrder = {"x", "yy"})
                      Lens<Point, Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'Point' has no method 'yy()' for the generated lens to read the"
                  + " argument 'yy'. The generated lens calls 'yy()' on 'source', so it needs a"
                  + " zero-parameter instance method of that name that the generated class in"
                  + " 'com.myapp' can call. Did you mean 'y'? Name in @ViaConstructor's"
                  + " 'parameterOrder' the accessors 'Point' declares, in the order its"
                  + " constructor takes them.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an accessor reading another type is named in full where the names collide")
    void accessorReadingAnotherTypeIsNamedInFullWhereTheNamesCollide() {
      var compilation =
          compile(
              external(
                  "Ticket",
                  """
                  public final class Ticket {
                      private final Id id;
                      public Ticket(Id id) { this.id = id; }
                      public Id id() { return id; }
                      public Ticket withId(Id id) { return new Ticket(id); }
                  }
                  """),
              external("Id", "public final class Id {}\n"),
              JavaFileObjects.forSourceString(
                  "com.myapp.Id",
                  """
                  package com.myapp;

                  public final class Id {}
                  """),
              spec(
                  "TicketOpticsSpec",
                  """
                  @ImportOptics
                  public interface TicketOpticsSpec extends OpticsSpec<Ticket> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Ticket, com.myapp.Id> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'id()' reads 'com.external.Id', not the lens's focus 'com.myapp.Id'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an accessor reading nothing, or a type a wildcard stands in, is offered no focus")
    void accessorReadingNothingIsOfferedNoFocus() {
      // A lens can be declared over what an accessor reads, but neither 'void' nor the type a
      // wildcard stands for can be written as a focus, so only the accessor is offered.
      var compilation =
          compile(
              external(
                  "Blank",
                  """
                  public final class Blank {
                      private final String id;
                      public Blank(String id) { this.id = id; }
                      public void id() {}
                      public String label() { return id; }
                      public Blank withId(String id) { return new Blank(id); }
                  }
                  """),
              external(
                  "Cell2",
                  """
                  public final class Cell2<T> {
                      private final T value;
                      public Cell2(T value) { this.value = value; }
                      public T value() { return value; }
                      public Cell2<T> withValue(String value) { return this; }
                  }
                  """),
              spec(
                  "BlankOpticsSpec",
                  """
                  @ImportOptics
                  public interface BlankOpticsSpec extends OpticsSpec<Blank> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Blank, String> id();
                  }
                  """),
              spec(
                  "Cell2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Cell2OpticsSpec extends OpticsSpec<Cell2<?>> {
                      @Wither(value = "withValue", getter = "value")
                      Lens<Cell2<?>, String> value();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'id()' reads 'void', not the lens's focus 'String'. The generated lens"
                  + " reads through 'source.id()' and hands what it reads back as its focus, which"
                  + " 'void' is not. Point @Wither's 'getter' at an accessor that reads 'String'.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'value()' reads '?', not the lens's focus 'String'. The generated lens"
                  + " reads through 'source.value()' and hands what it reads back as its focus,"
                  + " which '?' is not. Point @Wither's 'getter' at an accessor that reads"
                  + " 'String'.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a builder setter that takes no focus offers the getter as well as the setter")
    void builderSetterThatTakesNoFocusOffersTheGetterAsWell() {
      // @ViaBuilder names both halves, so the remedy can move either one; the wither's twin says
      // the same, and neither sends the author round in a circle.
      var compilation =
          compile(
              external(
                  "Ledger2",
                  """
                  public final class Ledger2 {
                      private final String id;
                      public Ledger2(String id) { this.id = id; }
                      public String getId() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public Builder id(Integer id) { return this; }
                          public Ledger2 build() { return new Ledger2(""); }
                      }
                  }
                  """),
              spec(
                  "Ledger2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Ledger2OpticsSpec extends OpticsSpec<Ledger2> {
                      @ViaBuilder(getter = "getId")
                      Lens<Ledger2, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: No method 'id' of 'Ledger2.Builder' takes the lens's focus type"
                  + " 'String'. The generated lens sets through 'id(newValue)' with the new value"
                  + " typed 'String'. Found on 'Ledger2.Builder': [id(Integer)]. Set @ViaBuilder's"
                  + " 'setter' to a method that takes the value the getter reads, or point"
                  + " @ViaBuilder's 'getter' at an accessor one of them takes and declare the focus"
                  + " as its type; otherwise rebuild 'Ledger2' with @Wither, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a builder step declared with a type variable is read on what it is bound to")
    void builderStepDeclaredWithATypeVariableIsReadOnWhatItIsBoundTo() {
      // javac infers such a step to the variable's bound where nothing else pins it down, and the
      // chain is read there too.
      var compilation =
          compile(
              external(
                  "Crate",
                  """
                  public final class Crate {
                      private final String id;
                      public Crate(String id) { this.id = id; }
                      public String getId() { return id; }

                      @SuppressWarnings("unchecked")
                      public <B extends CrateBuilder> B toBuilder() {
                          return (B) new CrateBuilder();
                      }
                  }
                  """),
              external(
                  "CrateBuilder",
                  """
                  public class CrateBuilder {
                      private String id;
                      public CrateBuilder id(String id) { this.id = id; return this; }
                      public Crate build() { return new Crate(id); }
                  }
                  """),
              spec(
                  "CrateOpticsSpec",
                  """
                  @ImportOptics
                  public interface CrateOpticsSpec extends OpticsSpec<Crate> {
                      @ViaBuilder(getter = "getId")
                      Lens<Crate, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.CrateOptics", "source.toBuilder().id(newValue).build()");
    }

    @Test
    @DisplayName("a builder step bounded by more than one type is left to javac")
    void builderStepBoundedByMoreThanOneTypeIsLeftToJavac() {
      // A bound naming more than one type leaves no single type to read the next call on, and
      // javac infers the step perfectly well from the bound itself. Crock's toBuilder is bounded
      // that way, and Crank's setter is.
      var compilation =
          compile(
              external(
                  "Crock",
                  """
                  public final class Crock {
                      private final String id;
                      public Crock(String id) { this.id = id; }
                      public String getId() { return id; }

                      @SuppressWarnings("unchecked")
                      public <B extends CrockBuilder & Cloneable> B toBuilder() {
                          return (B) new CrockBuilder();
                      }
                  }
                  """),
              external(
                  "CrockBuilder",
                  """
                  public class CrockBuilder implements Cloneable {
                      private String id;

                      @SuppressWarnings("unchecked")
                      public <B extends CrockBuilder & Cloneable> B id(String id) {
                          this.id = id;
                          return (B) this;
                      }

                      public Crock build() { return new Crock(id); }
                  }
                  """),
              external(
                  "Crank",
                  """
                  public final class Crank {
                      private final String id;
                      public Crank(String id) { this.id = id; }
                      public String getId() { return id; }
                      public CrankBuilder toBuilder() { return new CrankBuilder(); }
                  }
                  """),
              external(
                  "CrankBuilder",
                  """
                  public class CrankBuilder implements Cloneable {
                      private String id;

                      @SuppressWarnings("unchecked")
                      public <B extends CrankBuilder & Cloneable> B id(String id) {
                          this.id = id;
                          return (B) this;
                      }

                      public Crank build() { return new Crank(id); }
                  }
                  """),
              spec(
                  "CrockOpticsSpec",
                  """
                  @ImportOptics
                  public interface CrockOpticsSpec extends OpticsSpec<Crock> {
                      @ViaBuilder(getter = "getId")
                      Lens<Crock, String> id();
                  }
                  """),
              spec(
                  "CrankOpticsSpec",
                  """
                  @ImportOptics
                  public interface CrankOpticsSpec extends OpticsSpec<Crank> {
                      @ViaBuilder(getter = "getId")
                      Lens<Crank, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a type the setter hands back that cannot be named is refused too")
    void typeTheSetterHandsBackThatCannotBeNamedIsRefused() {
      var compilation =
          compile(
              external(
                  "Staged",
                  """
                  public final class Staged {
                      private final String id;
                      public Staged(String id) { this.id = id; }
                      public String getId() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public Step id(String id) { return new Step(); }
                      }
                  }

                  class Step {
                      public Staged build() { return new Staged(""); }
                  }
                  """),
              spec(
                  "StagedOpticsSpec",
                  """
                  @ImportOptics
                  public interface StagedOpticsSpec extends OpticsSpec<Staged> {
                      @ViaBuilder(getter = "getId")
                      Lens<Staged, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Step', which 'id(String)' hands back, cannot be named from"
                  + " 'com.myapp'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a builder the generated class cannot name is refused, whatever its members are")
    void builderTheGeneratedClassCannotNameIsRefused() {
      var compilation =
          compile(
              external(
                  "Sealed",
                  """
                  public final class Sealed {
                      private final String id;
                      public Sealed(String id) { this.id = id; }
                      public String getId() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      static final class Builder {
                          public Builder id(String id) { return this; }
                          public Sealed build() { return new Sealed(""); }
                      }
                  }
                  """),
              spec(
                  "SealedOpticsSpec",
                  """
                  @ImportOptics
                  public interface SealedOpticsSpec extends OpticsSpec<Sealed> {
                      @ViaBuilder(getter = "getId")
                      Lens<Sealed, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Builder', which 'toBuilder()' hands back, cannot be named from"
                  + " 'com.myapp'. The generated lens rebuilds through that type, and a class it"
                  + " cannot see is a compile error in a file its author never wrote. Make"
                  + " 'Builder' public, or rebuild 'Sealed' with @Wither, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a setter with no one-argument overload is refused, whatever the focus is")
    void setterWithNoOneArgumentOverloadIsRefused() {
      // Arity does not depend on the focus, so a wildcard one is no reason to leave this to javac.
      var compilation =
          compile(
              external(
                  "Pairy",
                  """
                  public final class Pairy {
                      private String id;
                      public Pairy() {}
                      public Pairy(Pairy other) { this.id = other.id; }
                      public String getId() { return id; }
                      public void setId(String id, boolean flag) { this.id = id; }
                  }
                  """),
              spec(
                  "PairyOpticsSpec",
                  """
                  @ImportOptics
                  public interface PairyOpticsSpec extends OpticsSpec<Pairy> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Pairy, ? extends CharSequence> getId();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: No method 'setId' of 'Pairy' takes one argument. The generated lens"
                  + " sets through 'setId(newValue)', passing the one value it sets. Found on"
                  + " 'Pairy': [setId(String, boolean)]. Set @ViaCopyAndSet's 'setter' to a method"
                  + " that takes the value the lens sets; otherwise rebuild 'Pairy' with @Wither,"
                  + " @ViaBuilder or @ViaConstructor.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName(
        "a parameterOrder that never names the lens is refused, since it would set nothing")
    void parameterOrderThatNeverNamesTheLensIsRefused() {
      var compilation =
          compile(
              external(
                  "Tagged2",
                  """
                  public final class Tagged2 {
                      private final String id;
                      private final String tag;
                      public Tagged2(String id, String tag) {
                          this.id = id;
                          this.tag = tag;
                      }
                      public String id() { return id; }
                      public String tag() { return tag; }
                  }
                  """),
              spec(
                  "Tagged2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Tagged2OpticsSpec extends OpticsSpec<Tagged2> {
                      @ViaConstructor(parameterOrder = {"tag", "tag"})
                      Lens<Tagged2, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' names no argument for the lens's own 'id'. The"
                  + " generated lens rebuilds 'Tagged2' from the order given, and passes the value"
                  + " it sets where the lens's own name stands; naming it nowhere would set"
                  + " nothing. Pass the arguments where the constructor's parameter names place them:"
                  + " @ViaConstructor(parameterOrder = {\"id\", \"tag\"}).");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a setter whose own type does not resolve is left to javac")
    void setterWhoseOwnTypeDoesNotResolveIsLeftToJavac(@TempDir Path dir) throws IOException {
      var compilation =
          compileAgainstAClassFileNamingAMissingType(
              dir,
              external(
                  "Vanish",
                  """
                  public final class Vanish {
                      private final String id;
                      public Vanish(String id) { this.id = id; }
                      public String id() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public com.external.Gone id(String id) { return null; }
                          public Vanish build() { return new Vanish(""); }
                      }
                  }
                  """),
              spec(
                  "VanishOpticsSpec",
                  """
                  @ImportOptics
                  public interface VanishOpticsSpec extends OpticsSpec<Vanish> {
                      @ViaBuilder
                      Lens<Vanish, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@ViaBuilder:"));
    }

    @Test
    @DisplayName("a wildcard focus leaves every name that depends on it to javac")
    void wildcardFocusLeavesEveryNameThatDependsOnItToJavac() {
      // The accessors still have to exist; which method the value binds, and what that method
      // hands back, is javac's to settle for a focus it infers.
      var compilation =
          compile(
              external(
                  "Loose",
                  """
                  public final class Loose {
                      private final String id;
                      public Loose(String id) { this.id = id; }
                      public Loose(Loose other) { this.id = other.id; }
                      public String id() { return id; }
                      public void setId(CharSequence id) {}
                      public void setId(CharSequence id, int at) {}
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          // Declared first, and returning something the chain could not go on
                          // from: which overload such a call binds is javac's to settle.
                          public Object id(Integer id) { return this; }
                          public Builder id(CharSequence id) { return this; }
                          public Loose build() { return new Loose(""); }
                      }
                  }
                  """),
              spec(
                  "LooseCopySpec",
                  """
                  @ImportOptics
                  public interface LooseCopySpec extends OpticsSpec<Loose> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Loose, ? extends CharSequence> id();
                  }
                  """),
              spec(
                  "LooseBuilderSpec",
                  """
                  @ImportOptics
                  public interface LooseBuilderSpec extends OpticsSpec<Loose> {
                      @ViaBuilder
                      Lens<Loose, ? extends CharSequence> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a builder type that does not resolve is left to javac")
    void builderTypeThatDoesNotResolveIsLeftToJavac(@TempDir Path dir) throws IOException {
      var compilation =
          compileAgainstAClassFileNamingAMissingType(
              dir,
              external(
                  "Absent",
                  """
                  public final class Absent {
                      private final String id;
                      public Absent(String id) { this.id = id; }
                      public String id() { return id; }
                      public com.external.Gone toBuilder() { return null; }
                  }
                  """),
              spec(
                  "AbsentSpec",
                  """
                  @ImportOptics
                  public interface AbsentSpec extends OpticsSpec<Absent> {
                      @ViaBuilder
                      Lens<Absent, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@ViaBuilder:"));
    }

    @Test
    @DisplayName("a source type that does not resolve is left to javac, whatever the strategy")
    void sourceTypeThatDoesNotResolveIsLeftToJavacWhateverTheStrategy() {
      var compilation =
          compile(
              spec(
                  "GhostWitherSpec",
                  """
                  @ImportOptics
                  public interface GhostWitherSpec extends OpticsSpec<com.external.Ghost> {
                      @Wither(value = "withId", getter = "id")
                      Lens<com.external.Ghost, String> id();
                  }
                  """),
              spec(
                  "GhostBuilderSpec",
                  """
                  @ImportOptics
                  public interface GhostBuilderSpec extends OpticsSpec<com.external.Ghost> {
                      @ViaBuilder
                      Lens<com.external.Ghost, String> id();
                  }
                  """),
              spec(
                  "GhostCopySpec",
                  """
                  @ImportOptics
                  public interface GhostCopySpec extends OpticsSpec<com.external.Ghost> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<com.external.Ghost, String> id();
                  }
                  """),
              spec(
                  "GhostConstructorSpec",
                  """
                  @ImportOptics
                  public interface GhostConstructorSpec extends OpticsSpec<com.external.Ghost> {
                      @ViaConstructor(parameterOrder = {"id"})
                      Lens<com.external.Ghost, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@Wither:"))
          .noneMatch(error -> error.getMessage(null).contains("@ViaBuilder:"))
          .noneMatch(error -> error.getMessage(null).contains("@ViaCopyAndSet:"))
          .noneMatch(error -> error.getMessage(null).contains("@ViaConstructor:"));
    }

    @Test
    @DisplayName("a setter whose parameter is inferred is left to javac")
    void setterWhoseParameterIsInferredIsLeftToJavac() {
      var compilation =
          compile(
              external(
                  "Generic",
                  """
                  public final class Generic {
                      private final String id;
                      public Generic(String id) { this.id = id; }
                      public Generic(Generic other) { this.id = other.id; }
                      public String id() { return id; }
                      public <V extends CharSequence> void setId(V id) {}
                  }
                  """),
              spec(
                  "GenericSpec",
                  """
                  @ImportOptics
                  public interface GenericSpec extends OpticsSpec<Generic> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Generic, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }
  }

  @Nested
  @DisplayName("@ViaCopyAndSet Setter Result")
  class ViaCopyAndSetSetterResult {

    /** A set function that hands back what the wither returns. */
    private static String keeping(String type, String setter) {
      return "(source, newValue) -> new %1$s(source).%2$s(newValue)".formatted(type, setter);
    }

    /** A set function that calls the setter for its effect and hands back the copy. */
    private static String dropping(String type, String setter) {
      return """
          (source, newValue) -> {
            %1$s copy = new %1$s(source);
            copy.%2$s(newValue);
            return copy;
          }"""
          .formatted(type, setter);
    }

    @Test
    @DisplayName("a wither that returns the source type whatever the call binds hands it back")
    void setterReturningTheSourceTypeMayHandItBack() {
      // A memo's wither hands back a new memo, and a rec's fluent wither the copy it set: the lens
      // hands back either. So it does where the call might bind either of a pane's two withers,
      // where a badge's wither takes a type variable of its own, and where a crate's wither retags
      // its type argument, which the call infers back. The memo's other methods of the name return
      // nothing, but no call passing one string reaches them.
      var compilation =
          compile(
              external(
                  "Memo",
                  """
                  public class Memo {
                      private String title;
                      public Memo() {}
                      public Memo(Memo other) { title = other.title; }
                      public String getTitle() { return title; }
                      public Memo withTitle(String title) {
                          Memo copy = new Memo(this);
                          copy.title = title;
                          return copy;
                      }
                      public void withTitle(String title, String locale, String... notes) {
                          this.title = title + locale + notes.length;
                      }
                      public void withTitle(int code) { this.title = "#" + code; }
                      public void withTitle(String title, int revision) { this.title = title; }
                      public void withTitle(int... codes) { this.title = "#" + codes.length; }
                      public void withTitle(long code, String... notes) { this.title = "#" + code; }
                  }
                  """),
              external(
                  "Rec",
                  """
                  public class Rec {
                      private String name;
                      public Rec() {}
                      public Rec(Rec other) { name = other.name; }
                      public String getName() { return name; }
                      public Rec withName(String name) { this.name = name; return this; }
                  }
                  """),
              external(
                  "Pane",
                  """
                  public class Pane {
                      private CharSequence title = "";
                      public Pane() {}
                      public Pane(Pane other) { title = other.title; }
                      public String getTitle() { return title.toString(); }
                      public Pane withTitle(String title) { return copyWith(title); }
                      public Pane withTitle(StringBuilder title) { return copyWith(title); }
                      private Pane copyWith(CharSequence title) {
                          Pane copy = new Pane(this);
                          copy.title = title;
                          return copy;
                      }
                  }
                  """),
              external(
                  "Badge",
                  """
                  public class Badge {
                      private String title;
                      public Badge() {}
                      public Badge(Badge other) { title = other.title; }
                      public String getTitle() { return title; }
                      public <T extends CharSequence> Badge withTitle(T title) {
                          Badge copy = new Badge(this);
                          copy.title = title.toString();
                          return copy;
                      }
                  }
                  """),
              external(
                  "Crate",
                  """
                  public class Crate<T> {
                      private String label;
                      public Crate() {}
                      public Crate(Crate<T> other) { label = other.label; }
                      public String getLabel() { return label; }
                      public <U> Crate<U> withLabel(String label) {
                          Crate<U> copy = new Crate<>();
                          copy.label = label;
                          return copy;
                      }
                  }
                  """),
              spec(
                  "MemoOpticsSpec",
                  """
                  @ImportOptics
                  public interface MemoOpticsSpec extends OpticsSpec<Memo> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Memo, String> getTitle();
                  }
                  """),
              spec(
                  "RecOpticsSpec",
                  """
                  @ImportOptics
                  public interface RecOpticsSpec extends OpticsSpec<Rec> {
                      @ViaCopyAndSet(setter = "withName")
                      Lens<Rec, String> getName();
                  }
                  """),
              spec(
                  "PaneOpticsSpec",
                  """
                  @ImportOptics
                  public interface PaneOpticsSpec extends OpticsSpec<Pane> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Pane, ? extends CharSequence> getTitle();
                  }
                  """),
              spec(
                  "BadgeOpticsSpec",
                  """
                  @ImportOptics
                  public interface BadgeOpticsSpec extends OpticsSpec<Badge> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Badge, String> getTitle();
                  }
                  """),
              spec(
                  "CrateOpticsSpec",
                  """
                  @ImportOptics
                  public interface CrateOpticsSpec extends OpticsSpec<Crate<String>> {
                      @ViaCopyAndSet(setter = "withLabel")
                      Lens<Crate<String>, String> getLabel();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.MemoOptics", keeping("Memo", "withTitle"));
      assertGeneratedCodeContains(compilation, "com.myapp.RecOptics", keeping("Rec", "withName"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.PaneOptics", keeping("Pane", "withTitle"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.BadgeOptics", keeping("Badge", "withTitle"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.CrateOptics", keeping("Crate<String>", "withLabel"));
    }

    @Test
    @DisplayName("a set method, and a wither that returns nothing, are called for their effect")
    void setterThatMayReturnSomethingElseIsCalledForItsEffect() {
      // A page's setter returns nothing, and a leaf's hands back the base it is declared on. A
      // sheet's fluent setter and a link's, which hands back the link it replaced, return the
      // source type, but a set method may hand back anything of it, so each is called for its
      // effect. A quire's string reaches only the wither that returns nothing, as the element of
      // its array, and a ledger's wither returns nothing. A vane's string binds the wither that
      // returns nothing, beside one taking any object, and a jar's wither returns a type that holds
      // no jar, so none drops a jar and none warns.
      var compilation =
          compile(
              external(
                  "Page",
                  """
                  public class Page {
                      private String title;
                      public Page() {}
                      public Page(Page other) { title = other.title; }
                      public String getTitle() { return title; }
                      public void setTitle(String title) { this.title = title; }
                  }
                  """),
              external(
                  "LeafBase",
                  """
                  public class LeafBase {
                      protected String title;
                      public LeafBase setTitle(String title) { this.title = title; return this; }
                  }
                  """),
              external(
                  "Leaf",
                  """
                  public class Leaf extends LeafBase {
                      public Leaf() {}
                      public Leaf(Leaf other) { title = other.title; }
                      public String getTitle() { return title; }
                  }
                  """),
              external(
                  "Sheet",
                  """
                  public class Sheet {
                      private String title;
                      public Sheet() {}
                      public Sheet(Sheet other) { title = other.title; }
                      public String getTitle() { return title; }
                      public Sheet setTitle(String title) { this.title = title; return this; }
                  }
                  """),
              external(
                  "Link",
                  """
                  public class Link {
                      private Link next;
                      public Link() {}
                      public Link(Link other) { next = other.next; }
                      public Link getNext() { return next; }
                      public Link setNext(Link next) {
                          Link replaced = this.next;
                          this.next = next;
                          return replaced;
                      }
                  }
                  """),
              external(
                  "Quire",
                  """
                  public class Quire {
                      private CharSequence title = "";
                      public Quire() {}
                      public Quire(Quire other) { title = other.title; }
                      public String getTitle() { return title.toString(); }
                      public Quire withTitle(StringBuilder title) { this.title = title; return this; }
                      public void withTitle(String... parts) { this.title = String.join(" ", parts); }
                  }
                  """),
              external(
                  "Vane",
                  """
                  public class Vane {
                      private Object title = "";
                      public Vane() {}
                      public Vane(Vane other) { title = other.title; }
                      public String getTitle() { return title.toString(); }
                      public void withTitle(String title) { this.title = title; }
                      public Vane withTitle(Object title) {
                          Vane copy = new Vane(this);
                          copy.title = title;
                          return copy;
                      }
                  }
                  """),
              external(
                  "Jar",
                  """
                  import java.util.Optional;

                  public class Jar {
                      private String label = "";
                      public Jar() {}
                      public Jar(Jar other) { label = other.label; }
                      public String getLabel() { return label; }
                      public Optional<String> withLabel(String label) {
                          String old = this.label;
                          this.label = label;
                          return Optional.of(old);
                      }
                  }
                  """),
              external(
                  "Ledger",
                  """
                  public class Ledger {
                      private String note;
                      public Ledger() {}
                      public Ledger(Ledger other) { note = other.note; }
                      public String getNote() { return note; }
                      public void withNote(String note) { this.note = note; }
                  }
                  """),
              spec(
                  "PageOpticsSpec",
                  """
                  @ImportOptics
                  public interface PageOpticsSpec extends OpticsSpec<Page> {
                      @ViaCopyAndSet(setter = "setTitle")
                      Lens<Page, String> getTitle();
                  }
                  """),
              spec(
                  "LeafOpticsSpec",
                  """
                  @ImportOptics
                  public interface LeafOpticsSpec extends OpticsSpec<Leaf> {
                      @ViaCopyAndSet(setter = "setTitle")
                      Lens<Leaf, String> getTitle();
                  }
                  """),
              spec(
                  "SheetOpticsSpec",
                  """
                  @ImportOptics
                  public interface SheetOpticsSpec extends OpticsSpec<Sheet> {
                      @ViaCopyAndSet(setter = "setTitle")
                      Lens<Sheet, String> getTitle();
                  }
                  """),
              spec(
                  "LinkOpticsSpec",
                  """
                  @ImportOptics
                  public interface LinkOpticsSpec extends OpticsSpec<Link> {
                      @ViaCopyAndSet(setter = "setNext")
                      Lens<Link, Link> getNext();
                  }
                  """),
              spec(
                  "QuireOpticsSpec",
                  """
                  @ImportOptics
                  public interface QuireOpticsSpec extends OpticsSpec<Quire> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Quire, String> getTitle();
                  }
                  """),
              spec(
                  "VaneOpticsSpec",
                  """
                  @ImportOptics
                  public interface VaneOpticsSpec extends OpticsSpec<Vane> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Vane, String> getTitle();
                  }
                  """),
              spec(
                  "JarOpticsSpec",
                  """
                  @ImportOptics
                  public interface JarOpticsSpec extends OpticsSpec<Jar> {
                      @ViaCopyAndSet(setter = "withLabel")
                      Lens<Jar, String> getLabel();
                  }
                  """),
              spec(
                  "LedgerOpticsSpec",
                  """
                  @ImportOptics
                  public interface LedgerOpticsSpec extends OpticsSpec<Ledger> {
                      @ViaCopyAndSet(setter = "withNote")
                      Lens<Ledger, String> getNote();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.PageOptics", dropping("Page", "setTitle"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.LeafOptics", dropping("Leaf", "setTitle"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.SheetOptics", dropping("Sheet", "setTitle"));
      assertGeneratedCodeContains(compilation, "com.myapp.LinkOptics", dropping("Link", "setNext"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.QuireOptics", dropping("Quire", "withTitle"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.VaneOptics", dropping("Vane", "withTitle"));
      assertGeneratedCodeContains(compilation, "com.myapp.JarOptics", dropping("Jar", "withLabel"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.LedgerOptics", dropping("Ledger", "withNote"));
    }

    @Test
    @DisplayName("a wither whose result the lens drops warns, unless the lens method suppresses it")
    void witherWhoseResultTheLensDropsWarns() {
      // A sprig's wither hands back the base it is declared on, and a slip's the class under
      // other type arguments, and a cask's an optional of one. A vial's returns whatever the call
      // is assigned to, a bundle's might lose the call to a method that returns nothing, and a
      // kit's has one taking any object beside it, which returns nothing. A parcel's, a bale's and
      // a bin's retags are inferred from the value they are
      // passed, from what they throw and through another variable's bound. So the lens can keep
      // none of their results. A twig's is the sprig's shape, on a lens method that says it sets
      // the copy after all.
      var compilation =
          compileReporting(
              external(
                  "SprigBase",
                  """
                  public class SprigBase {
                      protected String title;
                      public SprigBase withTitle(String title) {
                          SprigBase copy = new SprigBase();
                          copy.title = title;
                          return copy;
                      }
                  }
                  """),
              external(
                  "Sprig",
                  """
                  public class Sprig extends SprigBase {
                      public Sprig() {}
                      public Sprig(Sprig other) { title = other.title; }
                      public String getTitle() { return title; }
                  }
                  """),
              external(
                  "Twig",
                  """
                  public class Twig extends SprigBase {
                      public Twig() {}
                      public Twig(Twig other) { title = other.title; }
                      public String getTitle() { return title; }
                  }
                  """),
              external(
                  "Slip",
                  """
                  public class Slip<T> {
                      private String id = "";
                      public Slip() {}
                      public Slip(Slip<T> other) { id = other.id; }
                      public String getId() { return id; }
                      public Slip<String> withId(String id) {
                          Slip<String> copy = new Slip<>();
                          copy.id = id;
                          return copy;
                      }
                  }
                  """),
              external(
                  "Cask",
                  """
                  import java.util.Optional;

                  public class Cask {
                      private String label = "";
                      public Cask() {}
                      public Cask(Cask other) { label = other.label; }
                      public String getLabel() { return label; }
                      public Optional<Cask> withLabel(String label) {
                          Cask copy = new Cask(this);
                          copy.label = label;
                          return Optional.of(copy);
                      }
                  }
                  """),
              external(
                  "Vial",
                  """
                  public class Vial {
                      private String title;
                      public Vial() {}
                      public Vial(Vial other) { title = other.title; }
                      public String getTitle() { return title; }
                      @SuppressWarnings("unchecked")
                      public <R> R withTitle(String title) {
                          Vial copy = new Vial(this);
                          copy.title = title;
                          return (R) copy;
                      }
                  }
                  """),
              external(
                  "Bundle",
                  """
                  public class Bundle {
                      private CharSequence title = "";
                      public Bundle() {}
                      public Bundle(Bundle other) { title = other.title; }
                      public String getTitle() { return title.toString(); }
                      public Bundle withTitle(StringBuilder title) {
                          Bundle copy = new Bundle(this);
                          copy.title = title;
                          return copy;
                      }
                      public void withTitle(CharSequence title, Object... notes) { this.title = title; }
                  }
                  """),
              external(
                  "Kit",
                  """
                  public class Kit {
                      private Object title = "";
                      public Kit() {}
                      public Kit(Kit other) { title = other.title; }
                      public String getTitle() { return title.toString(); }
                      public Kit withTitle(String title) {
                          Kit copy = new Kit(this);
                          copy.title = title;
                          return copy;
                      }
                      public void withTitle(Object title) { this.title = title; }
                  }
                  """),
              external(
                  "Parcel",
                  """
                  public class Parcel<T> {
                      private final T value;
                      public Parcel(T value) { this.value = value; }
                      public Parcel(Parcel<T> other) { value = other.value; }
                      public T getValue() { return value; }
                      public <U> Parcel<U> withValue(U value) { return new Parcel<>(value); }
                  }
                  """),
              external(
                  "Bale",
                  """
                  public class Bale<T> {
                      private String label = "";
                      public Bale() {}
                      public Bale(Bale<T> other) { label = other.label; }
                      public String getLabel() { return label; }
                      public <U extends Exception> Bale<U> withLabel(String label) throws U {
                          Bale<U> copy = new Bale<>();
                          copy.label = label;
                          return copy;
                      }
                  }
                  """),
              external(
                  "Bin",
                  """
                  import java.util.List;

                  public class Bin<T> {
                      private List<?> items = List.of();
                      public Bin() {}
                      public Bin(Bin<T> other) { items = other.items; }
                      @SuppressWarnings("unchecked")
                      public List<Integer> getItems() { return (List<Integer>) items; }
                      public <U, P extends List<U>> Bin<U> withItems(P items) {
                          Bin<U> copy = new Bin<>();
                          copy.items = items;
                          return copy;
                      }
                  }
                  """),
              spec(
                  "SprigOpticsSpec",
                  """
                  @ImportOptics
                  public interface SprigOpticsSpec extends OpticsSpec<Sprig> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Sprig, String> getTitle();
                  }
                  """),
              spec(
                  "TwigOpticsSpec",
                  """
                  @ImportOptics
                  public interface TwigOpticsSpec extends OpticsSpec<Twig> {
                      @SuppressWarnings("dropped-result")
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Twig, String> getTitle();
                  }
                  """),
              spec(
                  "SlipOpticsSpec",
                  """
                  @ImportOptics
                  public interface SlipOpticsSpec extends OpticsSpec<Slip<Integer>> {
                      @ViaCopyAndSet(setter = "withId")
                      Lens<Slip<Integer>, String> getId();
                  }
                  """),
              spec(
                  "CaskOpticsSpec",
                  """
                  @ImportOptics
                  public interface CaskOpticsSpec extends OpticsSpec<Cask> {
                      @ViaCopyAndSet(setter = "withLabel")
                      Lens<Cask, String> getLabel();
                  }
                  """),
              spec(
                  "VialOpticsSpec",
                  """
                  @ImportOptics
                  public interface VialOpticsSpec extends OpticsSpec<Vial> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Vial, String> getTitle();
                  }
                  """),
              spec(
                  "BundleOpticsSpec",
                  """
                  @ImportOptics
                  public interface BundleOpticsSpec extends OpticsSpec<Bundle> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Bundle, ? extends CharSequence> getTitle();
                  }
                  """),
              spec(
                  "KitOpticsSpec",
                  """
                  @ImportOptics
                  public interface KitOpticsSpec extends OpticsSpec<Kit> {
                      @ViaCopyAndSet(setter = "withTitle")
                      Lens<Kit, String> getTitle();
                  }
                  """),
              spec(
                  "ParcelOpticsSpec",
                  """
                  @ImportOptics
                  public interface ParcelOpticsSpec extends OpticsSpec<Parcel<Integer>> {
                      @ViaCopyAndSet(setter = "withValue")
                      Lens<Parcel<Integer>, Integer> getValue();
                  }
                  """),
              spec(
                  "BaleOpticsSpec",
                  """
                  @ImportOptics
                  public interface BaleOpticsSpec extends OpticsSpec<Bale<java.io.IOException>> {
                      @ViaCopyAndSet(setter = "withLabel")
                      Lens<Bale<java.io.IOException>, String> getLabel();
                  }
                  """),
              spec(
                  "BinOpticsSpec",
                  """
                  @ImportOptics
                  public interface BinOpticsSpec extends OpticsSpec<Bin<String>> {
                      @ViaCopyAndSet(setter = "withItems")
                      Lens<Bin<String>, java.util.List<Integer>> getItems();
                  }
                  """));

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadWarningContaining(
              "@ViaCopyAndSet: The generated lens returns the copy it calls 'withTitle' on, and"
                  + " drops what 'withTitle' returns. A method spelt as a wither usually hands"
                  + " back a new value and leaves the copy as it was, and then the lens's set"
                  + " changes nothing. The lens can hand back the result only where every method"
                  + " of that name the new value can be passed to returns a 'Sprig', and"
                  + " 'withTitle(String)'"
                  + " returns 'SprigBase'. Where 'withTitle' sets the copy it is called on,"
                  + " annotate 'getTitle()' @SuppressWarnings(\"dropped-result\"); otherwise name"
                  + " a setter that sets the copy, or rebuild 'Sprig' with @ViaBuilder or"
                  + " @ViaConstructor.");
      assertThat(compilation)
          .hadWarningContaining("a 'Slip<Integer>', and 'withId(String)' returns 'Slip<String>'.");
      assertThat(compilation)
          .hadWarningContaining("a 'Cask', and 'withLabel(String)' returns 'Optional<Cask>'.");
      assertThat(compilation)
          .hadWarningContaining("a 'Vial', and 'withTitle(String)' returns 'R'. Where");
      assertThat(compilation)
          .hadWarningContaining(
              "a 'Bundle', and 'withTitle(CharSequence, Object...)' returns 'void'. Where"
                  + " 'withTitle' sets the copy it is called on, annotate 'getTitle()'"
                  + " @SuppressWarnings(\"dropped-result\"); otherwise name a setter");
      // @Wither binds the call itself, so it can serve the kit's wither.
      assertThat(compilation)
          .hadWarningContaining(
              "a 'Kit', and 'withTitle(Object)' returns 'void'. Where 'withTitle' sets the copy it"
                  + " is called on, annotate 'getTitle()' @SuppressWarnings(\"dropped-result\");"
                  + " otherwise rebuild 'Kit' with @Wither, @ViaBuilder or @ViaConstructor.");
      assertThat(compilation)
          .hadWarningContaining(
              "a 'Parcel<Integer>', and 'withValue(U)' returns 'Parcel<U>', whose type the call"
                  + " infers from more than what it is assigned to.");
      assertThat(compilation)
          .hadWarningContaining("a 'Bale<IOException>', and 'withLabel(String)' returns 'Bale<U>'");
      assertThat(compilation)
          .hadWarningContaining("a 'Bin<String>', and 'withItems(P)' returns 'Bin<U>'");
      assertThat(compilation).hadWarningCount(9);
      // A warning, so each lens is still written, calling the setter for its effect.
      assertGeneratedCodeContains(
          compilation, "com.myapp.SprigOptics", dropping("Sprig", "withTitle"));
      assertGeneratedCodeContains(
          compilation, "com.myapp.TwigOptics", dropping("Twig", "withTitle"));
    }
  }

  @Nested
  @DisplayName("@ViaConstructor Order")
  class ViaConstructorOrder {

    @Test
    @DisplayName("an order that swaps two parameters of one type is refused, offering the names'")
    void orderThatSwapsTwoParametersOfOneTypeIsRefused() {
      var compilation =
          compile(
              external("Point", POINT),
              spec(
                  "PointOpticsSpec",
                  """
                  @ImportOptics
                  public interface PointOpticsSpec extends OpticsSpec<Point> {
                      @ViaConstructor(parameterOrder = {"y", "x"})
                      Lens<Point, Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes 'source.y()' where 'Point(int x, int y)'"
                  + " takes 'x'. The generated lens rebuilds through 'new Point(source.y(),"
                  + " newValue)', and 'x' is named after 'x()', an accessor of 'Point' that reads a"
                  + " value it takes. Another value passed in its place is written into the wrong"
                  + " field, which breaks the lens laws. Pass the arguments where the constructor's"
                  + " parameter names place them: @ViaConstructor(parameterOrder = {\"x\", \"y\"}).");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("each name is read as its accessor's spelling, and the offer keeps the author's")
    void eachNameIsReadAsItsAccessorsSpelling() {
      var compilation =
          compile(
              external(
                  "Spot",
                  """
                  public final class Spot {
                      private final int x;
                      private final int y;
                      public Spot(int x, int y) { this.x = x; this.y = y; }
                      public int x() { return x; }
                      public int getX() { return x; }
                      public int getY() { return y; }
                  }
                  """),
              spec(
                  "SpotOpticsSpec",
                  """
                  @ImportOptics
                  public interface SpotOpticsSpec extends OpticsSpec<Spot> {
                      // 'getX' reads the parameter 'x' as well as 'x' does.
                      @ViaConstructor(parameterOrder = {"getX", "getY"})
                      Lens<Spot, Integer> getX();

                      @ViaConstructor(parameterOrder = {"getY", "getX"})
                      Lens<Spot, Integer> getY();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes the new value where 'Spot(int x, int y)'"
                  + " takes 'x'.");
      assertThat(compilation)
          .hadErrorContaining("@ViaConstructor(parameterOrder = {\"getX\", \"getY\"}).");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("the lens's own value in two places, and an unboxed focus, are named as passed")
    void lensValueInTwoPlacesAndAnUnboxedFocusAreNamedAsPassed() {
      var compilation =
          compile(
              external("Point", POINT),
              external(
                  "Span",
                  """
                  public final class Span {
                      private final long from;
                      private final long to;
                      public Span(long from, long to) { this.from = from; this.to = to; }
                      public Span(Number from, Number to) { this(-1L, -1L); }
                      public long from() { return from; }
                      public long to() { return to; }
                  }
                  """),
              spec(
                  "PointOpticsSpec",
                  """
                  @ImportOptics
                  public interface PointOpticsSpec extends OpticsSpec<Point> {
                      @ViaConstructor(parameterOrder = {"x", "x"})
                      Lens<Point, Integer> x();
                  }
                  """),
              spec(
                  "SpanOpticsSpec",
                  """
                  @ImportOptics
                  public interface SpanOpticsSpec extends OpticsSpec<Span> {
                      @ViaConstructor(parameterOrder = {"to", "from"})
                      Lens<Span, Long> from();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'parameterOrder' passes the new value where 'Point(int x, int y)' takes 'y'. The"
                  + " generated lens rebuilds through 'new Point(newValue, newValue)'");
      assertThat(compilation)
          .hadErrorContaining(
              "'parameterOrder' passes 'source.to()' where 'Span(long from, long to)' takes"
                  + " 'from'. The generated lens rebuilds through 'new Span(source.to(), (long)"
                  + " newValue)'");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a constructor with no parameter for the lens's own value cannot rebuild it")
    void constructorWithNoParameterForTheLensCannotRebuildIt() {
      var compilation =
          compile(
              external(
                  "Heat",
                  """
                  public final class Heat {
                      private final double kelvin;
                      public Heat(double kelvin) { this.kelvin = kelvin; }
                      public double kelvin() { return kelvin; }
                      public double celsius() { return kelvin - 273.15; }
                  }
                  """),
              spec(
                  "HeatOpticsSpec",
                  """
                  @ImportOptics
                  public interface HeatOpticsSpec extends OpticsSpec<Heat> {
                      @ViaConstructor(parameterOrder = {"celsius"})
                      Lens<Heat, Double> celsius();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'Heat(double kelvin)' takes no parameter named after the lens's own"
                  + " 'celsius()'. The generated lens rebuilds through 'new Heat(newValue)', passing"
                  + " the new value where 'Heat(double kelvin)' takes 'kelvin', a parameter its name"
                  + " says takes what 'kelvin()' reads, not what the lens reads. If"
                  + " 'celsius()' reads the same value as 'kelvin()', name the lens method 'kelvin'"
                  + " and write that name in the order too. @ViaConstructor cannot set 'celsius()'"
                  + " on a 'Heat', since no constructor takes a parameter named after it: rebuild"
                  + " 'Heat' with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a parameter named after nothing that reads leaves its place to the order")
    void parameterNamedAfterNothingLeavesItsPlaceToTheOrder() {
      var compilation =
          compile(
              external(
                  "Tag",
                  """
                  public final class Tag {
                      private final String id;
                      private final String text;
                      public Tag(String id, String label) { this.id = id; this.text = label; }
                      public String id() { return id; }
                      public String text() { return text; }
                  }
                  """),
              external(
                  "Price",
                  """
                  public final class Price {
                      private final long cents;
                      private final String currency;
                      public Price(long amount, String currency) {
                          this.cents = amount;
                          this.currency = currency;
                      }
                      public java.math.BigDecimal amount() {
                          return java.math.BigDecimal.valueOf(cents, 2);
                      }
                      public long cents() { return cents; }
                      public String currency() { return currency; }
                  }
                  """),
              spec(
                  "TagOpticsSpec",
                  """
                  @ImportOptics
                  public interface TagOpticsSpec extends OpticsSpec<Tag> {
                      // 'label' names nothing 'Tag' reads, so the names say nothing of the order.
                      @ViaConstructor(parameterOrder = {"id", "text"})
                      Lens<Tag, String> text();
                  }
                  """),
              spec(
                  "PriceOpticsSpec",
                  """
                  @ImportOptics
                  public interface PriceOpticsSpec extends OpticsSpec<Price> {
                      // 'amount()' reads a BigDecimal, which the parameter 'amount' cannot take.
                      @ViaConstructor(parameterOrder = {"cents", "currency"})
                      Lens<Price, Long> cents();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a class file that keeps no parameter names leaves the order to its author")
    void classFileThatKeepsNoParameterNamesLeavesTheOrderToItsAuthor(@TempDir Path dir)
        throws IOException {
      JavaFileObject swapped =
          spec(
              "PointOpticsSpec",
              """
              @ImportOptics
              public interface PointOpticsSpec extends OpticsSpec<Point> {
                  @ViaConstructor(parameterOrder = {"y", "x"})
                  Lens<Point, Integer> x();
              }
              """);

      assertThat(
              compileAgainstClassFile(
                  dir.resolve("plain"), List.of(), external("Point", POINT), swapped))
          .succeededWithoutWarnings();
      // Compiled with -g, the class file keeps them, and they hold the order as source does.
      assertThat(
              compileAgainstClassFile(
                  dir.resolve("debug"), List.of("-g"), external("Point", POINT), swapped))
          .hadErrorContaining(
              "'parameterOrder' passes 'source.y()' where 'Point(int x, int y)' takes 'x'.");
    }

    @Test
    @DisplayName("an order no constructor takes is refused, offering the order the names give")
    void orderNoConstructorTakesIsRefused() {
      var compilation =
          compile(
              external(
                  "Label",
                  """
                  public final class Label {
                      private final String id;
                      private final String tag;
                      public Label(String id, String tag) { this.id = id; this.tag = tag; }
                      public String id() { return id; }
                      public String tag() { return tag; }
                  }
                  """),
              external(
                  "Sign",
                  """
                  public final class Sign {
                      private final String id;
                      private final String tag;
                      public Sign(String first, String second) {
                          this.id = first;
                          this.tag = second;
                      }
                      public String id() { return id; }
                      public String tag() { return tag; }
                  }
                  """),
              spec(
                  "LabelOpticsSpec",
                  """
                  @ImportOptics
                  public interface LabelOpticsSpec extends OpticsSpec<Label> {
                      @ViaConstructor(parameterOrder = {"id"})
                      Lens<Label, String> id();
                  }
                  """),
              external(
                  "Pack",
                  """
                  public final class Pack {
                      private final String id;
                      public Pack(String id, String kind, String... tags) { this.id = id; }
                      public String id() { return id; }
                  }
                  """),
              spec(
                  "SignOpticsSpec",
                  """
                  @ImportOptics
                  public interface SignOpticsSpec extends OpticsSpec<Sign> {
                      @ViaConstructor(parameterOrder = {"id"})
                      Lens<Sign, String> id();
                  }
                  """),
              spec(
                  "LabelWideOpticsSpec",
                  """
                  @ImportOptics
                  public interface LabelWideOpticsSpec extends OpticsSpec<Label> {
                      // The order the names give, {"id", "tag"}, takes no CharSequence either, so it
                      // is not offered.
                      @ViaConstructor(parameterOrder = {"id"})
                      Lens<Label, CharSequence> id();
                  }
                  """),
              spec(
                  "PackOpticsSpec",
                  """
                  @ImportOptics
                  public interface PackOpticsSpec extends OpticsSpec<Pack> {
                      @ViaConstructor(parameterOrder = {"id"})
                      Lens<Pack, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No constructor of 'Label' takes (String), the arguments the"
                  + " generated lens rebuilds it with. The generated lens rebuilds through 'new"
                  + " Label(newValue)'. Constructors found: [Label(String id, String tag)]. Pass the"
                  + " arguments where the constructor's parameter names place them:"
                  + " @ViaConstructor(parameterOrder = {\"id\", \"tag\"}).");
      // Where no constructor's names give an order, the fix says what to write.
      assertThat(compilation)
          .hadErrorContaining(
              "Constructors found: [Sign(String first, String second)]. Name in"
                  + " @ViaConstructor's 'parameterOrder' one accessor of 'Sign' for each parameter"
                  + " of a constructor found, in the order it takes them, or rebuild 'Sign' with"
                  + " @Wither, @ViaBuilder or @ViaCopyAndSet.");
      // A variable-arity constructor is listed as its author wrote it; one argument is too few
      // for it, however many it takes after its fixed two.
      assertThat(compilation)
          .hadErrorContaining(
              "Constructors found: [Pack(String id, String kind, String... tags)].");
      assertThat(compilation)
          .hadErrorContaining(
              "No constructor of 'Label' takes (CharSequence), the arguments the generated lens"
                  + " rebuilds it with. The generated lens rebuilds through 'new Label(newValue)'."
                  + " Constructors found: [Label(String id, String tag)]. Name in"
                  + " @ViaConstructor's 'parameterOrder' one accessor of 'Label' for each parameter"
                  + " of a constructor found, in the order it takes them, or rebuild 'Label' with"
                  + " @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(4);
    }

    @Test
    @DisplayName("a focus no constructor takes is refused, offering the type its getter reads")
    void focusNoConstructorTakesIsRefused() {
      var compilation =
          compile(
              external(
                  "Count",
                  """
                  public final class Count {
                      private final int n;
                      public Count(int n) { this.n = n; }
                      public int n() { return n; }
                  }
                  """),
              external(
                  "Note",
                  """
                  public final class Note {
                      private final String text;
                      public Note(String text) { this.text = text; }
                      public String text() { return text; }
                  }
                  """),
              spec(
                  "CountOpticsSpec",
                  """
                  @ImportOptics
                  public interface CountOpticsSpec extends OpticsSpec<Count> {
                      @ViaConstructor(parameterOrder = {"n"})
                      Lens<Count, Number> n();
                  }
                  """),
              spec(
                  "NoteOpticsSpec",
                  """
                  @ImportOptics
                  public interface NoteOpticsSpec extends OpticsSpec<Note> {
                      // An order read from the names is held to the constructor in the same way.
                      @ViaConstructor
                      Lens<Note, CharSequence> text();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No constructor of 'Count' takes (Number), the arguments the"
                  + " generated lens rebuilds it with.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the lens's focus as 'Integer', the type 'n()' reads; otherwise rebuild 'Count'"
                  + " with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the lens's focus as 'String', the type 'text()' reads; otherwise rebuild 'Note'"
                  + " with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a type with no constructor the generated class can call is refused")
    void typeWithNoCallableConstructorIsRefused() {
      var compilation =
          compile(
              external(
                  "Code",
                  """
                  public final class Code {
                      private final String id;
                      private Code(String id) { this.id = id; }
                      public static Code of(String id) { return new Code(id); }
                      public String id() { return id; }
                  }
                  """),
              spec(
                  "CodeOpticsSpec",
                  """
                  @ImportOptics
                  public interface CodeOpticsSpec extends OpticsSpec<Code> {
                      @ViaConstructor(parameterOrder = {"id"})
                      Lens<Code, String> id();
                  }
                  """),
              spec(
                  "CodeReadOpticsSpec",
                  """
                  @ImportOptics
                  public interface CodeReadOpticsSpec extends OpticsSpec<Code> {
                      @ViaConstructor
                      Lens<Code, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'Code' declares no constructor the generated class in 'com.myapp' can call."
                  + " Rebuild 'Code' with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No 'parameterOrder' is written, and no constructor of 'Code'"
                  + " gives one by its parameter names.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a call javac cannot choose a constructor for is refused, boxing aside")
    void callJavacCannotChooseAConstructorForIsRefused() {
      // Each constructor takes the two Integers only by unboxing one of them, so neither is more
      // specific than the other: javac reports the call as ambiguous.
      var compilation =
          compile(
              external(
                  "Duo",
                  """
                  public final class Duo {
                      private final Integer a;
                      private final Integer b;
                      public Duo(int a, Integer b) { this.a = a; this.b = b; }
                      public Duo(Integer a, int b) { this.a = a; this.b = b; }
                      public Integer a() { return a; }
                      public Integer b() { return b; }
                  }
                  """),
              spec(
                  "DuoOpticsSpec",
                  """
                  @ImportOptics
                  public interface DuoOpticsSpec extends OpticsSpec<Duo> {
                      @ViaConstructor(parameterOrder = {"a", "b"})
                      Lens<Duo, Integer> a();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: The generated call 'new Duo(newValue, source.b())' cannot choose"
                  + " between 'Duo(int a, Integer b)' and 'Duo(Integer a, int b)'. Each of them"
                  + " takes (Integer, Integer), the arguments the generated lens rebuilds 'Duo'"
                  + " with, and none has parameters more specific than every other's. Rebuild 'Duo'"
                  + " with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName(
        "a call javac settles by inference is left to javac, and an array named after nothing"
            + " takes what the order passes")
    void callJavacSettlesByInferenceIsLeftToJavac() {
      var compilation =
          compile(
              external(
                  "Held",
                  """
                  public final class Held {
                      private final Object value;
                      private final String note;
                      public <V> Held(V value, String note) { this.value = value; this.note = note; }
                      public Object value() { return value; }
                      public String note() { return note; }
                  }
                  """),
              external(
                  "Words",
                  """
                  public final class Words {
                      private final String first;
                      private final String rest;
                      public Words(String... words) {
                          this.first = words[0];
                          this.rest = words[1];
                      }
                      public String first() { return first; }
                      public String rest() { return rest; }
                  }
                  """),
              spec(
                  "HeldOpticsSpec",
                  """
                  @ImportOptics
                  public interface HeldOpticsSpec extends OpticsSpec<Held> {
                      @ViaConstructor(parameterOrder = {"value", "note"})
                      Lens<Held, Object> value();
                  }
                  """),
              spec(
                  "WordsOpticsSpec",
                  """
                  @ImportOptics
                  public interface WordsOpticsSpec extends OpticsSpec<Words> {
                      @ViaConstructor(parameterOrder = {"first", "rest"})
                      Lens<Words, String> first();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("with no type to choose by, the names hold where one constructor alone could")
    void withNoTypeToChooseByTheNamesHoldWhereOneConstructorAloneCould() {
      var compilation =
          compile(
              external(
                  "Pair",
                  """
                  public final class Pair {
                      private final Integer x;
                      private final Integer y;
                      public Pair(Integer x, Integer y) { this.x = x; this.y = y; }
                      public Integer x() { return x; }
                      public Integer y() { return y; }
                  }
                  """),
              spec(
                  "PairOpticsSpec",
                  """
                  @ImportOptics
                  public interface PairOpticsSpec extends OpticsSpec<Pair> {
                      @ViaConstructor(parameterOrder = {"y", "x"})
                      Lens<Pair, ? extends Number> x();
                  }
                  """),
              spec(
                  "PairYOpticsSpec",
                  """
                  @ImportOptics
                  public interface PairYOpticsSpec extends OpticsSpec<Pair> {
                      @ViaConstructor(parameterOrder = {"x", "y"})
                      Lens<Pair, ? extends Number> y();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'parameterOrder' passes 'source.y()' where 'Pair(Integer x, Integer y)' takes"
                  + " 'x'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName(
        "with no type to choose by and more than one constructor that could, javac decides")
    void withNoTypeToChooseByAndSeveralConstructorsJavacDecides() {
      var compilation =
          compile(
              external(
                  "Twin",
                  """
                  public final class Twin {
                      private final Integer x;
                      private final Integer y;
                      public Twin(Integer x, Integer y) { this.x = x; this.y = y; }
                      public Twin(Integer x, String y) { this(x, Integer.valueOf(y)); }
                      public Integer x() { return x; }
                      public Integer y() { return y; }
                  }
                  """),
              external(
                  "Many",
                  """
                  public final class Many {
                      private final Integer x;
                      private final Integer y;
                      public Many(Integer x, Integer y) { this.x = x; this.y = y; }
                      public Many(Integer... xs) { this(xs[0], xs[1]); }
                      public Integer x() { return x; }
                      public Integer y() { return y; }
                  }
                  """),
              external(
                  "Bag",
                  """
                  public final class Bag {
                      private final String label;
                      private final Object item;
                      public Bag(String label, String item) { this.label = label; this.item = item; }
                      public String label() { return label; }
                      @SuppressWarnings("unchecked")
                      public <T> T item() { return (T) item; }
                  }
                  """),
              spec(
                  "TwinOpticsSpec",
                  """
                  @ImportOptics
                  public interface TwinOpticsSpec extends OpticsSpec<Twin> {
                      @ViaConstructor(parameterOrder = {"y", "x"})
                      Lens<Twin, ? extends Number> x();
                  }
                  """),
              spec(
                  "ManyOpticsSpec",
                  """
                  @ImportOptics
                  public interface ManyOpticsSpec extends OpticsSpec<Many> {
                      @ViaConstructor(parameterOrder = {"y", "x"})
                      Lens<Many, ? extends Number> x();
                  }
                  """),
              spec(
                  "BagOpticsSpec",
                  """
                  @ImportOptics
                  public interface BagOpticsSpec extends OpticsSpec<Bag> {
                      // 'item()' infers what it reads from the parameter it is passed to.
                      @ViaConstructor(parameterOrder = {"label", "item"})
                      Lens<Bag, String> label();
                  }
                  """));

      // Neither the swapped orders' constructors nor the inferred read is the checks' to settle.
      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName(
        "two constructors javac cannot order are refused, however one instantiation reads them")
    void twoConstructorsJavacCannotOrderAreRefused() {
      // No boxing enters the comparison: int is no subtype of Object, so neither X is more
      // specific; and a generic class's two constructors can take the same types under one
      // instantiation, which javac reports as ambiguous rather than settling.
      var compilation =
          compile(
              external(
                  "Mixed",
                  """
                  public final class Mixed {
                      private final Integer a;
                      private final int b;
                      public Mixed(int a, Object b) { this.a = a; this.b = 0; }
                      public Mixed(Object a, Object b) { this.a = 0; this.b = 0; }
                      public Integer a() { return a; }
                      public int b() { return b; }
                  }
                  """),
              external(
                  "Box",
                  """
                  public final class Box<T> {
                      private final Object content;
                      private final Object label;
                      public Box(T content, String label) { this.content = content; this.label = label; }
                      public Box(String content, T label) { this.content = content; this.label = label; }
                      public String content() { return String.valueOf(content); }
                      public String label() { return String.valueOf(label); }
                  }
                  """),
              spec(
                  "MixedOpticsSpec",
                  """
                  @ImportOptics
                  public interface MixedOpticsSpec extends OpticsSpec<Mixed> {
                      @ViaConstructor(parameterOrder = {"a", "b"})
                      Lens<Mixed, Integer> a();
                  }
                  """),
              spec(
                  "BoxOpticsSpec",
                  """
                  @ImportOptics
                  public interface BoxOpticsSpec extends OpticsSpec<Box<String>> {
                      @ViaConstructor(parameterOrder = {"content", "label"})
                      Lens<Box<String>, String> content();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "cannot choose between 'Mixed(int a, Object b)' and 'Mixed(Object a, Object b)'");
      assertThat(compilation)
          .hadErrorContaining(
              "cannot choose between 'Box(T content, String label)' and 'Box(String content, T"
                  + " label)'");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a parameter is read as the call reads it, and a raw read fits nowhere unchecked")
    void parameterIsReadAsTheCallReadsIt() {
      var compilation =
          compile(
              external(
                  "Held",
                  """
                  public final class Held<T> {
                      private final T value;
                      private final String note;
                      public <U extends T> Held(U value, String note) {
                          this.value = value;
                          this.note = note;
                      }
                      public T value() { return value; }
                      public String note() { return note; }
                  }
                  """),
              external(
                  "Grab",
                  """
                  public final class Grab {
                      private final String x;
                      private final Object y;
                      public Grab(String x, String y) { this.x = x; this.y = y; }
                      public String x() { return x; }
                      @SuppressWarnings("unchecked")
                      public <T> T y() { return (T) y; }
                  }
                  """),
              spec(
                  "HeldOpticsSpec",
                  """
                  @ImportOptics
                  public interface HeldOpticsSpec extends OpticsSpec<Held<String>> {
                      // The constructor's own U is read as its erasure, String here.
                      @ViaConstructor
                      Lens<Held<String>, String> note();
                  }
                  """),
              spec(
                  "GrabOpticsSpec",
                  """
                  @ImportOptics
                  public interface GrabOpticsSpec extends OpticsSpec<Grab> {
                      // 'y()' infers what it reads from the parameter it is passed to.
                      @ViaConstructor
                      Lens<Grab, String> x();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.HeldOptics", "new Held<String>(source.value(), newValue)");
      assertGeneratedCodeContains(
          compilation, "com.myapp.GrabOptics", "new Grab(newValue, source.y())");

      // A raw List passed where List<String> stands is an unchecked conversion, a warning in a
      // file the author cannot edit, so 'items()' names nothing the parameter takes.
      var raw =
          compile(
              external(
                  "Rows",
                  """
                  import java.util.List;
                  @SuppressWarnings("rawtypes")
                  public final class Rows {
                      private final List<String> items;
                      private final int n;
                      public Rows(List<String> items, int n) { this.items = items; this.n = n; }
                      public List items() { return items; }
                      public int n() { return n; }
                  }
                  """),
              spec(
                  "RowsOpticsSpec",
                  """
                  @ImportOptics
                  public interface RowsOpticsSpec extends OpticsSpec<Rows> {
                      @ViaConstructor
                      Lens<Rows, Integer> n();
                  }
                  """));

      assertThat(raw)
          .hadErrorContaining(
              "No 'parameterOrder' is written, and no constructor of 'Rows' gives one by its"
                  + " parameter names.");
      assertThat(raw).hadErrorCount(1);
    }

    @Test
    @DisplayName("each parameter named after an accessor is checked, whatever the others are named")
    void eachNamedParameterIsCheckedWhateverTheOthersAreNamed() {
      // 'text' names nothing 'Note3' reads, which leaves the swap of x and y to the other two.
      var compilation =
          compile(
              external(
                  "Note3",
                  """
                  public final class Note3 {
                      private final int x;
                      private final int y;
                      private final String label;
                      public Note3(int x, int y, String text) {
                          this.x = x;
                          this.y = y;
                          this.label = text;
                      }
                      public int x() { return x; }
                      public int y() { return y; }
                      public String label() { return label; }
                  }
                  """),
              spec(
                  "Note3OpticsSpec",
                  """
                  @ImportOptics
                  public interface Note3OpticsSpec extends OpticsSpec<Note3> {
                      @ViaConstructor(parameterOrder = {"y", "x", "label"})
                      Lens<Note3, Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'parameterOrder' passes 'source.y()' where 'Note3(int x, int y, String text)' takes"
                  + " 'x'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Pass the arguments where the constructor's parameter names place them:"
                  + " @ViaConstructor(parameterOrder = {\"x\", \"y\", \"label\"}).");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an abstract class or an interface is refused, having no constructor to call")
    void abstractClassOrInterfaceIsRefused() {
      var compilation =
          compile(
              external(
                  "Shape",
                  """
                  public abstract class Shape {
                      private final int sides;
                      protected Shape(int sides) { this.sides = sides; }
                      public int sides() { return sides; }
                  }
                  """),
              external(
                  "Named",
                  """
                  public interface Named {
                      String name();
                  }
                  """),
              spec(
                  "ShapeOpticsSpec",
                  """
                  @ImportOptics
                  public interface ShapeOpticsSpec extends OpticsSpec<Shape> {
                      @ViaConstructor
                      Lens<Shape, Integer> sides();
                  }
                  """),
              spec(
                  "NamedOpticsSpec",
                  """
                  @ImportOptics
                  public interface NamedOpticsSpec extends OpticsSpec<Named> {
                      @ViaConstructor(parameterOrder = {"name"})
                      Lens<Named, String> name();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'Shape' is an abstract class, and this strategy rebuilds it through"
                  + " a constructor. The generated set function calls 'new Shape(...)', which is not"
                  + " something that can be written for a type that cannot be instantiated. Name a"
                  + " concrete class as the spec's source type, or use @Wither, which rebuilds"
                  + " through a method and needs no constructor.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'Named' is an interface, and this strategy rebuilds it through a"
                  + " constructor.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("an accessor is read as the class implements it, not as an interface declares it")
    void accessorIsReadAsTheClassImplementsIt() {
      var compilation =
          compile(
              external(
                  "Pet",
                  """
                  public final class Pet extends PetBase implements Labelled {
                      private final int age;
                      public Pet(String name, int age) { super(name); this.age = age; }
                      public int age() { return age; }
                  }
                  """),
              external(
                  "PetBase",
                  """
                  public abstract class PetBase {
                      private final String name;
                      protected PetBase(String name) { this.name = name; }
                      public String name() { return name; }
                  }
                  """),
              external("Labelled", "public interface Labelled { CharSequence name(); }"),
              external(
                  "Toy",
                  """
                  public final class Toy extends ToyBase implements Tagged3 {
                      private final int size;
                      public Toy(String tag, int size) { super(tag); this.size = size; }
                      public int size() { return size; }
                  }
                  """),
              external(
                  "ToyBase",
                  """
                  public abstract class ToyBase {
                      private final String tag;
                      protected ToyBase(String tag) { this.tag = tag; }
                      public String tag() { return tag; }
                  }
                  """),
              external(
                  "Tagged3",
                  "public interface Tagged3 { default CharSequence tag() { return \"\"; } }"),
              spec(
                  "PetOpticsSpec",
                  """
                  @ImportOptics
                  public interface PetOpticsSpec extends OpticsSpec<Pet> {
                      @ViaConstructor
                      Lens<Pet, Integer> age();
                  }
                  """),
              spec(
                  "ToyOpticsSpec",
                  """
                  @ImportOptics
                  public interface ToyOpticsSpec extends OpticsSpec<Toy> {
                      @ViaConstructor(parameterOrder = {"tag", "size"})
                      Lens<Toy, Integer> size();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.PetOptics",
          "(source, newValue) -> new Pet(source.name(), newValue)");
    }

    @Test
    @DisplayName("a repaired order moves the written names about rather than repeating one")
    void repairedOrderMovesTheWrittenNamesAbout() {
      var compilation =
          compile(
              external(
                  "Nib",
                  """
                  public final class Nib {
                      private final int x;
                      private final int y;
                      public Nib(int x, int b) { this.x = x; this.y = b; }
                      public int x() { return x; }
                      public int y() { return y; }
                  }
                  """),
              external(
                  "Mop",
                  """
                  public final class Mop {
                      private final int x;
                      private final int y;
                      public Mop(int px, int y) { this.x = px; this.y = y; }
                      public int x() { return x; }
                      public int y() { return y; }
                  }
                  """),
              external(
                  "Badge",
                  """
                  public final class Badge {
                      private final String name;
                      private final int age;
                      public Badge(String name, int years) { this.name = name; this.age = years; }
                      public String name() { return name; }
                      public int age() { return age; }
                      public String email() { return name + "@example.com"; }
                  }
                  """),
              spec(
                  "BadgeOpticsSpec",
                  """
                  @ImportOptics
                  public interface BadgeOpticsSpec extends OpticsSpec<Badge> {
                      // 'email' names no parameter, so the lens's own name keeps the place
                      // 'years' leaves to the order.
                      @ViaConstructor(parameterOrder = {"email", "age"})
                      Lens<Badge, Integer> age();
                  }
                  """),
              spec(
                  "NibOpticsSpec",
                  """
                  @ImportOptics
                  public interface NibOpticsSpec extends OpticsSpec<Nib> {
                      @ViaConstructor(parameterOrder = {"y", "x"})
                      Lens<Nib, Integer> x();
                  }
                  """),
              spec(
                  "MopOpticsSpec",
                  """
                  @ImportOptics
                  public interface MopOpticsSpec extends OpticsSpec<Mop> {
                      @ViaConstructor(parameterOrder = {"y", "x"})
                      Lens<Mop, Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'parameterOrder' passes 'source.y()' where 'Nib(int x, int b)' takes 'x'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'parameterOrder' passes the new value where 'Mop(int px, int y)' takes 'y'.");
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> !error.getMessage(null).contains("'Badge"))
          .allMatch(
              error ->
                  error
                      .getMessage(null)
                      .contains(
                          "Pass the arguments where the constructor's parameter names place them:"
                              + " @ViaConstructor(parameterOrder = {\"x\", \"y\"})."));
      assertThat(compilation)
          .hadErrorContaining(
              "'parameterOrder' passes 'source.email()' where 'Badge(String name, int years)'"
                  + " takes 'name'.");
      assertThat(compilation)
          .hadErrorContaining("@ViaConstructor(parameterOrder = {\"name\", \"age\"}).");
      assertThat(compilation).hadErrorCount(3);
    }

    @Test
    @DisplayName("the focus the getter reads is offered only where the call would then bind")
    void focusTheGetterReadsIsOfferedOnlyWhereTheCallWouldThenBind() {
      var compilation =
          compile(
              external(
                  "Value",
                  """
                  public final class Value {
                      private final int v;
                      private final String s;
                      public Value(int v, String s) { this.v = v; this.s = s; }
                      public Value(Number v, String s) { this(v.intValue(), s); }
                      public int v() { return v; }
                      public String s() { return s; }
                  }
                  """),
              external(
                  "Duo",
                  """
                  public final class Duo {
                      private final Integer a;
                      private final Integer b;
                      public Duo(int a, Integer b) { this.a = a; this.b = b; }
                      public Duo(Integer a, int b) { this.a = a; this.b = b; }
                      public Integer a() { return a; }
                      public Integer b() { return b; }
                  }
                  """),
              spec(
                  "ValueOpticsSpec",
                  """
                  @ImportOptics
                  public interface ValueOpticsSpec extends OpticsSpec<Value> {
                      @ViaConstructor(parameterOrder = {"v", "s"})
                      Lens<Value, Object> v();
                  }
                  """),
              spec(
                  "DuoOpticsSpec",
                  """
                  @ImportOptics
                  public interface DuoOpticsSpec extends OpticsSpec<Duo> {
                      // Declared Integer, the call could still not choose a constructor.
                      @ViaConstructor(parameterOrder = {"a", "b"})
                      Lens<Duo, Number> a();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "No constructor of 'Value' takes (Object, String), the arguments the generated lens"
                  + " rebuilds it with.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the lens's focus as 'Integer', the type 'v()' reads; otherwise rebuild 'Value'"
                  + " with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains("'Duo'"))
          .singleElement()
          .matches(error -> !error.getMessage(null).contains("Declare the lens's focus"));
      assertThat(compilation).hadErrorCount(2);
    }
  }

  @Nested
  @DisplayName("@ViaConstructor Order Read From Parameter Names")
  class ViaConstructorOrderReadFromParameterNames {

    @Test
    @DisplayName("an order left unwritten is read from the constructor's parameter names")
    void orderLeftUnwrittenIsReadFromTheParameterNames() {
      var compilation =
          compile(
              external(
                  "Point",
                  """
                  public final class Point {
                      private final int x;
                      private final int y;
                      public Point(int x, int y) { this.x = x; this.y = y; }
                      public int getX() { return x; }
                      public int getY() { return y; }
                  }
                  """),
              external(
                  "Money",
                  """
                  public final class Money {
                      private final long cents;
                      private final String owner;
                      public Money(long cents, String owner) { this.cents = cents; this.owner = owner; }
                      public Money(Number cents, String owner) { this(-1L, owner); }
                      public long cents() { return cents; }
                      public String owner() { return owner; }
                  }
                  """),
              spec(
                  "PointOpticsSpec",
                  """
                  @ImportOptics
                  public interface PointOpticsSpec extends OpticsSpec<Point> {
                      @ViaConstructor
                      Lens<Point, Integer> getX();

                      @ViaConstructor
                      Lens<Point, Integer> getY();
                  }
                  """),
              spec(
                  "MoneyOpticsSpec",
                  """
                  @ImportOptics
                  public interface MoneyOpticsSpec extends OpticsSpec<Money> {
                      // Both constructors give one order, so there is one to read.
                      @ViaConstructor
                      Lens<Money, Long> cents();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.PointOptics",
          "(source, newValue) -> new Point(newValue, source.getY())");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.PointOptics",
          "(source, newValue) -> new Point(source.getX(), newValue)");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.MoneyOptics",
          "(source, newValue) -> new Money((long) newValue, source.owner())");
    }

    @Test
    @DisplayName("a lens no constructor takes a parameter for is refused, whatever its order")
    void lensNoConstructorTakesAParameterForIsRefused() {
      String sum =
          """
          public final class Sum {
              private final int x;
              private final int y;
              public Sum(int x, int y) { this.x = x; this.y = y; }
              public int x() { return x; }
              public int y() { return y; }
              public int total() { return x + y; }
          }
          """;
      var compilation =
          compile(
              external("Sum", sum),
              spec(
                  "SumOpticsSpec",
                  """
                  @ImportOptics
                  public interface SumOpticsSpec extends OpticsSpec<Sum> {
                      @ViaConstructor
                      Lens<Sum, Integer> total();
                  }
                  """),
              spec(
                  "SumPairOpticsSpec",
                  """
                  @ImportOptics
                  public interface SumPairOpticsSpec extends OpticsSpec<Sum> {
                      @ViaConstructor(parameterOrder = {"x", "y"})
                      Lens<Sum, Integer> total();
                  }
                  """),
              spec(
                  "SumAloneOpticsSpec",
                  """
                  @ImportOptics
                  public interface SumAloneOpticsSpec extends OpticsSpec<Sum> {
                      @ViaConstructor(parameterOrder = {"total"})
                      Lens<Sum, Integer> total();
                  }
                  """));

      String cannotSet =
          "@ViaConstructor cannot set 'total()' on a 'Sum', since no constructor takes a parameter"
              + " named after it: rebuild 'Sum' with @Wither, @ViaBuilder or @ViaCopyAndSet.";
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No constructor of 'Sum' takes a parameter named after the lens's own"
                  + " 'total()'. With no 'parameterOrder' written, the generated lens reads its order"
                  + " from a constructor's parameter names, and each parameter of every constructor"
                  + " is named after another accessor. Constructors found: [Sum(int x, int y)]. "
                  + cannotSet);
      // Neither an order that leaves the lens out nor one too short for it leads anywhere else.
      assertThat(compilation)
          .hadErrorContaining("'parameterOrder' names no argument for the lens's own 'total'.");
      assertThat(compilation).hadErrorContaining("No constructor of 'Sum' takes (Integer)");
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains(cannotSet))
          .hasSize(3);
      assertThat(compilation).hadErrorCount(3);
    }

    @Test
    @DisplayName(
        "no constructor whose names give an order is refused, saying why where names are lost")
    void noConstructorWhoseNamesGiveAnOrderIsRefused(@TempDir Path dir) throws IOException {
      String sign =
          """
          public final class Sign {
              private final String id;
              public Sign(String first) { this.id = first; }
              public String id() { return id; }
          }
          """;
      JavaFileObject signSpec =
          spec(
              "SignOpticsSpec",
              """
              @ImportOptics
              public interface SignOpticsSpec extends OpticsSpec<Sign> {
                  @ViaConstructor
                  Lens<Sign, String> id();
              }
              """);
      var compilation = compile(external("Sign", sign), signSpec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No 'parameterOrder' is written, and no constructor of 'Sign' gives"
                  + " one by its parameter names. With no order written, the generated lens takes"
                  + " it from a constructor each of whose parameters is named after an accessor of"
                  + " 'Sign' that reads a value it takes, as 'name()', 'getName()' or 'isName()' for"
                  + " a parameter 'name', and one of them the lens's own 'id()'. Constructors found:"
                  + " [Sign(String first)]. Name in @ViaConstructor's 'parameterOrder' the"
                  + " accessors 'Sign' declares, in the order its constructor takes them, or rebuild"
                  + " 'Sign' with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);

      // Read from a class file that keeps no names, the reason says why there are none.
      var unnamed =
          compileAgainstClassFile(
              dir.resolve("plain"),
              List.of(),
              external(
                  "Pt",
                  """
                  public final class Pt {
                      private final int x;
                      public Pt(int x) { this.x = x; }
                      public int x() { return x; }
                  }
                  """),
              spec(
                  "PtOpticsSpec",
                  """
                  @ImportOptics
                  public interface PtOpticsSpec extends OpticsSpec<Pt> {
                      @ViaConstructor
                      Lens<Pt, Integer> x();
                  }
                  """));
      assertThat(unnamed)
          .hadErrorContaining(
              "Constructors found: [Pt(int arg0)]. A class compiled with neither -parameters nor"
                  + " -g keeps no parameter names, and javac reads them as 'arg0', 'arg1'.");
      // A class file that kept its names draws no such reason, however little they name.
      var named =
          compileAgainstClassFile(
              dir.resolve("debug"), List.of("-g"), external("Sign", sign), signSpec);
      assertThat(named).hadErrorContaining("Constructors found: [Sign(String first)].");
      Assertions.assertThat(named.errors())
          .noneMatch(error -> error.getMessage(null).contains("keeps no parameter names"));
    }

    @Test
    @DisplayName("an order read from the names has to bind a constructor it was read from")
    void orderReadFromTheNamesHasToBindAConstructorItWasReadFrom() {
      // The names of the first constructor give {"x", "y"}; the call that order writes binds the
      // second, which swaps them, and would break the lens silently.
      var compilation =
          compile(
              external(
                  "Swap",
                  """
                  public final class Swap {
                      private final int x;
                      private final int y;
                      public Swap(Object x, Object y) { this((int) (Integer) y, (int) (Integer) x); }
                      public Swap(int first, int second) { this.y = first; this.x = second; }
                      public int x() { return x; }
                      public int y() { return y; }
                  }
                  """),
              external(
                  "Trio",
                  """
                  public final class Trio {
                      private final int x;
                      private final int y;
                      private final int z;
                      public Trio(Object x, Object y) { this((int) (Integer) x, (int) (Integer) y, 0); }
                      public Trio(int y, int z) { this(0, y, z); }
                      private Trio(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
                      public int x() { return x; }
                      public int y() { return y; }
                      public int z() { return z; }
                  }
                  """),
              spec(
                  "SwapOpticsSpec",
                  """
                  @ImportOptics
                  public interface SwapOpticsSpec extends OpticsSpec<Swap> {
                      @ViaConstructor
                      Lens<Swap, Integer> x();
                  }
                  """),
              spec(
                  "TrioOpticsSpec",
                  """
                  @ImportOptics
                  public interface TrioOpticsSpec extends OpticsSpec<Trio> {
                      // 'Trio(int y, int z)' is named after accessors too, just not the lens's.
                      @ViaConstructor
                      Lens<Trio, Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: The order read from the parameter names, {\"x\", \"y\"}, binds"
                  + " 'Swap(int first, int second)', not a constructor it was read from. No"
                  + " 'parameterOrder' is written, so the generated lens rebuilds through 'new"
                  + " Swap((int) newValue, source.y())', and javac binds that call to 'Swap(int"
                  + " first, int second)' rather than to a constructor whose parameters the order"
                  + " follows. Write the order 'Swap(int first, int second)' takes its arguments in"
                  + " as @ViaConstructor's 'parameterOrder', or rebuild 'Swap' with @Wither,"
                  + " @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation)
          .hadErrorContaining(
              "The order read from the parameter names, {\"x\", \"y\"}, binds 'Trio(int y, int"
                  + " z)', not a constructor it was read from.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName(
        "constructors whose names give different orders are refused, offering the one that passes"
            + " every value")
    void constructorsWhoseNamesGiveDifferentOrdersAreRefused() {
      var compilation =
          compile(
              external(
                  "Cube",
                  """
                  public final class Cube {
                      private final int x;
                      private final int y;
                      private final int z;
                      public Cube(int x, int y) { this(x, y, 0); }
                      public Cube(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
                      public int x() { return x; }
                      public int y() { return y; }
                      public int z() { return z; }
                  }
                  """),
              spec(
                  "CubeOpticsSpec",
                  """
                  @ImportOptics
                  public interface CubeOpticsSpec extends OpticsSpec<Cube> {
                      @ViaConstructor
                      Lens<Cube, Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No 'parameterOrder' is written, and more than one constructor of"
                  + " 'Cube' gives one by its parameter names. With no order written, the"
                  + " generated lens takes it from the one constructor each of whose parameters is"
                  + " named after an accessor that reads a value it takes, and here their orders"
                  + " differ, so the one to rebuild through has to be written. Write the one the"
                  + " lens rebuilds through: @ViaConstructor(parameterOrder = {\"x\", \"y\","
                  + " \"z\"}).");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an order read from a shorter constructor is refused where a longer one exists")
    void orderReadFromAShorterConstructorIsRefused() {
      var compilation =
          compile(
              external(
                  "Owner",
                  """
                  public final class Owner {
                      private final String name;
                      private final int age;
                      private final String nickname;
                      public Owner(String name, int age) { this(name, age, ""); }
                      public Owner(String name, int age, String nick) {
                          this.name = name;
                          this.age = age;
                          this.nickname = nick;
                      }
                      public String name() { return name; }
                      public int age() { return age; }
                      public String nickname() { return nickname; }
                  }
                  """),
              spec(
                  "OwnerOpticsSpec",
                  """
                  @ImportOptics
                  public interface OwnerOpticsSpec extends OpticsSpec<Owner> {
                      @ViaConstructor
                      Lens<Owner, Integer> age();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No 'parameterOrder' is written, and the order the parameter names"
                  + " give, {\"name\", \"age\"}, leaves out what 'Owner(String name, int age,"
                  + " String nick)' takes. Read from the names, the order rebuilds through a"
                  + " constructor of 2 parameters, and 'Owner(String name, int age, String nick)'"
                  + " takes more, so a value only it takes may come back reset after every set."
                  + " Write the order of the constructor the lens should rebuild through as"
                  + " @ViaConstructor's 'parameterOrder'; written out, {\"name\", \"age\"}"
                  + " rebuilds through the shorter one as it is. Otherwise rebuild 'Owner' with"
                  + " @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a record's canonical constructor is read by its components' names")
    void recordsCanonicalConstructorIsReadByItsComponentsNames(@TempDir Path dir)
        throws IOException {
      // A class file can drop the names of a compact constructor's parameters; its components
      // keep theirs. A constructor of the same length taking other types is not the canonical one.
      var fromClassFile =
          compileAgainstClassFile(
              dir,
              List.of("-parameters"),
              external(
                  "Span2",
                  """
                  public record Span2(int from, int to) {
                      public Span2 {
                          if (to < from) {
                              throw new IllegalArgumentException("backwards");
                          }
                      }
                  }
                  """),
              spec(
                  "Span2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Span2OpticsSpec extends OpticsSpec<Span2> {
                      @ViaConstructor
                      Lens<Span2, Integer> to();
                  }
                  """));
      assertThat(fromClassFile).succeededWithoutWarnings();

      var reordered =
          compile(
              external(
                  "Pair2",
                  """
                  public record Pair2(String name, int age) {
                      public Pair2(int age, String name) { this(name, age); }
                  }
                  """),
              spec(
                  "Pair2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Pair2OpticsSpec extends OpticsSpec<Pair2> {
                      @ViaConstructor
                      Lens<Pair2, String> name();
                  }
                  """));
      assertThat(reordered)
          .hadErrorContaining(
              "No 'parameterOrder' is written, and more than one constructor of 'Pair2' gives one"
                  + " by its parameter names.");
    }

    @Test
    @DisplayName(
        "a wildcard focus the call casts to a primitive is held to the constructor it binds")
    void wildcardFocusTheCallCastsIsHeldToTheConstructorItBinds() {
      var compilation =
          compile(
              external(
                  "Grid",
                  """
                  public final class Grid {
                      private final long x;
                      private final long y;
                      public Grid(long x, long y) { this.x = x; this.y = y; }
                      public Grid(int b, int a) { this((long) a, (long) b); }
                      public int x() { return (int) x; }
                      public int y() { return (int) y; }
                  }
                  """),
              spec(
                  "GridOpticsSpec",
                  """
                  @ImportOptics
                  public interface GridOpticsSpec extends OpticsSpec<Grid> {
                      @ViaConstructor
                      Lens<Grid, ? extends Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "The order read from the parameter names, {\"x\", \"y\"}, binds 'Grid(int b, int"
                  + " a)', not a constructor it was read from.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an order no constructor follows offers no order, only another strategy")
    void orderNoConstructorFollowsOffersOnlyAnotherStrategy() {
      // 'Trio(Object x, Object y)' names the order {"x", "y"}, but the call that order writes binds
      // 'Trio(int y, int z)', so no order is offered to paste.
      String trio =
          """
          public final class Trio {
              private final int x;
              private final int y;
              private final int z;
              public Trio(Object x, Object y) { this((int) (Integer) x, (int) (Integer) y, 0); }
              public Trio(int y, int z) { this(0, y, z); }
              private Trio(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
              public int x() { return x; }
              public int y() { return y; }
              public int z() { return z; }
          }
          """;
      var compilation =
          compile(
              external("Trio", trio),
              spec(
                  "TrioPairOpticsSpec",
                  """
                  @ImportOptics
                  public interface TrioPairOpticsSpec extends OpticsSpec<Trio> {
                      @ViaConstructor(parameterOrder = {"x", "y"})
                      Lens<Trio, Integer> x();
                  }
                  """),
              spec(
                  "TrioOtherOpticsSpec",
                  """
                  @ImportOptics
                  public interface TrioOtherOpticsSpec extends OpticsSpec<Trio> {
                      @ViaConstructor(parameterOrder = {"y", "z"})
                      Lens<Trio, Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'Trio(int y, int z)' takes no parameter named after the lens's own 'x()'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Add 'x' to @ViaConstructor's 'parameterOrder', at the place the constructor takes"
                  + " it, or rebuild 'Trio' with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("parameterOrder = {"));
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("of several orders the names give, only those the call would follow are offered")
    void ofSeveralOrdersOnlyThoseTheCallWouldFollowAreOffered() {
      // The first order binds the second constructor, whose names it crosses, so it is not
      // offered; where neither order would bind, another way is.
      var compilation =
          compile(
              external(
                  "Quad",
                  """
                  public final class Quad {
                      private final String z;
                      private final int x;
                      private final int y;
                      public Quad(String z, int x, Number y) { this(z, y.intValue(), x); }
                      public Quad(Object z, int y, int x) {
                          this.z = String.valueOf(z);
                          this.x = x;
                          this.y = y;
                      }
                      public String z() { return z; }
                      public int x() { return x; }
                      public int y() { return y; }
                  }
                  """),
              external(
                  "Fork",
                  """
                  public final class Fork {
                      private final Integer a;
                      private final Long b;
                      public Fork(Integer a, Long b) { this.a = a; this.b = b; }
                      public Fork(Long b, Integer a) { this(a, b); }
                      public Integer a() { return a; }
                      public Long b() { return b; }
                  }
                  """),
              external(
                  "Nib2",
                  """
                  public final class Nib2 {
                      private final String a;
                      private final String b;
                      public Nib2(Object a, String b) { this.a = (String) a; this.b = b; }
                      public Nib2(String b, Object a) { this.a = (String) a; this.b = b; }
                      public String a() { return a; }
                      public String b() { return b; }
                  }
                  """),
              external(
                  "Nib3",
                  """
                  public final class Nib3 {
                      private final String a;
                      private final String b;
                      public Nib3(CharSequence a, String b) { this.a = a.toString(); this.b = b; }
                      public Nib3(String b, CharSequence a) { this.a = a.toString(); this.b = b; }
                      public String a() { return a; }
                      public String b() { return b; }
                  }
                  """),
              spec(
                  "Nib3OpticsSpec",
                  """
                  @ImportOptics
                  public interface Nib3OpticsSpec extends OpticsSpec<Nib3> {
                      // Declared String, the focus would leave every order ambiguous all the same.
                      @ViaConstructor
                      Lens<Nib3, Object> a();
                  }
                  """),
              spec(
                  "Nib2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Nib2OpticsSpec extends OpticsSpec<Nib2> {
                      @ViaConstructor
                      Lens<Nib2, String> a();
                  }
                  """),
              spec(
                  "QuadOpticsSpec",
                  """
                  @ImportOptics
                  public interface QuadOpticsSpec extends OpticsSpec<Quad> {
                      @ViaConstructor
                      Lens<Quad, Integer> y();
                  }
                  """),
              spec(
                  "ForkOpticsSpec",
                  """
                  @ImportOptics
                  public interface ForkOpticsSpec extends OpticsSpec<Fork> {
                      @ViaConstructor
                      Lens<Fork, Number> a();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Write the one the lens rebuilds through:"
                  + " @ViaConstructor(parameterOrder = {\"z\", \"y\", \"x\"}).");
      assertThat(compilation)
          .hadErrorContaining(
              "more than one constructor of 'Fork' gives one by its parameter names. With no order"
                  + " written, the generated lens takes it from the one constructor each of whose"
                  + " parameters is named after an accessor that reads a value it takes, and here"
                  + " their orders differ, so the one to rebuild through has to be written. Declare"
                  + " the lens's focus as 'Integer', the type 'a()' reads; otherwise rebuild 'Fork'"
                  + " with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      // Where every order is ambiguous whatever the focus, only writing one or rebuilding is left.
      assertThat(compilation)
          .hadErrorContaining(
              "more than one constructor of 'Nib2' gives one by its parameter names. With no order"
                  + " written, the generated lens takes it from the one constructor each of whose"
                  + " parameters is named after an accessor that reads a value it takes, and here"
                  + " their orders differ, so the one to rebuild through has to be written. Write"
                  + " the order of the constructor the lens should rebuild through as"
                  + " @ViaConstructor's 'parameterOrder', or rebuild 'Nib2' with @Wither,"
                  + " @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation)
          .hadErrorContaining(
              "Write the order of the constructor the lens should rebuild through as"
                  + " @ViaConstructor's 'parameterOrder', or rebuild 'Nib3' with @Wither,"
                  + " @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(4);
    }

    @Test
    @DisplayName("a record's canonical order is read whatever longer constructors it declares")
    void recordsCanonicalOrderIsReadWhateverLongerConstructorsItDeclares() {
      var compilation =
          compile(
              external(
                  "Bounds",
                  """
                  public record Bounds(int lo, int hi) {
                      public Bounds(int lo, int hi, boolean inclusive) {
                          this(lo, inclusive ? hi : hi - 1);
                      }
                  }
                  """),
              spec(
                  "BoundsOpticsSpec",
                  """
                  @ImportOptics
                  public interface BoundsOpticsSpec extends OpticsSpec<Bounds> {
                      @ViaConstructor
                      Lens<Bounds, Integer> lo();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.BoundsOptics",
          "(source, newValue) -> new Bounds(newValue, source.hi())");
      // An order read from a shorter constructor is still held to the canonical one.
      var shorter =
          compile(
              external(
                  "Temp",
                  """
                  public record Temp(int celsius, String unit) {
                      public Temp(int fahrenheit) { this((fahrenheit - 32) * 5 / 9, "C"); }
                      public int fahrenheit() { return celsius * 9 / 5 + 32; }
                  }
                  """),
              spec(
                  "TempOpticsSpec",
                  """
                  @ImportOptics
                  public interface TempOpticsSpec extends OpticsSpec<Temp> {
                      @ViaConstructor
                      Lens<Temp, Integer> fahrenheit();
                  }
                  """));
      assertThat(shorter)
          .hadErrorContaining(
              "the order the parameter names give, {\"fahrenheit\"}, leaves out what"
                  + " 'Temp(int celsius, String unit)' takes.");
    }
  }

  @Nested
  @DisplayName("@ViaConstructor Shorter Constructor")
  class ViaConstructorShorterConstructor {

    /** A fee whose shorter constructor defaults the currency. */
    private static final String FEE =
        """
        public final class Fee {
            private final long amount;
            private final String currency;
            public Fee(long amount, String currency) {
                this.amount = amount;
                this.currency = currency;
            }
            public Fee(long amount) { this(amount, "GBP"); }
            public long amount() { return amount; }
            public String currency() { return currency; }
        }
        """;

    /** A record whose shorter constructor works out the area its canonical one takes. */
    private static final String RECT =
        """
        public record Rect(int w, int h, int area) {
            public Rect(int w, int h) { this(w, h, w * h); }
        }
        """;

    @Test
    @DisplayName("a written order through a shorter constructor warns, naming the value it resets")
    void writtenOrderThroughAShorterConstructorWarns() {
      var compilation =
          compileReporting(
              external("Fee", FEE),
              spec(
                  "FeeOpticsSpec",
                  """
                  @ImportOptics
                  public interface FeeOpticsSpec extends OpticsSpec<Fee> {
                      @ViaConstructor(parameterOrder = {"amount"})
                      Lens<Fee, Long> amount();
                  }
                  """));

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadWarningContaining(
              "@ViaConstructor: 'parameterOrder' {\"amount\"} passes nothing for 'currency()',"
                  + " which 'Fee(long amount, String currency)' takes. The generated lens rebuilds"
                  + " through 'new Fee(newValue)', so after every set 'currency()' reads what that"
                  + " constructor gives it, not what the source held, which breaks the lens laws"
                  + " unless the constructor derives it from what it is passed. Pass it:"
                  + " @ViaConstructor(parameterOrder = {\"amount\", \"currency\"}). If the"
                  + " constructor derives it on purpose, annotate 'amount()'"
                  + " @SuppressWarnings(\"shorter-constructor\").");
      assertThat(compilation).hadWarningCount(1);
      // A warning, so the lens is still written, through the constructor the order names.
      assertGeneratedCodeContains(
          compilation, "com.myapp.FeeOptics", "(source, newValue) -> new Fee(newValue)");
    }

    @Test
    @DisplayName("a record names each component its shorter constructor leaves out")
    void recordNamesEachComponentItsShorterConstructorLeavesOut() {
      var compilation =
          compileReporting(
              external(
                  "Box",
                  """
                  public record Box(int w, int h, String label) {
                      public Box(int w) { this(w, 1, "box"); }
                  }
                  """),
              spec(
                  "BoxOpticsSpec",
                  """
                  @ImportOptics
                  public interface BoxOpticsSpec extends OpticsSpec<Box> {
                      @ViaConstructor(parameterOrder = {"w"})
                      Lens<Box, Integer> w();
                  }
                  """));

      assertThat(compilation)
          .hadWarningContaining(
              "@ViaConstructor: 'parameterOrder' {\"w\"} passes nothing for 'h()' and 'label()',"
                  + " which 'Box(int w, int h, String label)' takes. The generated lens rebuilds"
                  + " through 'new Box(newValue)', so after every set 'h()' and 'label()' read what"
                  + " that constructor gives them, not what the source held, which breaks the lens"
                  + " laws unless the constructor derives them from what it is passed. Pass them:"
                  + " @ViaConstructor(parameterOrder = {\"w\", \"h\", \"label\"}). If the"
                  + " constructor derives them on purpose, annotate 'w()'"
                  + " @SuppressWarnings(\"shorter-constructor\").");
      assertThat(compilation).hadWarningCount(1);
    }

    @Test
    @DisplayName("of several longer constructors, the longest names what the order leaves out")
    void ofSeveralLongerConstructorsTheLongestNamesWhatTheOrderLeavesOut() {
      var compilation =
          compileReporting(
              external(
                  "Point3",
                  """
                  public final class Point3 {
                      private final int x;
                      private final int y;
                      private final int z;
                      public Point3(int x) { this(x, 0, 0); }
                      public Point3(int x, int y) { this(x, y, 0); }
                      public Point3(int x, int y, int z) {
                          this.x = x;
                          this.y = y;
                          this.z = z;
                      }
                      public int x() { return x; }
                      public int y() { return y; }
                      public int z() { return z; }
                  }
                  """),
              spec(
                  "Point3OpticsSpec",
                  """
                  @ImportOptics
                  public interface Point3OpticsSpec extends OpticsSpec<Point3> {
                      @ViaConstructor(parameterOrder = {"x"})
                      Lens<Point3, Integer> x();
                  }
                  """));

      assertThat(compilation)
          .hadWarningContaining(
              "@ViaConstructor: 'parameterOrder' {\"x\"} passes nothing for 'y()' and 'z()', which"
                  + " 'Point3(int x, int y, int z)' takes.");
      assertThat(compilation)
          .hadWarningContaining(
              "Pass them: @ViaConstructor(parameterOrder = {\"x\", \"y\", \"z\"}).");
      assertThat(compilation).hadWarningCount(1);
    }

    @Test
    @DisplayName(
        "orders read from the names that no constructor takes under the focus are not offered")
    void ordersReadFromTheNamesThatNoConstructorTakesUnderTheFocusAreNotOffered() {
      var compilation =
          compile(
              external(
                  "Cube",
                  """
                  public final class Cube {
                      private final int x;
                      private final int y;
                      private final int z;
                      public Cube(int x, int y) { this(x, y, 0); }
                      public Cube(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
                      public Cube(int... coordinates) { this(coordinates[0], coordinates[1]); }
                      public int x() { return x; }
                      public int y() { return y; }
                      public int z() { return z; }
                  }
                  """),
              spec(
                  "CubeOpticsSpec",
                  """
                  @ImportOptics
                  public interface CubeOpticsSpec extends OpticsSpec<Cube> {
                      // A Number is never cast to int, so neither order binds a constructor: each
                      // call falls to the variable-arity one, whose array takes no Number.
                      @ViaConstructor
                      Lens<Cube, Number> x();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No 'parameterOrder' is written, and more than one constructor of"
                  + " 'Cube' gives one by its parameter names.");
      assertThat(compilation).hadErrorCount(1);
      Assertions.assertThat(compilation.errors().getFirst().getMessage(null))
          .doesNotContain("@ViaConstructor(parameterOrder =");
    }

    @Test
    @DisplayName(
        "values two longer constructors each take are named, and no order is offered for them")
    void valuesTwoLongerConstructorsEachTakeAreNamed() {
      var compilation =
          compileReporting(
              external(
                  "Pin",
                  """
                  public final class Pin {
                      private final String label;
                      private final String note;
                      public Pin(String label, String note) { this.label = label; this.note = note; }
                      public Pin(String label, String... parts) { this(label, ""); }
                      public String label() { return label; }
                      public String note() { return note; }
                      public String[] parts() { return new String[0]; }
                  }
                  """),
              spec(
                  "PinOpticsSpec",
                  """
                  @ImportOptics
                  public interface PinOpticsSpec extends OpticsSpec<Pin> {
                      @ViaConstructor(parameterOrder = {"label"})
                      Lens<Pin, String> label();
                  }
                  """));

      // {"label", "note"} resets 'parts()', and {"label", "parts"} resets 'note()': no
      // constructor takes both, so no order is offered.
      assertThat(compilation)
          .hadWarningContaining(
              "@ViaConstructor: 'parameterOrder' {\"label\"} passes nothing for 'note()' and"
                  + " 'parts()', which 'Pin(String label, String note)' and 'Pin(String label,"
                  + " String... parts)' take.");
      assertThat(compilation)
          .hadWarningContaining(
              "No one constructor takes 'note()' and 'parts()', so any order leaves one out. If"
                  + " the constructor the order rebuilds through derives them on purpose, annotate"
                  + " 'label()' @SuppressWarnings(\"shorter-constructor\"); otherwise rebuild 'Pin'"
                  + " with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadWarningCount(1);
    }

    @Test
    @DisplayName("elements spread into an array count as one place against a longer constructor")
    void elementsSpreadIntoAnArrayCountAsOnePlaceAgainstALongerConstructor() {
      var compilation =
          compileReporting(
              external(
                  "Line",
                  """
                  public final class Line {
                      private final String label;
                      private final String note;
                      private final String extra;
                      public Line(String label, String... rest) { this(label, "", ""); }
                      public Line(String label, String note, String extra) {
                          this.label = label;
                          this.note = note;
                          this.extra = extra;
                      }
                      public String label() { return label; }
                      public String note() { return note; }
                      public String extra() { return extra; }
                      public String x() { return ""; }
                  }
                  """),
              spec(
                  "LineOpticsSpec",
                  """
                  @ImportOptics
                  public interface LineOpticsSpec extends OpticsSpec<Line> {
                      @ViaConstructor(parameterOrder = {"label", "x", "x", "x"})
                      Lens<Line, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadWarningContaining(
              "@ViaConstructor: 'parameterOrder' {\"label\", \"x\", \"x\", \"x\"} passes nothing"
                  + " for 'note()' and 'extra()', which 'Line(String label, String note, String"
                  + " extra)' takes.");
      assertThat(compilation).hadWarningCount(1);
    }

    @Test
    @DisplayName("an order is offered where one constructor takes every value the others take")
    void orderIsOfferedWhereOneConstructorTakesEveryValueTheOthersTake() {
      var compilation =
          compileReporting(
              external(
                  "Knot",
                  """
                  public final class Knot {
                      private final int x;
                      private final int y;
                      private final int z;
                      public Knot(int x) { this(x, 0, 0); }
                      public Knot(int x, int y) { this(x, y, 0); }
                      public Knot(int x, long z) { this(x, 0, (int) z); }
                      public Knot(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
                      public int x() { return x; }
                      public int y() { return y; }
                      public int z() { return z; }
                  }
                  """),
              spec(
                  "KnotOpticsSpec",
                  """
                  @ImportOptics
                  public interface KnotOpticsSpec extends OpticsSpec<Knot> {
                      @ViaConstructor(parameterOrder = {"x"})
                      Lens<Knot, Integer> x();
                  }
                  """));

      // The three-parameter constructor names both, so the two shorter ones add nothing to it.
      assertThat(compilation)
          .hadWarningContaining(
              "@ViaConstructor: 'parameterOrder' {\"x\"} passes nothing for 'y()' and 'z()', which"
                  + " 'Knot(int x, int y, int z)' takes.");
      assertThat(compilation)
          .hadWarningContaining(
              "Pass them: @ViaConstructor(parameterOrder = {\"x\", \"y\", \"z\"}).");
      assertThat(compilation).hadWarningCount(1);
    }

    @Test
    @DisplayName("a longer constructor that takes no parameter for the lens may be another view")
    void longerConstructorThatTakesNoParameterForTheLensMayBeAnotherView() {
      var compilation =
          compileReporting(
              external(
                  "Price",
                  """
                  public final class Price {
                      private final long cents;
                      public Price(long cents) { this.cents = cents; }
                      public Price(long pounds, int pence) { this(pounds * 100 + pence); }
                      public long cents() { return cents; }
                      public long pounds() { return cents / 100; }
                      public int pence() { return (int) (cents % 100); }
                  }
                  """),
              spec(
                  "PriceOpticsSpec",
                  """
                  @ImportOptics
                  public interface PriceOpticsSpec extends OpticsSpec<Price> {
                      @ViaConstructor(parameterOrder = {"cents"})
                      Lens<Price, Long> cents();
                  }
                  """));

      assertThat(compilation)
          .hadWarningContaining(
              "No order passes them with the new value, since 'Price(long pounds, int pence)'"
                  + " takes no parameter named after 'cents()'. Where that is the same value read"
                  + " another way, annotate 'cents()' @SuppressWarnings(\"shorter-constructor\");"
                  + " otherwise rebuild 'Price' with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadWarningCount(1);
    }

    @Test
    @DisplayName("a variable-arity constructor passed nothing for its array warns of the array")
    void variableArityConstructorPassedNothingForItsArrayWarns() {
      var compilation =
          compileReporting(
              external("Tag", TAG),
              spec(
                  "TagOpticsSpec",
                  """
                  @ImportOptics
                  public interface TagOpticsSpec extends OpticsSpec<Tag> {
                      @ViaConstructor(parameterOrder = {"label", "note"})
                      Lens<Tag, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadWarningContaining(
              "@ViaConstructor: 'parameterOrder' {\"label\", \"note\"} passes nothing for"
                  + " 'parts()', which 'Tag(String label, String note, String... parts)' takes.");
      assertThat(compilation)
          .hadWarningContaining(
              "Pass it: @ViaConstructor(parameterOrder = {\"label\", \"note\", \"parts\"}).");
      assertThat(compilation).hadWarningCount(1);
    }

    @Test
    @DisplayName("@SuppressWarnings on the lens method, or on the spec interface, keeps it quiet")
    void suppressionOnTheLensMethodOrTheSpecKeepsItQuiet() {
      var compilation =
          compile(
              external("Rect", RECT),
              external("Fee", FEE),
              spec(
                  "RectOpticsSpec",
                  """
                  @ImportOptics
                  public interface RectOpticsSpec extends OpticsSpec<Rect> {
                      // The shorter constructor works the area out, so the lens stays lawful.
                      @SuppressWarnings("shorter-constructor")
                      @ViaConstructor(parameterOrder = {"w", "h"})
                      Lens<Rect, Integer> w();
                  }
                  """),
              spec(
                  "FeeOpticsSpec",
                  """
                  @ImportOptics
                  @SuppressWarnings("shorter-constructor")
                  public interface FeeOpticsSpec extends OpticsSpec<Fee> {
                      @ViaConstructor(parameterOrder = {"amount"})
                      Lens<Fee, Long> amount();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.RectOptics",
          "(source, newValue) -> new Rect(newValue, source.h())");
    }

    @Test
    @DisplayName("no warning where every value a longer constructor's names take is passed")
    void noWarningWhereEveryValueALongerConstructorTakesIsPassed() {
      var compilation =
          compile(
              external(
                  "Temperature",
                  """
                  public final class Temperature {
                      private final double celsius;
                      public Temperature(double celsius) { this.celsius = celsius; }
                      public Temperature(float fahrenheit) { this((fahrenheit - 32) * 5 / 9.0); }
                      public double celsius() { return celsius; }
                      public float fahrenheit() { return (float) (celsius * 9 / 5 + 32); }
                  }
                  """),
              external(
                  "Log",
                  """
                  public final class Log {
                      private final String text;
                      public Log(String text) { this.text = text; }
                      public Log(String text, Object sink) { this.text = text; }
                      public String text() { return text; }
                  }
                  """),
              external("Fee", FEE),
              spec(
                  "QuietOpticsSpec",
                  """
                  @ImportOptics
                  public interface QuietOpticsSpec extends OpticsSpec<Temperature> {
                      // Two constructors of one length are two views of one value.
                      @ViaConstructor(parameterOrder = {"celsius"})
                      Lens<Temperature, Double> celsius();
                  }
                  """),
              spec(
                  "LogOpticsSpec",
                  """
                  @ImportOptics
                  public interface LogOpticsSpec extends OpticsSpec<Log> {
                      // 'sink' is named after nothing the type reads, so nothing is reset.
                      @ViaConstructor(parameterOrder = {"text"})
                      Lens<Log, String> text();
                  }
                  """),
              spec(
                  "FeeOpticsSpec",
                  """
                  @ImportOptics
                  public interface FeeOpticsSpec extends OpticsSpec<Fee> {
                      @ViaConstructor(parameterOrder = {"amount", "currency"})
                      Lens<Fee, Long> amount();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("where no order passes every value, the warning and the derived refusal say how")
    void whereNoOrderPassesEveryValueTheWarningAndTheDerivedRefusalSayHow() {
      var compilation =
          compileReporting(
              external(
                  "Owner",
                  """
                  public final class Owner {
                      private final String name;
                      private final int age;
                      private final String nickname;
                      public Owner(String name, int age) { this(name, age, "", null); }
                      public Owner(String name, int age, String nickname, Object tag) {
                          this.name = name;
                          this.age = age;
                          this.nickname = nickname;
                      }
                      public String name() { return name; }
                      public int age() { return age; }
                      public String nickname() { return nickname; }
                  }
                  """),
              spec(
                  "OwnerOpticsSpec",
                  """
                  @ImportOptics
                  public interface OwnerOpticsSpec extends OpticsSpec<Owner> {
                      @ViaConstructor(parameterOrder = {"name", "age"})
                      Lens<Owner, Integer> age();

                      @ViaConstructor
                      Lens<Owner, String> name();
                  }
                  """));

      // 'tag' is named after nothing, so no order the names give takes the longer constructor.
      assertThat(compilation)
          .hadWarningContaining(
              "If the constructor derives it on purpose, annotate 'age()'"
                  + " @SuppressWarnings(\"shorter-constructor\").");
      assertThat(compilation)
          .hadWarningContaining(
              "unless the constructor derives it from what it is passed. Write an order that"
                  + " passes it, or rebuild 'Owner' with @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation)
          .hadErrorContaining(
              "Write the order of the constructor the lens should rebuild through as"
                  + " @ViaConstructor's 'parameterOrder'; to rebuild through the shorter one on"
                  + " purpose, write out {\"name\", \"age\"} and annotate 'name()'"
                  + " @SuppressWarnings(\"shorter-constructor\"). Otherwise rebuild 'Owner' with"
                  + " @Wither, @ViaBuilder or @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an offer that leaves a value out is made only where none passes them all")
    void offerThatLeavesAValueOutIsMadeOnlyWhereNonePassesThemAll() {
      var compilation =
          compile(
              external(
                  "Span",
                  """
                  public final class Span {
                      private final int from;
                      private final int to;
                      private final String tag;
                      public Span(int from, int to) { this(from, to, "", null); }
                      public Span(int from, int to, String tag, Object owner) {
                          this.from = from;
                          this.to = to;
                          this.tag = tag;
                      }
                      public int from() { return from; }
                      public int to() { return to; }
                      public String tag() { return tag; }
                  }
                  """),
              spec(
                  "SpanOpticsSpec",
                  """
                  @ImportOptics
                  public interface SpanOpticsSpec extends OpticsSpec<Span> {
                      @ViaConstructor(parameterOrder = {"to", "from"})
                      Lens<Span, Integer> from();
                  }
                  """));

      // 'owner' is named after nothing, so the only order the names give is the shorter
      // constructor's, which leaves 'tag()' out: it is offered all the same, as the one that binds,
      // with the way to keep the warning it draws quiet.
      assertThat(compilation)
          .hadErrorContaining(
              "Pass the arguments where the constructor's parameter names place them:"
                  + " @ViaConstructor(parameterOrder = {\"from\", \"to\"}). Pasted, an order"
                  + " offered warns of a value a longer constructor takes that it passes nothing"
                  + " for; where the shorter one works it out on purpose, annotate 'from()'"
                  + " @SuppressWarnings(\"shorter-constructor\").");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("@ViaConstructor Variable Arity")
  class ViaConstructorVariableArity {

    @Test
    @DisplayName("a variable-arity constructor's fixed parameters are held to their names")
    void variableArityConstructorsFixedParametersAreHeldToTheirNames() {
      var compilation =
          compile(
              external("Tag", TAG),
              spec(
                  "TagOpticsSpec",
                  """
                  @ImportOptics
                  public interface TagOpticsSpec extends OpticsSpec<Tag> {
                      @ViaConstructor(parameterOrder = {"note", "label"})
                      Lens<Tag, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes 'source.note()' where 'Tag(String label,"
                  + " String note, String... parts)' takes 'label'. The generated lens rebuilds"
                  + " through 'new Tag(source.note(), newValue)', and 'label' is named after"
                  + " 'label()', an accessor of 'Tag' that reads a value it takes. Another value"
                  + " passed in its place is written into the wrong field, which breaks the lens"
                  + " laws. Pass the arguments where the constructor's parameter names place them:"
                  + " @ViaConstructor(parameterOrder = {\"label\", \"note\", \"parts\"}).");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a value passed into an array named after an accessor is refused")
    void valuePassedIntoAnArrayNamedAfterAnAccessorIsRefused() {
      var compilation =
          compile(
              external(
                  "Parts",
                  """
                  public final class Parts {
                      private final String label;
                      private final String[] parts;
                      public Parts(String label, String... parts) {
                          this.label = label;
                          this.parts = parts.clone();
                      }
                      public String label() { return label; }
                      public String first() { return parts.length == 0 ? "" : parts[0]; }
                      public String[] parts() { return parts.clone(); }
                  }
                  """),
              spec(
                  "PartsOpticsSpec",
                  """
                  @ImportOptics
                  public interface PartsOpticsSpec extends OpticsSpec<Parts> {
                      @ViaConstructor(parameterOrder = {"label", "first"})
                      Lens<Parts, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes 'source.first()' where 'Parts(String"
                  + " label, String... parts)' takes 'parts'.");
      assertThat(compilation)
          .hadErrorContaining("@ViaConstructor(parameterOrder = {\"label\", \"parts\"}).");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an array named after nothing takes what the order passes, and the fix says so")
    void arrayNamedAfterNothingTakesWhatTheOrderPasses() {
      var compilation =
          compile(
              external(
                  "Note",
                  """
                  public final class Note {
                      private final String label;
                      private final String note;
                      public Note(String label, String note, String... rest) {
                          this.label = label;
                          this.note = note;
                      }
                      public String label() { return label; }
                      public String note() { return note; }
                  }
                  """),
              spec(
                  "NoteOpticsSpec",
                  """
                  @ImportOptics
                  public interface NoteOpticsSpec extends OpticsSpec<Note> {
                      @ViaConstructor(parameterOrder = {"note", "label"})
                      Lens<Note, String> label();

                      @ViaConstructor(parameterOrder = {"label", "note"})
                      Lens<Note, String> note();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "Pass the arguments where the constructor's parameter names place them:"
                  + " @ViaConstructor(parameterOrder = {\"label\", \"note\"}).");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a variable-arity call whose arguments no constructor takes is refused")
    void variableArityCallWhoseArgumentsNoConstructorTakesIsRefused() {
      var compilation =
          compile(
              external(
                  "Counts",
                  """
                  public final class Counts {
                      private final String label;
                      public Counts(String label, int... counts) { this.label = label; }
                      public String label() { return label; }
                      public String note() { return ""; }
                  }
                  """),
              spec(
                  "CountsOpticsSpec",
                  """
                  @ImportOptics
                  public interface CountsOpticsSpec extends OpticsSpec<Counts> {
                      @ViaConstructor(parameterOrder = {"label", "note"})
                      Lens<Counts, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No constructor of 'Counts' takes (String, String), the arguments"
                  + " the generated lens rebuilds it with.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("of two variable-arity constructors, the most specific is held to its names")
    void ofTwoVariableArityConstructorsTheMostSpecificIsHeldToItsNames() {
      var pair =
          external(
              "Pair",
              """
              public final class Pair {
                  private final String a;
                  private final String b;
                  public Pair(String a, Object... rest) { this(a, String.valueOf(rest[0]), 0); }
                  public Pair(String a, String... rest) { this(a, rest[0], 0); }
                  private Pair(String a, String b, int unused) {
                      this.a = a;
                      this.b = b;
                  }
                  public String a() { return a; }
                  public String b() { return b; }
              }
              """);
      var compilation =
          compile(
              pair,
              spec(
                  "PairOpticsSpec",
                  """
                  @ImportOptics
                  public interface PairOpticsSpec extends OpticsSpec<Pair> {
                      @ViaConstructor(parameterOrder = {"a", "b"})
                      Lens<Pair, String> a();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.PairOptics",
          "(source, newValue) -> new Pair(newValue, source.b())");

      // javac binds Pair(String, String...), so it is that one's names a swap crosses.
      var swapped =
          compile(
              pair,
              spec(
                  "PairOpticsSpec",
                  """
                  @ImportOptics
                  public interface PairOpticsSpec extends OpticsSpec<Pair> {
                      @ViaConstructor(parameterOrder = {"b", "a"})
                      Lens<Pair, String> a();
                  }
                  """));
      assertThat(swapped)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes 'source.b()' where 'Pair(String a,"
                  + " String... rest)' takes 'a'.");
      // 'rest' is named after nothing, so its element is a place like any other, and the repair
      // keeps each name once.
      assertThat(swapped)
          .hadErrorContaining(
              "Another value passed in its place is written into the wrong field, which breaks the"
                  + " lens laws. Pass the arguments where the constructor's parameter names place"
                  + " them: @ViaConstructor(parameterOrder = {\"a\", \"b\"}).");
      assertThat(swapped).hadErrorCount(1);
    }

    @Test
    @DisplayName(
        "a variable-arity overload that cannot take the arguments does not hide one that can")
    void variableArityOverloadThatCannotTakeTheArgumentsDoesNotHideOneThatCan() {
      var compilation =
          compile(
              external(
                  "Tally",
                  """
                  public final class Tally {
                      private final String label;
                      private final String note;
                      public Tally(String label, String note, String... parts) {
                          this.label = label;
                          this.note = note;
                      }
                      public Tally(int count, int... sizes) { this("", ""); }
                      public String label() { return label; }
                      public String note() { return note; }
                  }
                  """),
              spec(
                  "TallyOpticsSpec",
                  """
                  @ImportOptics
                  public interface TallyOpticsSpec extends OpticsSpec<Tally> {
                      @ViaConstructor(parameterOrder = {"note", "label"})
                      Lens<Tally, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes 'source.note()' where 'Tally(String"
                  + " label, String note, String... parts)' takes 'label'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("two variable-arity constructors javac cannot choose between are refused")
    void twoVariableArityConstructorsJavacCannotChooseBetweenAreRefused() {
      var compilation =
          compile(
              external(
                  "Amb",
                  """
                  public final class Amb {
                      private final String a;
                      private final String b;
                      public Amb(String a, Object... rest) { this.a = a; this.b = ""; }
                      public Amb(Object a, String... rest) { this.a = ""; this.b = ""; }
                      public String a() { return a; }
                      public String b() { return b; }
                  }
                  """),
              spec(
                  "AmbOpticsSpec",
                  """
                  @ImportOptics
                  public interface AmbOpticsSpec extends OpticsSpec<Amb> {
                      @ViaConstructor(parameterOrder = {"a", "b"})
                      Lens<Amb, String> a();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: The generated call 'new Amb(newValue, source.b())' cannot choose"
                  + " between 'Amb(String a, Object... rest)' and 'Amb(Object a, String... rest)'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a generic variable-arity constructor beside another is left to javac")
    void genericVariableArityConstructorBesideAnotherIsLeftToJavac() {
      var compilation =
          compile(
              external(
                  "Gen",
                  """
                  public final class Gen {
                      private final String a;
                      private final String b;
                      public Gen(String a, Object... rest) { this.a = a; this.b = ""; }
                      @SafeVarargs
                      public <T extends CharSequence> Gen(String a, T... rest) {
                          this.a = a;
                          this.b = String.valueOf(rest[0]);
                      }
                      public String a() { return a; }
                      public String b() { return b; }
                  }
                  """),
              spec(
                  "GenOpticsSpec",
                  """
                  @ImportOptics
                  public interface GenOpticsSpec extends OpticsSpec<Gen> {
                      @ViaConstructor(parameterOrder = {"a", "b"})
                      Lens<Gen, String> a();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName(
        "a generic variable-arity constructor passed its array whole is held at fixed arity")
    void genericVariableArityConstructorPassedItsArrayWholeIsHeldAtFixedArity() {
      var compilation =
          compile(
              external(
                  "Words",
                  """
                  public final class Words {
                      private final String label;
                      private final String[] parts;
                      @SafeVarargs
                      public <T extends CharSequence> Words(String label, T... parts) {
                          this.label = label;
                          this.parts = new String[parts.length];
                      }
                      public String label() { return label; }
                      public String[] parts() { return parts.clone(); }
                  }
                  """),
              spec(
                  "WordsOpticsSpec",
                  """
                  @ImportOptics
                  public interface WordsOpticsSpec extends OpticsSpec<Words> {
                      @ViaConstructor(parameterOrder = {"label", "parts"})
                      Lens<Words, String> label();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.WordsOptics",
          "(source, newValue) -> new Words(newValue, source.parts())");
    }

    @Test
    @DisplayName("values passed one by one into an array named after an accessor offer it whole")
    void valuesPassedOneByOneIntoAnArrayNamedAfterAnAccessorOfferItWhole() {
      var bundle =
          external(
              "Bundle",
              """
              public final class Bundle {
                  private final String label;
                  private final Object[] parts;
                  public Bundle(String label, Object... parts) {
                      this.label = label;
                      this.parts = parts.clone();
                  }
                  public String label() { return label; }
                  public String x() { return ""; }
                  public String y() { return ""; }
                  public Object[] parts() { return parts.clone(); }
              }
              """);
      var compilation =
          compile(
              bundle,
              spec(
                  "BundleOpticsSpec",
                  """
                  @ImportOptics
                  public interface BundleOpticsSpec extends OpticsSpec<Bundle> {
                      @ViaConstructor(parameterOrder = {"label", "x", "y"})
                      Lens<Bundle, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes 'source.x()' where 'Bundle(String label,"
                  + " Object... parts)' takes 'parts'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Pass the arguments where the constructor's parameter names place them:"
                  + " @ViaConstructor(parameterOrder = {\"label\", \"parts\"}).");
      assertThat(compilation).hadErrorCount(1);

      // Each 'parts()' passed into the array would nest the array in itself.
      var repeated =
          compile(
              bundle,
              spec(
                  "BundleOpticsSpec",
                  """
                  @ImportOptics
                  public interface BundleOpticsSpec extends OpticsSpec<Bundle> {
                      @ViaConstructor(parameterOrder = {"label", "parts", "parts"})
                      Lens<Bundle, String> label();
                  }
                  """));
      assertThat(repeated)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes 'source.parts()' where 'Bundle(String"
                  + " label, Object... parts)' takes 'parts'. The generated lens rebuilds through"
                  + " 'new Bundle(newValue, source.parts(), source.parts())', and 'parts' is named"
                  + " after 'parts()', an accessor of 'Bundle' that reads a value it takes. A value"
                  + " passed into its array is an element of it, not the array 'parts()' reads,"
                  + " which breaks the lens laws.");
      assertThat(repeated).hadErrorCount(1);
    }

    @Test
    @DisplayName("an array javac would warn of passing whole is not offered")
    void arrayJavacWouldWarnOfPassingWholeIsNotOffered() {
      // A String[] passed to an Object... is a single element too, so javac warns of it in the
      // generated file, and no order offered may draw that.
      var compilation =
          compile(
              external(
                  "Strings",
                  """
                  public final class Strings {
                      private final String label;
                      private final Object[] parts;
                      public Strings(String label, Object... parts) {
                          this.label = label;
                          this.parts = parts.clone();
                      }
                      public String label() { return label; }
                      public String x() { return ""; }
                      public String[] parts() { return new String[0]; }
                  }
                  """),
              spec(
                  "StringsOpticsSpec",
                  """
                  @ImportOptics
                  public interface StringsOpticsSpec extends OpticsSpec<Strings> {
                      @ViaConstructor(parameterOrder = {"label", "x"})
                      Lens<Strings, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "which breaks the lens laws. Rebuild 'Strings' with @Wither, @ViaBuilder or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an order that crosses each constructor inference could pick is refused")
    void orderThatCrossesEachConstructorInferenceCouldPickIsRefused() {
      var compilation =
          compile(
              external(
                  "Mark",
                  """
                  public final class Mark {
                      private final String label;
                      private final String note;
                      @SafeVarargs
                      public <T extends CharSequence> Mark(String label, T... parts) {
                          this.label = label;
                          this.note = "";
                      }
                      public Mark(String label, String note, String... more) {
                          this.label = label;
                          this.note = note;
                      }
                      public String label() { return label; }
                      public String note() { return note; }
                  }
                  """),
              spec(
                  "MarkOpticsSpec",
                  """
                  @ImportOptics
                  public interface MarkOpticsSpec extends OpticsSpec<Mark> {
                      @ViaConstructor(parameterOrder = {"note", "label"})
                      Lens<Mark, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' passes 'source.note()' where 'Mark(String label,"
                  + " T... parts)' takes 'label'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a lens passed into an array is offered no alias of the array's accessor")
    void lensPassedIntoAnArrayIsOfferedNoAliasOfTheArraysAccessor() {
      var compilation =
          compile(
              external(
                  "Labels",
                  """
                  public final class Labels {
                      private final String[] labels;
                      public Labels(String... labels) { this.labels = labels.clone(); }
                      public String label() { return labels.length == 0 ? "" : labels[0]; }
                      public String[] labels() { return labels.clone(); }
                  }
                  """),
              spec(
                  "LabelsOpticsSpec",
                  """
                  @ImportOptics
                  public interface LabelsOpticsSpec extends OpticsSpec<Labels> {
                      @ViaConstructor(parameterOrder = {"label"})
                      Lens<Labels, String> label();
                  }
                  """));

      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'Labels(String... labels)' takes no parameter named after the"
                  + " lens's own 'label()'.");
      Assertions.assertThat(compilation.errors().getFirst().getMessage(null))
          .doesNotContain("name the lens method");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("Focus Unboxing")
  class FocusUnboxing {

    @Test
    @DisplayName("a focus wider than its getter's wrapper is passed as it is, never cast down")
    void focusWiderThanItsGettersWrapperIsNeverCastDown() {
      // A cast to int would compile on a Number, and throw for a Double at run time. Each refusal
      // leads with the focus that a primitive overload does take, whatever the strategy.
      var compilation =
          compile(
              external(
                  "Gauge",
                  """
                  public final class Gauge {
                      private int level;
                      public Gauge(int level) { this.level = level; }
                      public Gauge(String level) { this(Integer.parseInt(level)); }
                      public Gauge(Gauge other) { this(other.level); }
                      public int level() { return level; }
                      public Gauge withLevel(int level) { return new Gauge(level); }
                      public Gauge withLevel(String level) { return new Gauge(level); }
                      public Gauge withLevel(int level, String unit) { return new Gauge(level); }
                      public void setLevel(int level) { this.level = level; }
                      public void setLevel(String level) { this.level = Integer.parseInt(level); }
                      public Builder toBuilder() { return new Builder().level(level); }
                      public static final class Builder {
                          private int level;
                          public Builder level(int level) { this.level = level; return this; }
                          public Builder level(String level) { return level(Integer.parseInt(level)); }
                          public Gauge build() { return new Gauge(level); }
                      }
                  }
                  """),
              external(
                  "Plate",
                  """
                  public final class Plate {
                      private final int count;
                      public Plate(int count) { this.count = count; }
                      public int count() { return count; }
                      public Plate withCount(String count) { return new Plate(Integer.parseInt(count)); }
                      public Plate withCount(Boolean count) { return this; }
                      public Plate withCount(int count, String unit) { return new Plate(count); }
                  }
                  """),
              spec(
                  "GaugeOpticsSpec",
                  """
                  @ImportOptics
                  public interface GaugeOpticsSpec extends OpticsSpec<Gauge> {
                      @ViaConstructor(parameterOrder = {"level"})
                      Lens<Gauge, Number> level();
                  }
                  """),
              spec(
                  "GaugeWitherOpticsSpec",
                  """
                  @ImportOptics
                  public interface GaugeWitherOpticsSpec extends OpticsSpec<Gauge> {
                      @Wither(value = "withLevel", getter = "level")
                      Lens<Gauge, Number> level();
                  }
                  """),
              spec(
                  "GaugeCopyOpticsSpec",
                  """
                  @ImportOptics
                  public interface GaugeCopyOpticsSpec extends OpticsSpec<Gauge> {
                      @ViaCopyAndSet(setter = "setLevel")
                      Lens<Gauge, Number> level();
                  }
                  """),
              spec(
                  "GaugeBuilderOpticsSpec",
                  """
                  @ImportOptics
                  public interface GaugeBuilderOpticsSpec extends OpticsSpec<Gauge> {
                      @ViaBuilder
                      Lens<Gauge, Number> level();
                  }
                  """),
              spec(
                  "PlateOpticsSpec",
                  """
                  @ImportOptics
                  public interface PlateOpticsSpec extends OpticsSpec<Plate> {
                      // An Integer focus is the wrapper already, so the focus is not the remedy.
                      @Wither(value = "withCount", getter = "count")
                      Lens<Plate, Integer> count();
                  }
                  """),
              spec(
                  "PlateWideOpticsSpec",
                  """
                  @ImportOptics
                  public interface PlateWideOpticsSpec extends OpticsSpec<Plate> {
                      // No overload takes the int, so declaring the wrapper would not help.
                      @Wither(value = "withCount", getter = "count")
                      Lens<Plate, Number> count();
                  }
                  """));

      String narrower =
          "Declare the lens's focus as 'Integer', the type 'level()' reads, which the call then"
              + " passes unboxed; otherwise rebuild 'Gauge' with";
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: No constructor of 'Gauge' takes (Number), the arguments the"
                  + " generated lens rebuilds it with.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: No method 'withLevel' of 'Gauge' takes the lens's focus type 'Number'.");
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains(narrower))
          .extracting(
              error -> error.getMessage(null).substring(0, error.getMessage(null).indexOf(':')))
          .containsExactlyInAnyOrder("@Wither", "@ViaCopyAndSet", "@ViaBuilder");
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains("'Plate'"))
          .hasSize(2)
          .allMatch(error -> error.getMessage(null).contains("Name a wither that takes the value"));
      assertThat(compilation).hadErrorCount(6);
    }

    @Test
    @DisplayName("a wildcard focus is the getter's wrapper, and is still cast to its primitive")
    void wildcardFocusIsStillCastToItsPrimitive() {
      var compilation =
          compile(
              external(
                  "Dial",
                  """
                  public final class Dial {
                      private final long level;
                      public Dial(long level) { this.level = level; }
                      public Dial(Number level) { this(-1L); }
                      public long level() { return level; }
                  }
                  """),
              spec(
                  "DialOpticsSpec",
                  """
                  @ImportOptics
                  public interface DialOpticsSpec extends OpticsSpec<Dial> {
                      @ViaConstructor(parameterOrder = {"level"})
                      Lens<Dial, ? extends Number> level();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.DialOptics", "(source, newValue) -> new Dial((long) newValue)");
    }

    @Test
    @DisplayName("a wildcard focus is cast only where its lower bound unboxes to the getter's read")
    void wildcardFocusIsCastOnlyWhereItsLowerBoundUnboxes() {
      var compilation =
          compile(
              external(
                  "Meter",
                  """
                  public final class Meter {
                      private final long level;
                      public Meter(long level) { this.level = level; }
                      public Meter(Number level) { this(-1L); }
                      public long level() { return level; }
                  }
                  """),
              spec(
                  "MeterLongOpticsSpec",
                  """
                  @ImportOptics
                  public interface MeterLongOpticsSpec extends OpticsSpec<Meter> {
                      @ViaConstructor(parameterOrder = {"level"})
                      Lens<Meter, ? super Long> level();
                  }
                  """),
              spec(
                  "MeterNumberOpticsSpec",
                  """
                  @ImportOptics
                  public interface MeterNumberOpticsSpec extends OpticsSpec<Meter> {
                      // A '? super Number' focus sets any Number, which a cast to long would throw on.
                      @ViaConstructor(parameterOrder = {"level"})
                      Lens<Meter, ? super Number> level();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.MeterLongOptics",
          "(source, newValue) -> new Meter((long) newValue)");
      assertGeneratedCodeContains(
          compilation, "com.myapp.MeterNumberOptics", "(source, newValue) -> new Meter(newValue)");
    }
  }
}
