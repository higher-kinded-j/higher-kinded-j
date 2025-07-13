// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.configaudit;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

public class ConfigAuditExample {

  public static void main(String[] args) {
    System.out.println("Building the declarative auditor optic...");

    Prism<AppConfig, AppConfig> gcpLiveOnlyPrism =
        Prism.of(
            config -> {
              String rawTarget = DeploymentTarget.toRawString().get(config.target());
              return "gcp|live".equals(rawTarget) ? Optional.of(config) : Optional.empty();
            },
            config -> config);

    // This chain is now fully corrected, with each part converted to a Traversal
    // before being composed with the next.
    Traversal<AppConfig, byte[]> auditTraversal =
        AppConfigTraversals.settings()
            .andThen(SettingLenses.value().asTraversal())
            .andThen(SettingValuePrisms.encryptedValue().asTraversal())
            .andThen(EncryptedValueLenses.base64Value().asTraversal())
            .andThen(EncryptedValueIsos.base64.asTraversal()); // <<< FIX IS HERE

    Traversal<AppConfig, byte[]> finalAuditor =
        gcpLiveOnlyPrism.asTraversal().andThen(auditTraversal);

    var configs = createSampleConfigs();

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
    var prodDb = new Setting("db.password", new EncryptedValue("c2VjcmV0X3Bhc3N3b3Jk"));
    var stagingDb = new Setting("db.password", new EncryptedValue("c3RhZ2luZ19wYXNz"));
    var apiKey = new Setting("api.key", new EncryptedValue("c3VwZXJfYXBpX2tleQ=="));

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
