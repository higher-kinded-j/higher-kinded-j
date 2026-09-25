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
