// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.configaudit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

public class ConfigAuditExample {

  public static void main(String[] args) {
    var configs = createSampleConfigs();
    var firstConfig = configs.get(0);

    System.out.println("Original Config: " + firstConfig);
    System.out.println("------------------------------------------");

    // =======================================================================
    // SCENARIO 1: Using `with*` helpers for targeted updates
    // =======================================================================
    System.out.println("--- Scenario 1: Using `with*` Helpers ---");

    // A simple, shallow update is very easy.
    var renamedConfig = AppConfigLenses.withAppId(firstConfig, "RenamedBillingService");
    System.out.println("After `withAppId`: " + renamedConfig);

    // A deeper update requires a few steps, but is still clear.
    // Let's update the db.password for the first config.
    List<Setting> newSettings = new ArrayList<>();
    for (Setting setting : firstConfig.settings()) {
      if ("db.password".equals(setting.key()) && setting.value() instanceof EncryptedValue ev) {
        // Create a new EncryptedValue with the updated base64 string
        var newEncryptedValue =
            EncryptedValueLenses.withBase64Value(ev, "dXBkYXRlZF9wYXNzd29yZA==");
        // Create a new Setting with the new value
        newSettings.add(SettingLenses.withValue(setting, newEncryptedValue));
      } else {
        newSettings.add(setting); // Keep the old setting
      }
    }
    // Create the final config with the new list of settings
    var updatedPasswordConfig = AppConfigLenses.withSettings(firstConfig, newSettings);
    System.out.println("After updating password: " + updatedPasswordConfig);
    System.out.println("------------------------------------------");

    // =======================================================================
    // SCENARIO 2: Using composed optics for deep, bulk auditing
    // =======================================================================
    System.out.println("--- Scenario 2: Using Composed Optics for Deep Auditing ---");
    System.out.println("Building the declarative auditor optic...");

    Prism<AppConfig, AppConfig> gcpLiveOnlyPrism =
        Prism.of(
            config -> {
              String rawTarget = DeploymentTarget.toRawString().get(config.target());
              return "gcp|live".equals(rawTarget) ? Optional.of(config) : Optional.empty();
            },
            config -> config);

    // This traversal composes multiple optics to create a powerful tool that
    // finds all Base64-encoded passwords.
    Traversal<AppConfig, byte[]> auditTraversal =
        AppConfigTraversals.settings()
            .andThen(SettingLenses.value().asTraversal())
            .andThen(SettingValuePrisms.encryptedValue().asTraversal())
            .andThen(EncryptedValueLenses.base64Value().asTraversal())
            .andThen(EncryptedValueIsos.base64.asTraversal());

    // Further compose it to only apply to configs matching the prism.
    Traversal<AppConfig, byte[]> finalAuditor =
        gcpLiveOnlyPrism.asTraversal().andThen(auditTraversal);

    System.out.println("\n🔎 Running audit across all configurations...");
    for (AppConfig config : configs) {
      List<byte[]> passwords = Traversals.getAll(finalAuditor, config);

      String targetString = DeploymentTarget.toRawString().get(config.target());
      System.out.printf(
          "\nAudit for App '%s' (Target: %s): Found %d passwords.",
          config.appId(), targetString, passwords.size());

      if (!passwords.isEmpty()) {
        System.out.println(" Decoded values:");
        passwords.forEach(p -> System.out.println("  -> \"" + new String(p) + "\""));
      }
    }
  }

  private static List<AppConfig> createSampleConfigs() {
    var prodDb =
        new Setting("db.password", new EncryptedValue("c2VjcmV0X3Bhc3N3b3Jk")); // "secret_password"
    var stagingDb =
        new Setting("db.password", new EncryptedValue("c3RhZ2luZ19wYXNz")); // "staging_pass"
    var apiKey =
        new Setting("api.key", new EncryptedValue("c3VwZXJfYXBpX2tleQ==")); // "super_api_key"

    return List.of(
        new AppConfig(
            "BillingService",
            List.of(new Setting("threads", new IntValue(4)), prodDb),
            new DeploymentTarget("gcp", "live")),
        new AppConfig("AuthService", List.of(stagingDb), new DeploymentTarget("gcp", "staging")),
        new AppConfig("DataPipeline", List.of(prodDb), new DeploymentTarget("aws", "live")),
        new AppConfig(
            "ReportingService", List.of(apiKey, prodDb), new DeploymentTarget("gcp", "live")));
  }
}
