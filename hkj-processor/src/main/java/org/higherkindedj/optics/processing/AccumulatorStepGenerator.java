// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

/**
 * Generates the staged accumulating-assembly builder classes (issue #581).
 *
 * <p>Six families are stamped from one template, one class per arity, each into its carrier's own
 * package (per the ArchUnit package-structure rules: {@code Validated*} classes reside in {@code
 * ..validated..}, {@code EitherOrBoth*} in {@code ..eitherorboth..}, and the effect-path stages
 * alongside {@code ValidationPath} in {@code ..effect..}):
 *
 * <ul>
 *   <li>{@code ValidatedAccumN} / {@code ValidatedFieldsN} over {@code Validated}
 *   <li>{@code ValidationPathAccumN} / {@code ValidationPathFieldsN} over {@code ValidationPath}
 *   <li>{@code EitherOrBothAccumN} / {@code EitherOrBothFieldsN} over {@code EitherOrBoth}
 * </ul>
 *
 * <p>Every stage transition delegates to the existing accumulation primitives — {@code
 * Validated.ap(..., NonEmptyList.semigroup())} with the accumulator on the <b>function side</b>
 * (which yields declaration-order errors), {@code ValidationPath.zipWithAccum}, or {@code
 * EitherOrBoth.zipWithAccum} (both combine {@code semigroup.combine(accumulated, next)}, likewise
 * declaration order). The {@code Fields} flavour prepends labels via {@code FieldError.at} on the
 * incoming value before the merge, so accumulation itself never inspects labels.
 *
 * <p>The {@code Fields} flavour also ends in {@code construct(f, fallbackMessage)}, beside {@code
 * apply} (the same refusal rule {@link GuardedConstruction} emits for the generated mappings, kept
 * separate because that one guards a constructor call it writes itself): a function that may refuse
 * the accumulated fields, typically a record's canonical constructor enforcing an invariant, runs
 * inside a guard that reports a {@code RuntimeException} it throws as an unlabelled {@code
 * FieldError}. The generic flavour has no {@code FieldError} to build, so it offers {@code apply}
 * only.
 */
final class AccumulatorStepGenerator {

  private static final String VALIDATED_PACKAGE = "org.higherkindedj.hkt.validated";
  private static final String EFFECT_PACKAGE = "org.higherkindedj.hkt.effect";
  private static final String EITHER_OR_BOTH_PACKAGE = "org.higherkindedj.hkt.eitherorboth";

  private static final String[] TYPE_PARAMS = {
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P"
  };

  /** The carrier a family accumulates over. */
  private enum Carrier {
    VALIDATED,
    VALIDATION_PATH,
    EITHER_OR_BOTH
  }

  /** One of the six stage families. */
  private record Family(String prefix, Carrier carrier, boolean labelled, String packageName) {}

  /**
   * How a carrier's {@code construct} binds the accumulated fields to the guarded call: what opens
   * the bind, the call that wraps the constructed value, the refusal, and what closes it. Only the
   * caller's function sits inside the guard, so the wrapping call, which rejects a {@code null} the
   * function returned, throws to the caller as {@code apply} does.
   */
  private record GuardedBind(String bind, String wrap, String refused, String end) {}

  private static final List<Family> FAMILIES =
      List.of(
          new Family("ValidatedAccum", Carrier.VALIDATED, false, VALIDATED_PACKAGE),
          new Family("ValidatedFields", Carrier.VALIDATED, true, VALIDATED_PACKAGE),
          new Family("ValidationPathAccum", Carrier.VALIDATION_PATH, false, EFFECT_PACKAGE),
          new Family("ValidationPathFields", Carrier.VALIDATION_PATH, true, EFFECT_PACKAGE),
          new Family("EitherOrBothAccum", Carrier.EITHER_OR_BOTH, false, EITHER_OR_BOTH_PACKAGE),
          new Family("EitherOrBothFields", Carrier.EITHER_OR_BOTH, true, EITHER_OR_BOTH_PACKAGE));

  private AccumulatorStepGenerator() {}

  /**
   * Generates every family for the requested arity range. The originating element (the annotated
   * package) is passed to the {@link javax.annotation.processing.Filer} so build tools can track
   * which input triggered each generated file (incremental compilation support).
   */
  static void generate(
      int minArity, int maxArity, ProcessingEnvironment env, Element originatingElement)
      throws IOException {
    for (Family family : FAMILIES) {
      for (int arity = minArity; arity <= maxArity; arity++) {
        String className = family.prefix() + arity;
        JavaFileObject file =
            env.getFiler()
                .createSourceFile(family.packageName() + "." + className, originatingElement);
        try (Writer writer = file.openWriter()) {
          writer.write(generateStage(family, arity, maxArity));
        }
      }
    }
  }

  // =============================================================================
  // Template
  // =============================================================================

  private static String generateStage(Family family, int arity, int maxArity) {
    boolean terminal = arity == maxArity;
    StringBuilder sb = new StringBuilder();
    sb.append("// Generated by hkj-processor. Do not edit.\n");
    sb.append("package ").append(family.packageName()).append(";\n\n");
    appendImports(sb, family, arity, terminal);
    appendClassJavadoc(sb, family, arity, terminal);
    sb.append("@Generated\n");
    sb.append("public final class ")
        .append(family.prefix())
        .append(arity)
        .append("<")
        .append(classTypeParams(family, arity))
        .append("> {\n\n");
    sb.append("  private final ").append(stateType(family, arity)).append(" accumulated;\n\n");
    sb.append("  ")
        .append(family.prefix())
        .append(arity)
        .append("(")
        .append(stateType(family, arity))
        .append(" accumulated) {\n");
    sb.append("    this.accumulated = accumulated;\n");
    sb.append("  }\n\n");
    if (!terminal) {
      if (family.labelled()) {
        appendFieldMethod(sb, family, arity);
      }
      appendAndMethod(sb, family, arity);
    }
    appendApplyMethod(sb, family, arity);
    if (family.labelled()) {
      appendConstructMethod(sb, family, arity);
    }
    sb.append("}\n");
    return sb.toString();
  }

  private static void appendImports(StringBuilder sb, Family family, int arity, boolean terminal) {
    List<String> imports = new ArrayList<>();
    imports.add("java.util.Objects");
    if (arity == 1) {
      imports.add("java.util.function.Function");
    } else if (arity == 2) {
      imports.add("java.util.function.BiFunction");
    } else {
      imports.add("org.higherkindedj.hkt.function.Function" + arity);
    }
    imports.add("org.higherkindedj.hkt.nonemptylist.NonEmptyList");
    if (!terminal) {
      imports.add("org.higherkindedj.hkt.tuple.Tuple");
    }
    if (arity >= 2) {
      imports.add("org.higherkindedj.hkt.tuple.Tuple" + arity);
    }
    // Carrier types and FieldError resolve from the family's own package where possible; the
    // effect and eitherorboth families import FieldError from the validated package.
    if (family.labelled() && !VALIDATED_PACKAGE.equals(family.packageName())) {
      imports.add("org.higherkindedj.hkt.validated.FieldError");
      if (family.carrier() == Carrier.VALIDATION_PATH) {
        // construct binds the path's Validated to the guarded call.
        imports.add("org.higherkindedj.hkt.validated.Validated");
      }
    }
    imports.add("org.higherkindedj.optics.annotations.Generated");
    imports.stream().sorted().forEach(i -> sb.append("import ").append(i).append(";\n"));
    sb.append("\n");
  }

  private static void appendClassJavadoc(
      StringBuilder sb, Family family, int arity, boolean terminal) {
    String carrierName =
        switch (family.carrier()) {
          case VALIDATED -> "Validated";
          case VALIDATION_PATH -> "ValidationPath";
          case EITHER_OR_BOTH -> "EitherOrBoth";
        };
    boolean tolerant = family.carrier() == Carrier.EITHER_OR_BOTH;
    String flavour =
        family.labelled()
            ? "a labelled ({@code FieldError}) " + (tolerant ? "tolerant " : "") + "accumulating"
            : (tolerant ? "a tolerant accumulating" : "an accumulating");
    sb.append("/**\n");
    sb.append(" * Stage ")
        .append(arity)
        .append(" of ")
        .append(flavour)
        .append(" {@code ")
        .append(carrierName)
        .append("} assembly.\n");
    sb.append(" *\n");
    if (tolerant) {
      sb.append(
          " * <p>Warnings accumulate ({@code Both}) while the value keeps flowing; any {@code"
              + " Left}\n"
              + " * makes the assembly {@code Left}, still keeping every warning, in"
              + " field-declaration order.\n");
    } else {
      sb.append(" * <p>All errors accumulate; they emerge in field-declaration order.\n");
    }
    if (terminal) {
      sb.append(" *\n");
      sb.append(
          family.labelled()
              ? " * <p>Terminal stage: {@code apply} or {@code construct} only. For wider"
                  + " assemblies, nest a\n * sub-record per group of fields.\n"
              : " * <p>Terminal stage: {@code apply} only. For wider assemblies, nest a sub-record"
                  + " per\n * group of fields.\n");
    }
    sb.append(" */\n");
  }

  // =============================================================================
  // Type helpers
  // =============================================================================

  private static String fieldTypeParams(int arity) {
    return Arrays.stream(TYPE_PARAMS, 0, arity).collect(Collectors.joining(", "));
  }

  private static String classTypeParams(Family family, int arity) {
    return family.labelled() ? fieldTypeParams(arity) : "X, " + fieldTypeParams(arity);
  }

  private static String errorChannel(Family family) {
    return family.labelled() ? "NonEmptyList<FieldError>" : "NonEmptyList<X>";
  }

  private static String valueType(int arity) {
    return arity == 1 ? "A" : "Tuple" + arity + "<" + fieldTypeParams(arity) + ">";
  }

  private static String stateType(Family family, int arity) {
    String carrier =
        switch (family.carrier()) {
          case VALIDATED -> "Validated";
          case VALIDATION_PATH -> "ValidationPath";
          case EITHER_OR_BOTH -> "EitherOrBoth";
        };
    return carrier + "<" + errorChannel(family) + ", " + valueType(arity) + ">";
  }

  /**
   * The public carrier type accepted by {@code and}/{@code field} and returned by {@code apply}.
   */
  private static String surfaceType(Family family, String of) {
    String carrier =
        switch (family.carrier()) {
          case VALIDATED -> "Validated";
          case VALIDATION_PATH -> "ValidationPath";
          case EITHER_OR_BOTH -> "EitherOrBoth";
        };
    return carrier + "<" + errorChannel(family) + ", " + of + ">";
  }

  private static String nextStageType(Family family, int arity) {
    String next = TYPE_PARAMS[arity];
    return family.prefix()
        + (arity + 1)
        + "<"
        + (family.labelled() ? "" : "X, ")
        + fieldTypeParams(arity)
        + ", "
        + next
        + ">";
  }

  /** Rebuilds the accumulated tuple with the next field appended. */
  private static String tupleRebuild(int arity, String accVar, String nextVar) {
    if (arity == 1) {
      return "Tuple.of(" + accVar + ", " + nextVar + ")";
    }
    String spread =
        IntStream.rangeClosed(1, arity)
            .mapToObj(i -> accVar + "._" + i + "()")
            .collect(Collectors.joining(", "));
    return "Tuple.of(" + spread + ", " + nextVar + ")";
  }

  private static String applyFunctionType(int arity) {
    if (arity == 1) {
      return "Function<? super A, ? extends R>";
    }
    String bounds =
        Arrays.stream(TYPE_PARAMS, 0, arity)
            .map(p -> "? super " + p)
            .collect(Collectors.joining(", "));
    String name = arity == 2 ? "BiFunction" : "Function" + arity;
    return name + "<" + bounds + ", ? extends R>";
  }

  private static String applySpread(int arity) {
    return IntStream.rangeClosed(1, arity)
        .mapToObj(i -> "t._" + i + "()")
        .collect(Collectors.joining(", "));
  }

  // =============================================================================
  // Methods
  // =============================================================================

  private static void appendFieldMethod(StringBuilder sb, Family family, int arity) {
    String next = TYPE_PARAMS[arity];
    String errs = family.carrier() == Carrier.EITHER_OR_BOTH ? "warnings'" : "errors'";
    sb.append("  /**\n");
    sb.append("   * Adds the next validated field, prepending {@code label} onto each of its ")
        .append(errs)
        .append("\n");
    sb.append("   * paths.\n");
    sb.append("   */\n");
    sb.append("  public <")
        .append(next)
        .append("> ")
        .append(nextStageType(family, arity))
        .append(" field(String label, ")
        .append(surfaceType(family, next))
        .append(" value) {\n");
    sb.append("    Objects.requireNonNull(label, \"label must not be null\");\n");
    sb.append("    Objects.requireNonNull(value, \"value must not be null\");\n");
    String labelled =
        switch (family.carrier()) {
          case VALIDATED -> "value.mapError(errors -> errors.map(err -> err.at(label)))";
          case VALIDATION_PATH ->
              "Path.validatedNel(\n"
                  + "        value.run().mapError(errors -> errors.map(err -> err.at(label))))";
          case EITHER_OR_BOTH -> "value.mapLeft(errors -> errors.map(err -> err.at(label)))";
        };
    sb.append("    return and(").append(labelled).append(");\n");
    sb.append("  }\n\n");
  }

  private static String mergeExpression(Family family, int arity, String accVar) {
    String rebuild = tupleRebuild(arity, accVar, "next");
    return switch (family.carrier()) {
      case VALIDATED ->
          "value.ap(\n"
              + "            accumulated.map("
              + accVar
              + " -> next -> "
              + rebuild
              + "),\n"
              + "            NonEmptyList.semigroup())";
      case VALIDATION_PATH ->
          "accumulated.zipWithAccum(\n            value, ("
              + accVar
              + ", next) -> "
              + rebuild
              + ")";
      case EITHER_OR_BOTH ->
          "accumulated.zipWithAccum(\n"
              + "            value,\n"
              + "            NonEmptyList.semigroup(),\n"
              + "            ("
              + accVar
              + ", next) -> "
              + rebuild
              + ")";
    };
  }

  private static void appendAndMethod(StringBuilder sb, Family family, int arity) {
    String next = TYPE_PARAMS[arity];
    String accVar = arity == 1 ? "first" : "t";
    boolean tolerant = family.carrier() == Carrier.EITHER_OR_BOTH;
    if (family.labelled()) {
      sb.append("  /**\n");
      sb.append("   * Adds the next field without attaching a label: for values whose ")
          .append(tolerant ? "warnings" : "errors")
          .append(" already\n");
      sb.append(
          "   * carry their paths (for example a pre-labelled sub-assembly that must not be\n");
      sb.append("   * re-prefixed) or genuinely unattributable ")
          .append(tolerant ? "warnings" : "errors")
          .append(". Prefer {@code field(label, value)}\n");
      sb.append("   * for leaf validators.\n");
      sb.append("   */\n");
    } else if (tolerant) {
      sb.append(
          "  /** Adds the next field; warnings accumulate while the value keeps flowing. */\n");
    } else {
      sb.append("  /** Adds the next validated field; all errors accumulate. */\n");
    }
    sb.append("  public <")
        .append(next)
        .append("> ")
        .append(nextStageType(family, arity))
        .append(" and(")
        .append(surfaceType(family, next))
        .append(" value) {\n");
    sb.append("    Objects.requireNonNull(value, \"value must not be null\");\n");
    String merge = mergeExpression(family, arity, accVar);
    sb.append("    return new ")
        .append(family.prefix())
        .append(arity + 1)
        .append("<>(\n        ")
        .append(merge)
        .append(");\n");
    sb.append("  }\n\n");
  }

  private static void appendApplyMethod(StringBuilder sb, Family family, int arity) {
    if (family.labelled()) {
      sb.append("  /**\n");
      sb.append("   * Completes the assembly by applying {@code f} to the accumulated fields.\n");
      sb.append(
          "   *\n   * <p>{@code f} runs whatever it is handed, so an exception it throws escapes"
              + " the assembly.\n   * Where it may refuse the fields, a record's canonical"
              + " constructor enforcing an invariant,\n   * {@code construct} guards the call"
              + " instead.\n");
      sb.append("   */\n");
    } else {
      sb.append(
          "  /** Completes the assembly by applying {@code f} to the accumulated fields. */\n");
    }
    sb.append("  public <R> ")
        .append(surfaceType(family, "R"))
        .append(" apply(")
        .append(applyFunctionType(arity))
        .append(" f) {\n");
    sb.append("    Objects.requireNonNull(f, \"f must not be null\");\n");
    String mapped =
        arity == 1
            ? "accumulated.map(f)"
            : "accumulated.map(t -> f.apply(" + applySpread(arity) + "))";
    sb.append("    return ").append(mapped).append(";\n");
    sb.append("  }\n");
  }

  /**
   * The labelled families' guarded terminal: {@code construct}, for a function that may refuse the
   * accumulated fields, typically a record's canonical constructor enforcing an invariant. It runs
   * only once every field is valid, and a {@code RuntimeException} it throws becomes an unlabelled
   * {@code FieldError} carrying its message, or {@code fallbackMessage} when that is missing or
   * blank, so an enclosing {@code field(label, ...)} locates it. Only {@code f} runs inside the
   * guard. A tolerant ({@code EitherOrBoth}) refusal keeps the warnings already accumulated.
   */
  private static void appendConstructMethod(StringBuilder sb, Family family, int arity) {
    boolean tolerant = family.carrier() == Carrier.EITHER_OR_BOTH;
    sb.append("\n  /**\n");
    sb.append(
        "   * Completes the assembly like {@code apply}, for a function that may refuse the"
            + " accumulated\n"
            + "   * fields, typically a record's canonical constructor enforcing an invariant. Once"
            + " every\n"
            + "   * field is valid, a {@code RuntimeException} {@code f} throws becomes an"
            + " unlabelled {@code\n"
            + "   * FieldError} carrying its message, or {@code fallbackMessage} when that is"
            + " missing or blank,\n"
            + "   * so an enclosing {@code field(label, ...)} locates it. Only {@code f} runs"
            + " inside the guard,\n"
            + "   * so a {@code null} it returns still throws as it does from {@code apply}. Any"
            + " {@code\n"
            + "   * RuntimeException} counts, so a bug in {@code f} reaches whoever reads the"
            + " errors as its\n"
            + "   * message: keep it to checks on its arguments.\n");
    if (tolerant) {
      sb.append("   * A refusal keeps every warning accumulated so far, beside the refusal.\n");
    }
    sb.append("   *\n");
    sb.append("   * @param f the function over the accumulated fields; must not be null\n");
    sb.append(
        "   * @param fallbackMessage the message when the exception carries none, such as {@code \"not"
            + " a\n"
            + "   *     valid Range\"}; must not be null or blank\n");
    sb.append("   * @param <R> the assembled type\n");
    sb.append(
        "   * @return the assembled value, or the fields' "
            + (tolerant ? "warnings and the refusal" : "errors, or the refusal")
            + "\n");
    sb.append(
        "   * @throws NullPointerException if {@code f} or {@code fallbackMessage} is null\n");
    sb.append("   * @throws IllegalArgumentException if {@code fallbackMessage} is blank\n");
    sb.append("   */\n");
    sb.append("  public <R> ")
        .append(surfaceType(family, "R"))
        .append(" construct(")
        .append(applyFunctionType(arity))
        .append(" f, String fallbackMessage) {\n");
    sb.append("    Objects.requireNonNull(f, \"f must not be null\");\n");
    sb.append(
        "    Objects.requireNonNull(fallbackMessage, \"fallbackMessage must not be null\");\n");
    sb.append("    if (fallbackMessage.isBlank()) {\n");
    sb.append("      throw new IllegalArgumentException(\"fallbackMessage must not be blank\");\n");
    sb.append("    }\n");
    String call = "f.apply(" + (arity == 1 ? "t" : applySpread(arity)) + ")";
    String refusal =
        "FieldError.of(message == null || message.isBlank() ? fallbackMessage : message)";
    GuardedBind guard =
        switch (family.carrier()) {
          case VALIDATED ->
              new GuardedBind(
                  "accumulated.flatMap(\n        ",
                  "Validated.validNel",
                  "Validated.invalidNel(" + refusal + ")",
                  ")");
          case VALIDATION_PATH ->
              new GuardedBind(
                  "Path.validatedNel(\n        accumulated.run().flatMap(\n            ",
                  "Validated.validNel",
                  "Validated.invalidNel(" + refusal + ")",
                  "))");
          case EITHER_OR_BOTH ->
              new GuardedBind(
                  "accumulated.flatMap(\n        NonEmptyList.semigroup(),\n        ",
                  "EitherOrBoth.right",
                  "EitherOrBoth.left(NonEmptyList.single(" + refusal + "))",
                  ")");
        };
    // The lambda's body indents under wherever the bind left it, so every carrier reads alike.
    String lambda = " ".repeat(guard.bind().length() - guard.bind().lastIndexOf('\n') - 1);
    String body = lambda + "  ";
    String inner = body + "  ";
    sb.append("    return ").append(guard.bind()).append("t -> {\n");
    sb.append(body).append("R constructed;\n");
    sb.append(body).append("try {\n");
    sb.append(inner).append("constructed = ").append(call).append(";\n");
    sb.append(body).append("} catch (RuntimeException refused) {\n");
    sb.append(inner).append("String message = refused.getMessage();\n");
    sb.append(inner).append("return ").append(guard.refused()).append(";\n");
    sb.append(body).append("}\n");
    sb.append(body).append("return ").append(guard.wrap()).append("(constructed);\n");
    sb.append(lambda).append("}").append(guard.end()).append(";\n");
    sb.append("  }\n");
  }
}
