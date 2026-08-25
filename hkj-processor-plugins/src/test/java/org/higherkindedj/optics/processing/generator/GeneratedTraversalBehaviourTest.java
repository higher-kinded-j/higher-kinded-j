// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A generated traversal reaches every element of its container and puts the results back where it
 * found them.
 *
 * <p>The shapes below are the ones whose generated source names something other than the component
 * type as written — the type a wildcard stands for, a boxed primitive, an array the traversal
 * cannot create by name, a record's own type variables. Compiling proves only that what was named
 * is denotable; each case here runs the generated class to show that it still traverses.
 */
@DisplayName("A generated traversal")
class GeneratedTraversalBehaviourTest {

  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.example.Leaf",
          """
          package com.example;

          public record Leaf(String name) {}
          """);

  @Test
  @DisplayName("should reach every element of a List<? extends Leaf>")
  void shouldTraverseWildcardList() throws Exception {
    var compiled = compile("List<? extends Leaf> items");
    Object leaves = List.of(compiled.leaf("a"), compiled.leaf("b"));
    Object holder = compiled.holder(List.class, leaves);

    assertThat(names(Traversals.getAll(compiled.traversal("items"), holder)))
        .containsExactly("a", "b");

    Object updated = Traversals.modify(compiled.traversal("items"), compiled::shout, holder);

    assertThat(names((List<?>) compiled.component(updated, "items"))).containsExactly("A", "B");
  }

  @Test
  @DisplayName("should reach every value of a Map<String, ? extends Leaf>")
  void shouldTraverseWildcardMapValues() throws Exception {
    var compiled = compile("Map<String, ? extends Leaf> byKey");
    Map<String, Object> entries = new HashMap<>();
    entries.put("first", compiled.leaf("a"));
    entries.put("second", compiled.leaf("b"));
    Object holder = compiled.holder(Map.class, entries);

    assertThat(names(Traversals.getAll(compiled.traversal("byKey"), holder)))
        .containsExactlyInAnyOrder("a", "b");

    Object updated = Traversals.modify(compiled.traversal("byKey"), compiled::shout, holder);
    Map<?, ?> newEntries = (Map<?, ?>) compiled.component(updated, "byKey");

    assertThat(keys(newEntries)).containsExactlyInAnyOrder("first", "second");
    assertThat(names(List.copyOf(newEntries.values()))).containsExactlyInAnyOrder("A", "B");
  }

  @Test
  @DisplayName("should reach every value of a concrete Map, keys untouched")
  void shouldTraverseConcreteMapValues() throws Exception {
    var compiled = compile("Map<String, Leaf> byKey");
    Map<String, Object> entries = new HashMap<>();
    entries.put("first", compiled.leaf("a"));
    entries.put("second", compiled.leaf("b"));
    Object holder = compiled.holder(Map.class, entries);

    assertThat(names(Traversals.getAll(compiled.traversal("byKey"), holder)))
        .containsExactlyInAnyOrder("a", "b");

    Object updated = Traversals.modify(compiled.traversal("byKey"), compiled::shout, holder);
    Map<?, ?> newEntries = (Map<?, ?>) compiled.component(updated, "byKey");

    assertThat(keys(newEntries)).containsExactlyInAnyOrder("first", "second");
    assertThat(names(List.copyOf(newEntries.values()))).containsExactlyInAnyOrder("A", "B");
  }

  @Test
  @DisplayName("should reach every element of a primitive array, unboxing them back into it")
  void shouldTraversePrimitiveArray() throws Exception {
    var compiled = compile("int[] counts");
    Object holder = compiled.holder(int[].class, new int[] {1, 2, 3});

    assertThat(Traversals.getAll(compiled.traversal("counts"), holder)).containsExactly(1, 2, 3);

    Object updated =
        Traversals.modify(compiled.traversal("counts"), count -> ((Integer) count) + 1, holder);

    assertThat((int[]) compiled.component(updated, "counts")).containsExactly(2, 3, 4);
  }

  @Test
  @DisplayName(
      "should reach every element of an array whose element type cannot be created by name")
  void shouldTraverseArrayOfParameterisedElements() throws Exception {
    var compiled = compileDeclaration("record Holder(List<Leaf>[] rows) {}");
    Object rows = java.lang.reflect.Array.newInstance(List.class, 2);
    java.lang.reflect.Array.set(rows, 0, List.of(compiled.leaf("a")));
    java.lang.reflect.Array.set(rows, 1, List.of(compiled.leaf("b")));
    Object holder = compiled.holder(List[].class, rows);

    Object updated =
        Traversals.modify(
            compiled.traversal("rows"),
            row -> List.copyOf(((List<?>) row).stream().map(compiled::shout).toList()),
            holder);

    Object[] newRows = (Object[]) compiled.component(updated, "rows");
    assertThat(newRows).hasSize(2);
    assertThat(names((List<?>) newRows[0])).containsExactly("A");
    assertThat(names((List<?>) newRows[1])).containsExactly("B");
  }

  @Test
  @DisplayName("should traverse a generic record, which is generated with its type variables")
  void shouldTraverseGenericRecord() throws Exception {
    var compiled = compileDeclaration("record Holder<T>(String label, List<T> items) {}");
    Object holder =
        compiled
            .loader()
            .load("com.example.Holder")
            .getConstructor(String.class, List.class)
            .newInstance("squad", List.of(compiled.leaf("a"), compiled.leaf("b")));

    assertThat(names(Traversals.getAll(compiled.traversal("items"), holder)))
        .containsExactly("a", "b");

    Object updated = Traversals.modify(compiled.traversal("items"), compiled::shout, holder);

    assertThat(names((List<?>) compiled.component(updated, "items"))).containsExactly("A", "B");
    assertThat(compiled.component(updated, "label")).isEqualTo("squad");
  }

  /** Compiles a one-component record through the processor and loads what came out. */
  private static Compiled compile(String component) {
    return compileDeclaration("record Holder(%s) {}".formatted(component));
  }

  /** Compiles a record written out in full, for the shapes one component cannot express. */
  private static Compiled compileDeclaration(String declaration) {
    JavaFileObject holder =
        JavaFileObjects.forSourceString(
            "com.example.Holder",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateTraversals;
            import java.util.List;
            import java.util.Map;

            @GenerateTraversals
            public %s
            """
                .formatted(declaration));

    Compilation compilation =
        javac().withProcessors(new TraversalProcessor()).compile(holder, LEAF);
    assertThat(compilation.errors().stream().map(error -> error.getMessage(null)))
        .as("the holder and its generated traversals should compile")
        .isEmpty();
    return new Compiled(new GeneratedClassLoader(compilation));
  }

  /** The generated classes, and the reflection needed to drive types the test cannot name. */
  private record Compiled(GeneratedClassLoader loader) {

    Object leaf(String name) throws Exception {
      return loader.load("com.example.Leaf").getConstructor(String.class).newInstance(name);
    }

    Object holder(Class<?> componentType, Object value) throws Exception {
      return loader.load("com.example.Holder").getConstructor(componentType).newInstance(value);
    }

    @SuppressWarnings("unchecked") // The generated traversal is over types this test cannot name.
    Traversal<Object, Object> traversal(String componentName) throws Exception {
      return (Traversal<Object, Object>)
          loader.load("com.example.HolderTraversals").getMethod(componentName).invoke(null);
    }

    Object component(Object holder, String componentName) throws Exception {
      return holder.getClass().getMethod(componentName).invoke(holder);
    }

    /** A leaf with its name in upper case, the modification each case applies. */
    Object shout(Object leaf) {
      try {
        return leaf(name(leaf).toUpperCase());
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static List<String> keys(Map<?, ?> entries) {
    return entries.keySet().stream().map(String::valueOf).toList();
  }

  private static List<String> names(List<?> leaves) {
    return leaves.stream().map(GeneratedTraversalBehaviourTest::name).toList();
  }

  private static String name(Object leaf) {
    try {
      return (String) leaf.getClass().getMethod("name").invoke(leaf);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Loads the classes a {@link Compilation} wrote, falling back to the test's own classpath. The
   * same job is done by {@code RuntimeCompilationHelper} in hkj-processor, which these tests cannot
   * reach: the traversal generators live here, and the module dependency runs the other way.
   */
  private static final class GeneratedClassLoader extends ClassLoader {
    private final Map<String, byte[]> bytecode = new HashMap<>();

    GeneratedClassLoader(Compilation compilation) {
      super(GeneratedTraversalBehaviourTest.class.getClassLoader());
      for (JavaFileObject file : compilation.generatedFiles()) {
        if (file.getKind() != JavaFileObject.Kind.CLASS) {
          continue;
        }
        String path = file.getName().replace("/" + StandardLocation.CLASS_OUTPUT + "/", "");
        String name = path.substring(0, path.length() - ".class".length()).replace('/', '.');
        bytecode.put(name, read(file));
      }
    }

    Class<?> load(String name) throws ClassNotFoundException {
      return loadClass(name);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      byte[] bytes = bytecode.get(name);
      if (bytes == null) {
        throw new ClassNotFoundException(name);
      }
      return defineClass(name, bytes, 0, bytes.length);
    }

    private static byte[] read(JavaFileObject file) {
      try (InputStream in = file.openInputStream()) {
        return in.readAllBytes();
      } catch (IOException e) {
        throw new IllegalStateException("Could not read " + file.getName(), e);
      }
    }
  }
}
