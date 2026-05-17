// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.tools.Diagnostic;

/**
 * Parsed configuration for the HKJ checker, derived from the plugin arguments.
 *
 * <p>Arguments are passed after the plugin name in the {@code -Xplugin} option. javac splits the
 * {@code -Xplugin} value on whitespace into the plugin name followed by its arguments, so the
 * supported form is:
 *
 * <pre>{@code
 * -Xplugin:HKJChecker disable=path-type-mismatch,effect-composition severity=warn
 * }</pre>
 *
 * <p>For robustness each argument is also split on {@code ;} and {@code ,}, so a single combined
 * token such as {@code "disable=path-type-mismatch;severity=warn"} is accepted as well.
 *
 * <h2>Supported directives</h2>
 *
 * <ul>
 *   <li>{@code disable=<id>[,<id>...]} — turn the listed checks off entirely
 *   <li>{@code severity=error|warn} — global default severity (default {@code error})
 *   <li>{@code severity:<id>=error|warn} — override the severity of a single check; wins over the
 *       global default and over a check's built-in default (so the warn-default checks {@code
 *       error-type-mismatch} / {@code map-nests-effect} can be promoted to {@code error})
 * </ul>
 *
 * <p>Unknown keys and unparseable values are ignored rather than failing the build: a typo in a
 * compiler argument must not break compilation.
 */
public final class CheckerConfig {

  /** Check id for {@link PathTypeMismatchChecker}. */
  public static final String PATH_TYPE_MISMATCH = "path-type-mismatch";

  /** Check id for {@link EffectCompositionChecker}. */
  public static final String EFFECT_COMPOSITION = "effect-composition";

  /** Check id for {@link TransformerMissingMonadChecker}. */
  public static final String TRANSFORMER_MISSING_MONAD = "transformer-missing-monad";

  /** Check id for {@link FreeSwitchExhaustivenessChecker}. */
  public static final String FREE_SWITCH_EXHAUSTIVE = "free-switch-exhaustive";

  /** Check id for {@link DiscardedEffectChecker}. */
  public static final String DISCARDED_EFFECT = "discarded-effect";

  /** Check id for {@link StateTMapTArityChecker}. */
  public static final String STATE_T_MAPT_ARITY = "state-t-mapt-arity";

  /** Check id for {@link ErrorTypeMismatchChecker}. */
  public static final String ERROR_TYPE_MISMATCH = "error-type-mismatch";

  /** Check id for {@link KindValueNarrowChecker}. */
  public static final String KIND_VALUE_NARROW = "kind-value-narrow";

  /** Check id for {@link WitnessArityChecker}. */
  public static final String WITNESS_ARITY = "witness-arity";

  /** Check id for {@link ViaNonPathChecker}. */
  public static final String VIA_NON_PATH = "via-non-path";

  /** Check id for {@link MapReturnsPathChecker}. */
  public static final String MAP_NESTS_EFFECT = "map-nests-effect";

  /** Check id for {@link MigrationNudgeChecker} (advisory; warn-default). */
  public static final String MIGRATION_NUDGE = "migration-nudge";

  /**
   * Checks whose built-in default is {@code WARNING}: they are the sole compile-time signal over
   * code javac accepts, so they only nudge until a per-check override promotes them.
   */
  private static final Set<String> WARN_DEFAULT_CHECKS =
      Set.of(ERROR_TYPE_MISMATCH, MAP_NESTS_EFFECT, MIGRATION_NUDGE);

  private final Set<String> disabled;
  private final Diagnostic.Kind severity;
  private final Map<String, Diagnostic.Kind> perCheckSeverity;

  private CheckerConfig(
      Set<String> disabled,
      Diagnostic.Kind severity,
      Map<String, Diagnostic.Kind> perCheckSeverity) {
    this.disabled = disabled;
    this.severity = severity;
    this.perCheckSeverity = perCheckSeverity;
  }

  /**
   * Returns the default configuration: all checks enabled, global severity {@code error}.
   *
   * @return the default configuration
   */
  public static CheckerConfig defaults() {
    return new CheckerConfig(Set.of(), Diagnostic.Kind.ERROR, Map.of());
  }

  /**
   * Parses the plugin arguments into a configuration.
   *
   * @param args the raw arguments passed to {@link HKJCheckerPlugin#init}; may be empty
   * @return the parsed configuration, falling back to {@link #defaults()} for anything omitted or
   *     unrecognised
   */
  public static CheckerConfig parse(String... args) {
    Set<String> disabled = new HashSet<>();
    Diagnostic.Kind severity = Diagnostic.Kind.ERROR;
    Map<String, Diagnostic.Kind> perCheck = new HashMap<>();

    if (args != null) {
      for (String arg : args) {
        if (arg == null) {
          continue;
        }
        for (String directive : arg.split("[;\\s]+")) {
          if (directive.isBlank()) {
            continue;
          }
          int eq = directive.indexOf('=');
          if (eq <= 0) {
            continue; // not a key=value token; ignore
          }
          String key = directive.substring(0, eq).trim();
          String value = directive.substring(eq + 1).trim();
          if (key.equals("disable")) {
            for (String id : value.split(",")) {
              String trimmed = id.trim();
              if (!trimmed.isEmpty()) {
                disabled.add(trimmed);
              }
            }
          } else if (key.equals("severity")) {
            Diagnostic.Kind parsed = parseSeverity(value);
            if (parsed != null) {
              severity = parsed;
            }
          } else if (key.startsWith("severity:")) {
            String checkId = key.substring("severity:".length()).trim();
            Diagnostic.Kind parsed = parseSeverity(value);
            if (!checkId.isEmpty() && parsed != null) {
              perCheck.put(checkId, parsed);
            }
          }
          // Anything else: ignore so a typo cannot break the build.
        }
      }
    }
    return new CheckerConfig(Set.copyOf(disabled), severity, Map.copyOf(perCheck));
  }

  private static Diagnostic.Kind parseSeverity(String value) {
    return switch (value.toLowerCase()) {
      case "error" -> Diagnostic.Kind.ERROR;
      case "warn", "warning" -> Diagnostic.Kind.WARNING;
      default -> null; // invalid: keep the existing/default severity
    };
  }

  /**
   * Returns whether the given check should run.
   *
   * @param checkId one of the {@code *_ID} constants in this class
   * @return {@code true} unless the check was explicitly disabled
   */
  public boolean isEnabled(String checkId) {
    return !disabled.contains(checkId);
  }

  /**
   * Returns the global default severity.
   *
   * @return {@link Diagnostic.Kind#ERROR} by default, or {@link Diagnostic.Kind#WARNING} if {@code
   *     severity=warn} was set
   */
  public Diagnostic.Kind severity() {
    return severity;
  }

  /**
   * Resolves the severity for a specific check.
   *
   * <p>Resolution order: an explicit {@code severity:<id>=…} override; otherwise a warn-default
   * check ({@code error-type-mismatch} / {@code map-nests-effect}) stays {@code WARNING} (the
   * global severity does not promote it — it must be opted up explicitly); otherwise the global
   * {@link #severity()}.
   *
   * @param checkId one of the {@code *_ID} constants in this class
   * @return the severity at which that check should report
   */
  public Diagnostic.Kind severityFor(String checkId) {
    Diagnostic.Kind override = perCheckSeverity.get(checkId);
    if (override != null) {
      return override;
    }
    if (WARN_DEFAULT_CHECKS.contains(checkId)) {
      return Diagnostic.Kind.WARNING;
    }
    return severity;
  }
}
