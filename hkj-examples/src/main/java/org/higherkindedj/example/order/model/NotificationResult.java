// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Result of sending order notifications.
 *
 * @param emailSent whether the email notification was sent
 * @param smsSent whether the SMS notification was sent
 * @param emailMessageId the message ID from the email provider, if sent
 */
@GenerateLenses
public record NotificationResult(
    boolean emailSent, boolean smsSent, Optional<String> emailMessageId) {
  /**
   * Creates a result indicating all notifications were sent.
   *
   * @param emailMessageId the email message ID
   * @return a fully successful notification result
   */
  public static NotificationResult allSent(String emailMessageId) {
    return new NotificationResult(true, true, Optional.of(emailMessageId));
  }

  /**
   * Creates a result indicating only email was sent.
   *
   * @param emailMessageId the email message ID
   * @return a partial notification result
   */
  public static NotificationResult emailOnly(String emailMessageId) {
    return new NotificationResult(true, false, Optional.of(emailMessageId));
  }

  /**
   * Creates a result indicating no notifications were sent.
   *
   * @return a failed notification result
   */
  public static NotificationResult none() {
    return new NotificationResult(false, false, Optional.empty());
  }
}
