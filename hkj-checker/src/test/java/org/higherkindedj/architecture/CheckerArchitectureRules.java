// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.PrintStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckerArchitectureRules {
  @Test
  @DisplayName("production code must use an explicit locale")
  void productionCodeMustUseAnExplicitLocale() {
    noClasses()
        .should()
        .callMethod(String.class, "format", String.class, Object[].class)
        .orShould()
        .callMethod(String.class, "formatted", Object[].class)
        .orShould()
        .callMethod(String.class, "toLowerCase")
        .orShould()
        .callMethod(String.class, "toUpperCase")
        .orShould()
        .callMethod(PrintStream.class, "printf", String.class, Object[].class)
        .orShould()
        .callMethod(PrintStream.class, "format", String.class, Object[].class)
        .because("library code must pass Locale.ROOT rather than use the default locale")
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.higherkindedj.checker"));
  }
}
