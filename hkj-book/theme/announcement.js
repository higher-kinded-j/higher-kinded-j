(function() {
    'use strict';

    // One file at the site root holds the announcement, written by every /latest/ deploy from
    // .github/scripts/announcement.json, so all copies of the book that carry this script read the
    // same one. "Site Announcements" in docs/STYLE-GUIDE.md says how to post one.
    //
    // Each copy keeps the script it was built with, so the file must stay readable by the oldest
    // one: add optional fields only, never rename, remove, loosen or change the meaning of one, and
    // keep the /announcement.json path and the dismissal key. .github/scripts/announcement-check.cjs
    // checks the same rules at least as strictly; keep the two in step.
    const ANNOUNCEMENT_JSON_PATH = '/announcement.json';
    const DISMISSED_KEY = 'hkj-announcement-dismissed';

    /**
     * Today's date in the reader's time zone, as YYYY-MM-DD
     */
    function today() {
        const now = new Date();
        const pad = (n) => String(n).padStart(2, '0');
        return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
    }

    /**
     * Whether a value is a real calendar date written as YYYY-MM-DD
     */
    function isDate(value) {
        if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
        const date = new Date(`${value}T00:00:00Z`);
        return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value);
    }

    /**
     * Whether the file holds an announcement whose last day has not passed. An empty object, a
     * missing field or a date that is not a real YYYY-MM-DD shows nothing.
     */
    function isCurrent(announcement) {
        return announcement !== null && typeof announcement === 'object'
            && typeof announcement.id === 'string' && announcement.id !== ''
            && typeof announcement.text === 'string' && announcement.text !== ''
            && isDate(announcement.showUntil)
            && today() <= announcement.showUntil;
    }

    /**
     * The href for a link that is an https URL or a path on this site, or null for anything else.
     * Browsers read a backslash as a slash and drop tabs and line breaks, so "/\evil.example" would
     * leave the site: the link is refused unless it resolves to https or to this site's origin.
     */
    function safeHref(link) {
        if (typeof link !== 'string' || /[\\\s\u0000-\u001f]/.test(link)) return null;
        try {
            const url = new URL(link, window.location.origin);
            if (link.startsWith('/')) {
                return url.origin === window.location.origin ? url.href : null;
            }
            return link.startsWith('https://') && url.protocol === 'https:' && url.hostname !== '' ? url.href : null;
        } catch (error) {
            return null;
        }
    }

    function isDismissed(id) {
        try {
            return localStorage.getItem(DISMISSED_KEY) === id;
        } catch (error) {
            return false;
        }
    }

    /**
     * Show the announcement just above the page's main content, outside main so that a screen
     * reader's jump to main skips it. It is built from text nodes, so the file cannot add markup.
     */
    function showAnnouncement(announcement) {
        const main = document.querySelector('#content main');
        if (!main) return;

        const bar = document.createElement('aside');
        bar.className = 'hkj-announcement';
        bar.setAttribute('aria-label', 'Announcement');

        const icon = document.createElement('i');
        icon.className = 'fa fa-bullhorn';
        icon.setAttribute('aria-hidden', 'true');

        const p = document.createElement('p');
        p.append(announcement.text);
        const href = safeHref(announcement.link);
        if (href !== null && typeof announcement.linkText === 'string' && announcement.linkText !== '') {
            const link = document.createElement('a');
            link.href = href;
            link.textContent = announcement.linkText;
            p.append(' ', link);
        }

        const dismiss = document.createElement('button');
        dismiss.type = 'button';
        dismiss.className = 'hkj-announcement-dismiss';
        dismiss.setAttribute('aria-label', 'Dismiss announcement');
        dismiss.textContent = '×';
        dismiss.addEventListener('click', () => {
            try {
                localStorage.setItem(DISMISSED_KEY, announcement.id);
            } catch (error) {
                // Without storage the bar still closes, and comes back on the next page
            }
            const hadFocus = bar.contains(document.activeElement);
            bar.remove();
            // Keep keyboard focus on the page rather than dropping it to the body
            const heading = main.querySelector('h1, h2');
            if (hadFocus && heading) {
                heading.setAttribute('tabindex', '-1');
                heading.focus({ preventScroll: true });
            }
        });

        bar.append(icon, p, dismiss);
        main.before(bar);
    }

    async function init() {
        try {
            const response = await fetch(ANNOUNCEMENT_JSON_PATH);
            // No file, as when the book is served locally: show nothing
            if (!response.ok) return;
            const announcement = await response.json();
            if (isCurrent(announcement) && !isDismissed(announcement.id)) {
                showAnnouncement(announcement);
            }
        } catch (error) {
            // A file that is not JSON, or a failed fetch: show nothing
        }
    }

    // Initialise when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
