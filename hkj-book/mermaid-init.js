// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.
//
// Customised for this book's Catppuccin themes: Latte is light; Frappé,
// Macchiato and Mocha are dark. "Auto" resolves via prefers-color-scheme
// (mdBook applies the resolved theme name to <html> before this runs).

(() => {
    const darkThemes = ['frappe', 'macchiato', 'mocha'];
    const lightThemes = ['latte', 'light', 'rust'];

    const classList = document.getElementsByTagName('html')[0].classList;

    let lastThemeWasLight = true;
    for (const cssClass of classList) {
        if (darkThemes.includes(cssClass)) {
            lastThemeWasLight = false;
            break;
        }
    }

    const theme = lastThemeWasLight ? 'default' : 'dark';
    mermaid.initialize({ startOnLoad: true, theme });

    // Mermaid renders once at load, so switching between a light and a dark
    // page theme needs a refresh to re-render the diagrams to match.

    const prefersDark = () =>
        window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;

    const reloadIfLightnessChanges = (selectedIsLight) => {
        if (selectedIsLight !== lastThemeWasLight) {
            window.location.reload();
        }
    };

    for (const darkTheme of darkThemes) {
        document.getElementById(darkTheme)?.addEventListener('click', () => {
            reloadIfLightnessChanges(false);
        });
    }

    for (const lightTheme of lightThemes) {
        document.getElementById(lightTheme)?.addEventListener('click', () => {
            reloadIfLightnessChanges(true);
        });
    }

    document.getElementById('default_theme')?.addEventListener('click', () => {
        reloadIfLightnessChanges(!prefersDark());
    });
})();
