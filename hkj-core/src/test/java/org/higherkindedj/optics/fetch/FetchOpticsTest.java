// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.fetch.FetchKindHelper.FETCH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.Optic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capability A release, slice 1: {@link FetchOptics#fetchEach} lets a list of identifiers be
 * fetched into a list of resolved entities of a different type, in one batched round.
 */
@DisplayName("FetchOptics.fetchEach: heterogeneous list fetch")
class FetchOpticsTest {

  /** A team carrying integer member ids. */
  record Team(String name, List<Integer> memberIds) {}

  /** The same team with member ids resolved into names. */
  record EnrichedTeam(String name, List<String> memberNames) {}

  /** The type-changing optic: ids in, names out, the structure rebuilt. */
  private static final Optic<Team, EnrichedTeam, Integer, String> MEMBER_FETCH =
      FetchOptics.fetchEach(Team::memberIds, (team, names) -> new EnrichedTeam(team.name(), names));

  /** Resolves integer ids into person names. */
  private static final Function<Set<Integer>, Map<Integer, String>> NAME_DIRECTORY =
      ids -> {
        Map<Integer, String> out = new HashMap<>();
        for (Integer id : ids) {
          out.put(
              id,
              switch (id) {
                case 1 -> "Alice";
                case 2 -> "Bob";
                case 3 -> "Cara";
                default -> "User#" + id;
              });
        }
        return out;
      };

  @Test
  @DisplayName("heterogeneous fetch: ids in, names out, in one batched round")
  void heterogeneousFetchRebuildsTheStructure() {
    Team team = new Team("Engineering", List.of(1, 2, 3));

    Kind<FetchKind.Witness<Integer, String>, EnrichedTeam> program =
        MEMBER_FETCH.modifyF(
            id -> FETCH.widen(Fetch.<Integer, String>fetch(id)),
            team,
            FetchApplicative.<Integer, String>instance());

    Fetch.RunResult<Integer, EnrichedTeam> result =
        Fetch.runCached(FETCH.narrow(program), NAME_DIRECTORY);

    assertThat(result.value())
        .isEqualTo(new EnrichedTeam("Engineering", List.of("Alice", "Bob", "Cara")));
    assertThat(result.rounds()).isEqualTo(1);
    assertThat(result.backendCalls()).isEqualTo(1);
    assertThat(result.fetchedBatches().get(0)).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  @DisplayName("an empty list is rebuilt without any fetch")
  void emptyListRebuiltWithoutFetch() {
    Team team = new Team("Empty", List.of());

    Kind<FetchKind.Witness<Integer, String>, EnrichedTeam> program =
        MEMBER_FETCH.modifyF(
            id -> FETCH.widen(Fetch.<Integer, String>fetch(id)),
            team,
            FetchApplicative.<Integer, String>instance());

    Fetch.RunResult<Integer, EnrichedTeam> result =
        Fetch.runCached(FETCH.narrow(program), NAME_DIRECTORY);

    assertThat(result.value()).isEqualTo(new EnrichedTeam("Empty", List.of()));
    assertThat(result.rounds()).isZero();
    assertThat(result.backendCalls()).isZero();
  }

  @Test
  @DisplayName("the original structure is consulted by rebuild (other fields preserved)")
  void rebuildSeesTheOriginalStructure() {
    // The rebuild function receives the original Team -- it can read `name` and any field not in
    // the list focus -- so heterogeneous fetch is non-destructive for the rest of the structure.
    Team team = new Team("Platform", List.of(1));

    Kind<FetchKind.Witness<Integer, String>, EnrichedTeam> program =
        MEMBER_FETCH.modifyF(
            id -> FETCH.widen(Fetch.<Integer, String>fetch(id)),
            team,
            FetchApplicative.<Integer, String>instance());

    EnrichedTeam result = Fetch.runCached(FETCH.narrow(program), NAME_DIRECTORY).value();

    assertThat(result.name()).isEqualTo("Platform");
    assertThat(result.memberNames()).containsExactly("Alice");
  }
}
