#!/usr/bin/env python3
"""
Update versions.json metadata file for documentation versioning.

This script maintains a versions.json file that tracks all available
documentation versions (both releases and latest snapshot).
"""

import json
import argparse
from pathlib import Path
from datetime import datetime, timezone


def load_versions(repo_dir: Path) -> dict:
    """Load existing versions.json or create new structure."""
    versions_file = repo_dir / "versions.json"

    if versions_file.exists():
        with open(versions_file, 'r') as f:
            return json.load(f)

    # Default structure
    return {
        "latest": {
            "path": "/latest/",
            "label": "Latest (Snapshot)",
            "version": "main",
            "updated": None
        },
        "stable": None,
        "versions": []
    }


def parse_version(version_string: str) -> tuple:
    """
    Parse a version string into a tuple of integers for proper sorting.
    Handles formats like 'v0.1.9', 'v0.1.10', etc.

    Returns a tuple of (major, minor, patch) as integers.
    Falls back to string comparison if parsing fails.
    """
    try:
        # Remove 'v' prefix and split on '.'
        parts = version_string.lstrip('v').split('.')
        # Convert to integers, handling up to 3 parts (major.minor.patch)
        return tuple(int(p) for p in parts[:3])
    except (ValueError, AttributeError):
        # Fallback for unparseable versions.
        return (0, 0, 0)  # Will be sorted as the oldest version.


def update_versions_file(repo_dir: Path, version: str, version_label: str):
    """Update versions.json with new version information."""

    data = load_versions(repo_dir)
    current_time = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')

    if version == "latest":
        # Update latest snapshot
        data["latest"]["updated"] = current_time
        data["latest"]["label"] = version_label
    else:
        # Add or update a release version
        version_entry = {
            "version": version,
            "path": f"/{version}/",
            "label": version_label,
            "releaseDate": current_time
        }

        # Remove existing entry if present (for re-deployments)
        data["versions"] = [v for v in data["versions"] if v["version"] != version]

        # Add new entry
        data["versions"].append(version_entry)

        # Sort versions in reverse order (newest first) using semantic versioning
        data["versions"].sort(key=lambda v: parse_version(v["version"]), reverse=True)

        # Update stable pointer to latest version
        if data["versions"]:
            data["stable"] = data["versions"][0]["version"]

    # Write updated versions.json
    versions_file = repo_dir / "versions.json"
    with open(versions_file, 'w') as f:
        json.dump(data, f, indent=2)

    print(f"Updated versions.json with version: {version}")
    print(f"Current stable version: {data['stable']}")
    print(f"Total release versions: {len(data['versions'])}")


def main():
    parser = argparse.ArgumentParser(description="Update versions.json metadata")
    parser.add_argument("--repo-dir", type=Path, required=True,
                        help="Path to the gh-pages repository")
    parser.add_argument("--version", type=str, required=True,
                        help="Version identifier (e.g., v0.1.9 or 'latest')")
    parser.add_argument("--version-label", type=str, required=True,
                        help="Human-readable version label")

    args = parser.parse_args()

    update_versions_file(
        repo_dir=args.repo_dir,
        version=args.version,
        version_label=args.version_label
    )


if __name__ == "__main__":
    main()
