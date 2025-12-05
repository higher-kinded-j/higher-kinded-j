# Versioned Documentation System

This document explains how the versioned documentation system works for the Higher-Kinded-J book.

## Overview

The documentation now supports multiple versions, allowing users to view docs for both:
- **Latest (Snapshot)**: Always reflects the current `main` branch
- **Tagged Releases**: Specific versions like v0.1.9, v0.1.8, etc.

Users can switch between versions using a dropdown selector in the book's menu bar.

## Architecture

### Directory Structure

The documentation is deployed to `higher-kinded-j.github.io` with the following structure:

```
higher-kinded-j.github.io/
├── index.html              # Root redirect page (redirects to stable version)
├── versions.json           # Metadata about all versions
├── latest/                 # Snapshot from main branch
│   ├── index.html
│   ├── home.html
│   └── ...
├── v0.1.9/                 # Tagged release v0.1.9
│   ├── index.html
│   ├── home.html
│   └── ...
└── v0.1.8/                 # Tagged release v0.1.8
    └── ...
```

### Key Components

1. **Deployment Workflow** (`.github/workflows/deploy-mdbook-versioned.yml`)
   - Triggers on push to `main` (for changes to `hkj-book/`, workflow, or scripts) OR when a version tag is pushed
   - Deploys to `/latest/` for main branch commits
   - Deploys to `/vX.Y.Z/` for tagged releases
   - Updates `versions.json` metadata file
   - Preserves previous versions with `keep_files: true`
   - Updates root `index.html` to redirect to stable version

2. **Version Metadata** (`versions.json`)
   - Tracks all available documentation versions
   - Identifies the latest stable (tagged) release
   - Used by the version switcher UI

3. **Version Switcher UI**
   - **HTML**: Added to `hkj-book/theme/index.hbs`
   - **JavaScript**: `hkj-book/theme/version-switcher.js`
   - **CSS**: `hkj-book/theme/additional-hkj.css`
   - Displays a dropdown in the menu bar
   - Shows version badges
   - Displays outdated version warnings

4. **Scripts**
   - `update_versions.py`: Updates versions.json metadata
   - `root-index.html`: Landing page with version selection

## Usage

### For End Users

1. **Accessing Documentation**
   - Visit `https://higher-kinded-j.github.io/` (redirects to stable version)
   - Or directly: `https://higher-kinded-j.github.io/latest/`
   - Or specific version: `https://higher-kinded-j.github.io/v0.1.9/`

2. **Switching Versions**
   - Use the dropdown selector in the top-left menu bar
   - The system preserves your current page when switching (if available)
   - Falls back to home page if the page doesn't exist in target version

3. **Version Indicators**
   - **Badge**: Shows current version next to book title
   - **Warning Banner**: Appears when viewing outdated versions
   - **Dropdown**: Shows all available versions

### For Maintainers

#### Deploying Latest (Snapshot)

The latest snapshot deploys automatically when changes are pushed to `main`:

```bash
git checkout main
# Make changes to hkj-book/...
git commit -m "docs: Update documentation"
git push origin main
```

This triggers the workflow and deploys to `/latest/`.

#### Releasing a New Version

To create a new versioned release:

1. **Tag the release** (following the library version):
   ```bash
   git tag v0.1.10
   git push origin v0.1.10
   ```

2. The workflow automatically:
   - Detects the version tag
   - Builds the book
   - Deploys to `/v0.1.10/`
   - Updates `versions.json` with the new version
   - Marks it as the new stable version

#### Manual Deployment

You can also trigger deployment manually via GitHub Actions:

1. Go to Actions → "Deploy Versioned mdBook"
2. Click "Run workflow"
3. Enter the version (e.g., `v0.1.9` or `latest`)
4. Click "Run workflow"

#### Backfilling Old Versions

To add documentation for previous releases:

1. Checkout the release tag:
   ```bash
   git checkout v0.1.8
   ```

2. Manually trigger the workflow (see above)

3. Repeat for each version you want to backfill

## Version Metadata Format

The `versions.json` file has the following structure:

```json
{
  "latest": {
    "path": "/latest/",
    "label": "Latest (Snapshot)",
    "version": "main",
    "updated": "2025-01-20T10:30:00Z"
  },
  "stable": "v0.1.9",
  "versions": [
    {
      "version": "v0.1.9",
      "path": "/v0.1.9/",
      "label": "v0.1.9",
      "releaseDate": "2025-01-15T14:20:00Z"
    },
    {
      "version": "v0.1.8",
      "path": "/v0.1.8/",
      "label": "v0.1.8",
      "releaseDate": "2024-12-20T09:15:00Z"
    }
  ]
}
```

## Technical Details

### Version Detection

The version switcher JavaScript detects the current version from the URL path:
- `/latest/hkts/usage-guide.html` → version = "latest"
- `/v0.1.9/hkts/usage-guide.html` → version = "v0.1.9"

### URL Preservation

When switching versions, the system tries to preserve the current page path:
- Current: `/latest/hkts/usage-guide.html`
- Switch to v0.1.9: `/v0.1.9/hkts/usage-guide.html`
- If page doesn't exist: `/v0.1.9/home.html` (fallback)

### Outdated Version Warnings

If you're viewing a version that's not the latest or stable, a warning banner appears at the top of the content area.

### Responsive Design

- Desktop: Full version selector and badges visible
- Tablet: Compact version selector
- Mobile: Version selector hidden to save space

## Migration Notes

### Old Workflow

The old workflow (`.github/workflows/deploy-mdbook.yml`) deployed directly to the root of `higher-kinded-j.github.io` on every push to main.

### New Workflow

The new workflow (`.github/workflows/deploy-mdbook-versioned.yml`) deploys to versioned subdirectories.

### Transition Plan

1. ✅ New workflow created
2. ⏳ Test the new workflow
3. ⏳ Disable or remove old workflow once confirmed working
4. ⏳ Optionally backfill previous versions

## Troubleshooting

### Version Selector Not Showing

- Check that `versions.json` exists at the root of the site
- Verify JavaScript console for errors
- Ensure `version-switcher.js` is being loaded

### Wrong Version Detected

- Check the URL path format
- Verify the version directory exists
- Look at browser console for detection logic output

### Deployment Failures

- Check GitHub Actions logs
- Verify `GH_PAGES_PAT` secret is set and valid
- Ensure Python scripts are executable

## Cleanup Old Root Documentation

If you migrated from the old (non-versioned) documentation system, old files may still exist at the root of the gh-pages repository. To clean these up:

1. Go to **Actions** → **Cleanup Old Root Documentation**
2. Click **Run workflow**
3. First run with `dry_run=true` to see what would be deleted
4. If the list looks correct, run again with `dry_run=false` to actually delete

The cleanup workflow removes all root-level files except:
- `index.html` (redirect page)
- `versions.json` (version metadata)
- `latest/` (snapshot directory)
- `v*/` (versioned release directories)
- `.nojekyll` and `CNAME` (GitHub Pages config)

## Future Enhancements

Potential improvements to consider:

- [ ] Version comparison view
- [x] ~~Automated version archival (remove old versions)~~ - Addressed with a manual cleanup workflow (`cleanup-old-docs.yml`).
- [x] ~~Better 404 handling for missing pages in specific versions~~ - Custom 404.html redirects old root URLs to `/latest/`.
- [ ] Version-specific search indices
- [ ] RSS feed for documentation updates
- [ ] Version aliases (e.g., `/stable/` pointing to latest stable)

## References

- Workflow: `.github/workflows/deploy-mdbook-versioned.yml`
- Scripts: `.github/scripts/update_versions.py`, `.github/scripts/root-index.html`
- Theme files: `hkj-book/theme/index.hbs`, `hkj-book/theme/version-switcher.js`, `hkj-book/theme/additional-hkj.css`
