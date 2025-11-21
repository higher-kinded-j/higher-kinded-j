(function() {
    'use strict';

    // Configuration
    const VERSIONS_JSON_PATH = '/versions.json';
    const VERSION_SELECTOR_ID = 'version-selector';

    /**
     * Detect the current version from the URL path
     * Returns: version string (e.g., "v0.1.9" or "latest")
     */
    function detectCurrentVersion() {
        const path = window.location.pathname;

        // Check if we're in a version subdirectory
        const versionMatch = path.match(/^\/(v\d+\.\d+\.\d+|latest)\//);
        if (versionMatch) {
            return versionMatch[1];
        }

        // If at root or unknown, assume latest
        return 'latest';
    }

    /**
     * Get the current page path relative to the version root
     * Example: /v0.1.9/hkts/usage-guide.html -> /hkts/usage-guide.html
     */
    function getCurrentPagePath() {
        const path = window.location.pathname;
        const versionMatch = path.match(/^\/(v\d+\.\d+\.\d+|latest)(\/.*)?$/);

        if (versionMatch && versionMatch[2]) {
            return versionMatch[2];
        }

        // Default to home page
        return '/home.html';
    }

    /**
     * Build the URL for switching to a different version
     * Tries to preserve the current page path
     */
    function buildVersionUrl(targetVersion, currentPagePath) {
        // Remove leading slash if present
        const pagePath = currentPagePath.startsWith('/') ? currentPagePath.slice(1) : currentPagePath;

        // Build the new URL
        if (targetVersion === 'latest') {
            return `/latest/${pagePath}`;
        } else {
            return `/${targetVersion}/${pagePath}`;
        }
    }

    /**
     * Populate the version selector dropdown
     */
    function populateVersionSelector(versionsData, currentVersion) {
        const selector = document.getElementById(VERSION_SELECTOR_ID);
        if (!selector) {
            console.warn('Version selector element not found');
            return;
        }

        // Clear existing options
        selector.innerHTML = '';

        // Add latest option
        const latestOption = document.createElement('option');
        latestOption.value = 'latest';
        latestOption.textContent = versionsData.latest.label;
        if (currentVersion === 'latest') {
            latestOption.selected = true;
        }
        selector.appendChild(latestOption);

        // Add separator if there are releases
        if (versionsData.versions && versionsData.versions.length > 0) {
            const separator = document.createElement('option');
            separator.disabled = true;
            separator.textContent = '──────────';
            selector.appendChild(separator);

            // Add release versions
            versionsData.versions.forEach(function(versionInfo) {
                const option = document.createElement('option');
                option.value = versionInfo.version;

                // Build label
                let label = versionInfo.version;
                if (versionInfo.version === versionsData.stable) {
                    label += ' (Stable)';
                }
                option.textContent = label;

                if (currentVersion === versionInfo.version) {
                    option.selected = true;
                }

                selector.appendChild(option);
            });
        }

        // Add change event listener
        selector.addEventListener('change', function() {
            const selectedVersion = this.value;
            if (selectedVersion && selectedVersion !== currentVersion) {
                const currentPagePath = getCurrentPagePath();
                const newUrl = buildVersionUrl(selectedVersion, currentPagePath);

                // Check if the target page exists, otherwise go to home
                checkPageExists(newUrl, function(exists) {
                    if (exists) {
                        window.location.href = newUrl;
                    } else {
                        // Fallback to version home page
                        const homeUrl = selectedVersion === 'latest' ? '/latest/home.html' : `/${selectedVersion}/home.html`;
                        window.location.href = homeUrl;
                    }
                });
            }
        });
    }

    /**
     * Check if a page exists by making a HEAD request
     * This prevents users from hitting 404 pages when switching versions
     */
    function checkPageExists(url, callback) {
        // Use fetch with a HEAD request to check if the page exists without downloading it
        fetch(url, { method: 'HEAD' })
            .then(function(response) {
                // response.ok is true for statuses in the range 200-299
                callback(response.ok);
            })
            .catch(function() {
                // A network error occurred, assume the page doesn't exist
                callback(false);
            });
    }

    /**
     * Add version badge/indicator to the page
     */
    function addVersionBadge(currentVersion, versionsData) {
        const menuTitle = document.querySelector('.menu-title');
        if (!menuTitle) return;

        const badge = document.createElement('span');
        badge.className = 'version-badge';
        badge.setAttribute('aria-label', 'Current documentation version');

        let badgeText = currentVersion;
        if (currentVersion === 'latest') {
            badgeText = 'Latest';
            badge.classList.add('version-badge-snapshot');
        } else if (currentVersion === versionsData.stable) {
            badge.classList.add('version-badge-stable');
        }

        badge.textContent = badgeText;
        menuTitle.appendChild(badge);
    }

    /**
     * Show an outdated version warning banner
     */
    function showOutdatedWarning(currentVersion, stableVersion) {
        if (currentVersion === 'latest' || currentVersion === stableVersion) {
            return; // No warning needed
        }

        const content = document.getElementById('content');
        if (!content) return;

        const warning = document.createElement('div');
        warning.className = 'version-warning';
        warning.innerHTML = `
            <div class="version-warning-content">
                <strong>⚠️ Outdated Documentation</strong>
                <p>
                    You are viewing documentation for version <strong>${currentVersion}</strong>.
                    The latest stable version is <strong>${stableVersion}</strong>.
                    <a href="/${stableVersion}${getCurrentPagePath()}">View latest version</a>
                </p>
            </div>
        `;

        content.insertBefore(warning, content.firstChild);
    }

    /**
     * Initialize the version switcher
     */
    function init() {
        const currentVersion = detectCurrentVersion();

        // Fetch versions data
        fetch(VERSIONS_JSON_PATH)
            .then(function(response) {
                if (!response.ok) {
                    throw new Error('Failed to load versions.json');
                }
                return response.json();
            })
            .then(function(versionsData) {
                // Populate the version selector
                populateVersionSelector(versionsData, currentVersion);

                // Add version badge
                addVersionBadge(currentVersion, versionsData);

                // Show outdated warning if applicable
                if (versionsData.stable && currentVersion !== 'latest' && currentVersion !== versionsData.stable) {
                    showOutdatedWarning(currentVersion, versionsData.stable);
                }
            })
            .catch(function(error) {
                console.error('Error loading version information:', error);

                // Fallback: just show current version in selector
                const selector = document.getElementById(VERSION_SELECTOR_ID);
                if (selector) {
                    selector.innerHTML = `<option value="${currentVersion}">${currentVersion}</option>`;
                }
            });
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
