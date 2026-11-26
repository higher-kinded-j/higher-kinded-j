// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles sources the two ways Gradle's incremental compiler runs an aggregating processor over
 * them. A clean build compiles them from source. The build after one unrelated change compiles only
 * the changed file, having deleted everything an aggregating processor generated, and hands javac
 * each unchanged type carrying an annotation such a processor claims by name, so the processor
 * reads it from the class file the earlier build wrote. Both builds run every processor this module
 * registers, each a fresh instance, with {@code -g} as Gradle passes by default.
 */
final class Reprocessing {

  private Reprocessing() {}

  /**
   * What one build generated and reported.
   *
   * @param succeeded whether javac succeeded
   * @param generated each generated source, by its path under the source output
   * @param diagnostics each diagnostic as its kind and message, sorted: a type read from a class
   *     file has no position, and javac meets its members in another order than its source's
   */
  record Run(boolean succeeded, Map<String, String> generated, List<String> diagnostics) {
    Run {
      generated = Collections.unmodifiableSortedMap(new TreeMap<>(generated));
      diagnostics = List.copyOf(diagnostics);
    }
  }

  /** A build's result, and the types Gradle would hand the next build by name. */
  private record Build(Run run, Set<String> aggregated) {
    Build {
      aggregated = Collections.unmodifiableSortedSet(new TreeSet<>(aggregated));
    }
  }

  /** Compiles {@code sources} under {@code dir} as a clean build does. */
  static Run fromSource(Path dir, List<JavaFileObject> sources, List<String> options)
      throws IOException {
    return fromSource(dir, sources, options, List::of);
  }

  /**
   * Compiles {@code sources} under {@code dir} as a clean build does, with the processors {@code
   * ahead} supplies run ahead of this module's, as another library's run beside them.
   */
  static Run fromSource(
      Path dir, List<JavaFileObject> sources, List<String> options, Supplier<List<Processor>> ahead)
      throws IOException {
    return clean(dir, sources, options, ahead).run();
  }

  /**
   * Compiles {@code sources} under {@code dir} as a clean build does, then as the next build does
   * after one new, unannotated source file, and returns what the second build generated and
   * reported.
   */
  static Run fromClassNames(Path dir, List<JavaFileObject> sources, List<String> options)
      throws IOException {
    return fromClassNames(dir, sources, options, List::of);
  }

  /**
   * {@link #fromClassNames(Path, List, List)}, with the processors {@code ahead} supplies run ahead
   * of this module's in both builds, as another library's run beside them. It is asked once for
   * each build, the clean one first, and is to supply fresh processors each time. Gradle deletes
   * what they generate before the second build too, and they write it again there. They are to
   * write sources and resources only: a class file one writes through the filer has no source here
   * to be deleted by, so it would stay on the second build's classpath.
   */
  static Run fromClassNames(
      Path dir, List<JavaFileObject> sources, List<String> options, Supplier<List<Processor>> ahead)
      throws IOException {
    return fromClassNames(dir, sources, List.of(), options, ahead);
  }

  /**
   * {@link #fromClassNames(Path, List, List, Supplier)}, with each of {@code recompiled}, one of
   * {@code sources} declaring one top-level type, compiled from source in the second build, as
   * Gradle recompiles a changed file and hands the unchanged annotated types over by name. Where
   * any is, it is the changed file, in place of the new, unannotated one.
   */
  static Run fromClassNames(
      Path dir,
      List<JavaFileObject> sources,
      List<JavaFileObject> recompiled,
      List<String> options,
      Supplier<List<Processor>> ahead)
      throws IOException {
    Path first = dir.resolve("first");
    Build clean = clean(first, sources, options, ahead);
    if (!clean.run().succeeded()) {
      throw new AssertionError("the clean build failed: " + clean.run().diagnostics());
    }
    // A recompiled type's class files go as its generated ones do, and it is not handed over.
    // Each source is named with '/', as each generated one is.
    Set<String> recompiledSources =
        recompiled.stream()
            .map(source -> source.getName().replaceFirst("^/", ""))
            .collect(Collectors.toSet());
    Set<String> removed = new TreeSet<>(clean.run().generated().keySet());
    removed.addAll(recompiledSources);
    Path classes = withoutGenerated(first, removed, dir.resolve("classes"));
    Path second = dir.resolve("second");
    List<Path> files =
        recompiled.isEmpty()
            ? List.of(placeholder(second.resolve("src")))
            : writeSources(second.resolve("src"), recompiled);
    Set<String> recompiledTypes =
        recompiledSources.stream()
            .map(source -> source.substring(0, source.length() - ".java".length()))
            .map(source -> source.replace('/', '.'))
            .collect(Collectors.toSet());
    return compile(
            second,
            files,
            clean.aggregated().stream().filter(type -> !recompiledTypes.contains(type)).toList(),
            List.of(classes),
            options,
            withRegistered(ahead))
        .run();
  }

  /**
   * Compiles one new, unannotated source under {@code dir} against the class files in {@code
   * classes}, handing the processors {@code classNames}, as Gradle's incremental build hands over
   * an unchanged annotated type read from its class file.
   */
  static Run byName(Path dir, Path classes, List<String> classNames, List<Processor> processors)
      throws IOException {
    return compile(
            dir,
            List.of(placeholder(dir.resolve("src"))),
            classNames,
            List.of(classes),
            List.of(),
            processors)
        .run();
  }

  /** The new, unannotated source a second build compiles where no other source changed. */
  private static Path placeholder(Path src) throws IOException {
    return Files.writeString(
        Files.createDirectories(src.resolve("probe")).resolve("Touched.java"),
        "package probe;\n\nfinal class Touched {}\n");
  }

  /**
   * Compiles {@code sources} under {@code dir} against the class files {@code earlier} compiles to,
   * as Gradle's incremental build compiles a changed source against an unchanged type's class file.
   * {@code earlier} is compiled with the processors where {@code processed}, so its class files
   * hold what they generate, as a previous build's output does, and without them otherwise, as a
   * hand-written class is.
   */
  static Run againstClassFiles(
      Path dir,
      List<JavaFileObject> earlier,
      boolean processed,
      List<JavaFileObject> sources,
      List<String> options)
      throws IOException {
    Path first = dir.resolve("earlier");
    Build compiled =
        build(first, earlier, List.of(), options, processed ? registeredProcessors() : List.of());
    if (!compiled.run().succeeded()) {
      throw new AssertionError("the earlier build failed: " + compiled.run().diagnostics());
    }
    return build(
            dir.resolve("later"),
            sources,
            List.of(first.resolve("out")),
            options,
            registeredProcessors())
        .run();
  }

  private static Build clean(
      Path dir, List<JavaFileObject> sources, List<String> options, Supplier<List<Processor>> ahead)
      throws IOException {
    return build(dir, sources, List.of(), options, withRegistered(ahead));
  }

  /** The processors {@code ahead} supplies, then this module's. */
  private static List<Processor> withRegistered(Supplier<List<Processor>> ahead) {
    return Stream.concat(ahead.get().stream(), registeredProcessors().stream()).toList();
  }

  private static Build build(
      Path dir,
      List<JavaFileObject> sources,
      List<Path> classDirs,
      List<String> options,
      List<Processor> processors)
      throws IOException {
    return compile(
        dir, writeSources(dir.resolve("src"), sources), List.of(), classDirs, options, processors);
  }

  /** Writes each source under {@code src}, at the path its name gives it. */
  private static List<Path> writeSources(Path src, List<JavaFileObject> sources)
      throws IOException {
    List<Path> files = new ArrayList<>();
    for (JavaFileObject source : sources) {
      // JavaFileObjects.forSourceString names the file /com/example/Contact.java, or Contact.java
      // in the unnamed package.
      Path file = src.resolve(source.getName().replaceFirst("^/", ""));
      Files.createDirectories(file.getParent());
      files.add(Files.writeString(file, source.getCharContent(true)));
    }
    return files;
  }

  /**
   * Copies the clean build's class files to {@code target}, leaving out every one of a generated
   * type and every resource a processor wrote, as Gradle deletes them before the next build.
   */
  private static Path withoutGenerated(Path build, Set<String> generatedSources, Path target)
      throws IOException {
    Set<String> generatedTypes = typesOf(generatedSources).collect(Collectors.toSet());
    Path out = build.resolve("out");
    try (Stream<Path> walk = Files.walk(out)) {
      for (Path file : walk.filter(Files::isRegularFile).toList()) {
        String relative = slashed(out.relativize(file));
        if (relative.endsWith(".class")
            && !ofGenerated(
                relative.substring(0, relative.length() - ".class".length()), generatedTypes)) {
          Files.createDirectories(target.resolve(relative).getParent());
          Files.copy(file, target.resolve(relative));
        }
      }
    }
    return target;
  }

  /**
   * Whether a class file's binary name, with '/' between packages, is a generated type's, or that
   * of a class nested in one. A top-level name may hold a '$' too, so each name up to one is asked.
   */
  private static boolean ofGenerated(String binary, Set<String> generatedTypes) {
    return IntStream.rangeClosed(0, binary.length())
        .filter(end -> end == binary.length() || binary.charAt(end) == '$')
        .mapToObj(end -> binary.substring(0, end))
        .anyMatch(generatedTypes::contains);
  }

  /** The types the Java sources among {@code files} declare, with '/' between packages. */
  private static Stream<String> typesOf(Collection<String> files) {
    return files.stream()
        .filter(file -> file.endsWith(".java"))
        .map(file -> file.substring(0, file.length() - ".java".length()));
  }

  /** A relative path with '/' between its names, whatever the platform's separator. */
  private static String slashed(Path relative) {
    return relative.toString().replace(File.separatorChar, '/');
  }

  private static List<Processor> registeredProcessors() {
    return GeneratorTestHelper.registeredProcessors().toList();
  }

  private static Build compile(
      Path dir,
      List<Path> files,
      List<String> classNames,
      List<Path> classDirs,
      List<String> options,
      List<Processor> processors)
      throws IOException {
    Path out = Files.createDirectories(dir.resolve("out"));
    Path gen = Files.createDirectories(dir.resolve("gen"));
    List<File> classpath = new ArrayList<>();
    classDirs.forEach(classDir -> classpath.add(classDir.toFile()));
    classpath.addAll(GeneratorTestHelper.classpathWith());
    List<String> javacOptions =
        new ArrayList<>(
            List.of(
                "-g",
                "-d",
                out.toString(),
                "-s",
                gen.toString(),
                "-sourcepath",
                "",
                "-classpath",
                classpath.stream()
                    .map(File::toString)
                    .collect(Collectors.joining(File.pathSeparator))));
    javacOptions.addAll(options);
    AggregatedTypes aggregated = new AggregatedTypes(aggregatedAnnotations());
    // First, as javac offers a later processor no annotation an earlier one has claimed.
    List<Processor> all = new ArrayList<>(List.of(aggregated));
    all.addAll(processors);
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean succeeded;
    try (StandardJavaFileManager fileManager =
        javac.getStandardFileManager(diagnostics, null, null)) {
      JavaCompiler.CompilationTask task =
          javac.getTask(
              null,
              fileManager,
              diagnostics,
              javacOptions,
              classNames.isEmpty() ? null : classNames,
              fileManager.getJavaFileObjectsFromPaths(files));
      task.setProcessors(all);
      succeeded = task.call();
    }
    Map<String, String> generated = new TreeMap<>();
    try (Stream<Path> walk = Files.walk(gen)) {
      for (Path file : walk.filter(Files::isRegularFile).toList()) {
        generated.put(slashed(gen.relativize(file)), Files.readString(file));
      }
    }
    Set<String> generatedTypes =
        typesOf(generated.keySet())
            .map(source -> source.replace('/', '.'))
            .collect(Collectors.toSet());
    return new Build(
        new Run(
            succeeded,
            generated,
            diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
                .filter(line -> !line.startsWith(Diagnostic.Kind.NOTE + ": Some input files"))
                .sorted()
                .toList()),
        aggregated.types().stream()
            .filter(type -> !generatedTypes.contains(type))
            .collect(Collectors.toCollection(TreeSet::new)));
  }

  /** Every annotation a processor this module registers with Gradle as aggregating claims. */
  private static Set<String> aggregatedAnnotations() throws IOException {
    Map<String, String> categories = GeneratorTestHelper.gradleRegistrations();
    return GeneratorTestHelper.registeredProcessors()
        .filter(processor -> "aggregating".equals(categories.get(processor.getClass().getName())))
        .flatMap(processor -> processor.getSupportedAnnotationTypes().stream())
        .collect(Collectors.toSet());
  }

  /**
   * Records the top-level types carrying an annotation an aggregating processor claims, as Gradle
   * records them for the next build to hand over by name. It claims nothing.
   */
  private static final class AggregatedTypes extends AbstractProcessor {

    private final Set<String> claimed;
    private final Set<String> types = new TreeSet<>();

    AggregatedTypes(Set<String> claimed) {
      this.claimed = claimed;
    }

    Set<String> types() {
      return types;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
      annotations.stream()
          .filter(annotation -> claimed.contains(annotation.getQualifiedName().toString()))
          .flatMap(annotation -> round.getElementsAnnotatedWith(annotation).stream())
          .map(Reprocessing::recordedName)
          .forEach(types::add);
      return false;
    }
  }

  /**
   * The name Gradle records an annotated element under: its top-level type's, or, for an annotated
   * package, its {@code package-info}'s.
   */
  private static String recordedName(Element element) {
    if (element instanceof PackageElement annotatedPackage) {
      return annotatedPackage.getQualifiedName() + ".package-info";
    }
    Element at = element;
    while (!(at instanceof TypeElement type
        && type.getEnclosingElement().getKind() == ElementKind.PACKAGE)) {
      at = at.getEnclosingElement();
    }
    return type.getQualifiedName().toString();
  }
}
