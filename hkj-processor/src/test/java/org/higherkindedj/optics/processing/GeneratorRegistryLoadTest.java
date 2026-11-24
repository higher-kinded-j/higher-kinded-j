// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A plugin on the processor path that cannot be loaded is reported, and the others still load,
 * rather than one broken jar stopping every compilation with an uncaught exception.
 */
@DisplayName("GeneratorRegistry.load: a plugin that cannot be loaded")
class GeneratorRegistryLoadTest {

  private static final String SERVICES =
      "META-INF/services/org.higherkindedj.optics.processing.spi.TraversableGenerator";

  /** Records each message, as javac's messager would print it. */
  private static final class RecordingMessager implements Messager {
    private final List<String> errors = new ArrayList<>();

    @Override
    public void printMessage(Diagnostic.Kind kind, CharSequence message) {
      if (kind == Diagnostic.Kind.ERROR) {
        errors.add(message.toString());
      }
    }

    @Override
    public void printMessage(Diagnostic.Kind kind, CharSequence message, Element element) {
      printMessage(kind, message);
    }

    @Override
    public void printMessage(
        Diagnostic.Kind kind, CharSequence message, Element element, AnnotationMirror annotation) {
      printMessage(kind, message);
    }

    @Override
    public void printMessage(
        Diagnostic.Kind kind,
        CharSequence message,
        Element element,
        AnnotationMirror annotation,
        AnnotationValue value) {
      printMessage(kind, message);
    }
  }

  /**
   * A class file for {@code plugin.OldOptionalGenerator}, as a plugin built against 0.5.0 would
   * have it: it extends the built-in {@code OptionalGenerator} and overrides the form of {@code
   * generateModifyF} that takes a package, which the built-in declares final. javac refuses to
   * compile such a class, so it is written directly.
   */
  private static byte[] oldSubclass() {
    ClassDesc builtIn =
        ClassDesc.of("org.higherkindedj.optics.processing.generator.basejdk.OptionalGenerator");
    MethodTypeDesc packageForm =
        MethodTypeDesc.of(
            ClassDesc.of("com.palantir.javapoet.CodeBlock"),
            ClassDesc.of("javax.lang.model.element.RecordComponentElement"),
            ClassDesc.of("com.palantir.javapoet.ClassName"),
            ConstantDescs.CD_List,
            ConstantDescs.CD_String);
    return ClassFile.of()
        .build(
            ClassDesc.of("plugin.OldOptionalGenerator"),
            type ->
                type.withSuperclass(builtIn)
                    .withFlags(ClassFile.ACC_PUBLIC)
                    .withMethodBody(
                        ConstantDescs.INIT_NAME,
                        ConstantDescs.MTD_void,
                        ClassFile.ACC_PUBLIC,
                        code ->
                            code.aload(0)
                                .invokespecial(
                                    builtIn, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                .return_())
                    .withMethodBody(
                        "generateModifyF",
                        packageForm,
                        ClassFile.ACC_PUBLIC,
                        code -> code.aconst_null().areturn()));
  }

  /**
   * A compilation's element utilities, which {@code load} only keys what it has reported by: equal
   * to itself alone.
   */
  private static Elements compilation() {
    return (Elements)
        Proxy.newProxyInstance(
            Elements.class.getClassLoader(),
            new Class<?>[] {Elements.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == args[0];
                  case "toString" -> "a compilation's Elements";
                  default -> throw new AssertionError("load reads no Elements: " + method);
                });
  }

  /** A loader over {@code dir} beside the test's own, with the 0.5.x plugin and a missing one. */
  private static URLClassLoader brokenPlugins(Path dir) throws IOException {
    Path classFile = dir.resolve("plugin/OldOptionalGenerator.class");
    Files.createDirectories(classFile.getParent());
    Files.write(classFile, oldSubclass());
    Path services = dir.resolve(SERVICES);
    Files.createDirectories(services.getParent());
    Files.writeString(services, "plugin.OldOptionalGenerator\nplugin.Missing\n");
    return new URLClassLoader(
        new URL[] {dir.toUri().toURL()}, GeneratorRegistryLoadTest.class.getClassLoader());
  }

  @Test
  @DisplayName("is reported as an error naming it, and every other plugin still loads")
  void isReportedAndTheOthersLoad(@TempDir Path dir) throws IOException {
    RecordingMessager messager = new RecordingMessager();

    List<TraversableGenerator> loaded;
    try (URLClassLoader loader = brokenPlugins(dir)) {
      loaded = GeneratorRegistry.load(compilation(), loader, messager);
    }

    assertThat(messager.errors).hasSize(2);
    // What, why and the fix, then the JVM's own report, last.
    assertThat(messager.errors.getFirst())
        .startsWith(
            "A TraversableGenerator plugin on the annotation processor path cannot be loaded. It"
                + " does not link against this hkj-processor")
        .contains("Update the plugin to a release built for this hkj-processor")
        .contains("override the generateModifyF form that takes a GenerationContext instead")
        .contains("The JVM reported: java.lang.IncompatibleClassChangeError: class")
        .contains("plugin.OldOptionalGenerator overrides final method");
    assertThat(messager.errors.getLast())
        .contains("or a class it names is missing, does not link or is not a TraversableGenerator")
        .contains("Add the jar holding any class the plugin is missing")
        .doesNotContain("GenerationContext")
        .endsWith("Provider plugin.Missing not found");
    // The plugins on the test's own processor path, the built-in OptionalGenerator among them.
    assertThat(loaded)
        .extracting(generator -> generator.getClass().getSimpleName())
        .contains("OptionalGenerator", "ListGenerator")
        .doesNotContain("OldOptionalGenerator");
  }

  @Test
  @DisplayName("is reported once per compilation, however many processors load the plugins")
  void isReportedOncePerCompilation(@TempDir Path dir) throws IOException {
    RecordingMessager messager = new RecordingMessager();
    Elements compilation = compilation();

    try (URLClassLoader loader = brokenPlugins(dir)) {
      List<TraversableGenerator> first = GeneratorRegistry.load(compilation, loader, messager);
      List<TraversableGenerator> second = GeneratorRegistry.load(compilation, loader, messager);
      assertThat(messager.errors).as("the second processor of one compilation").hasSize(2);
      // Generators do not override equals, so these are the very instances loaded first.
      assertThat(second).as("the same generators, loaded once").containsExactlyElementsOf(first);
      GeneratorRegistry.load(compilation(), loader, messager);
    }

    assertThat(messager.errors).as("another compilation").hasSize(4);
  }

  /**
   * A class file for {@code name}, extending {@code superName}, with a constructor calling the
   * superclass's; with {@code failingInitialiser}, its static initialiser throws, as a plugin's
   * does where a resource it reads is missing.
   */
  private static byte[] generator(String name, String superName, boolean failingInitialiser) {
    ClassDesc superclass = ClassDesc.of(superName);
    ClassDesc failure = ClassDesc.of("java.lang.IllegalStateException");
    return ClassFile.of()
        .build(
            ClassDesc.of(name),
            type -> {
              type.withSuperclass(superclass)
                  .withFlags(ClassFile.ACC_PUBLIC)
                  .withMethodBody(
                      ConstantDescs.INIT_NAME,
                      ConstantDescs.MTD_void,
                      ClassFile.ACC_PUBLIC,
                      code ->
                          code.aload(0)
                              .invokespecial(
                                  superclass, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                              .return_());
              if (failingInitialiser) {
                type.withMethodBody(
                    ConstantDescs.CLASS_INIT_NAME,
                    ConstantDescs.MTD_void,
                    ClassFile.ACC_STATIC,
                    code ->
                        code.new_(failure)
                            .dup()
                            .ldc("plugin.properties is missing")
                            .invokespecial(
                                failure,
                                ConstantDescs.INIT_NAME,
                                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String))
                            .athrow());
              }
            });
  }

  /** A loader over {@code dir} beside the test's own, holding these plugins, in this order. */
  private static URLClassLoader plugins(Path dir, Map<String, byte[]> classes) throws IOException {
    for (Map.Entry<String, byte[]> plugin : classes.entrySet()) {
      Path classFile = dir.resolve(plugin.getKey().replace('.', '/') + ".class");
      Files.createDirectories(classFile.getParent());
      Files.write(classFile, plugin.getValue());
    }
    Path services = dir.resolve(SERVICES);
    Files.createDirectories(services.getParent());
    Files.writeString(services, String.join("\n", classes.keySet()) + "\n");
    return new URLClassLoader(
        new URL[] {dir.toUri().toURL()}, GeneratorRegistryLoadTest.class.getClassLoader());
  }

  private static final String OPTIONAL_GENERATOR =
      "org.higherkindedj.optics.processing.generator.basejdk.OptionalGenerator";

  @Test
  @DisplayName("is reported once where two fail alike, and the plugins after them still load")
  void pluginsThatFailAlikeAreReportedOnceAndTheRestLoad(@TempDir Path dir) throws IOException {
    // Both extend a class the path lacks, so the JVM reports the same missing class for each.
    Map<String, byte[]> classes = new LinkedHashMap<>();
    classes.put("plugin.NeedsA", generator("plugin.NeedsA", "com.lib.LibBase", false));
    classes.put("plugin.NeedsB", generator("plugin.NeedsB", "com.lib.LibBase", false));
    classes.put(
        "plugin.GoodOptionalGenerator",
        generator("plugin.GoodOptionalGenerator", OPTIONAL_GENERATOR, false));
    RecordingMessager messager = new RecordingMessager();

    List<TraversableGenerator> loaded;
    try (URLClassLoader loader = plugins(dir, classes)) {
      loaded = GeneratorRegistry.load(compilation(), loader, messager);
    }

    assertThat(messager.errors)
        .singleElement()
        .asString()
        .contains("a class it names is missing")
        // The JVM raised it, not the plugin, so no frame of the plugin's follows.
        .endsWith(
            "java.lang.NoClassDefFoundError: com/lib/LibBase, caused by"
                + " java.lang.ClassNotFoundException: com.lib.LibBase");
    assertThat(loaded)
        .extracting(generator -> generator.getClass().getSimpleName())
        .contains("GoodOptionalGenerator");
  }

  @Test
  @DisplayName("names the deepest cause, and where it was thrown, where an initialiser fails")
  void namesTheDeepestCause(@TempDir Path dir) throws IOException {
    RecordingMessager messager = new RecordingMessager();

    try (URLClassLoader loader =
        plugins(
            dir,
            Map.of(
                "plugin.Uninitialised",
                generator("plugin.Uninitialised", OPTIONAL_GENERATOR, true)))) {
      GeneratorRegistry.load(compilation(), loader, messager);
    }

    assertThat(messager.errors)
        .singleElement()
        .asString()
        .contains("Provider plugin.Uninitialised could not be instantiated")
        .contains("caused by java.lang.IllegalStateException: plugin.properties is missing")
        .contains(", at plugin.Uninitialised.<clinit>(");
  }

  @Test
  @DisplayName("reports each of a long run of broken plugins, and loads the plugin after them")
  void loadsPastALongRunOfBrokenPlugins(@TempDir Path dir) throws IOException {
    // More than the run of read failures that ends the loading: a broken plugin moves it on.
    Path classFile = dir.resolve("plugin/GoodOptionalGenerator.class");
    Files.createDirectories(classFile.getParent());
    Files.write(classFile, generator("plugin.GoodOptionalGenerator", OPTIONAL_GENERATOR, false));
    List<String> entries = new ArrayList<>();
    for (int index = 0; index < GeneratorRegistry.READ_FAILURES_IN_A_ROW + 6; index++) {
      entries.add("plugin.Missing" + index);
    }
    entries.add("plugin.GoodOptionalGenerator");
    Path services = dir.resolve(SERVICES);
    Files.createDirectories(services.getParent());
    Files.writeString(services, String.join("\n", entries) + "\n");
    RecordingMessager messager = new RecordingMessager();

    List<TraversableGenerator> loaded;
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {dir.toUri().toURL()}, GeneratorRegistryLoadTest.class.getClassLoader())) {
      loaded = GeneratorRegistry.load(compilation(), loader, messager);
    }

    assertThat(messager.errors).hasSize(GeneratorRegistry.READ_FAILURES_IN_A_ROW + 6);
    assertThat(loaded)
        .extracting(generator -> generator.getClass().getSimpleName())
        .contains("GoodOptionalGenerator");
  }

  @Test
  @Timeout(10)
  @DisplayName("moves past a service file it cannot read, and loads the ones after it")
  void movesPastAServiceFileItCannotRead() {
    // The first file the loader lists is gone; the test's own follow it.
    ClassLoader oneUnreadable =
        new ClassLoader(GeneratorRegistryLoadTest.class.getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> urls = new ArrayList<>();
            if (name.equals(SERVICES)) {
              urls.add(Path.of("/nonexistent/hkj").resolve(SERVICES).toUri().toURL());
            }
            urls.addAll(Collections.list(super.getResources(name)));
            return Collections.enumeration(urls);
          }
        };
    RecordingMessager messager = new RecordingMessager();

    List<TraversableGenerator> loaded =
        GeneratorRegistry.load(compilation(), oneUnreadable, messager);

    assertThat(messager.errors)
        .singleElement()
        .asString()
        .contains("Error accessing configuration file");
    assertThat(loaded)
        .extracting(generator -> generator.getClass().getSimpleName())
        .contains("OptionalGenerator", "ListGenerator");
  }

  @Test
  @Timeout(10)
  @DisplayName("names a cause once where a chain of causes loops back on itself")
  void namesACauseOnceWhereTheChainLoops() {
    RuntimeException outer = new RuntimeException("outer");
    RuntimeException inner = new RuntimeException("inner", outer);
    outer.initCause(inner);
    // Without frames of its own, the report names no frame it was thrown from.
    outer.setStackTrace(new StackTraceElement[0]);
    inner.setStackTrace(new StackTraceElement[0]);

    assertThat(GeneratorRegistry.jvmReport(outer))
        .isEqualTo(
            "java.lang.RuntimeException: outer, caused by java.lang.RuntimeException: inner");
  }

  @Test
  @Timeout(10)
  @DisplayName("ends where the service files cannot be read, which ServiceLoader cannot move past")
  void endsWhereTheServiceFilesCannotBeRead() {
    // ServiceLoader fails on every call when the loader cannot list the files, here with a cause
    // that reads differently each time, which is still reported once.
    ClassLoader unreadable =
        new ClassLoader(GeneratorRegistryLoadTest.class.getClassLoader()) {
          private int attempts;

          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            throw new IOException("the disk went away, attempt " + attempts++);
          }
        };
    RecordingMessager messager = new RecordingMessager();

    List<TraversableGenerator> loaded = GeneratorRegistry.load(compilation(), unreadable, messager);

    assertThat(loaded).isEmpty();
    assertThat(messager.errors)
        .singleElement()
        .asString()
        .contains("The annotation processor path's service files could not be read")
        .contains("replace it with a fresh copy")
        .doesNotContain("Update the plugin")
        .contains("Error locating configuration files")
        .contains("caused by java.io.IOException: the disk went away");
  }
}
