// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.example.proto.ShelfProto.Shelf;
import com.google.protobuf.FieldMask;
import com.google.protobuf.ListValue;
import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Map;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.laws.LensLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * protobuf-java messages as the source types of {@code @ViaBuilder} lenses, pinned against
 * protobuf-java's own well-known types: each is a message protoc generated, so its builder is the
 * one a user's messages have.
 */
@DisplayName("ImportOptics - protobuf-java messages")
class ImportOpticsProtobufTest {

  /** A spec in {@code com.example}, with the imports every spec here needs. */
  private static JavaFileObject spec(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import com.example.proto.ShelfProto.Shelf;
        import com.google.protobuf.FieldMask;
        import com.google.protobuf.ListValue;
        import com.google.protobuf.Struct;
        import com.google.protobuf.Value;
        import java.util.List;
        import java.util.Map;
        import org.higherkindedj.optics.Lens;
        import org.higherkindedj.optics.annotations.ImportOptics;
        import org.higherkindedj.optics.annotations.OpticsSpec;
        import org.higherkindedj.optics.annotations.ViaBuilder;

        """
            + body);
  }

  @Test
  @DisplayName("a lens through a repeated or a map field's adder replaces the field, lawfully")
  @SuppressWarnings("unchecked") // the generated lenses are handed back as Object
  void lensThroughAFieldsAdderReplacesTheField() throws ReflectiveOperationException {
    // toBuilder() copies the field into the builder, and addAllPaths, putAllFields and
    // addAllValues add to what it holds, so each lens clears the field first.
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(
                spec(
                    "MaskOpticsSpec",
                    """
                    @ImportOptics
                    public interface MaskOpticsSpec extends OpticsSpec<FieldMask> {
                      @ViaBuilder(getter = "getPathsList", setter = "addAllPaths")
                      Lens<FieldMask, List<String>> paths();
                    }
                    """),
                spec(
                    "StructOpticsSpec",
                    """
                    @ImportOptics
                    public interface StructOpticsSpec extends OpticsSpec<Struct> {
                      @ViaBuilder(getter = "getFieldsMap", setter = "putAllFields")
                      Lens<Struct, Map<String, Value>> fields();
                    }
                    """),
                spec(
                    "ListOpticsSpec",
                    """
                    @ImportOptics
                    public interface ListOpticsSpec extends OpticsSpec<ListValue> {
                      @ViaBuilder(getter = "getValuesList", setter = "addAllValues")
                      Lens<ListValue, List<Value>> values();
                    }
                    """));
    assertThat(compilation).succeeded();
    RuntimeCompilationHelper.CompiledResult compiled =
        new RuntimeCompilationHelper.CompiledResult(compilation);

    Lens<FieldMask, List<String>> paths =
        (Lens<FieldMask, List<String>>) compiled.invokeStatic("com.example.MaskOptics", "paths");
    FieldMask mask = FieldMask.newBuilder().addPaths("a").build();
    Assertions.assertThat(paths.set(List.of("b"), mask).getPathsList()).containsExactly("b");
    LensLaws.assertLensLaws(paths, mask, List.of("b"), List.of("c", "d"));

    Lens<Struct, Map<String, Value>> fields =
        (Lens<Struct, Map<String, Value>>)
            compiled.invokeStatic("com.example.StructOptics", "fields");
    Value one = Value.newBuilder().setNumberValue(1).build();
    Value two = Value.newBuilder().setStringValue("two").build();
    Struct struct = Struct.newBuilder().putFields("x", one).build();
    Assertions.assertThat(fields.set(Map.of("y", two), struct).getFieldsMap())
        .containsExactly(Map.entry("y", two));
    LensLaws.assertLensLaws(fields, struct, Map.of("y", two), Map.of("x", two, "z", one));

    Lens<ListValue, List<Value>> values =
        (Lens<ListValue, List<Value>>) compiled.invokeStatic("com.example.ListOptics", "values");
    ListValue list = ListValue.newBuilder().addValues(one).build();
    Assertions.assertThat(values.set(List.of(two), list).getValuesList()).containsExactly(two);
    LensLaws.assertLensLaws(values, list, List.of(two), List.of(one, two));
  }

  @Test
  @DisplayName("each field is cleared by its own name, an open enum's numbers included")
  @SuppressWarnings("unchecked") // the generated lenses are handed back as Object
  void eachFieldIsClearedByItsOwnName() throws ReflectiveOperationException {
    // 'all_tags' has a clear method of its own, and its single adder shares the name addAllTags
    // with the method that adds a whole 'tags', which clears 'tags' alone. An open enum's numbers
    // are added by addAllKindsValue and putAllKindByValue, beside clearKinds() and clearKindBy().
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(
                spec(
                    "ShelfOpticsSpec",
                    """
                    @ImportOptics
                    public interface ShelfOpticsSpec extends OpticsSpec<Shelf> {
                      @ViaBuilder(getter = "getTagsList", setter = "addAllTags")
                      Lens<Shelf, List<String>> tags();

                      @ViaBuilder(getter = "getAllTagsList", setter = "addAllAllTags")
                      Lens<Shelf, List<String>> allTags();

                      @ViaBuilder(getter = "getSizesMap", setter = "putAllSizes")
                      Lens<Shelf, Map<String, Integer>> sizes();

                      @ViaBuilder(getter = "getKindsValueList", setter = "addAllKindsValue")
                      Lens<Shelf, List<Integer>> kinds();

                      @ViaBuilder(getter = "getKindByValueMap", setter = "putAllKindByValue")
                      Lens<Shelf, Map<String, Integer>> kindBy();

                      @ViaBuilder(getter = "getTintByValueMap", setter = "putAllTintByValue")
                      Lens<Shelf, Map<String, Integer>> tintBy();

                      @ViaBuilder(getter = "getTintByValueList", setter = "addAllTintByValue")
                      Lens<Shelf, List<String>> tintByValue();

                      @ViaBuilder(getter = "getSizesValueMap", setter = "putAllSizesValue")
                      Lens<Shelf, Map<String, Integer>> sizesValue();
                    }
                    """));
    assertThat(compilation).succeeded();
    for (String chain :
        List.of(
            "source.toBuilder().clearTags().addAllTags(newValue).build()",
            "source.toBuilder().clearAllTags().addAllAllTags(newValue).build()",
            "source.toBuilder().clearSizes().putAllSizes(newValue).build()",
            "source.toBuilder().clearKinds().addAllKindsValue(newValue).build()",
            "source.toBuilder().clearKindBy().putAllKindByValue(newValue).build()",
            // tint_by's numbers clear tint_by, and the list named tint_by_value clears itself
            "source.toBuilder().clearTintBy().putAllTintByValue(newValue).build()",
            "source.toBuilder().clearTintByValue().addAllTintByValue(newValue).build()",
            // sizes holds no enum, so sizes_value's adder is its own field's
            "source.toBuilder().clearSizesValue().putAllSizesValue(newValue).build()")) {
      assertThat(compilation)
          .generatedSourceFile("com.example.ShelfOptics")
          .contentsAsUtf8String()
          .contains(chain);
    }
    RuntimeCompilationHelper.CompiledResult compiled =
        new RuntimeCompilationHelper.CompiledResult(compilation);
    Shelf shelf =
        Shelf.newBuilder()
            .addTags("t")
            .addAllTags("a")
            .putSizes("s", 1)
            .addKindsValue(1)
            .putKindByValue("k", 2)
            .putTintByValue("t", 1)
            .addTintByValue("keep")
            .build();

    Lens<Shelf, List<String>> tags =
        (Lens<Shelf, List<String>>) compiled.invokeStatic("com.example.ShelfOptics", "tags");
    Shelf retagged = tags.set(List.of("u"), shelf);
    Assertions.assertThat(retagged.getTagsList()).containsExactly("u");
    Assertions.assertThat(retagged.getAllTagsList()).containsExactly("a");
    LensLaws.assertLensLaws(tags, shelf, List.of("u"), List.of("v", "w"));

    Lens<Shelf, List<String>> allTags =
        (Lens<Shelf, List<String>>) compiled.invokeStatic("com.example.ShelfOptics", "allTags");
    Assertions.assertThat(allTags.set(List.of("b"), shelf).getTagsList()).containsExactly("t");
    LensLaws.assertLensLaws(allTags, shelf, List.of("b"), List.of("c"));

    Lens<Shelf, Map<String, Integer>> sizes =
        (Lens<Shelf, Map<String, Integer>>)
            compiled.invokeStatic("com.example.ShelfOptics", "sizes");
    LensLaws.assertLensLaws(sizes, shelf, Map.of("m", 2), Map.of("n", 3));

    // A number no constant of the enum names is kept, as an open enum keeps it.
    Lens<Shelf, List<Integer>> kinds =
        (Lens<Shelf, List<Integer>>) compiled.invokeStatic("com.example.ShelfOptics", "kinds");
    LensLaws.assertLensLaws(kinds, shelf, List.of(2), List.of(7, 1));

    Lens<Shelf, Map<String, Integer>> kindBy =
        (Lens<Shelf, Map<String, Integer>>)
            compiled.invokeStatic("com.example.ShelfOptics", "kindBy");
    LensLaws.assertLensLaws(kindBy, shelf, Map.of("j", 1), Map.of("k", 9));

    Lens<Shelf, Map<String, Integer>> tintBy =
        (Lens<Shelf, Map<String, Integer>>)
            compiled.invokeStatic("com.example.ShelfOptics", "tintBy");
    Assertions.assertThat(tintBy.set(Map.of("u", 2), shelf).getTintByValueList())
        .containsExactly("keep");
    LensLaws.assertLensLaws(tintBy, shelf, Map.of("u", 2), Map.of("v", 1));
  }

  @Test
  @DisplayName("a wildcard focus binds the adder its getter's read takes, and clears the field")
  @SuppressWarnings("unchecked") // the generated lens is handed back as Object
  void wildcardFocusBindsTheAdderItsGettersReadTakes() throws ReflectiveOperationException {
    // addAllTags is both the adder of one 'all_tags' and the method that adds a whole 'tags'.
    // javac infers the wildcard focus as what getTagsList() reads, so the call binds the method
    // that adds a whole 'tags', which adds to what toBuilder() copied: the lens clears it first.
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(
                spec(
                    "ShelfOpticsSpec",
                    """
                    @ImportOptics
                    public interface ShelfOpticsSpec extends OpticsSpec<Shelf> {
                      @ViaBuilder(getter = "getTagsList", setter = "addAllTags")
                      Lens<Shelf, ? extends List<String>> tags();
                    }
                    """));
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("com.example.ShelfOptics")
        .contentsAsUtf8String()
        .contains("source.toBuilder().clearTags().addAllTags(newValue).build()");
    // The focus javac infers is the ProtocolStringList the getter reads, so the values set are too.
    Lens<Shelf, ProtocolStringList> tags =
        (Lens<Shelf, ProtocolStringList>)
            new RuntimeCompilationHelper.CompiledResult(compilation)
                .invokeStatic("com.example.ShelfOptics", "tags");
    Shelf shelf = Shelf.newBuilder().addTags("a").addTags("b").build();
    Assertions.assertThat(tags.modify(read -> read, shelf).getTagsList()).containsExactly("a", "b");
    LensLaws.assertLensLaws(
        tags,
        shelf,
        Shelf.newBuilder().addTags("u").build().getTagsList(),
        Shelf.newBuilder().addTags("v").addTags("w").build().getTagsList());
  }

  @Test
  @DisplayName("an accessor named after the field is offered the getter and adder for it whole")
  void accessorNamedAfterTheFieldIsOfferedTheGetterAndAdder() {
    // A map field's getPoints() is deprecated, and a call to it fails a build under -Werror in
    // the generated file, so getPointsMap() is offered, as getTagsList() is for a repeated one.
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(
                spec(
                    "MaskOpticsSpec",
                    """
                    @ImportOptics
                    public interface MaskOpticsSpec extends OpticsSpec<FieldMask> {
                      @ViaBuilder(getter = "getPathsList")
                      Lens<FieldMask, List<String>> paths();
                    }
                    """),
                spec(
                    "WrittenOpticsSpec",
                    """
                    @ImportOptics
                    public interface WrittenOpticsSpec extends OpticsSpec<FieldMask> {
                      @ViaBuilder(getter = "getPathsList", setter = "setPaths")
                      Lens<FieldMask, List<String>> paths();
                    }
                    """),
                spec(
                    "StructOpticsSpec",
                    """
                    @ImportOptics
                    public interface StructOpticsSpec extends OpticsSpec<Struct> {
                      @ViaBuilder(getter = "getFieldsMap")
                      Lens<Struct, Map<String, Value>> fields();
                    }
                    """),
                spec(
                    "PathsOpticsSpec",
                    """
                    @ImportOptics
                    public interface PathsOpticsSpec extends OpticsSpec<FieldMask> {
                      @ViaBuilder
                      Lens<FieldMask, List<String>> paths();
                    }
                    """),
                spec(
                    "FieldsOpticsSpec",
                    """
                    @ImportOptics
                    public interface FieldsOpticsSpec extends OpticsSpec<Struct> {
                      @ViaBuilder
                      Lens<Struct, Map<String, Value>> fields();
                    }
                    """),
                spec(
                    "BeanOpticsSpec",
                    """
                    @ImportOptics
                    public interface BeanOpticsSpec extends OpticsSpec<FieldMask> {
                      @ViaBuilder(getter = "getPaths", setter = "addAllPaths")
                      Lens<FieldMask, List<String>> paths();
                    }
                    """),
                spec(
                    "NearOpticsSpec",
                    """
                    @ImportOptics
                    public interface NearOpticsSpec extends OpticsSpec<Struct> {
                      @ViaBuilder(getter = "getField", setter = "putAllFields")
                      Lens<Struct, Map<String, Value>> fields();
                    }
                    """),
                spec(
                    "MisspeltOpticsSpec",
                    """
                    @ImportOptics
                    public interface MisspeltOpticsSpec extends OpticsSpec<FieldMask> {
                      @ViaBuilder(getter = "getPathsList", setter = "addAllPath")
                      Lens<FieldMask, List<String>> paths();
                    }
                    """),
                spec(
                    "AdderOpticsSpec",
                    """
                    @ImportOptics
                    public interface AdderOpticsSpec extends OpticsSpec<FieldMask> {
                      @ViaBuilder(getter = "getPathsList", setter = "addPaths")
                      Lens<FieldMask, List<String>> paths();
                    }
                    """));
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@ViaBuilder: 'FieldMask.Builder' has no method 'paths' for the generated lens to set"
                + " through.");
    assertThat(compilation)
        .hadErrorContaining(
            "No method 'setPaths' of 'FieldMask.Builder' takes the lens's focus type"
                + " 'List<String>'.");
    for (String misspelt : List.of("'paths'", "'setPaths'")) {
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains(misspelt))
          .singleElement()
          .satisfies(
              error ->
                  Assertions.assertThat(error.getMessage(null))
                      .contains("Did you mean 'addAllPaths'?"));
    }
    assertThat(compilation).hadErrorContaining("Did you mean 'putAllFields'?");
    assertThat(compilation)
        .hadErrorContaining(
            "@ViaBuilder: 'FieldMask' has no method 'paths()' for the generated lens to read the"
                + " value it focuses.");
    assertThat(compilation).hadErrorContaining("Did you mean 'getPathsList'?");
    assertThat(compilation)
        .hadErrorContaining(
            "@ViaBuilder: 'Struct' has no method 'fields()' for the generated lens to read the"
                + " value it focuses.");
    assertThat(compilation).hadErrorContaining("Did you mean 'getFieldsMap'?");
    Assertions.assertThat(compilation.errors())
        .noneMatch(error -> error.getMessage(null).contains("Did you mean 'getFields'?"));
    // A bean's getter, a near miss of the field, and the adder of one element are offered the
    // method that reads or writes the field whole.
    for (List<String> offer :
        List.of(
            List.of("'FieldMask' has no method 'getPaths()'", "Did you mean 'getPathsList'?"),
            List.of("'Struct' has no method 'getField()'", "Did you mean 'getFieldsMap'?"),
            List.of("No method 'addPaths' of 'FieldMask.Builder'", "Did you mean 'addAllPaths'?"),
            List.of("has no method 'addAllPath'", "Did you mean 'addAllPaths'?"))) {
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains(offer.getFirst()))
          .singleElement()
          .satisfies(
              error -> Assertions.assertThat(error.getMessage(null)).contains(offer.getLast()));
    }
    assertThat(compilation).hadErrorCount(9);
  }
}
