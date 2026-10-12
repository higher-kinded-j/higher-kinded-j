// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.navigator;

import java.util.List;
import java.util.Map;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.jspecify.annotations.Nullable;

/** The records {@code NavigatorAsPathTest} navigates: a navigator is a path. */
final class NavigatorAsPath {
  private NavigatorAsPath() {}
}

@GenerateFocus(widenFields = "replicas")
record AppEnv(
    DbConfig db, String name, @Nullable DbConfig fallback, Map<String, DbConfig> replicas) {}

@GenerateFocus
record DbConfig(String url, int port, Pool pool) {}

@GenerateFocus
record Pool(int size, List<String> hosts) {}
