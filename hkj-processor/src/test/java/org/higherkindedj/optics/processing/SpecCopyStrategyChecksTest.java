// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a spec's copy strategy is held to: which method a generated call binds, and whether every
 * method name the strategy carries is one the generated class can call.
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

  /** Compiles under the lint a call bound to the wrong method would trip, as an error. */
  private static Compilation compile(JavaFileObject... sources) {
    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions("-Xlint:unchecked,rawtypes,static", "-Werror")
        .compile(sources);
  }

  @Nested
  @DisplayName("@Wither Call Binding")
  class WitherCallBinding {

    @Test
    @DisplayName("each lens is checked against the overload its focus binds")
    void eachLensIsCheckedAgainstTheOverloadItsFocusBinds() {
      // In each class one overload returns the source type, and it is not the one the call binds:
      // an Integer binds withN(Integer), which needs no unboxing, and a String binds
      // withId(String), the most specific overload that takes one.
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
                  "BoxedOpticsSpec",
                  """
                  @ImportOptics
                  public interface BoxedOpticsSpec extends OpticsSpec<Boxed> {
                      @Wither(value = "withN", getter = "n")
                      Lens<Boxed, Integer> n();
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
              "@Wither: 'withN(Integer)' returns 'Object', not the source type 'Boxed'. The"
                  + " generated lens sets through 'source.withN(newValue)' with the new value"
                  + " typed 'Integer', which binds 'withN(Integer)', and hands its result back as"
                  + " the source type 'Boxed', which 'Object' is not.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'RawDraft', not the source type 'RawDraft<T>'");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'Tagged<String>', not the source type"
                  + " 'Tagged<T>'");
      assertThat(compilation).hadErrorCount(3);
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
      assertGeneratedCodeContains(compilation, "com.myapp.CounterOptics", "source.withN(newValue)");
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
      assertThat(compilation).hadErrorContaining("cannot find symbol");
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
                  + " nothing. Add 'id' to @ViaConstructor's 'parameterOrder', at the place the"
                  + " constructor takes it.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a setter whose own type does not resolve is left to javac")
    void setterWhoseOwnTypeDoesNotResolveIsLeftToJavac() {
      var compilation =
          compile(
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
    void builderTypeThatDoesNotResolveIsLeftToJavac() {
      var compilation =
          compile(
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
}
