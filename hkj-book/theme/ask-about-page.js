// "Ask about this page": adds a comments icon to the menu bar, beside "Suggest an edit", and a
// giscus thread at the foot of the page. The thread is a GitHub Discussion in the Q&A category,
// keyed by the page's path without its version prefix, so every version of the book shares one
// thread per page. A badge on the icon shows how many questions the page already has.
//
// The page-specific term is set here at run time, so the giscus snippet's data-term is not used.

(function () {
  const REPO = 'higher-kinded-j/higher-kinded-j';
  const REPO_ID = 'R_kgDOOUBrhQ';
  const CATEGORY = 'Q&A';
  const CATEGORY_ID = 'DIC_kwDOOUBrhc4CqvMW';
  const SECTION_ID = 'ask-about-page';
  const GISCUS_ORIGIN = 'https://giscus.app';

  // mdBook's theme class on <html> to the giscus theme of the same name.
  const GISCUS_THEMES = {
    latte: 'catppuccin_latte',
    frappe: 'catppuccin_frappe',
    macchiato: 'catppuccin_macchiato',
    mocha: 'catppuccin_mocha',
  };

  // The page's path with the version prefix (/latest/ or /v0.4.11/) removed.
  function pageTerm() {
    const path = window.location.pathname.replace(/^\/(v\d+\.\d+\.\d+|latest)\//, '/');
    const term = path.replace(/^\/+/, '');
    return term === '' ? 'index.html' : term;
  }

  function giscusTheme() {
    const cls = Array.from(document.documentElement.classList).find((c) => c in GISCUS_THEMES);
    return GISCUS_THEMES[cls] || 'preferred_color_scheme';
  }

  function sendToGiscus(message) {
    const frame = document.querySelector('iframe.giscus-frame');
    if (frame) {
      frame.contentWindow.postMessage({ giscus: message }, GISCUS_ORIGIN);
    }
  }

  let embedded = false;
  let giscusScript = null;

  function embedGiscus(container, term) {
    if (embedded) {
      return;
    }
    embedded = true;
    const script = document.createElement('script');
    script.src = GISCUS_ORIGIN + '/client.js';
    script.async = true;
    script.crossOrigin = 'anonymous';
    const attributes = {
      'data-repo': REPO,
      'data-repo-id': REPO_ID,
      'data-category': CATEGORY,
      'data-category-id': CATEGORY_ID,
      'data-mapping': 'specific',
      'data-term': term,
      'data-strict': '1',
      'data-reactions-enabled': '1',
      'data-emit-metadata': '0',
      'data-input-position': 'top',
      'data-theme': giscusTheme(),
      'data-lang': 'en',
      'data-loading': 'lazy',
    };
    Object.keys(attributes).forEach((name) => script.setAttribute(name, attributes[name]));
    // A failed download leaves nothing to retry on, so drop the script and let the next
    // activation of the icon try again.
    script.addEventListener('error', () => {
      script.remove();
      giscusScript = null;
      embedded = false;
    });
    giscusScript = script;
    container.appendChild(script);
  }

  function showCount(link, count) {
    const badge = link.querySelector('.ask-about-page-badge');
    const label = 'Ask about this page';
    if (count > 0) {
      badge.textContent = count > 99 ? '99+' : String(count);
      badge.hidden = false;
      const noun = count === 1 ? 'question' : 'questions';
      link.title = label + ' (' + count + ' ' + noun + ')';
      link.setAttribute('aria-label', label + ', ' + count + ' ' + noun + ' so far');
    }
  }

  // A page with no thread yet answers 404, which is a count of zero, not an error.
  function loadCount(link, term) {
    const url =
      GISCUS_ORIGIN +
      '/api/discussions?repo=' + encodeURIComponent(REPO) +
      '&term=' + encodeURIComponent(term) +
      '&category=' + encodeURIComponent(CATEGORY) +
      '&strict=true';
    fetch(url)
      .then((response) => (response.ok ? response.json() : null))
      .then((data) => {
        if (data && data.discussion) {
          showCount(link, data.discussion.totalCommentCount || 0);
        }
      })
      .catch(() => {});
  }

  function buildSection() {
    const section = document.createElement('section');
    section.id = SECTION_ID;
    section.className = 'ask-about-page';
    section.tabIndex = -1;
    section.innerHTML =
      '<h2>Ask about this page</h2>' +
      '<p>Questions and answers about this page are kept in the project\'s ' +
      '<a href="https://github.com/' + REPO + '/discussions/categories/q-a">GitHub Discussions</a>. ' +
      'Sign in with GitHub to ask or reply.</p>' +
      '<div class="ask-about-page-thread"></div>';
    return section;
  }

  function buildLink() {
    const link = document.createElement('a');
    link.href = '#' + SECTION_ID;
    link.className = 'ask-about-page-link';
    link.title = 'Ask about this page';
    link.setAttribute('aria-label', 'Ask about this page');
    link.innerHTML =
      '<span class="ask-about-page-icon"><i class="fa fa-comments"></i>' +
      '<span class="ask-about-page-badge" hidden></span></span>';
    return link;
  }

  function init() {
    if (window.location.pathname.endsWith('/print.html')) {
      return;
    }
    const buttons = document.querySelector('.right-buttons');
    const main = document.querySelector('#content main');
    if (!buttons || !main) {
      return;
    }

    const term = pageTerm();
    const section = buildSection();
    const link = buildLink();
    main.appendChild(section);
    buttons.appendChild(link);

    const thread = section.querySelector('.ask-about-page-thread');
    const load = () => embedGiscus(thread, term);

    link.addEventListener('click', (event) => {
      event.preventDefault();
      load();
      section.scrollIntoView({ behavior: 'smooth', block: 'start' });
      section.focus({ preventScroll: true });
    });

    if ('IntersectionObserver' in window) {
      const observer = new IntersectionObserver(
        (entries) => {
          if (entries.some((entry) => entry.isIntersecting)) {
            observer.disconnect();
            load();
          }
        },
        { rootMargin: '400px' }
      );
      observer.observe(section);
    } else {
      load();
    }

    new MutationObserver(() => {
      const theme = giscusTheme();
      // Until the frame exists, client.js reads the theme from this attribute when it runs.
      if (giscusScript) {
        giscusScript.setAttribute('data-theme', theme);
      }
      sendToGiscus({ setConfig: { theme } });
    }).observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });

    loadCount(link, term);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
