// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Bundled skills")
class BundledSkillsTest {

  @Test
  @DisplayName("ship only hkj-* skills, so a contributor skill stays out of consumer projects")
  void shipOnlyHkjSkills() throws IOException {
    try (InputStream manifest =
        BundledSkillsTest.class.getResourceAsStream("/META-INF/hkj-skills/manifest.txt")) {
      assertThat(manifest).as("the bundled skills manifest").isNotNull();
      List<String> files =
          new String(manifest.readAllBytes(), UTF_8)
              .lines()
              .filter(line -> !line.isBlank())
              .toList();
      assertThat(files).isNotEmpty().allMatch(file -> file.startsWith("hkj-"));
    }
  }
}
