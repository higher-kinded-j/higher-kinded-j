// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;

/**
 * Verifies the declarative recipe resources under {@code META-INF/rewrite} load and validate from
 * the classpath. This exercises the same resource-loading path used by both the OpenRewrite Gradle
 * and Maven plugins, so a malformed YAML or a dangling recipe reference fails the build here rather
 * than in a downstream consumer.
 */
class RecipeCatalogTest {

  private static Environment env() {
    // Scan the whole runtime classpath (as the real plugins do) so referenced
    // OpenRewrite recipes such as org.openrewrite.java.AddImport also resolve.
    return Environment.builder().scanRuntimeClasspath().build();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "org.higherkindedj.openrewrite.AddArityBounds",
        "org.higherkindedj.openrewrite.EffectAlgebraMigration",
        "org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0",
      })
  void compositeRecipeLoadsAndValidates(String recipeName) {
    Recipe recipe = env().activateRecipes(recipeName);

    assertThat(recipe.getRecipeList())
        .as("composite %s should aggregate sub-recipes", recipeName)
        .isNotEmpty();
    assertThat(recipe.validateAll())
        .as("all sub-recipes of %s must validate", recipeName)
        .allSatisfy(v -> assertThat(v.isValid()).isTrue());
  }

  @Test
  void publicDeclarativeRecipeNamesAreDiscoverable() {
    // The names users activate from the YAML recipe groups (see hkj-openrewrite/README.md).
    var names = env().listRecipeDescriptors().stream().map(d -> d.getName()).toList();

    assertThat(names)
        .contains(
            "org.higherkindedj.openrewrite.AddArityBounds",
            "org.higherkindedj.openrewrite.AddWitnessArityToWitness",
            "org.higherkindedj.openrewrite.AddArityBoundsToTypeParameters",
            "org.higherkindedj.openrewrite.EffectAlgebraMigration",
            "org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0",
            "org.higherkindedj.openrewrite.RenameStateTKindNarrowK",
            "org.higherkindedj.openrewrite.RenameKindValidatorNarrowWithPattern");
  }

  @Test
  void underlyingJavaRecipeClassesAreDiscoverable() {
    // The Java Recipe classes that the declarative recipes (and the effect-algebra
    // composite) delegate to, discovered by their class FQN.
    var names = env().listRecipeDescriptors().stream().map(d -> d.getName()).toList();

    assertThat(names)
        .contains(
            "org.higherkindedj.openrewrite.AddArityBoundsToTypeParametersRecipe",
            "org.higherkindedj.openrewrite.AddWitnessArityToWitnessClass",
            "org.higherkindedj.openrewrite.AddHandleErrorCaseRecipe",
            "org.higherkindedj.openrewrite.ConvertRawFreeToFreePathRecipe",
            "org.higherkindedj.openrewrite.DetectInjectBoilerplateRecipe",
            "org.higherkindedj.openrewrite.SwapTryFoldToFoldFailureFirstRecipe");
  }

  @Test
  void noRecipeFailsValidation() {
    for (Recipe r : env().listRecipes()) {
      if (r.getName().startsWith("org.higherkindedj")) {
        assertThat(r.validate().isValid()).as("recipe %s should validate", r.getName()).isTrue();
      }
    }
  }
}
