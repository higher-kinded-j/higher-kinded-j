// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * Recipe that adds {@code F extends WitnessArity<TypeArity.Unary>} bounds to generic type
 * parameters that are used with Kind or HKT type class interfaces.
 *
 * <p>This recipe identifies type parameters that need bounds by looking for:
 *
 * <ul>
 *   <li>Parameters used as the first type argument to Kind&lt;F, ?&gt;
 *   <li>Parameters used with type class interfaces like Monad&lt;F&gt;, Functor&lt;F&gt;, etc.
 *   <li>Parameters used as the F in transformer types like MaybeT&lt;F, ?&gt;
 * </ul>
 */
public class AddArityBoundsToTypeParametersRecipe extends Recipe {

  private static final String WITNESS_ARITY_FQN = "org.higherkindedj.hkt.WitnessArity";
  private static final String TYPE_ARITY_FQN = "org.higherkindedj.hkt.TypeArity";

  /** Type class interfaces that indicate the type parameter needs WitnessArity bound. */
  private static final Set<String> TYPE_CLASS_INTERFACES =
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

  /** Transformer types where the F parameter needs WitnessArity bound. */
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

  @Override
  public String getDisplayName() {
    return "Add arity bounds to type parameters";
  }

  @Override
  public String getDescription() {
    return "Adds 'extends WitnessArity<TypeArity.Unary>' bounds to generic type parameters "
        + "that are used with Kind<F, ?> or type class interfaces like Monad<F>. "
        + "For type parameters that already have bounds, adds WitnessArity as an additional bound.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "bounds", "migration");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      // Track which type parameter names need bounds in the current context
      private final Set<String> paramsNeedingBounds = new HashSet<>();

      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {

        // Clear and populate params needing bounds for this method
        paramsNeedingBounds.clear();

        if (method.getTypeParameters() != null) {
          for (J.TypeParameter tp : method.getTypeParameters()) {
            String paramName = getTypeParameterName(tp);
            if (paramName != null
                && !hasWitnessArityBound(tp)
                && isUsedWithKindOrTypeClass(paramName, method)) {
              paramsNeedingBounds.add(paramName);
            }
          }
        }

        // If any params need bounds, schedule imports
        if (!paramsNeedingBounds.isEmpty()) {
          doAfterVisit(new AddImport<>(WITNESS_ARITY_FQN, null, false));
          doAfterVisit(new AddImport<>(TYPE_ARITY_FQN, null, false));
        }

        // Visit children (will process type parameters)
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

        paramsNeedingBounds.clear();
        return md;
      }

      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDecl, ExecutionContext ctx) {

        // Clear and populate params needing bounds for this class
        Set<String> classParamsNeedingBounds = new HashSet<>();

        if (classDecl.getTypeParameters() != null) {
          for (J.TypeParameter tp : classDecl.getTypeParameters()) {
            String paramName = getTypeParameterName(tp);
            if (paramName != null
                && !hasWitnessArityBound(tp)
                && isUsedInClassHierarchy(paramName, classDecl)) {
              classParamsNeedingBounds.add(paramName);
            }
          }
        }

        // Merge with existing (in case of nested contexts)
        paramsNeedingBounds.addAll(classParamsNeedingBounds);

        // If any params need bounds, schedule imports
        if (!classParamsNeedingBounds.isEmpty()) {
          doAfterVisit(new AddImport<>(WITNESS_ARITY_FQN, null, false));
          doAfterVisit(new AddImport<>(TYPE_ARITY_FQN, null, false));
        }

        // Visit children
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

        // Remove class-level params (keep method-level if in nested context)
        paramsNeedingBounds.removeAll(classParamsNeedingBounds);

        return cd;
      }

      @Override
      public J.TypeParameter visitTypeParameter(J.TypeParameter typeParam, ExecutionContext ctx) {
        J.TypeParameter tp = super.visitTypeParameter(typeParam, ctx);

        String paramName = getTypeParameterName(tp);
        if (paramName == null) {
          return tp;
        }

        // Check if this parameter needs a WitnessArity bound
        if (!paramsNeedingBounds.contains(paramName)) {
          return tp;
        }

        // Already has WitnessArity bound
        if (hasWitnessArityBound(tp)) {
          return tp;
        }

        // Build the WitnessArity<TypeArity.Unary> bound AST manually
        TypeTree witnessArityBound = createWitnessArityBound();

        // Build new bounds list
        List<TypeTree> newBounds = new ArrayList<>();
        if (tp.getBounds() != null) {
          newBounds.addAll(tp.getBounds());
        }
        newBounds.add(witnessArityBound);

        // Return modified type parameter with the new bound
        return tp.withBounds(newBounds);
      }

      /**
       * Creates the AST for WitnessArity&lt;TypeArity.Unary&gt;.
       *
       * <p>Constructs: J.ParameterizedType with J.Identifier "WitnessArity" and type argument
       * J.FieldAccess "TypeArity.Unary"
       */
      private TypeTree createWitnessArityBound() {
        // Create TypeArity.Unary as a FieldAccess
        J.Identifier typeArityId =
            new J.Identifier(
                Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), "TypeArity", null, null);

        J.Identifier unaryId =
            new J.Identifier(
                Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), "Unary", null, null);

        J.FieldAccess typeArityUnary =
            new J.FieldAccess(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                typeArityId,
                JLeftPadded.build(unaryId),
                null);

        // Create WitnessArity identifier
        J.Identifier witnessArityId =
            new J.Identifier(
                Tree.randomId(),
                Space.SINGLE_SPACE,
                Markers.EMPTY,
                List.of(),
                "WitnessArity",
                null,
                null);

        // Create the parameterized type WitnessArity<TypeArity.Unary>
        // Type arguments are wrapped in JContainer<Expression>
        JRightPadded<Expression> typeArg =
            new JRightPadded<>(typeArityUnary, Space.EMPTY, Markers.EMPTY);

        JContainer<Expression> typeArguments =
            JContainer.build(Space.EMPTY, List.of(typeArg), Markers.EMPTY);

        return new J.ParameterizedType(
            Tree.randomId(),
            Space.SINGLE_SPACE,
            Markers.EMPTY,
            witnessArityId,
            typeArguments,
            null);
      }

      private boolean hasWitnessArityBound(J.TypeParameter tp) {
        if (tp.getBounds() == null || tp.getBounds().isEmpty()) {
          return false;
        }

        for (TypeTree bound : tp.getBounds()) {
          // Check by type information
          JavaType type = bound.getType();
          String fqn = getFullyQualifiedName(type);
          if (WITNESS_ARITY_FQN.equals(fqn)) {
            return true;
          }

          // Check by AST structure (when type info isn't resolved)
          if (bound instanceof J.ParameterizedType pt) {
            if (pt.getClazz() instanceof J.Identifier id) {
              if ("WitnessArity".equals(id.getSimpleName())) {
                return true;
              }
            }
          } else if (bound instanceof J.Identifier id) {
            if ("WitnessArity".equals(id.getSimpleName())) {
              return true;
            }
          }
        }
        return false;
      }

      private boolean isUsedWithKindOrTypeClass(String paramName, J.MethodDeclaration md) {
        // Check method parameters
        if (md.getParameters() != null) {
          for (Object param : md.getParameters()) {
            if (param instanceof J.VariableDeclarations vd) {
              if (usesTypeParamWithKindOrTypeClass(paramName, vd.getTypeExpression())) {
                return true;
              }
            }
          }
        }

        // Check return type
        if (md.getReturnTypeExpression() != null) {
          if (usesTypeParamWithKindOrTypeClass(paramName, md.getReturnTypeExpression())) {
            return true;
          }
        }

        return false;
      }

      private boolean isUsedInClassHierarchy(String paramName, J.ClassDeclaration cd) {
        // Check extends clause
        if (cd.getExtends() != null) {
          if (usesTypeParamWithKindOrTypeClass(paramName, cd.getExtends())) {
            return true;
          }
        }

        // Check implements clauses
        if (cd.getImplements() != null) {
          for (TypeTree impl : cd.getImplements()) {
            if (usesTypeParamWithKindOrTypeClass(paramName, impl)) {
              return true;
            }
          }
        }

        return false;
      }

      private boolean usesTypeParamWithKindOrTypeClass(String paramName, TypeTree typeTree) {
        if (typeTree == null) {
          return false;
        }

        // Check AST structure first (more reliable when type info isn't available)
        if (typeTree instanceof J.ParameterizedType pt) {
          String typeName = extractTypeName(pt.getClazz());
          if (typeName != null) {
            // Check if it's Kind<F, ?> or a type class
            if ("Kind".equals(typeName)
                || TYPE_CLASS_INTERFACES.stream().anyMatch(fqn -> fqn.endsWith("." + typeName))
                || TRANSFORMER_TYPES.stream().anyMatch(fqn -> fqn.endsWith("." + typeName))) {
              // Check if first type argument matches our param
              List<Expression> typeParams = pt.getTypeParameters();
              if (typeParams != null && !typeParams.isEmpty()) {
                Expression firstArg = typeParams.get(0);
                if (firstArg instanceof J.Identifier id) {
                  if (id.getSimpleName().equals(paramName)) {
                    return true;
                  }
                }
              }
            }
          }

          // Recursively check type arguments
          List<Expression> typeParams = pt.getTypeParameters();
          if (typeParams != null) {
            for (Expression arg : typeParams) {
              if (arg instanceof TypeTree tt && usesTypeParamWithKindOrTypeClass(paramName, tt)) {
                return true;
              }
            }
          }
        }

        // Also check by type information if available
        JavaType type = typeTree.getType();
        if (type instanceof JavaType.Parameterized parameterized) {
          String fqn = parameterized.getFullyQualifiedName();

          // Check if it's a type class interface or Kind
          if (TYPE_CLASS_INTERFACES.contains(fqn)
              || TRANSFORMER_TYPES.contains(fqn)
              || "org.higherkindedj.hkt.Kind".equals(fqn)) {
            // Check if the first type argument matches our param
            List<JavaType> typeArgs = parameterized.getTypeParameters();
            if (!typeArgs.isEmpty()) {
              JavaType firstArg = typeArgs.get(0);
              if (firstArg instanceof JavaType.GenericTypeVariable gtv) {
                if (gtv.getName().equals(paramName)) {
                  return true;
                }
              }
            }
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
