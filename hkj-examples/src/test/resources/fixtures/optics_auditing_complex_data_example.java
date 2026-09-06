// Fixture for hkj-book/src/optics/auditing_complex_data_example.md
//
// The page audits an application configuration for encrypted values, and reaches once for a server
// configuration to show the same shape one level deeper. Both models are declared here - the first
// mirrors `org.higherkindedj.example.configaudit`, whose variants are package-private and so
// cannot be imported from a snippet.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateIsos;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.Test;

record DeploymentTarget(String platform, String environment) {

  @GenerateIsos
  public static Iso<DeploymentTarget, String> toRawString() {
    return Iso.of(
        target -> target.platform() + "|" + target.environment(),
        raw -> new DeploymentTarget(raw.split("\\|")[0], raw.split("\\|")[1]));
  }
}

@GeneratePrisms
sealed interface SettingValue permits StringValue, IntValue, EncryptedValue {}

record StringValue(String value) implements SettingValue {}

@GenerateLenses
record IntValue(int value) implements SettingValue {}

@GenerateLenses
record EncryptedValue(String base64Value) implements SettingValue {

  @GenerateIsos
  public static Iso<String, byte[]> base64() {
    return Iso.of(
        base64Str -> Base64.getDecoder().decode(base64Str),
        bytes -> Base64.getEncoder().encodeToString(bytes));
  }
}

@GenerateLenses
record Setting(String key, SettingValue value) {}

@GenerateLenses
@GenerateTraversals
record AppConfig(String name, List<Setting> settings, DeploymentTarget target) {}

record ConfigDto(String raw) {}

record AuditEntry(
    String configName,
    String settingKey,
    String encryptedValue,
    Instant auditTime,
    String auditorId) {}

record AuditReport(List<AuditEntry> entries, Instant generatedAt, String auditorId) {}

// The server configuration the page reaches for once, to show the same audit path running one
// level deeper.
@GenerateLenses
record EncryptedCredential(String base64Secret) implements Credential {

  @GenerateIsos
  public static Iso<String, byte[]> base64ToBytes() {
    return Iso.of(
        base64Str -> Base64.getDecoder().decode(base64Str),
        bytes -> Base64.getEncoder().encodeToString(bytes));
  }
}

@GeneratePrisms
sealed interface Credential permits EncryptedCredential, PlainCredential {}

record PlainCredential(String secret) implements Credential {}

@GenerateLenses
@GenerateTraversals
record Production(String region, List<Credential> credentials) implements Environment {}

record Staging(String region) implements Environment {}

@GeneratePrisms
sealed interface Environment permits Production, Staging {}

@GenerateLenses
@GenerateTraversals
record ServerConfig(String name, List<Environment> environments) {}

// The conversions the legacy-bridge snippet names.
class Auditing {

  static AppConfig toAppConfig(ConfigDto dto) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static ConfigDto toConfigDto(AppConfig config) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Cipher {

  byte[] encrypt(byte[] plaintext) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final int MAX_AUDITS = 10;

  static final AppConfig config = sample();

  static final AppConfig someConfig = config;

  static final AppConfig originalConfig = config;

  static final List<AppConfig> configs = List.of();

  static final ConfigDto someDto = sample();

  static final List<byte[]> auditResults = new java.util.ArrayList<>();

  static final String encodedValue = "dGVzdA==";

  static final Cipher newCipher = new Cipher();

  static final Prism<AppConfig, AppConfig> gcpLiveOnlyPrism = sample();

  static final Traversal<AppConfig, byte[]> finalAuditor = sample();

  static final Traversal<AppConfig, String> base64Strings = sample();

  static final Traversal<AppConfig, byte[]> auditTraversal = sample();

  static final Prism<AppConfig, AppConfig> devEnvironmentPrism = sample();

  static final Prism<AppConfig, AppConfig> stagingEnvironmentPrism = sample();

  static final Traversal<Setting, Setting> settingFilter = sample();

  static final Traversal<Setting, AuditEntry> auditEntryMapper = sample();

  static boolean shouldAudit(AppConfig toAudit) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static boolean hasEncryptedData(AppConfig toAudit) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static byte[] performDetailedAudit(AppConfig toAudit) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static Traversal<AppConfig, byte[]> createAuditTraversal() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  boolean isGcpLive(AppConfig toCheck) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}
