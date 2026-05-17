// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

/**
 * Recipe that adds {@code F extends WitnessArity<TypeArity.Unary>} (or {@code
 * WitnessArity<TypeArity.Binary>}) bounds to generic type parameters that are used with {@code
 * Kind}/{@code Kind2} or HKT type class interfaces.
 *
 * <p>A type parameter needs a bound when it appears as the witness position (first type argument)
 * of any of the following, anywhere in its declaring scope — including method parameters, return
 * types, fields, local variables, the class hierarchy, nested generic positions, and wildcard
 * bounds:
 *
 * <ul>
 *   <li>{@code Kind<F, ?>} / unary type classes (e.g. {@code Monad<F>}) / transformers — emits
 *       {@code WitnessArity<TypeArity.Unary>}
 *   <li>{@code Kind2<F, ?, ?>} / binary type classes ({@code Bifunctor<F>}, {@code Profunctor<F>})
 *       — emits {@code WitnessArity<TypeArity.Binary>}
 * </ul>
 *
 * <p>When a type parameter already has a bound, {@code WitnessArity} is appended as an intersection
 * bound.
 */
public class AddArityBoundsToTypeParametersRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public AddArityBoundsToTypeParametersRecipe() {}

  private static final String WITNESS_ARITY_FQN = "org.higherkindedj.hkt.WitnessArity";
  private static final String TYPE_ARITY_FQN = "org.higherkindedj.hkt.TypeArity";
  private static final String KIND_FQN = "org.higherkindedj.hkt.Kind";
  private static final String KIND2_FQN = "org.higherkindedj.hkt.Kind2";

  /** Unary type class interfaces: the first type argument is a {@code TypeArity.Unary} witness. */
  private static final Set<String> UNARY_TYPE_CLASSES =
      Set.of(
          "org.higherkindedj.hkt.Functor",
          "org.higherkindedj.hkt.Applicative",
          "org.higherkindedj.hkt.Monad",
          "org.higherkindedj.hkt.MonadError",
          "org.higherkindedj.hkt.Foldable",
          "org.higherkindedj.hkt.Traverse",
          "org.higherkindedj.hkt.Contravariant",
          "org.higherkindedj.hkt.Comonad",
          "org.higherkindedj.hkt.Alternative",
          "org.higherkindedj.hkt.MonadPlus",
          "org.higherkindedj.hkt.MonadZero",
          "org.higherkindedj.hkt.Selective",
          "org.higherkindedj.hkt.SemigroupK");

  /**
   * Binary type class interfaces: the first type argument is a {@code TypeArity.Binary} witness.
   */
  private static final Set<String> BINARY_TYPE_CLASSES =
      Set.of("org.higherkindedj.hkt.Bifunctor", "org.higherkindedj.hkt.Profunctor");

  /** Transformer types where the (unary) F parameter needs a WitnessArity bound. */
  private static final Set<String> TRANSFORMER_TYPES =
      Set.of(
          "org.higherkindedj.hkt.maybe_t.MaybeT",
          "org.higherkindedj.hkt.maybe_t.MaybeTKind",
          "org.higherkindedj.hkt.optional_t.OptionalT",
          "org.higherkindedj.hkt.optional_t.OptionalTKind",
          "org.higherkindedj.hkt.either_t.EitherT",
          "org.higherkindedj.hkt.either_t.EitherTKind",
          "org.higherkindedj.hkt.reader_t.ReaderT",
          "org.higherkindedj.hkt.reader_t.ReaderTKind",
          "org.higherkindedj.hkt.state_t.StateT",
          "org.higherkindedj.hkt.state_t.StateTKind");

  private enum Arity {
    UNARY,
    BINARY
  }

  @Override
  public String getDisplayName() {
    return "Add arity bounds to type parameters";
  }

  @Override
  public String getDescription() {
    return "Adds 'extends WitnessArity<TypeArity.Unary>' (or TypeArity.Binary for Kind2 and "
        + "binary type classes) bounds to generic type parameters used as a witness with Kind, "
        + "Kind2, type classes, or transformers. For type parameters that already have bounds, "
        + "adds WitnessArity as an additional intersection bound.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "bounds", "migration");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      // Type parameter name -> required arity, for the type parameters in scope.
      private final Map<String, Arity> paramArity = new HashMap<>();

      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {

        Map<String, Arity> methodParams = new HashMap<>();
        if (method.getTypeParameters() != null) {
          for (J.TypeParameter tp : method.getTypeParameters()) {
            String name = getTypeParameterName(tp);
            if (name != null && !hasWitnessArityBound(tp)) {
              Arity arity = scanArity(name, method, ctx);
              if (arity != null) {
                methodParams.put(name, arity);
              }
            }
          }
        }

        paramArity.putAll(methodParams);
        if (!methodParams.isEmpty()) {
          doAfterVisit(new AddImport<>(WITNESS_ARITY_FQN, null, false));
          doAfterVisit(new AddImport<>(TYPE_ARITY_FQN, null, false));
        }

        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

        methodParams.keySet().forEach(paramArity::remove);
        return md;
      }

      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDecl, ExecutionContext ctx) {

        Map<String, Arity> classParams = new HashMap<>();
        if (classDecl.getTypeParameters() != null) {
          for (J.TypeParameter tp : classDecl.getTypeParameters()) {
            String name = getTypeParameterName(tp);
            if (name != null && !hasWitnessArityBound(tp)) {
              Arity arity = scanArity(name, classDecl, ctx);
              if (arity != null) {
                classParams.put(name, arity);
              }
            }
          }
        }

        paramArity.putAll(classParams);
        if (!classParams.isEmpty()) {
          doAfterVisit(new AddImport<>(WITNESS_ARITY_FQN, null, false));
          doAfterVisit(new AddImport<>(TYPE_ARITY_FQN, null, false));
        }

        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

        classParams.keySet().forEach(paramArity::remove);
        return cd;
      }

      @Override
      public J.TypeParameter visitTypeParameter(J.TypeParameter typeParam, ExecutionContext ctx) {
        J.TypeParameter tp = super.visitTypeParameter(typeParam, ctx);

        String name = getTypeParameterName(tp);
        if (name == null || !paramArity.containsKey(name) || hasWitnessArityBound(tp)) {
          return tp;
        }

        TypeTree witnessArityBound = createWitnessArityBound(paramArity.get(name));

        JContainer<TypeTree> existing = tp.getPadding().getBounds();
        boolean hasExistingBounds = existing != null && !existing.getElements().isEmpty();

        if (hasExistingBounds) {
          // Append as an intersection bound: `T extends Existing & WitnessArity<...>`.
          // The "&" separator is emitted by the printer; we only own the surrounding
          // spaces (trailing space on the previous bound, leading space on ours).
          List<JRightPadded<TypeTree>> elements =
              new ArrayList<>(existing.getPadding().getElements());
          int lastIdx = elements.size() - 1;
          elements.set(lastIdx, elements.get(lastIdx).withAfter(Space.SINGLE_SPACE));
          elements.add(
              new JRightPadded<>(
                  witnessArityBound.withPrefix(Space.SINGLE_SPACE), Space.EMPTY, Markers.EMPTY));
          return tp.getPadding()
              .withBounds(JContainer.build(existing.getBefore(), elements, Markers.EMPTY));
        }

        // No existing bounds: render `T extends WitnessArity<...>`. The container's
        // before-space is the space before `extends`; the element prefix is the
        // space after `extends`.
        JRightPadded<TypeTree> only =
            new JRightPadded<>(
                witnessArityBound.withPrefix(Space.SINGLE_SPACE), Space.EMPTY, Markers.EMPTY);
        return tp.getPadding()
            .withBounds(JContainer.build(Space.SINGLE_SPACE, List.of(only), Markers.EMPTY));
      }

      /**
       * Scans an entire declaration subtree (method or class) for any witness-position use of the
       * named type parameter. Returns the required arity, or {@code null} if the parameter is not
       * used as a witness. {@code BINARY} wins if the parameter is used both ways.
       */
      private Arity scanArity(String paramName, J scope, ExecutionContext ctx) {
        Arity[] found = new Arity[1];
        new JavaIsoVisitor<ExecutionContext>() {
          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration cd, ExecutionContext c) {
            // Do not descend into a nested type that shadows the parameter name.
            return cd != scope && declaresTypeParam(cd.getTypeParameters(), paramName)
                ? cd
                : super.visitClassDeclaration(cd, c);
          }

          @Override
          public J.MethodDeclaration visitMethodDeclaration(
              J.MethodDeclaration md, ExecutionContext c) {
            // Do not descend into a method that shadows the parameter name with
            // its own generic parameter of the same name.
            return md != scope && declaresTypeParam(md.getTypeParameters(), paramName)
                ? md
                : super.visitMethodDeclaration(md, c);
          }

          @Override
          public J.ParameterizedType visitParameterizedType(
              J.ParameterizedType pt, ExecutionContext c) {
            Arity a = arityIfWitness(paramName, pt);
            if (a == Arity.BINARY) {
              found[0] = Arity.BINARY;
            } else if (a == Arity.UNARY && found[0] == null) {
              found[0] = Arity.UNARY;
            }
            return super.visitParameterizedType(pt, c);
          }
        }.visit(scope, ctx);
        return found[0];
      }

      private boolean declaresTypeParam(List<J.TypeParameter> typeParams, String name) {
        if (typeParams == null) {
          return false;
        }
        for (J.TypeParameter tp : typeParams) {
          if (name.equals(getTypeParameterName(tp))) {
            return true;
          }
        }
        return false;
      }

      /**
       * If {@code pt} is a recognised HKT carrier whose first type argument is the named parameter
       * (directly or as a wildcard bound), returns the arity that carrier implies; else null.
       */
      private Arity arityIfWitness(String paramName, J.ParameterizedType pt) {
        Arity carrierArity = carrierArity(pt);
        if (carrierArity == null) {
          return null;
        }
        List<Expression> args = pt.getTypeParameters();
        if (args == null || args.isEmpty()) {
          return null;
        }
        return referencesParam(args.get(0), paramName) ? carrierArity : null;
      }

      /** Classifies the carrier type of a parameterized type as unary, binary, or not a carrier. */
      private Arity carrierArity(J.ParameterizedType pt) {
        // Prefer resolved type information when available.
        if (pt.getType() instanceof JavaType.Parameterized p) {
          String fqn = p.getFullyQualifiedName();
          if (KIND2_FQN.equals(fqn) || BINARY_TYPE_CLASSES.contains(fqn)) {
            return Arity.BINARY;
          }
          if (KIND_FQN.equals(fqn)
              || UNARY_TYPE_CLASSES.contains(fqn)
              || TRANSFORMER_TYPES.contains(fqn)) {
            return Arity.UNARY;
          }
        }
        // Fall back to the simple name from the AST.
        String name = extractTypeName(pt.getClazz());
        if (name == null) {
          return null;
        }
        if ("Kind2".equals(name) || endsWithSimpleName(BINARY_TYPE_CLASSES, name)) {
          return Arity.BINARY;
        }
        if ("Kind".equals(name)
            || endsWithSimpleName(UNARY_TYPE_CLASSES, name)
            || endsWithSimpleName(TRANSFORMER_TYPES, name)) {
          return Arity.UNARY;
        }
        return null;
      }

      private boolean endsWithSimpleName(Set<String> fqns, String simpleName) {
        return fqns.stream().anyMatch(fqn -> fqn.endsWith("." + simpleName));
      }

      /** True if the expression is the type parameter, or a wildcard bounded by it. */
      private boolean referencesParam(Expression arg, String paramName) {
        if (arg instanceof J.Identifier id) {
          return paramName.equals(id.getSimpleName());
        }
        if (arg instanceof J.Wildcard w && w.getBoundedType() instanceof J.Identifier id) {
          return paramName.equals(id.getSimpleName());
        }
        if (arg.getType() instanceof JavaType.GenericTypeVariable gtv) {
          return paramName.equals(gtv.getName());
        }
        return false;
      }

      private TypeTree createWitnessArityBound(Arity arity) {
        J.Identifier typeArityId =
            new J.Identifier(
                Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), "TypeArity", null, null);

        J.Identifier arityId =
            new J.Identifier(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                List.of(),
                arity == Arity.BINARY ? "Binary" : "Unary",
                null,
                null);

        J.FieldAccess typeArityArity =
            new J.FieldAccess(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                typeArityId,
                JLeftPadded.build(arityId),
                null);

        // Surrounding whitespace is owned by the enclosing JRightPadded element prefix.
        J.Identifier witnessArityId =
            new J.Identifier(
                Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), "WitnessArity", null, null);

        JRightPadded<Expression> typeArg =
            new JRightPadded<>(typeArityArity, Space.EMPTY, Markers.EMPTY);

        JContainer<Expression> typeArguments =
            JContainer.build(Space.EMPTY, List.of(typeArg), Markers.EMPTY);

        return new J.ParameterizedType(
            Tree.randomId(), Space.EMPTY, Markers.EMPTY, witnessArityId, typeArguments, null);
      }

      private boolean hasWitnessArityBound(J.TypeParameter tp) {
        if (tp.getBounds() == null || tp.getBounds().isEmpty()) {
          return false;
        }
        for (TypeTree bound : tp.getBounds()) {
          JavaType type = bound.getType();
          if (WITNESS_ARITY_FQN.equals(getFullyQualifiedName(type))) {
            return true;
          }
          if (bound instanceof J.ParameterizedType pt
              && pt.getClazz() instanceof J.Identifier id
              && "WitnessArity".equals(id.getSimpleName())) {
            return true;
          }
          if (bound instanceof J.Identifier id && "WitnessArity".equals(id.getSimpleName())) {
            return true;
          }
        }
        return false;
      }

      private String extractTypeName(NameTree clazz) {
        if (clazz instanceof J.Identifier id) {
          return id.getSimpleName();
        } else if (clazz instanceof J.FieldAccess fa) {
          return fa.getSimpleName();
        }
        return null;
      }

      private String getTypeParameterName(J.TypeParameter tp) {
        if (tp.getName() instanceof J.Identifier id) {
          return id.getSimpleName();
        }
        return null;
      }

      private String getFullyQualifiedName(JavaType type) {
        if (type instanceof JavaType.Parameterized parameterized) {
          return parameterized.getFullyQualifiedName();
        } else if (type instanceof JavaType.Class classType) {
          return classType.getFullyQualifiedName();
        }
        return null;
      }
    };
  }
}
