// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.io.IOException;
import java.io.Writer;
import javax.annotation.processing.ProcessingEnvironment;

/**
 * Generates {@code *PathStepsN} classes for all 9 ForPath effect types.
 *
 * <p>Each path type follows the same structural pattern as the hand-written ForPath inner classes
 * but is generated as a top-level public final class with a package-private constructor.
 */
final class ForPathStepGenerator {

  private static final String PACKAGE = "org.higherkindedj.hkt.expression";
  private static final String[] TYPE_PARAMS = {
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"
  };

  private static final String YIELD_NULL_MSG = "The yield function must not return null.";

  // Value type params for types with extra type param that would collide.
  // Either has extra E, so skip E: A, B, C, D, F, G, H, I, J, K, L, N
  // Note: M is skipped because 'm' is used as the local monad variable name in generated code,
  // which would collide with the lambda parameter derived from M.toLowerCase().
  private static final String[] EITHER_VALUE_PARAMS = {
    "A", "B", "C", "D", "F", "G", "H", "I", "J", "K", "L", "N"
  };
  // Generic has extra F, so skip F: A, B, C, D, E, G, H, I, J, K, L, M
  private static final String[] GENERIC_VALUE_PARAMS = {
    "A", "B", "C", "D", "E", "G", "H", "I", "J", "K", "L", "M"
  };

  private ForPathStepGenerator() {}

  /**
   * Returns the value type parameters for a descriptor, avoiding collision with extra type params.
   */
  private static String[] valueParams(PathTypeDescriptor desc) {
    if ("E".equals(desc.extraTypeParamName) && !desc.isGeneric) return EITHER_VALUE_PARAMS;
    if (desc.isGeneric) return GENERIC_VALUE_PARAMS;
    return TYPE_PARAMS;
  }

  // =========================================================================
  // Descriptor for each path type
  // =========================================================================

  private record PathTypeDescriptor(
      String pathTypeName, // e.g. "MaybePath"
      String stepsPrefix, // e.g. "MaybePathSteps"
      String witnessType, // e.g. "MaybeKind.Witness"
      String monadType, // e.g. "MaybeMonad"
      String monadAccess, // e.g. "MaybeMonad.INSTANCE" or "EitherMonad.instance()"
      boolean isStaticMonad, // true if monad is static field
      String kindHelperField, // e.g. "MaybeKindHelper.MAYBE"
      String pathFactoryExpr, // e.g. "Path.maybe" -- will be combined with narrow/runKind
      boolean filterable, // true for Maybe, Optional, NonDet
      boolean hasExtraTypeParam, // true for Either (E) and Generic (F)
      String extraTypeParamName, // "E" for Either, "F" for Generic, null otherwise
      String extraTypeParamBound, // null for Either (unbounded), bound for Generic
      boolean isGeneric, // true only for Generic (uses runKind, instance monad)
      boolean isNonDet, // true only for NonDet (uses NonDetPath.of)
      int currentMaxArity // current hand-written max arity
      ) {}

  private static final PathTypeDescriptor[] PATH_TYPES = {
    new PathTypeDescriptor(
        "MaybePath",
        "MaybePathSteps",
        "MaybeKind.Witness",
        "MaybeMonad",
        "MaybeMonad.INSTANCE",
        true,
        "MaybeKindHelper.MAYBE",
        "Path.maybe",
        true,
        false,
        null,
        null,
        false,
        false,
        1),
    new PathTypeDescriptor(
        "OptionalPath",
        "OptionalPathSteps",
        "OptionalKind.Witness",
        "OptionalMonad",
        "OptionalMonad.INSTANCE",
        true,
        "OptionalKindHelper.OPTIONAL",
        "Path.optional",
        true,
        false,
        null,
        null,
        false,
        false,
        1),
    new PathTypeDescriptor(
        "EitherPath",
        "EitherPathSteps",
        "EitherKind.Witness<E>",
        "EitherMonad",
        "EitherMonad.instance()",
        false,
        "EitherKindHelper.EITHER",
        "Path.either",
        false,
        true,
        "E",
        null,
        false,
        false,
        1),
    new PathTypeDescriptor(
        "TryPath",
        "TryPathSteps",
        "TryKind.Witness",
        "TryMonad",
        "TryMonad.INSTANCE",
        true,
        "TryKindHelper.TRY",
        "Path.tryPath",
        false,
        false,
        null,
        null,
        false,
        false,
        1),
    new PathTypeDescriptor(
        "IOPath",
        "IOPathSteps",
        "IOKind.Witness",
        "IOMonad",
        "IOMonad.INSTANCE",
        true,
        "IOKindHelper.IO_OP",
        "Path.ioPath",
        false,
        false,
        null,
        null,
        false,
        false,
        1),
    new PathTypeDescriptor(
        "VTaskPath",
        "VTaskPathSteps",
        "VTaskKind.Witness",
        "VTaskMonad",
        "VTaskMonad.INSTANCE",
        true,
        "VTaskKindHelper.VTASK",
        "Path.vtaskPath",
        false,
        false,
        null,
        null,
        false,
        false,
        1),
    new PathTypeDescriptor(
        "IdPath",
        "IdPathSteps",
        "IdKind.Witness",
        "IdMonad",
        "IdMonad.instance()",
        false,
        "IdKindHelper.ID",
        "Path.idPath",
        false,
        false,
        null,
        null,
        false,
        false,
        1),
    new PathTypeDescriptor(
        "NonDetPath",
        "NonDetPathSteps",
        "ListKind.Witness",
        "ListMonad",
        "ListMonad.INSTANCE",
        true,
        "ListKindHelper.LIST",
        "NonDetPath.of",
        true,
        false,
        null,
        null,
        false,
        true,
        1),
    new PathTypeDescriptor(
        "GenericPath",
        "GenericPathSteps",
        "F",
        "Monad",
        null,
        false,
        null,
        "GenericPath.of",
        false,
        true,
        "F",
        "F extends WitnessArity<TypeArity.Unary>",
        true,
        false,
        1),
  };

  // =========================================================================
  // Entry point
  // =========================================================================

  static void generate(int minArity, int maxArity, ProcessingEnvironment processingEnv)
      throws IOException {
    for (PathTypeDescriptor desc : PATH_TYPES) {
      // Always generate from the next arity after the hand-written max, regardless of minArity.
      // Each path type has its own current max arity (e.g. Maybe=5, Optional=3, Either=3).
      int startArity = desc.currentMaxArity + 1;
      for (int n = startArity; n <= maxArity; n++) {
        boolean terminal = (n == maxArity);
        generatePathSteps(desc, n, terminal, processingEnv);
      }
    }
  }

  // =========================================================================
  // Generate a single *PathStepsN class
  // =========================================================================

  private static void generatePathSteps(
      PathTypeDescriptor desc, int n, boolean terminal, ProcessingEnvironment processingEnv)
      throws IOException {
    String className = desc.stepsPrefix + n;
    String qualifiedName = PACKAGE + "." + className;

    StringBuilder sb = new StringBuilder();

    // Header
    sb.append("// Generated by hkj-processor. Do not edit.\n");
    sb.append("package ").append(PACKAGE).append(";\n\n");

    // Imports
    appendImports(sb, desc, n, terminal);

    // Class javadoc
    sb.append("/** Step ")
        .append(n)
        .append(" in a ")
        .append(desc.pathTypeName)
        .append(" comprehension. */\n");
    sb.append("@Generated\n");

    // Class declaration
    sb.append("public final class ").append(className);
    sb.append("<");
    appendClassTypeParams(sb, desc, n);
    sb.append("> {\n\n");

    // Fields
    appendFields(sb, desc, n);

    // Constructor
    appendConstructor(sb, desc, className, n);

    // from(), let(), focus() -- only if not terminal
    if (!terminal) {
      appendFromMethod(sb, desc, n);
      appendLetMethod(sb, desc, n);
      appendFocusMethod(sb, desc, n);
    }

    // when() -- only for filterable
    if (desc.filterable) {
      appendWhenMethod(sb, desc, className, n);
    }

    // match() -- only for filterable and not terminal
    if (desc.filterable && !terminal) {
      appendMatchMethod(sb, desc, n);
    }

    // yield spread function
    appendYieldSpread(sb, desc, n);

    // yield Function<TupleN, R>
    appendYieldTuple(sb, desc, n);

    sb.append("}\n");

    Writer writer = processingEnv.getFiler().createSourceFile(qualifiedName).openWriter();
    writer.write(sb.toString());
    writer.close();
  }

  // =========================================================================
  // Imports
  // =========================================================================

  private static void appendImports(
      StringBuilder sb, PathTypeDescriptor desc, int n, boolean terminal) {
    sb.append("import java.util.Objects;\n");
    if (desc.filterable) {
      sb.append("import java.util.Optional;\n");
    }
    sb.append("import java.util.function.Function;\n");
    if (n == 2) {
      sb.append("import java.util.function.BiFunction;\n");
    }
    if (desc.filterable) {
      sb.append("import java.util.function.Predicate;\n");
    }

    // HKT imports
    sb.append("import org.higherkindedj.hkt.Kind;\n");
    if (desc.isGeneric) {
      sb.append("import org.higherkindedj.hkt.Monad;\n");
      sb.append("import org.higherkindedj.hkt.TypeArity;\n");
      sb.append("import org.higherkindedj.hkt.WitnessArity;\n");
    }

    // Path effect imports
    if (desc.isGeneric) {
      sb.append("import org.higherkindedj.hkt.effect.GenericPath;\n");
    } else if (desc.isNonDet) {
      sb.append("import org.higherkindedj.hkt.effect.NonDetPath;\n");
    } else {
      sb.append("import org.higherkindedj.hkt.effect.").append(desc.pathTypeName).append(";\n");
      sb.append("import org.higherkindedj.hkt.effect.Path;\n");
    }

    // Monad/KindHelper imports (for non-Generic)
    if (!desc.isGeneric) {
      appendMonadKindHelperImports(sb, desc);
    }

    // Function imports
    if (n >= 3) {
      sb.append("import org.higherkindedj.hkt.function.Function").append(n).append(";\n");
    }

    // Tuple imports
    sb.append("import org.higherkindedj.hkt.tuple.Tuple;\n");
    sb.append("import org.higherkindedj.hkt.tuple.Tuple").append(n).append(";\n");
    if (!terminal) {
      sb.append("import org.higherkindedj.hkt.tuple.Tuple").append(n + 1).append(";\n");
    }

    sb.append("import org.higherkindedj.optics.annotations.Generated;\n");
    sb.append("\n");
  }

  private static void appendMonadKindHelperImports(StringBuilder sb, PathTypeDescriptor desc) {
    // Determine the witness/monad/helper packages based on the path type
    switch (desc.stepsPrefix) {
      case "MaybePathSteps" -> {
        sb.append("import org.higherkindedj.hkt.maybe.MaybeKind;\n");
        sb.append("import org.higherkindedj.hkt.maybe.MaybeKindHelper;\n");
        sb.append("import org.higherkindedj.hkt.maybe.MaybeMonad;\n");
      }
      case "OptionalPathSteps" -> {
        sb.append("import org.higherkindedj.hkt.optional.OptionalKind;\n");
        sb.append("import org.higherkindedj.hkt.optional.OptionalKindHelper;\n");
        sb.append("import org.higherkindedj.hkt.optional.OptionalMonad;\n");
      }
      case "EitherPathSteps" -> {
        sb.append("import org.higherkindedj.hkt.either.EitherKind;\n");
        sb.append("import org.higherkindedj.hkt.either.EitherKindHelper;\n");
        sb.append("import org.higherkindedj.hkt.either.EitherMonad;\n");
      }
      case "TryPathSteps" -> {
        sb.append("import org.higherkindedj.hkt.trymonad.TryKind;\n");
        sb.append("import org.higherkindedj.hkt.trymonad.TryKindHelper;\n");
        sb.append("import org.higherkindedj.hkt.trymonad.TryMonad;\n");
      }
      case "IOPathSteps" -> {
        sb.append("import org.higherkindedj.hkt.io.IOKind;\n");
        sb.append("import org.higherkindedj.hkt.io.IOKindHelper;\n");
        sb.append("import org.higherkindedj.hkt.io.IOMonad;\n");
      }
      case "VTaskPathSteps" -> {
        sb.append("import org.higherkindedj.hkt.vtask.VTaskKind;\n");
        sb.append("import org.higherkindedj.hkt.vtask.VTaskKindHelper;\n");
        sb.append("import org.higherkindedj.hkt.vtask.VTaskMonad;\n");
      }
      case "IdPathSteps" -> {
        sb.append("import org.higherkindedj.hkt.id.IdKind;\n");
        sb.append("import org.higherkindedj.hkt.id.IdKindHelper;\n");
        sb.append("import org.higherkindedj.hkt.id.IdMonad;\n");
      }
      case "NonDetPathSteps" -> {
        sb.append("import org.higherkindedj.hkt.list.ListKind;\n");
        sb.append("import org.higherkindedj.hkt.list.ListKindHelper;\n");
        sb.append("import org.higherkindedj.hkt.list.ListMonad;\n");
      }
      default -> {}
    }
  }

  // =========================================================================
  // Class type parameters
  // =========================================================================

  private static void appendClassTypeParams(StringBuilder sb, PathTypeDescriptor desc, int n) {
    String[] vp = valueParams(desc);
    if (desc.isGeneric) {
      sb.append("F extends WitnessArity<TypeArity.Unary>");
      for (int i = 0; i < n; i++) {
        sb.append(", ").append(vp[i]);
      }
    } else if (desc.hasExtraTypeParam) {
      // Either: E, A, B, C, ...
      sb.append(desc.extraTypeParamName);
      for (int i = 0; i < n; i++) {
        sb.append(", ").append(vp[i]);
      }
    } else {
      for (int i = 0; i < n; i++) {
        if (i > 0) sb.append(", ");
        sb.append(vp[i]);
      }
    }
  }

  /** Appends only the value type params (A, B, C, ...) for use in TupleN<> references. */
  private static void appendValueTypeParams(StringBuilder sb, PathTypeDescriptor desc, int n) {
    String[] vp = valueParams(desc);
    for (int i = 0; i < n; i++) {
      if (i > 0) sb.append(", ");
      sb.append(vp[i]);
    }
  }

  // =========================================================================
  // Fields
  // =========================================================================

  private static void appendFields(StringBuilder sb, PathTypeDescriptor desc, int n) {
    if (desc.isGeneric) {
      // Instance monad field
      sb.append("  private final Monad<F> monad;\n");
    } else if (desc.hasExtraTypeParam && !desc.isGeneric) {
      // Either: no static field, uses private static method
      // Nothing here for field; monad() method added below
    } else if (desc.isStaticMonad) {
      sb.append("  private static final ")
          .append(desc.monadType)
          .append(" MONAD = ")
          .append(desc.monadAccess)
          .append(";\n");
    } else {
      // Id: static final from factory method
      sb.append("  private static final ")
          .append(desc.monadType)
          .append(" MONAD = ")
          .append(desc.monadAccess)
          .append(";\n");
    }

    sb.append("  private final Kind<")
        .append(desc.witnessType)
        .append(", Tuple")
        .append(n)
        .append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">> computation;\n\n");

    // Either: private static monad() helper method
    if (desc.hasExtraTypeParam && !desc.isGeneric) {
      sb.append("  private static <E> EitherMonad<E> monad() {\n");
      sb.append("    return EitherMonad.instance();\n");
      sb.append("  }\n\n");
    }
  }

  // =========================================================================
  // Constructor
  // =========================================================================

  private static void appendConstructor(
      StringBuilder sb, PathTypeDescriptor desc, String className, int n) {
    sb.append("  ").append(className).append("(");
    if (desc.isGeneric) {
      sb.append("Monad<F> monad, ");
    }
    sb.append("Kind<").append(desc.witnessType).append(", Tuple").append(n).append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">> computation) {\n");
    if (desc.isGeneric) {
      sb.append("    this.monad = monad;\n");
    }
    sb.append("    this.computation = computation;\n");
    sb.append("  }\n\n");
  }

  // =========================================================================
  // from() method
  // =========================================================================

  private static void appendFromMethod(StringBuilder sb, PathTypeDescriptor desc, int n) {
    String[] vp = valueParams(desc);
    int next = n + 1;
    String nextType = vp[n];
    String nextClassName = desc.stepsPrefix + next;

    // Method signature
    sb.append("  public <").append(nextType).append("> ").append(nextClassName).append("<");
    appendNextClassTypeArgs(sb, desc, next);
    sb.append("> from(\n");
    sb.append("      Function<Tuple").append(n).append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">, ").append(fromReturnPathType(desc, nextType)).append("> next) {\n");

    // Method body
    if (needsLocalMonad(desc)) {
      sb.append("    EitherMonad<E> m = monad();\n");
      appendFromBody(sb, desc, n, next, nextType, "m");
    } else if (desc.isGeneric) {
      appendFromBody(sb, desc, n, next, nextType, "monad");
    } else {
      appendFromBody(sb, desc, n, next, nextType, "MONAD");
    }

    // Return
    sb.append("    return new ").append(nextClassName).append("<>(");
    if (desc.isGeneric) {
      sb.append("monad, ");
    }
    sb.append("newComp);\n");
    sb.append("  }\n\n");
  }

  private static void appendFromBody(
      StringBuilder sb,
      PathTypeDescriptor desc,
      int n,
      int next,
      String nextType,
      String monadVar) {
    sb.append("    Kind<").append(desc.witnessType).append(", Tuple").append(next).append("<");
    appendValueTypeParams(sb, desc, next);
    sb.append(">> newComp =\n");
    sb.append("        ").append(monadVar).append(".flatMap(\n");
    sb.append("            t -> ").append(monadVar).append(".map(\n");
    sb.append("                ").append(nextType.toLowerCase()).append(" -> Tuple.of(");
    for (int i = 0; i < n; i++) {
      sb.append("t._").append(i + 1).append("(), ");
    }
    sb.append(nextType.toLowerCase()).append("),\n");

    // The widen/runKind expression
    sb.append("                ");
    if (desc.isGeneric) {
      sb.append("next.apply(t).runKind()");
    } else {
      sb.append(desc.kindHelperField).append(".widen(next.apply(t).run())");
    }
    sb.append("),\n");
    sb.append("            computation);\n");
  }

  // =========================================================================
  // let() method
  // =========================================================================

  private static void appendLetMethod(StringBuilder sb, PathTypeDescriptor desc, int n) {
    String[] vp = valueParams(desc);
    int next = n + 1;
    String nextType = vp[n];
    String nextClassName = desc.stepsPrefix + next;

    // Method signature
    sb.append("  public <").append(nextType).append("> ").append(nextClassName).append("<");
    appendNextClassTypeArgs(sb, desc, next);
    sb.append("> let(\n");
    sb.append("      Function<Tuple").append(n).append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">, ").append(nextType).append("> f) {\n");

    // Method body
    String m = monadVar(desc, sb);
    sb.append("    Kind<").append(desc.witnessType).append(", Tuple").append(next).append("<");
    appendValueTypeParams(sb, desc, next);
    sb.append(">> newComp =\n");
    sb.append("        ").append(m).append(".map(\n");
    sb.append("            t -> Tuple.of(");
    for (int i = 0; i < n; i++) {
      sb.append("t._").append(i + 1).append("(), ");
    }
    sb.append("f.apply(t)),\n");
    sb.append("            computation);\n");

    // Return
    sb.append("    return new ").append(nextClassName).append("<>(");
    if (desc.isGeneric) {
      sb.append("monad, ");
    }
    sb.append("newComp);\n");
    sb.append("  }\n\n");
  }

  // =========================================================================
  // focus() method (non-terminal only)
  // =========================================================================

  private static void appendFocusMethod(StringBuilder sb, PathTypeDescriptor desc, int n) {
    String[] vp = valueParams(desc);
    int next = n + 1;
    String nextType = vp[n];
    String nextClassName = desc.stepsPrefix + next;

    // Method signature
    sb.append("  public <").append(nextType).append("> ").append(nextClassName).append("<");
    appendNextClassTypeArgs(sb, desc, next);
    sb.append("> focus(\n");
    sb.append("      Function<Tuple").append(n).append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">, ").append(nextType).append("> extractor) {\n");
    sb.append("    Objects.requireNonNull(extractor, \"extractor must not be null\");\n");
    sb.append("    return let(extractor);\n");
    sb.append("  }\n\n");
  }

  // =========================================================================
  // when() method (filterable only)
  // =========================================================================

  private static void appendWhenMethod(
      StringBuilder sb, PathTypeDescriptor desc, String className, int n) {
    sb.append("  public ").append(className).append("<");
    appendClassTypeParams(sb, desc, n);
    sb.append("> when(\n");
    sb.append("      Predicate<Tuple").append(n).append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">> predicate) {\n");

    String m = monadVar(desc, sb);
    sb.append("    Kind<").append(desc.witnessType).append(", Tuple").append(n).append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">> newComp =\n");
    sb.append("        ").append(m).append(".flatMap(\n");
    sb.append("            t -> predicate.test(t) ? ")
        .append(m)
        .append(".of(t) : ")
        .append(m)
        .append(".zero(),\n");
    sb.append("            computation);\n");
    sb.append("    return new ").append(className).append("<>(");
    if (desc.isGeneric) {
      sb.append("monad, ");
    }
    sb.append("newComp);\n");
    sb.append("  }\n\n");
  }

  // =========================================================================
  // match() method (filterable + non-terminal)
  // =========================================================================

  private static void appendMatchMethod(StringBuilder sb, PathTypeDescriptor desc, int n) {
    String[] vp = valueParams(desc);
    int next = n + 1;
    String nextType = vp[n];
    String nextClassName = desc.stepsPrefix + next;

    sb.append("  public <").append(nextType).append("> ").append(nextClassName).append("<");
    appendNextClassTypeArgs(sb, desc, next);
    sb.append("> match(\n");
    sb.append("      Function<Tuple").append(n).append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">, Optional<").append(nextType).append(">> matcher) {\n");
    sb.append("    Objects.requireNonNull(matcher, \"matcher must not be null\");\n");

    String m = monadVar(desc, sb);
    sb.append("    Kind<").append(desc.witnessType).append(", Tuple").append(next).append("<");
    appendValueTypeParams(sb, desc, next);
    sb.append(">> newComp =\n");
    sb.append("        ").append(m).append(".flatMap(\n");
    sb.append("            t ->\n");
    sb.append("                matcher\n");
    sb.append("                    .apply(t)\n");
    sb.append("                    .map(")
        .append(nextType.toLowerCase())
        .append(" -> ")
        .append(m)
        .append(".of(Tuple.of(");
    for (int i = 0; i < n; i++) {
      sb.append("t._").append(i + 1).append("(), ");
    }
    sb.append(nextType.toLowerCase()).append(")))\n");
    sb.append("                    .orElseGet(").append(m).append("::zero),\n");
    sb.append("            this.computation);\n");

    sb.append("    return new ").append(nextClassName).append("<>(");
    if (desc.isGeneric) {
      sb.append("monad, ");
    }
    sb.append("newComp);\n");
    sb.append("  }\n\n");
  }

  // =========================================================================
  // yield with spread function
  // =========================================================================

  private static void appendYieldSpread(StringBuilder sb, PathTypeDescriptor desc, int n) {
    String funcType = spreadFunctionType(n);
    String returnType = yieldReturnType(desc);

    sb.append("  public <R> ").append(returnType).append(" yield(").append(funcType).append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(", R> f) {\n");

    String m = monadVar(desc, sb);
    sb.append("    Kind<").append(desc.witnessType).append(", R> result =\n");
    sb.append("        ").append(m).append(".map(\n");
    sb.append("            t -> Objects.requireNonNull(\n");
    sb.append("                f.apply(");
    for (int i = 0; i < n; i++) {
      if (i > 0) sb.append(", ");
      sb.append("t._").append(i + 1).append("()");
    }
    sb.append("),\n");
    sb.append("                \"").append(YIELD_NULL_MSG).append("\"),\n");
    sb.append("            computation);\n");

    appendReturnPath(sb, desc);
    sb.append("  }\n\n");
  }

  // =========================================================================
  // yield with Function<TupleN, R>
  // =========================================================================

  private static void appendYieldTuple(StringBuilder sb, PathTypeDescriptor desc, int n) {
    String returnType = yieldReturnType(desc);

    sb.append("  public <R> ")
        .append(returnType)
        .append(" yield(Function<Tuple")
        .append(n)
        .append("<");
    appendValueTypeParams(sb, desc, n);
    sb.append(">, R> f) {\n");

    String m = monadVar(desc, sb);
    sb.append("    Kind<").append(desc.witnessType).append(", R> result =\n");
    sb.append("        ").append(m).append(".map(\n");
    sb.append("            t -> Objects.requireNonNull(f.apply(t), \"")
        .append(YIELD_NULL_MSG)
        .append("\"),\n");
    sb.append("            computation);\n");

    appendReturnPath(sb, desc);
    sb.append("  }\n");
  }

  // =========================================================================
  // Return path expression in yield
  // =========================================================================

  private static void appendReturnPath(StringBuilder sb, PathTypeDescriptor desc) {
    if (desc.isGeneric) {
      sb.append("    return GenericPath.of(result, monad);\n");
    } else if (desc.isNonDet) {
      sb.append("    return NonDetPath.of(")
          .append(desc.kindHelperField)
          .append(".narrow(result));\n");
    } else {
      sb.append("    return ")
          .append(desc.pathFactoryExpr)
          .append("(")
          .append(desc.kindHelperField)
          .append(".narrow(result));\n");
    }
  }

  // =========================================================================
  // Helper methods
  // =========================================================================

  /** Gets the return type for yield methods, e.g. "MaybePath<R>" or "EitherPath<E, R>". */
  private static String yieldReturnType(PathTypeDescriptor desc) {
    if (desc.isGeneric) {
      return "GenericPath<F, R>";
    } else if (desc.hasExtraTypeParam) {
      return desc.pathTypeName + "<" + desc.extraTypeParamName + ", R>";
    } else {
      return desc.pathTypeName + "<R>";
    }
  }

  /** Gets the path type for from() parameter, e.g. "MaybePath<G>" or "EitherPath<E, G>". */
  private static String fromReturnPathType(PathTypeDescriptor desc, String nextType) {
    if (desc.isGeneric) {
      return "GenericPath<F, " + nextType + ">";
    } else if (desc.hasExtraTypeParam) {
      return desc.pathTypeName + "<" + desc.extraTypeParamName + ", " + nextType + ">";
    } else {
      return desc.pathTypeName + "<" + nextType + ">";
    }
  }

  private static String spreadFunctionType(int n) {
    if (n == 2) return "BiFunction";
    return "Function" + n;
  }

  /** Returns a reference to the monad, and emits a local variable if needed (for Either). */
  private static String monadVar(PathTypeDescriptor desc, StringBuilder sb) {
    if (needsLocalMonad(desc)) {
      sb.append("    EitherMonad<E> m = monad();\n");
      return "m";
    } else if (desc.isGeneric) {
      return "monad";
    } else {
      return "MONAD";
    }
  }

  /** Returns the monad reference string without emitting code. */
  private static String monadRef(PathTypeDescriptor desc) {
    if (needsLocalMonad(desc)) return "m";
    if (desc.isGeneric) return "monad";
    return "MONAD";
  }

  private static boolean needsLocalMonad(PathTypeDescriptor desc) {
    return desc.hasExtraTypeParam && !desc.isGeneric;
  }

  /** Appends the type args for the next step class reference. */
  private static void appendNextClassTypeArgs(StringBuilder sb, PathTypeDescriptor desc, int next) {
    String[] vp = valueParams(desc);
    if (desc.isGeneric) {
      sb.append("F");
      for (int i = 0; i < next; i++) {
        sb.append(", ").append(vp[i]);
      }
    } else if (desc.hasExtraTypeParam) {
      sb.append(desc.extraTypeParamName);
      for (int i = 0; i < next; i++) {
        sb.append(", ").append(vp[i]);
      }
    } else {
      for (int i = 0; i < next; i++) {
        if (i > 0) sb.append(", ");
        sb.append(vp[i]);
      }
    }
  }
}
