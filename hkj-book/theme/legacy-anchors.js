// Send an old deep link to where its section lives now.
//
// mdbook can redirect a page, and the book's redirect template carries the
// fragment across, but mdbook refuses to redirect a page that still exists. So
// a redirect cannot help when a section moves off a page that stays: every
// link to `basics.html#optional-bridge` then lands at the top of basics, with
// nothing to say the section is elsewhere. This map is the missing half, and the book's anchor check keeps
// the live links honest in the other direction. It also carries a heading
// renamed on its own page when the old id would mislead, such as one that
// gave a time estimate.
//
// An entry maps an old "page.html#fragment" to its new "page.html#fragment",
// relative to the book root. Matching is on the path suffix, so the same entry
// works under /latest/ and under a versioned copy. A re-deployed version is
// built with the current theme, so it runs this map too: an older copy of a
// page that still carries the fragment keeps the reader, because a fragment
// that resolves on the page is never redirected.
(function () {
  "use strict";

  var MOVED = {
    "transformers/common_errors.html#3-method-mapt-cannot-be-applied-on-statet": "transformers/statet_transformer.html#transforming-the-outer-monad-with-mapt",
    "transformers/common_errors.html#4-the-phantom-l-on-eithertfromeither--eitherright": "transformers/common_errors.html#3-the-phantom-l-on-eithertfromeither--eitherright",
    "transformers/common_errors.html#5-cannot-find-symbol-value-on-a-kind": "transformers/common_errors.html#4-cannot-find-symbol-value-on-a-kind",
    "transformers/common_errors.html#6-method-forfrom-is-not-applicable-with-the-wrong-monad": "transformers/common_errors.html#5-method-forfrom-is-not-applicable-with-the-wrong-monad",
    "mapping/basics.html#the-null-contract-precisely": "mapping/rules.html#the-null-contract-precisely",
    "mapping/basics.html#same-typed-containers-cross-as-copies": "mapping/rules.html#same-typed-containers-cross-as-copies",
    "mapping/basics.html#how-the-two-default-families-are-told-apart": "mapping/rules.html#how-the-two-default-families-are-told-apart",
    "mapping/basics.html#derived-fields-and-the-emission-tiers": "mapping/rules.html#derived-fields-and-the-emission-tiers",
    "mapping/structure.html#how-a-dependencys-specs-are-found": "mapping/rules.html#how-a-dependencys-specs-are-found",
    "mapping/codecs.html#admonition-the-inheritance-edge-cases-precisely": "mapping/rules.html#inheriting-one-member-twice",
    "mapping/generics.html#admonition-boundaries": "mapping/rules.html#generic-boundaries",
    "mapping/basics.html#optional-bridge": "mapping/absence.html#optional-bridge",
    "mapping/basics.html#constructor-invariants": "mapping/absence.html#constructor-invariants",
    "mapping/basics.html#the-fine-print": "mapping/basics.html#bind-in-the-caller",
    "mapping/codecs.html#your-own-canon-validatedprismcanonical": "mapping/codecs.html#your-own-canon",
    "mapping/merge_envelopes.html#admonition-fine-grained-or-coarse-variants": "mapping/merge_envelopes.html#fine-grained-or-coarse-variants",
    "mapping/beans_patch.html#bean-shaped-wire-targets": "mapping/beans.html#bean-shaped-wire-targets",
    "mapping/beans_patch.html#bean-projections": "mapping/beans.html#bean-projections",
    "mapping/beans_patch.html#accessors-meant-to-stay-out": "mapping/beans.html#accessors-meant-to-stay-out",
    "mapping/beans_patch.html#one-directional-beans": "mapping/beans.html#one-directional-beans",
    "mapping/compiler_errors.html#private-access-in-an-impl": "mapping/compiler_errors.html#component-cannot-be-reached",
    "mapping/compiler_errors.html#inside-a-generated-impl": "mapping/compiler_errors.html#where-the-message-came-from",
    "release-history.html#v0410-30-august-2026": "release-history/v0_4_10.html#v0410-30-august-2026",
    "release-history.html#v049-31-july-2026": "release-history/v0_4_9.html#v049-31-july-2026",
    "release-history.html#v048-17-july-2026": "release-history/v0_4_8.html#v048-17-july-2026",
    "release-history.html#v047-26-june-2026": "release-history/v0_4_7.html#v047-26-june-2026",
    "release-history.html#v046-7-june-2026": "release-history/v0_4_6.html#v046-7-june-2026",
    "release-history.html#v045-22-may-2026": "release-history/v0_4_5.html#v045-22-may-2026",
    "release-history.html#v044-16-may-2026": "release-history/v0_4_4.html#v044-16-may-2026",
    "release-history.html#v043-7-may-2026": "release-history/v0_4_3.html#v043-7-may-2026",
    "release-history.html#v042-18-april-2026": "release-history/v0_4_2.html#v042-18-april-2026",
    "release-history.html#v041-8-april-2026": "release-history/v0_4_1.html#v041-8-april-2026",
    "release-history.html#v040-22-march-2026": "release-history/v0_4_0.html#v040-22-march-2026",
    "release-history.html#v037-15-march-2026": "release-history/v0_3.html#v037-15-march-2026",
    "release-history.html#v036-6-march-2026": "release-history/v0_3.html#v036-6-march-2026",
    "release-history.html#v035-15-february-2026": "release-history/v0_3.html#v035-15-february-2026",
    "release-history.html#v034-31-january-2026": "release-history/v0_3.html#v034-31-january-2026",
    "release-history.html#v033-24-january-2026": "release-history/v0_3.html#v033-24-january-2026",
    "release-history.html#v032-17-january-2026": "release-history/v0_3.html#v032-17-january-2026",
    "release-history.html#v031-15-january-2026": "release-history/v0_3.html#v031-15-january-2026",
    "release-history.html#v030-4-january-2026": "release-history/v0_3.html#v030-4-january-2026",
    "release-history.html#v028-26-december-2025": "release-history/earlier.html#v028-26-december-2025",
    "release-history.html#v027-20-december-2025": "release-history/earlier.html#v027-20-december-2025",
    "release-history.html#v026-19-december-2025": "release-history/earlier.html#v026-19-december-2025",
    "release-history.html#v025-9-december-2025": "release-history/earlier.html#v025-9-december-2025",
    "release-history.html#v024-3-december-2025": "release-history/earlier.html#v024-3-december-2025",
    "release-history.html#v023-1-december-2025": "release-history/earlier.html#v023-1-december-2025",
    "release-history.html#v022-29-november-2025": "release-history/earlier.html#v022-29-november-2025",
    "release-history.html#v021-23-november-2025": "release-history/earlier.html#v021-23-november-2025",
    "release-history.html#v020-21-november-2025": "release-history/earlier.html#v020-21-november-2025",
    "release-history.html#v019-14-november-2025": "release-history/earlier.html#v019-14-november-2025",
    "release-history.html#v018-9-september-2025": "release-history/earlier.html#v018-9-september-2025",
    "release-history.html#v017-29-august-2025": "release-history/earlier.html#v017-29-august-2025",
    "release-history.html#v016-14-july-2025": "release-history/earlier.html#v016-14-july-2025",
    "release-history.html#v015-12-june-2025": "release-history/earlier.html#v015-12-june-2025",
    "release-history.html#v014-5-june-2025": "release-history/earlier.html#v014-5-june-2025",
    "release-history.html#v013-31-may-2025": "release-history/earlier.html#v013-31-may-2025",
    "release-history.html#v010-3-may-2025": "release-history/earlier.html#v010-3-may-2025",
    "release-history.html#0411-snapshot-latest": "release-history/v0_4_11.html",
    "release-history.html#recent-releases": "release-history.html#releases-at-a-glance",
    "release-history.html#earlier-releases": "release-history/earlier.html",
    "release-history.html#documentation--tutorial-improvements": "release-history/v0_4_4.html#documentation--tutorial-improvements",
    "release-history.html#documentation--tutorial-improvements-1": "release-history/v0_4_3.html#documentation--tutorial-improvements",
    "tutorials/ch_intro.html#fifteen-journeys": "tutorials/ch_intro.html#journeys",
    "tutorials/concurrency/scope_resource_journey.html#tutorial-1-structured-concurrency-with-scope-15-minutes": "tutorials/concurrency/scope_resource_journey.html#tutorial-1-structured-concurrency-with-scope",
    "tutorials/concurrency/scope_resource_journey.html#tutorial-2-resource-management-15-minutes": "tutorials/concurrency/scope_resource_journey.html#tutorial-2-resource-management",
    "tutorials/concurrency/vtask_journey.html#tutorial-1-vtask-fundamentals-25-minutes": "tutorials/concurrency/vtask_journey.html#tutorial-1-vtask-fundamentals",
    "tutorials/concurrency/vtask_journey.html#tutorial-2-vtaskpath-effect-api-20-minutes": "tutorials/concurrency/vtask_journey.html#tutorial-2-vtaskpath-effect-api",
    "tutorials/coretypes/advanced_journey.html#tutorial-08-natural-transformations-10-minutes": "tutorials/coretypes/advanced_journey.html#tutorial-08-natural-transformations",
    "tutorials/coretypes/advanced_journey.html#tutorial-09-coyoneda-10-minutes": "tutorials/coretypes/advanced_journey.html#tutorial-09-coyoneda",
    "tutorials/coretypes/advanced_journey.html#tutorial-10-free-applicative-10-minutes": "tutorials/coretypes/advanced_journey.html#tutorial-10-free-applicative",
    "tutorials/coretypes/advanced_journey.html#tutorial-11-static-analysis-10-minutes": "tutorials/coretypes/advanced_journey.html#tutorial-11-static-analysis",
    "tutorials/coretypes/error_handling_journey.html#tutorial-05-monaderror-handling-10-minutes": "tutorials/coretypes/error_handling_journey.html#tutorial-05-monaderror-handling",
    "tutorials/coretypes/error_handling_journey.html#tutorial-06-concrete-types-10-minutes": "tutorials/coretypes/error_handling_journey.html#tutorial-06-concrete-types",
    "tutorials/coretypes/error_handling_journey.html#tutorial-07-real-world-10-minutes": "tutorials/coretypes/error_handling_journey.html#tutorial-07-real-world",
    "tutorials/coretypes/error_handling_journey.html#tutorial-12-accumulating-assembly-10-minutes": "tutorials/coretypes/error_handling_journey.html#tutorial-12-accumulating-assembly",
    "tutorials/coretypes/foundations_journey.html#tutorial-00-one-line-six-layers-10-minutes": "tutorials/coretypes/foundations_journey.html#tutorial-00-one-line-six-layers",
    "tutorials/coretypes/foundations_journey.html#tutorial-01-kind-basics-10-minutes": "tutorials/coretypes/foundations_journey.html#tutorial-01-kind-basics",
    "tutorials/coretypes/foundations_journey.html#tutorial-02-functor-mapping-10-minutes": "tutorials/coretypes/foundations_journey.html#tutorial-02-functor-mapping",
    "tutorials/coretypes/foundations_journey.html#tutorial-03-applicative-combining-10-minutes": "tutorials/coretypes/foundations_journey.html#tutorial-03-applicative-combining",
    "tutorials/coretypes/foundations_journey.html#tutorial-04-monad-chaining-10-minutes": "tutorials/coretypes/foundations_journey.html#tutorial-04-monad-chaining",
    "tutorials/effect/effect_journey.html#part-1-fundamentals-35-min-9-exercises": "tutorials/effect/effect_journey.html#part-1-fundamentals",
    "tutorials/effect/effect_journey.html#part-2-advanced-35-min-8-exercises": "tutorials/effect/effect_journey.html#part-2-advanced",
    "tutorials/expression/forpath_parallel_journey.html#tutorial-02-forpath-parallel-composition-20-minutes": "tutorials/expression/forpath_parallel_journey.html#tutorial-02-forpath-parallel-composition",
    "tutorials/expression/forstate_journey.html#tutorial-01-forstate-basics-25-minutes": "tutorials/expression/forstate_journey.html#tutorial-01-forstate-basics",
    "tutorials/learning_paths.html#error-mastery-70-min": "tutorials/learning_paths.html#error-mastery",
    "tutorials/learning_paths.html#dsl-power-70-min": "tutorials/learning_paths.html#dsl-power",
    "tutorials/learning_paths.html#effect-api-complete-65-min": "tutorials/learning_paths.html#effect-api-complete",
    "tutorials/learning_paths.html#concurrency-complete-75-min": "tutorials/learning_paths.html#concurrency-complete",
    "tutorials/optics/batching_journey.html#tutorial-21-optic-driven-request-batching-15-minutes": "tutorials/optics/batching_journey.html#tutorial-21-optic-driven-request-batching",
    "tutorials/optics/batching_journey.html#tutorial-22-plan-introspection-and-guardrails-12-minutes": "tutorials/optics/batching_journey.html#tutorial-22-plan-introspection-and-guardrails",
    "tutorials/optics/batching_journey.html#tutorial-23-n-ary-coupled-lenses-10-minutes": "tutorials/optics/batching_journey.html#tutorial-23-n-ary-coupled-lenses",
    "tutorials/optics/boundary_mapping_journey.html#tutorial-24-multi-edit-and-sparse-updates-12-minutes": "tutorials/optics/boundary_mapping_journey.html#tutorial-24-multi-edit-and-sparse-updates",
    "tutorials/optics/boundary_mapping_journey.html#tutorial-25-validatedprism-10-minutes": "tutorials/optics/boundary_mapping_journey.html#tutorial-25-validatedprism",
    "tutorials/optics/boundary_mapping_journey.html#tutorial-26-record-mapping-12-minutes": "tutorials/optics/boundary_mapping_journey.html#tutorial-26-record-mapping",
    "tutorials/optics/boundary_mapping_journey.html#tutorial-27-boundary-edge-cases-15-minutes": "tutorials/optics/boundary_mapping_journey.html#tutorial-27-boundary-edge-cases",
    "tutorials/optics/fluent_free_journey.html#tutorial-09-fluent-optics-api-10-minutes": "tutorials/optics/fluent_free_journey.html#tutorial-09-fluent-optics-api",
    "tutorials/optics/fluent_free_journey.html#tutorial-10-advanced-prism-patterns-10-minutes": "tutorials/optics/fluent_free_journey.html#tutorial-10-advanced-prism-patterns",
    "tutorials/optics/fluent_free_journey.html#tutorial-11-free-monad-dsl-15-minutes": "tutorials/optics/fluent_free_journey.html#tutorial-11-free-monad-dsl",
    "optics/focus_dsl.html#five-minute-focus-dsl": "optics/focus_dsl.html#the-whole-feature-on-one-screen",
    "tutorials/optics/focus_dsl_journey.html#tutorial-12-focus-dsl-basics-10-minutes": "tutorials/optics/focus_dsl_journey.html#tutorial-12-focus-dsl-basics",
    "tutorials/optics/focus_dsl_journey.html#tutorial-13-advanced-focus-dsl-10-minutes": "tutorials/optics/focus_dsl_journey.html#tutorial-13-advanced-focus-dsl",
    "tutorials/optics/focus_dsl_journey.html#tutorial-19-navigator-generation-10-minutes": "tutorials/optics/focus_dsl_journey.html#tutorial-19-navigator-generation",
    "tutorials/optics/focus_dsl_journey.html#tutorial-20-container-navigation-5-minutes": "tutorials/optics/focus_dsl_journey.html#tutorial-20-container-navigation",
    "tutorials/optics/lens_prism_journey.html#tutorial-01-lens-basics-10-minutes": "tutorials/optics/lens_prism_journey.html#tutorial-01-lens-basics",
    "tutorials/optics/lens_prism_journey.html#tutorial-02-lens-composition-10-minutes": "tutorials/optics/lens_prism_journey.html#tutorial-02-lens-composition",
    "tutorials/optics/lens_prism_journey.html#tutorial-03-prism-basics-10-minutes": "tutorials/optics/lens_prism_journey.html#tutorial-03-prism-basics",
    "tutorials/optics/lens_prism_journey.html#tutorial-04-affine-basics-10-minutes": "tutorials/optics/lens_prism_journey.html#tutorial-04-affine-basics",
    "tutorials/optics/traversals_journey.html#tutorial-05-traversal-basics-10-minutes": "tutorials/optics/traversals_journey.html#tutorial-05-traversal-basics",
    "tutorials/optics/traversals_journey.html#tutorial-06-optics-composition-10-minutes": "tutorials/optics/traversals_journey.html#tutorial-06-optics-composition",
    "tutorials/optics/traversals_journey.html#tutorial-07-generated-optics-10-minutes": "tutorials/optics/traversals_journey.html#tutorial-07-generated-optics",
    "tutorials/optics/traversals_journey.html#tutorial-08-real-world-optics-10-minutes": "tutorials/optics/traversals_journey.html#tutorial-08-real-world-optics",
    "tutorials/resilience/resilience_journey.html#tutorial-1-circuit-breaker-10-minutes": "tutorials/resilience/resilience_journey.html#tutorial-1-circuit-breaker",
    "tutorials/resilience/resilience_journey.html#tutorial-2-saga-10-minutes": "tutorials/resilience/resilience_journey.html#tutorial-2-saga",
    "tutorials/resilience/resilience_journey.html#tutorial-3-retry-bulkhead--combined-resilience-10-minutes": "tutorials/resilience/resilience_journey.html#tutorial-3-retry-bulkhead--combined-resilience",
    "tutorials/resilience/resilience_journey.html#tutorial-4-path-api-resilience-10-minutes": "tutorials/resilience/resilience_journey.html#tutorial-4-path-api-resilience",
    "tutorials/transformers/transformers_journey.html#tutorial-01-when-path-isnt-enough-30-min-6-exercises": "tutorials/transformers/transformers_journey.html#tutorial-01-when-path-isnt-enough",
    "tutorials/transformers/transformers_journey.html#tutorial-02-async-with-absence-25-min-5-exercises": "tutorials/transformers/transformers_journey.html#tutorial-02-async-with-absence",
    "tutorials/transformers/transformers_journey.html#tutorial-03-stacking-transformers-15-min-4-exercises": "tutorials/transformers/transformers_journey.html#tutorial-03-stacking-transformers",
    "tutorials/transformers/transformers_journey.html#tutorial-04-polymorphic-capabilities-mtl-30-40-min-14-exercises": "tutorials/transformers/transformers_journey.html#tutorial-04-polymorphic-capabilities-mtl",
    "tutorials/tutorials_intro.html#thirteen-focused-journeys": "tutorials/tutorials_intro.html#focused-journeys",
    "tutorials/tutorials_intro.html#expression-journey": "tutorials/tutorials_intro.html#expression-journeys",
    "tutorials/tutorials_intro.html#practical-fp-4-sessions": "tutorials/tutorials_intro.html#practical-fp",
    "tutorials/tutorials_intro.html#optics-specialist-4-sessions": "tutorials/tutorials_intro.html#optics-specialist",
    "tutorials/tutorials_intro.html#full-curriculum-13-sessions": "tutorials/tutorials_intro.html#full-curriculum",
  };

  function target() {
    var hash = window.location.hash;
    if (!hash || hash.length < 2) return null;
    var pathname = window.location.pathname;
    for (var from in MOVED) {
      if (!Object.prototype.hasOwnProperty.call(MOVED, from)) continue;
      var split = from.indexOf("#");
      var page = from.slice(0, split);
      var fragment = from.slice(split);
      if (fragment !== hash) continue;
      // The path suffix, so /latest/ and /v0.4.10/ both match.
      if (pathname.slice(-page.length) !== page) continue;
      var root = pathname.slice(0, pathname.length - page.length);
      return root + MOVED[from];
    }
    return null;
  }

  function follow() {
    // A fragment that resolves on this page is not stale, whatever the map says.
    var hash = window.location.hash;
    if (hash && hash.length > 1) {
      var id = decodeURIComponent(hash.slice(1));
      if (document.getElementById(id)) return;
    }
    var moved = target();
    if (moved) window.location.replace(moved);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", follow);
  } else {
    follow();
  }
  window.addEventListener("hashchange", follow);
})();
