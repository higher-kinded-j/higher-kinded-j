// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;

/**
 * Compiles a consumer's sources with NullAway in JSpecify mode, every source in a
 * {@code @NullMarked} package, as a consumer checking only {@code @NullMarked} code runs it.
 */
final class NullAwayCompiler {

  /** The two configurations a consumer runs NullAway in, as the book's nullness section names. */
  enum Configuration {
    /**
     * JSpecify mode alone, under which NullAway checks a lambda against {@code Function} and its
     * subinterfaces, and against no other functional interface from the JDK.
     */
    JSPECIFY_MODE_ALONE("in JSpecify mode alone", List.of()),
    /**
     * With both options the book names: the JSpecify JDK models, which annotate the JDK's other
     * functional interfaces, and the wildcard handling that holds a {@code Supplier<@Nullable T>}
     * to a {@code Supplier<? extends A>} parameter.
     */
    BOTH_OPTIONS(
        "with both options",
        List.of(
            "-XepOpt:NullAway:JSpecifyJDKModels=true",
            "-XepOpt:NullAway:HandleWildcardGenerics=true"));

    private final String displayName;
    private final List<String> flags;

    Configuration(String displayName, List<String> flags) {
      this.displayName = displayName;
      this.flags = flags;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  private static final JavaFileObject NULL_MARKED_PACKAGE =
      JavaFileObjects.forSourceString(
          "com.example.package-info",
          """
          @NullMarked
          package com.example;

          import org.jspecify.annotations.NullMarked;
          """);

  private NullAwayCompiler() {}

  static Compilation compile(Configuration configuration, JavaFileObject... sources) {
    return compile(configuration, List.of(), List.of(), sources);
  }

  static Compilation compile(
      Configuration configuration,
      List<Processor> processors,
      List<String> extraErrorProneFlags,
      JavaFileObject... sources) {
    String errorProne =
        String.join(
            " ",
            Stream.of(
                    Stream.of(
                        "-Xplugin:ErrorProne",
                        "-XepDisableAllChecks",
                        "-Xep:NullAway:ERROR",
                        "-XepOpt:NullAway:OnlyNullMarked=true",
                        "-XepOpt:NullAway:JSpecifyMode=true"),
                    configuration.flags.stream(),
                    extraErrorProneFlags.stream())
                .flatMap(flags -> flags)
                .toList());
    return javac()
        .withProcessors(processors)
        .withOptions(
            // Error Prone looks for a plugin checker such as NullAway on the processor path only.
            "--processor-path",
            System.getProperty("java.class.path"),
            "-XDcompilePolicy=simple",
            "--should-stop=ifError=FLOW",
            errorProne)
        .compile(Stream.concat(Stream.of(NULL_MARKED_PACKAGE), Arrays.stream(sources)).toList());
  }

  static JavaFileObject source(String qualifiedName, String body) {
    return JavaFileObjects.forSourceString(qualifiedName, body);
  }
}
