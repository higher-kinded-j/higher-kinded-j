// Send an old deep link to where its section lives now.
//
// mdbook can redirect a page, but a page redirect drops the fragment, and it
// refuses to redirect a page that still exists. Neither helps when a section
// moves off a page that stays: every link to `basics.html#optional-bridge`
// then lands at the top of basics, with nothing to say the section is
// elsewhere. This map is the missing half, and the book's anchor check keeps
// the live links honest in the other direction.
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
    "release-history.html#0411-snapshot-latest": "release-history/unreleased.html",
    "release-history.html#recent-releases": "release-history.html#releases-at-a-glance",
    "release-history.html#earlier-releases": "release-history/earlier.html",
    "release-history.html#documentation--tutorial-improvements": "release-history/v0_4_4.html#documentation--tutorial-improvements",
    "release-history.html#documentation--tutorial-improvements-1": "release-history/v0_4_3.html#documentation--tutorial-improvements",
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
