// Chapter-title detector: adds `chapter-intro` class to <body> on the
// top-level chapter intro pages (Scope A). Works alongside the Catppuccin
// theme classes so `additional-hkj.css` can style those titles distinctly
// per theme without requiring per-page HTML edits.
//
// If a new top-level chapter intro is added, append its path suffix below.

(function () {
  const CHAPTER_INTRO_PATHS = [
    '/quickstart.html',
    '/cheatsheet.html',
    '/effect/ch_intro.html',
    '/optics/ch_intro.html',
    '/transformers/ch_intro.html',
    '/hkts/foundations_intro.html',
    '/examples/ch_intro.html',
    '/tutorials/ch_intro.html',
    '/tooling/ch_intro.html',
    '/spring/ch_intro.html',
    '/reference_intro.html',
    '/project_info_intro.html',
  ];

  function applyChapterIntroClass() {
    const path = window.location.pathname;
    if (CHAPTER_INTRO_PATHS.some((p) => path.endsWith(p))) {
      document.body.classList.add('chapter-intro');
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', applyChapterIntroClass);
  } else {
    applyChapterIntroClass();
  }
})();
