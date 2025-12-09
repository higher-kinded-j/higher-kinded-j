(function() {
    'use strict';

    /**
     * Adds a "Show menu" link to page navigation sections.
     * The link only appears when the sidebar is closed.
     */

    const LINK_CLASS = 'nav-show-sidebar';
    const LINK_TEXT = 'Show menu';

    /**
     * Check if sidebar is currently hidden
     */
    function isSidebarHidden() {
        const html = document.documentElement;
        const sidebar = document.querySelector('.sidebar');

        // mdBook uses 'sidebar-hidden' class on html element
        if (html.classList.contains('sidebar-hidden')) {
            return true;
        }

        // Fallback: check if sidebar is visually hidden
        if (sidebar) {
            const style = window.getComputedStyle(sidebar);
            const transform = style.transform || style.webkitTransform;
            // Sidebar is hidden when translated off-screen
            if (transform && transform !== 'none' && transform.includes('matrix')) {
                const matrix = new DOMMatrix(transform);
                return matrix.m41 < 0; // Translated left (hidden)
            }
        }

        return false;
    }

    /**
     * Toggle the sidebar open
     */
    function openSidebar() {
        const html = document.documentElement;
        html.classList.remove('sidebar-hidden');
        html.classList.add('sidebar-visible');

        // Also try clicking the toggle button for proper state sync
        const toggleButton = document.getElementById('sidebar-toggle');
        if (toggleButton && isSidebarHidden()) {
            toggleButton.click();
        }

        updateLinkVisibility();
    }

    /**
     * Update visibility of all nav sidebar links
     */
    function updateLinkVisibility() {
        const links = document.querySelectorAll('.' + LINK_CLASS);
        const hidden = isSidebarHidden();

        links.forEach(function(link) {
            link.style.display = hidden ? 'inline-block' : 'none';
        });
    }

    /**
     * Find navigation sections and inject the sidebar link
     */
    function injectNavLinks() {
        // Look for the navigation pattern: **Previous:** or **Next:**
        // These are typically in <p> tags with <strong> elements
        const content = document.querySelector('.content main');
        if (!content) return;

        // Find paragraphs containing navigation links
        const paragraphs = content.querySelectorAll('p');
        let navParagraph = null;

        paragraphs.forEach(function(p) {
            const text = p.textContent || '';
            if (text.includes('Previous:') || text.includes('Next:')) {
                navParagraph = p;
            }
        });

        if (!navParagraph) return;

        // Check if we already added the link
        if (navParagraph.querySelector('.' + LINK_CLASS)) return;

        // Create the show menu link
        const link = document.createElement('a');
        link.href = '#';
        link.className = LINK_CLASS;
        link.textContent = LINK_TEXT;
        link.setAttribute('aria-label', 'Open sidebar navigation menu');
        link.addEventListener('click', function(e) {
            e.preventDefault();
            openSidebar();
        });

        // Insert before the navigation paragraph
        const separator = document.createElement('span');
        separator.className = 'nav-show-sidebar-separator';
        separator.innerHTML = ' | ';
        separator.style.display = isSidebarHidden() ? 'inline' : 'none';

        // Add to the beginning of the nav paragraph
        navParagraph.insertBefore(separator, navParagraph.firstChild);
        navParagraph.insertBefore(link, navParagraph.firstChild);

        // Set initial visibility
        updateLinkVisibility();
    }

    /**
     * Set up observers to detect sidebar state changes
     */
    function observeSidebarChanges() {
        // Watch for class changes on html element
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.attributeName === 'class') {
                    updateLinkVisibility();
                    // Also update separator visibility
                    const separators = document.querySelectorAll('.nav-show-sidebar-separator');
                    const hidden = isSidebarHidden();
                    separators.forEach(function(sep) {
                        sep.style.display = hidden ? 'inline' : 'none';
                    });
                }
            });
        });

        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['class']
        });

        // Also listen for window resize (sidebar may auto-hide on mobile)
        let resizeTimeout;
        window.addEventListener('resize', function() {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(function() {
                updateLinkVisibility();
                const separators = document.querySelectorAll('.nav-show-sidebar-separator');
                const hidden = isSidebarHidden();
                separators.forEach(function(sep) {
                    sep.style.display = hidden ? 'inline' : 'none';
                });
            }, 100);
        });
    }

    /**
     * Initialize
     */
    function init() {
        injectNavLinks();
        observeSidebarChanges();
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
