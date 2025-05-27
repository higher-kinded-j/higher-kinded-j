// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.alias;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Specifies the generation of a type alias interface for a Higher-Kinded Type. */
@Repeatable(GenerateHKTAliases.class)
@Retention(RetentionPolicy.SOURCE) // Important: Only needed during compilation
@Target(ElementType.TYPE) // Typically placed on a dummy interface or package-info.java
public @interface GenerateHKTAlias {
  /**
   * @return The simple name of the alias interface to be generated (e.g., "WorkflowTask").
   */
  String name();

  /**
   * @return The base HKT interface the alias will extend (e.g., Kind.class, MonadError.class).
   */
  Class<?> baseInterface();

  /**
   * @return The primary HKT witness type (e.g., EitherTKind.Witness.class).
   */
  Class<?> hktWitness();

  /**
   * * @return For HKT transformers like F&lt;G, A&gt; or F&lt;G, E, A&gt;, this is the witness for
   * the inner HKT 'G' (e.g., CompletableFutureKind.Witness.class for
   * EitherT&lt;CompletableFutureKind.Witness, E, A&gt;).
   */
  Class<?> f_hktWitness() default void.class; // Default indicates it's not used

  /**
   * * @return The error type 'E' if it's part of the HKT structure (e.g., DomainError.class for
   * EitherT&lt;F, DomainError, A&gt;) or for MonadError&lt;HKT, E&gt;.
   */
  Class<?> errorType() default void.class; // Default indicates it's not used

  /**
   * @return For HKTs wrapping a concrete, possibly generic, type directly in the Kind's second
   *     parameter, like Kind&lt;F, MyType&lt;X, Y&gt;&gt;. (e.g., Either.class for Kind&lt;F,
   *     Either&lt;L,R&gt;&gt;). If the alias is generic itself (using genericParameters), this
   *     usually isn't needed for the main value type 'A' in Kind&lt;F, A&gt;.
   */
  Class<?> valueType() default void.class;

  /**
   * @return Generic arguments for the 'valueType', if 'valueType' itself is generic. (e.g.,
   *     {DomainError.class, FinalResult.class} for Kind&lt;F, Either&lt;DomainError,
   *     FinalResult&gt;&gt;).
   */
  Class<?>[] valueTypeArgs() default {};

  /**
   * @return Generic type parameters for the alias itself (e.g., {"A"}, {"A", "B"}). This will make
   *     the generated alias interface generic, like {@code public interface MyAlias<A> ...}. The
   *     last generic parameter is typically used as the value type 'A' in Kind&lt;Witness, A&gt;.
   */
  String[] genericParameters() default {};

  /**
   * @return The package where the alias interface should be generated. If empty, uses a default.
   */
  String targetPackage() default "";
}
