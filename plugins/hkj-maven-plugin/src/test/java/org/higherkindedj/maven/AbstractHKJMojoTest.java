// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AbstractHKJMojo")
class AbstractHKJMojoTest {

  private static final List<String> SETTINGS =
      Arrays.stream(HKJConfiguration.class.getRecordComponents())
          .map(RecordComponent::getName)
          .toList();

  @Nested
  @DisplayName("shared configuration")
  class SharedConfiguration {

    @Test
    @DisplayName("the goals' fields are the configuration's settings")
    void fieldsMatchSettings() {
      List<String> fields =
          Arrays.stream(AbstractHKJMojo.class.getDeclaredFields())
              .filter(f -> !Modifier.isStatic(f.getModifiers()))
              .map(Field::getName)
              .toList();

      assertThat(fields).containsExactlyInAnyOrderElementsOf(SETTINGS);
    }

    @Test
    @DisplayName("every goal in the descriptor declares every setting")
    void descriptorDeclaresSettings() throws IOException {
      String descriptor;
      try (InputStream in =
          AbstractHKJMojoTest.class.getResourceAsStream("/META-INF/maven/plugin.xml")) {
        assertThat(in).isNotNull();
        descriptor = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      Matcher mojo = Pattern.compile("<mojo>([\\s\\S]*?)</mojo>").matcher(descriptor);
      int goals = 0;
      while (mojo.find()) {
        goals++;
        List<String> parameters = new ArrayList<>();
        Matcher name = Pattern.compile("<parameter>\\s*<name>(\\w+)</name>").matcher(mojo.group(1));
        while (name.find()) {
          parameters.add(name.group(1));
        }
        assertThat(parameters).containsAll(SETTINGS);
      }
      assertThat(goals).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("execution settings")
  class ExecutionSettings {

    private final List<String> warnings = new ArrayList<>();

    private HKJDiagnosticsMojo goalWith(String field, Object value)
        throws ReflectiveOperationException {
      HKJDiagnosticsMojo goal = new HKJDiagnosticsMojo();
      goal.setLog(
          new SystemStreamLog() {
            @Override
            public void warn(CharSequence content) {
              warnings.add(content.toString());
            }
          });
      Field setting = AbstractHKJMojo.class.getDeclaredField(field);
      setting.setAccessible(true);
      setting.set(goal, value);
      return goal;
    }

    @Test
    @DisplayName("warns about a setting that differs from the one the plugin reads")
    void warnsAboutDifferingSetting() throws ReflectiveOperationException {
      HKJConfiguration read = new HKJConfiguration("0.4.10", true, false, false, true);

      goalWith("skills", Boolean.TRUE).warnAboutExecutionSettings(read);

      assertThat(warnings)
          .singleElement()
          .asString()
          .contains("<skills>true</skills> on this execution is not read")
          .contains("where skills is false");
    }

    @Test
    @DisplayName("is quiet about a setting that agrees, or one that was not given")
    void quietWhenAgreeingOrAbsent() throws ReflectiveOperationException {
      HKJConfiguration read = new HKJConfiguration("0.4.10", true, false, false, true);

      goalWith("version", "0.4.10").warnAboutExecutionSettings(read);
      goalWith("spring", null).warnAboutExecutionSettings(read);

      assertThat(warnings).isEmpty();
    }
  }
}
