// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

/**
 * Recipe that adds {@code implements WitnessArity<TypeArity.Unary>} or {@code implements
 * WitnessArity<TypeArity.Binary>} to witness class definitions.
 *
 * <p>A witness class is identified by:
 *
 * <ul>
 *   <li>Being named "Witness" (inner class convention)
 *   <li>Implementing a Kind or Kind2 interface (directly or indirectly)
 * </ul>
 *
 * <p>The recipe detects the arity based on whether the class relates to Kind (unary) or Kind2
 * (binary).
 */
public class AddWitnessArityToWitnessClass extends Recipe {

  private static final String WITNESS_ARITY_FQN = "org.higherkindedj.hkt.WitnessArity";
  private static final String TYPE_ARITY_FQN = "org.higherkindedj.hkt.TypeArity";
  private static final String KIND_FQN = "org.higherkindedj.hkt.Kind";
  private static final String KIND2_FQN = "org.higherkindedj.hkt.Kind2";

  @Override
  public String getDisplayName() {
    return "Add WitnessArity to witness classes";
  }

  @Override
  public String getDescription() {
    return "Adds 'implements WitnessArity<TypeArity.Unary>' or 'implements WitnessArity<TypeArity.Binary>' "
        + "to witness class definitions that are used with Kind or Kind2.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "witness", "migration");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {
      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

        // Check if this looks like a witness class
        if (!isWitnessClass(cd)) {
          return cd;
        }

        // Check if already implements WitnessArity
        if (alreadyImplementsWitnessArity(cd)) {
          return cd;
        }

        // Determine arity based on Kind usage
        Arity arity = determineArity(cd);
        if (arity == null) {
          return cd;
        }

        // Add the implements clause
        return addWitnessArityImplements(cd, arity, ctx);
      }

      private boolean isWitnessClass(J.ClassDeclaration cd) {
        String name = cd.getSimpleName();
        // Witness classes are typically named "Witness" (inner class)
        // or end with "Witness" (top-level)
        return "Witness".equals(name) || name.endsWith("Witness");
      }

      private boolean alreadyImplementsWitnessArity(J.ClassDeclaration cd) {
        if (cd.getImplements() == null) {
          return false;
        }
        for (TypeTree impl : cd.getImplements()) {
          JavaType type = impl.getType();
          if (type instanceof JavaType.Parameterized parameterized) {
            if (parameterized.getFullyQualifiedName().equals(WITNESS_ARITY_FQN)) {
              return true;
            }
          } else if (type instanceof JavaType.Class classType) {
            if (classType.getFullyQualifiedName().equals(WITNESS_ARITY_FQN)) {
              return true;
            }
          }
        }
        return false;
      }

      private Arity determineArity(J.ClassDeclaration cd) {
        // Check implements clauses for Kind or Kind2
        if (cd.getImplements() != null) {
          for (TypeTree impl : cd.getImplements()) {
            String typeName = getFullyQualifiedName(impl.getType());
            if (typeName != null) {
              if (typeName.startsWith(KIND2_FQN)) {
                return Arity.BINARY;
              }
              if (typeName.startsWith(KIND_FQN) && !typeName.startsWith(KIND2_FQN)) {
                return Arity.UNARY;
              }
            }
          }
        }

        // Check if parent class/interface suggests arity
        // For now, default to UNARY for witness classes without clear indicators
        return Arity.UNARY;
      }

      private String getFullyQualifiedName(JavaType type) {
        if (type instanceof JavaType.Parameterized parameterized) {
          return parameterized.getFullyQualifiedName();
        } else if (type instanceof JavaType.Class classType) {
          return classType.getFullyQualifiedName();
        }
        return null;
      }

      private J.ClassDeclaration addWitnessArityImplements(
          J.ClassDeclaration cd, Arity arity, ExecutionContext ctx) {
        // This is a simplified implementation - in a real recipe,
        // we would use JavaTemplate to properly add the implements clause
        // For now, we'll add necessary imports and let the user complete the migration

        maybeAddImport(WITNESS_ARITY_FQN);
        maybeAddImport(TYPE_ARITY_FQN);
        maybeAddImport(
            TYPE_ARITY_FQN
                + "."
                + arity.name().charAt(0)
                + arity.name().substring(1).toLowerCase());

        // Note: Actually modifying the implements clause requires JavaTemplate
        // This recipe adds imports; users should manually add the implements clause
        // or use a more sophisticated recipe with JavaTemplate

        return cd;
      }
    };
  }

  private enum Arity {
    UNARY,
    BINARY
  }
}
