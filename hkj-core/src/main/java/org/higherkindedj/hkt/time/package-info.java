// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Effectful time: {@link org.higherkindedj.hkt.time.TimeSource} lifts {@link java.time.Clock} into
 * {@code IO}/{@code VTask} so reading the current instant is a lazy, composable effect and
 * deterministic in tests.
 */
@NullMarked
package org.higherkindedj.hkt.time;

import org.jspecify.annotations.NullMarked;
