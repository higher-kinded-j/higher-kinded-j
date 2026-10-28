// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

/**
 * Adds the Path family to code that names a capability interface: {@code Chainable<A>} becomes
 * {@code Chainable<?, A>}, and likewise {@code Combinable<A>}, {@code Effectful<A>}, {@code
 * Recoverable<E, A>} and {@code Accumulating<E, A>}.
 *
 * <p>From 0.6.0 each capability interface takes the implementing Path's family as its first type
 * parameter, so a type written with the old arguments no longer compiles. An open family, {@code
 * ?}, keeps what such code does when it maps, recovers with {@code recover} or {@code handleError},
 * or runs the path it is given, and a provider's {@code createPath} returns {@code Chainable<?,
 * A>}, as the interface now declares. Code that chains or combines through a capability type,
 * calling {@code via}, {@code then}, {@code zipWith}, {@code recoverWith} or {@code orElse} on it,
 * needs a type parameter for the family instead, as in {@code <P, A> Chainable<P, A>}, which is
 * left to the author.
 *
 * <p>A type already written with the family, and a raw type, are left as they are, so the recipe
 * can run more than once, before or after the project moves to 0.6.0. A class that implements
 * {@code Combinable}, by name or anonymously, is left too, members included: it names its own class
 * as the family, as a generated {@code @PathSource} Path does.
 */
public class AddPathFamilyToCapabilityTypesRecipe extends Recipe {

  /** Each capability interface, and how many type arguments it took before the family. */
  private static final Map<String, Integer> ARGUMENTS_BEFORE_THE_FAMILY =
      Map.of(
          "org.higherkindedj.hkt.effect.capability.Combinable", 1,
          "org.higherkindedj.hkt.effect.capability.Chainable", 1,
          "org.higherkindedj.hkt.effect.capability.Effectful", 1,
          "org.higherkindedj.hkt.effect.capability.Recoverable", 2,
          "org.higherkindedj.hkt.effect.capability.Accumulating", 2);

  /** Creates a new instance of this recipe. */
  public AddPathFamilyToCapabilityTypesRecipe() {}

  @Override
  public String getDisplayName() {
    return "Add the Path family to capability types";
  }

  @Override
  public String getDescription() {
    return "From 0.6.0 the Effect Path capability interfaces take the implementing Path's family"
        + " as their first type parameter. This rewrites `Chainable<A>` to `Chainable<?, A>`, and"
        + " likewise `Combinable`, `Effectful`, `Recoverable<E, A>` and `Accumulating<E, A>`, in"
        + " variables, fields, parameters, return types, type arguments and `instanceof`, including"
        + " a `PathProvider`'s `createPath`. Code that calls `via`, `then`, `zipWith`, `recoverWith`"
        + " or `orElse` through a capability type needs a type parameter for the family instead,"
        + " which is left to the author, as is a class that implements `Combinable`.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "migration", "effect-path");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {
      @Override
      public J.ParameterizedType visitParameterizedType(
          J.ParameterizedType type, ExecutionContext ctx) {
        J.ParameterizedType visited = super.visitParameterizedType(type, ctx);
        String capability = capabilityName(visited);
        List<Expression> arguments = visited.getTypeParameters();
        if (capability == null
            || arguments == null
            || arguments.size() != ARGUMENTS_BEFORE_THE_FAMILY.get(capability)
            || arguments.stream().anyMatch(J.Empty.class::isInstance)
            || insideAnImplementation()) {
          return visited;
        }
        List<Expression> withFamily = new ArrayList<>(arguments.size() + 1);
        withFamily.add(
            new J.Wildcard(
                Tree.randomId(), arguments.getFirst().getPrefix(), Markers.EMPTY, null, null));
        withFamily.add(arguments.getFirst().withPrefix(Space.SINGLE_SPACE));
        withFamily.addAll(arguments.subList(1, arguments.size()));
        return visited.withTypeParameters(withFamily);
      }

      /**
       * Whether the type sits in a class that implements a capability interface, named or
       * anonymous: its supertype and its members name its own family, which is left to the author.
       */
      private boolean insideAnImplementation() {
        return getCursor()
            .getPathAsStream()
            .anyMatch(
                node ->
                    switch (node) {
                      case J.ClassDeclaration declared ->
                          (declared.getImplements() != null
                                  && declared.getImplements().stream()
                                      .anyMatch(t -> namesACapability(t.getType())))
                              || (declared.getExtends() != null
                                  && namesACapability(declared.getExtends().getType()));
                      case J.NewClass created ->
                          created.getBody() != null
                              && created.getClazz() != null
                              && namesACapability(created.getClazz().getType());
                      default -> false;
                    });
      }
    };
  }

  private static boolean namesACapability(JavaType type) {
    JavaType.FullyQualified named = TypeUtils.asFullyQualified(type);
    return named != null && ARGUMENTS_BEFORE_THE_FAMILY.containsKey(named.getFullyQualifiedName());
  }

  /** The capability interface a type names, or {@code null} if it names none. */
  private static String capabilityName(J.ParameterizedType type) {
    JavaType.FullyQualified named = TypeUtils.asFullyQualified(type.getClazz().getType());
    if (named == null) {
      named = TypeUtils.asFullyQualified(type.getType());
    }
    return named != null && ARGUMENTS_BEFORE_THE_FAMILY.containsKey(named.getFullyQualifiedName())
        ? named.getFullyQualifiedName()
        : null;
  }
}
