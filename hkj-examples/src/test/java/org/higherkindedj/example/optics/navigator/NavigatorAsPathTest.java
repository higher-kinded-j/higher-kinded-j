// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.navigator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A navigator is a path: it is held, passed and composed wherever a path of its tier is, and it
 * keeps navigating as a {@code var}.
 */
@DisplayName("A navigator is a path")
class NavigatorAsPathTest {

  private static final DbConfig PRIMARY =
      new DbConfig("jdbc:h2:mem", 5432, new Pool(4, List.of("a", "b")));

  private static final AppEnv ENV =
      new AppEnv(
          PRIMARY,
          "app",
          null,
          Map.of("eu", new DbConfig("jdbc:h2:eu", 6543, new Pool(2, List.of("c")))));

  @Test
  @DisplayName("a navigator is assignable to a typed path variable")
  void typedVariable() {
    FocusPath<AppEnv, DbConfig> db = AppEnvFocus.db();
    assertThat(db.get(ENV).url()).isEqualTo("jdbc:h2:mem");
  }

  @Test
  @DisplayName("an Effect Path's focus takes a navigator")
  void eitherPathFocus() {
    EitherPath<String, DbConfig> db = Path.<String, AppEnv>right(ENV).focus(AppEnvFocus.db());
    assertThat(db.run().getRight().port()).isEqualTo(5432);
  }

  @Test
  @DisplayName("a ForPath focus step takes a navigator")
  void forPathStep() {
    EitherPath<String, String> url =
        ForPath.from(Path.<String, AppEnv>right(ENV))
            .focus(AppEnvFocus.db())
            .yield((env, db) -> db.url());
    assertThat(url.run().getRight()).isEqualTo("jdbc:h2:mem");
  }

  @Test
  @DisplayName("ReaderPath.magnify takes a navigator")
  void readerMagnify() {
    ReaderPath<AppEnv, String> url =
        ReaderPath.<DbConfig>ask().map(DbConfig::url).magnify(AppEnvFocus.db());
    assertThat(url.run(ENV)).isEqualTo("jdbc:h2:mem");
  }

  @Test
  @DisplayName("a navigator offers the whole path API: via, each, asTraversal, traced, labels")
  void wholePathApi() {
    FocusPath<AppEnv, String> url = AppEnvFocus.db().via(DbConfigFocus.url());
    assertThat(url.get(ENV)).isEqualTo("jdbc:h2:mem");

    FocusPath<Pool, List<String>> hostList =
        FocusPath.of(Lens.of(Pool::hosts, (pool, hosts) -> new Pool(pool.size(), hosts)));
    TraversalPath<AppEnv, String> hosts =
        AppEnvFocus.db().pool().via(hostList).each(EachInstances.listEach());
    assertThat(hosts.getAll(ENV)).containsExactly("a", "b");

    assertThat(AppEnvFocus.db().asTraversal().getAll(ENV)).hasSize(1);
    List<String> seen = new ArrayList<>();
    assertThat(AppEnvFocus.db().traced((env, db) -> seen.add(db.url())).get(ENV))
        .isEqualTo(PRIMARY);
    assertThat(seen).containsExactly("jdbc:h2:mem");
    assertThat(AppEnvFocus.db().pathString()).isEqualTo("db");
    assertThat(AppEnvFocus.db().pool().segments()).containsExactly("db", "pool");
  }

  @Test
  @DisplayName("a var chain keeps navigating, and the var is still a path")
  void varChain() {
    var db = AppEnvFocus.db();
    var resized = db.pool().size().set(8, ENV);
    assertThat(resized.db().pool().size()).isEqualTo(8);
    assertThat(portOf(db, ENV)).isEqualTo(5432);
  }

  @Test
  @DisplayName("a navigator compares and prints as the path it wraps")
  void equalityAndLabels() {
    var db = AppEnvFocus.db();
    assertThat(db.toString()).isEqualTo(db.toPath().toString());
    assertThat(db.segments()).isEqualTo(db.toPath().segments());
    assertThat(db).isEqualTo(new AppEnvFocus.DbNavigator<>(db.toPath()));
    assertThat(db).hasSameHashCodeAs(db.toPath());
  }

  @Test
  @DisplayName(
      "a navigator over a nullable field is an AffinePath, and one over a widened map a"
          + " TraversalPath")
  void affineAndTraversalTiers() {
    AffinePath<AppEnv, DbConfig> fallback = AppEnvFocus.fallback();
    assertThat(fallback.getOptional(ENV)).isEmpty();
    assertThat(AppEnvFocus.fallback().url().getOptional(ENV)).isEmpty();

    TraversalPath<AppEnv, DbConfig> replicas = AppEnvFocus.replicas();
    assertThat(replicas.count(ENV)).isEqualTo(1);
    assertThat(AppEnvFocus.replicas().port().getAll(ENV)).containsExactly(6543);
  }

  private static int portOf(FocusPath<AppEnv, DbConfig> path, AppEnv env) {
    return path.get(env).port();
  }
}
