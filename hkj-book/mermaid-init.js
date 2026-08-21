// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.
//
// Customised for this book's Catppuccin themes: Latte is light; Frappé,
// Macchiato and Mocha are dark. "Auto" resolves via prefers-color-scheme
// (mdBook applies the resolved theme name to <html> before this runs).

(() => {
    const darkThemes = ['frappe', 'macchiato', 'mocha', 'ayu', 'navy', 'coal'];

    const isLight = () => {
        for (const cssClass of document.documentElement.classList) {
            if (darkThemes.includes(cssClass)) {
                return false;
            }
        }
        return true;
    };

    const lastThemeWasLight = isLight();
    mermaid.initialize({
        startOnLoad: false,
        theme: lastThemeWasLight ? 'default' : 'dark',
    });

    // Render ourselves at window load instead of startOnLoad, so we run after
    // every DOMContentLoaded script has finished with the page. Mermaid reads
    // each element's markup as diagram source, so first collapse anything
    // another script injected into a diagram (change-tracking word underlines,
    // for instance) back to plain text.
    const renderAll = () => {
        document.querySelectorAll('pre.mermaid').forEach((el) => {
            if (el.firstElementChild) {
                el.textContent = el.textContent;
            }
        });
        mermaid.run().catch((err) => console.error('mermaid render failed', err));
    };
    if (document.readyState === 'complete') {
        renderAll();
    } else {
        window.addEventListener('load', renderAll);
    }

    // Mermaid renders once at load. mdBook swaps the <html> theme class both
    // on theme-menu clicks and, in Auto mode, when the system colour scheme
    // changes, so watch the class itself and re-render (via a reload)
    // whenever the lightness actually changes.
    new MutationObserver(() => {
        if (isLight() !== lastThemeWasLight) {
            window.location.reload();
        }
    }).observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['class'],
    });
})();
