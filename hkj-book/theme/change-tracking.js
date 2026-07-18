// Change tracking runtime.
//
// The mdBook preprocessor (`hkj_changes_preprocessor.py`) injects empty marker
// divs (`.hkj-ct-mark`) immediately before each new/changed block, carrying:
//   data-kind  = "new" | "modified"
//   data-run   = "solo" | "start" | "mid" | "end"   (position in a change run)
//   data-words = base64(JSON array of changed word tokens)  (modified only)
//   data-since = baseline version label (e.g. "v0.4.7")
//
// This script:
//   1. groups marker+block sequences into runs and wraps each run in a
//      `.hkj-new-content` container (the vertical margin line),
//   2. underlines the changed words inside modified blocks,
//   3. installs a three-state header toggle (full / lines / off), persisted.
//
// All DOM work happens on already-rendered HTML, so markdown is never touched.

(function () {
  'use strict';

  var STORAGE_KEY = 'hkj-ct-state';
  var STATES = ['full', 'lines', 'off'];
  var STATE_META = {
    full: { label: 'Changes: full', title: 'Change tracking: margin line + changed words' },
    lines: { label: 'Changes: lines', title: 'Change tracking: margin line only' },
    off: { label: 'Changes: off', title: 'Change tracking: hidden' }
  };
  var STRIP = /^[`*_\[\](){}#>\-.,:;!?"']+|[`*_\[\](){}#>\-.,:;!?"']+$/g;

  // --- state persistence ----------------------------------------------------

  // The preference is scoped per docs version (latest / vX.Y.Z / local build),
  // so a choice made on one version never leaks across the latest<->release
  // boundary: each version re-evaluates its own default and remembers its own
  // toggle independently. Detection mirrors version-switcher.js.
  function currentVersion() {
    try {
      var m = window.location.pathname.match(/^\/(v\d+\.\d+\.\d+|latest)(\/|$)/);
      if (m) return m[1];
    } catch (e) { /* window.location may be unavailable */ }
    return 'local';
  }

  function storageKey() {
    return STORAGE_KEY + ':' + currentVersion();
  }

  // The latest snapshot (deployed under /latest/) is where new/changed content
  // is most relevant, so change tracking defaults to 'full' there. Released
  // versions (/vX.Y.Z/) and everywhere else default to 'off' so historical or
  // baseline-less docs read cleanly. A stored preference for THIS version wins.
  function defaultState() {
    return currentVersion() === 'latest' ? 'full' : 'off';
  }

  function readState() {
    try {
      var stored = window.localStorage.getItem(storageKey());
      if (STATES.indexOf(stored) !== -1) return stored;
    } catch (e) { /* localStorage may be unavailable */ }
    return defaultState();
  }

  function applyState(state) {
    var root = document.documentElement;
    STATES.forEach(function (s) { root.classList.remove('hkj-ct-' + s); });
    root.classList.add('hkj-ct-' + state);
    try { window.localStorage.setItem(storageKey(), state); } catch (e) { /* ignore */ }
  }

  // --- word underlining -----------------------------------------------------

  function decodeWords(mark) {
    var raw = mark.getAttribute('data-words');
    if (!raw) return null;
    try {
      var json = decodeURIComponent(escape(window.atob(raw)));
      var arr = JSON.parse(json);
      return Array.isArray(arr) ? arr : null;
    } catch (e) {
      return null;
    }
  }

  function underlineWords(el, words) {
    if (!words || !words.length) return;
    var counts = Object.create(null);
    words.forEach(function (w) { counts[w] = (counts[w] || 0) + 1; });

    var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
      acceptNode: function (node) {
        // Skip code, pre and already-processed spans.
        var p = node.parentNode;
        while (p && p !== el) {
          var tag = p.nodeName;
          if (tag === 'CODE' || tag === 'PRE' ||
              (p.classList && p.classList.contains('hkj-word-changed'))) {
            return NodeFilter.FILTER_REJECT;
          }
          p = p.parentNode;
        }
        return node.nodeValue.trim() ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      }
    });

    var targets = [];
    var current;
    while ((current = walker.nextNode())) { targets.push(current); }

    targets.forEach(function (node) {
      var parts = node.nodeValue.split(/(\s+)/);
      var changedHere = false;
      parts.forEach(function (tok) {
        if (tok && !/^\s+$/.test(tok)) {
          var core = tok.replace(STRIP, '');
          if (core && counts[core] > 0) { changedHere = true; }
        }
      });
      if (!changedHere) return;

      var frag = document.createDocumentFragment();
      parts.forEach(function (tok) {
        if (tok === '') return;
        if (/^\s+$/.test(tok)) {
          frag.appendChild(document.createTextNode(tok));
          return;
        }
        var core = tok.replace(STRIP, '');
        if (core && counts[core] > 0) {
          counts[core] -= 1;
          var span = document.createElement('span');
          span.className = 'hkj-word-changed';
          span.textContent = tok;
          frag.appendChild(span);
        } else {
          frag.appendChild(document.createTextNode(tok));
        }
      });
      node.parentNode.replaceChild(frag, node);
    });
  }

  // --- run grouping + wrapping ---------------------------------------------

  function collectEntries(content) {
    var marks = content.querySelectorAll('.hkj-ct-mark');
    var entries = [];
    Array.prototype.forEach.call(marks, function (mark) {
      entries.push({
        mark: mark,
        block: mark.nextElementSibling,
        kind: mark.getAttribute('data-kind') || 'new',
        run: mark.getAttribute('data-run') || 'solo',
        words: decodeWords(mark)
      });
    });
    return entries;
  }

  function groupRuns(entries) {
    var runs = [];
    var open = null;
    entries.forEach(function (entry) {
      if (entry.run === 'solo') {
        runs.push([entry]);
        open = null;
      } else if (entry.run === 'start') {
        open = [entry];
        runs.push(open);
      } else if ((entry.run === 'mid' || entry.run === 'end') && open) {
        open.push(entry);
        if (entry.run === 'end') open = null;
      } else {
        // Malformed sequence: fall back to a standalone run.
        runs.push([entry]);
        open = null;
      }
    });
    return runs;
  }

  function wrapRun(run) {
    var blocks = run.map(function (e) { return e.block; }).filter(Boolean);
    if (!blocks.length) {
      run.forEach(function (e) { if (e.mark.parentNode) e.mark.remove(); });
      return;
    }
    var wrapper = document.createElement('div');
    wrapper.className = 'hkj-new-content';
    var since = run[0].mark.getAttribute('data-since');
    if (since) wrapper.setAttribute('data-since', since);

    blocks[0].parentNode.insertBefore(wrapper, blocks[0]);
    // Remove markers first so they don't get pulled into the wrapper.
    run.forEach(function (e) { if (e.mark.parentNode) e.mark.remove(); });
    // Move the contiguous DOM range from the first block to the last, inclusive.
    // Cherry-picking only the marked blocks would strand any unmarked element
    // rendered between them - e.g. an h2 subtitle whose preceding h1 shares its
    // marker (title and subtitle are one markdown block) - leaving it behind as
    // the last sibling, visible as an orphaned heading at the foot of the page.
    var lastBlock = blocks[blocks.length - 1];
    var node = blocks[0];
    while (node) {
      var next = node.nextSibling;
      wrapper.appendChild(node);
      if (node === lastBlock) break;
      node = next;
    }

    // Underline changed words per modified block.
    run.forEach(function (entry) {
      if (entry.kind === 'modified' && entry.block) {
        underlineWords(entry.block, entry.words);
      }
    });
  }

  function processContent() {
    var content = document.getElementById('content') || document.querySelector('main');
    if (!content) return;
    var entries = collectEntries(content);
    if (!entries.length) return;
    groupRuns(entries).forEach(wrapRun);
  }

  // --- sidebar TOC decoration ----------------------------------------------

  function readManifest() {
    var el = document.getElementById('hkj-ct-manifest');
    if (!el) return null;
    var since = el.getAttribute('data-since') || '';
    var changed = [];
    var raw = el.getAttribute('data-changed');
    if (raw) {
      try {
        var json = decodeURIComponent(escape(window.atob(raw)));
        var arr = JSON.parse(json);
        if (Array.isArray(arr)) changed = arr;
      } catch (e) { /* leave changed empty */ }
    }
    return { changed: changed, since: since };
  }

  function decorateSidebar(changedPaths) {
    if (!changedPaths || !changedPaths.length) return;
    var sidebar = document.querySelector('#sidebar .chapter') ||
                  document.querySelector('#sidebar') ||
                  document.querySelector('.sidebar');
    if (!sidebar) return;

    changedPaths.forEach(function (path) {
      // Match the sidebar link for this chapter by href suffix (hrefs are
      // relative and depth-dependent, so endsWith is the robust match).
      var links = sidebar.querySelectorAll('a[href]');
      Array.prototype.forEach.call(links, function (link) {
        var href = link.getAttribute('href').split('#')[0].split('?')[0];
        if (!href.endsWith(path)) return;
        link.classList.add('hkj-ct-changed');

        // Flag ancestor sections so a collapsed group still signals that
        // something inside it changed (hollow dot, styled by CSS). In mdBook a
        // section heading and its children are *siblings*: the children live in
        // an <ol.section> wrapped in a bare <li>, and the heading is the
        // <li.chapter-item> immediately before that wrapper. Climb by hopping
        // wrapper -> previous sibling (the heading) -> its enclosing <ol>.
        var li = link.closest('li');
        while (li) {
          var ol = li.parentElement;                 // enclosing <ol>
          var wrap = ol && ol.parentElement;         // bare <li> holding <ol>
          if (!wrap || wrap.tagName !== 'LI') break;  // reached top <ol.chapter>
          var headingLi = wrap.previousElementSibling;
          if (!headingLi || headingLi.tagName !== 'LI') break;
          var headingLink = headingLi.querySelector(':scope > a');
          if (headingLink && !headingLink.classList.contains('hkj-ct-changed')) {
            headingLink.classList.add('hkj-ct-changed-descendant');
          }
          li = headingLi;
        }
      });
    });
  }

  // --- header toggle --------------------------------------------------------

  function installToggle(sinceLabel) {
    var container = document.querySelector('.right-buttons') ||
                    document.querySelector('#menu-bar .menu-title');
    if (!container || document.getElementById('hkj-ct-toggle')) return;

    var button = document.createElement('button');
    button.id = 'hkj-ct-toggle';
    button.type = 'button';
    button.className = 'icon-button';

    var dot = document.createElement('span');
    dot.className = 'hkj-ct-dot';
    var label = document.createElement('span');
    label.className = 'hkj-ct-label';
    button.appendChild(dot);
    button.appendChild(label);

    function render() {
      var state = readState();
      var meta = STATE_META[state];
      label.textContent = meta.label;
      var title = meta.title;
      if (sinceLabel) title += ' (since ' + sinceLabel + ')';
      button.title = title;
      button.setAttribute('aria-label', title);
    }

    button.addEventListener('click', function () {
      var next = (STATES.indexOf(readState()) + 1) % STATES.length;
      applyState(STATES[next]);
      render();
    });

    container.appendChild(button);
    render();
  }

  // --- init -----------------------------------------------------------------

  function init() {
    applyState(readState());
    processContent();
    var manifest = readManifest();
    if (manifest) {
      decorateSidebar(manifest.changed);
      installToggle(manifest.since);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
