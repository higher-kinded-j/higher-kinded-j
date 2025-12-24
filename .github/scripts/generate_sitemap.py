import os
from datetime import datetime
import html # For unescaping HTML entities if needed in URLs, though typically not for filenames

def generate_sitemap(start_dir, base_url, output_file):
    """
    Generates a sitemap.xml by scanning for .html files in a directory.

    Args:
        start_dir (str): The directory containing the built HTML files (e.g., "hkj-book/book").
        base_url (str): The base URL of the website (e.g., "https://higher-kinded-j.github.io/").
        output_file (str): The full path where sitemap.xml should be saved (e.g., "hkj-book/book/sitemap.xml").
    """
    if not base_url.endswith('/'):
        base_url += '/'

    urls = []
    # Files to exclude from the sitemap
    excluded_files = ["404.html", "print.html"]
    # You can add more files to exclude if needed, e.g., "toc.html" if you don't want it indexed

    for root, _, files in os.walk(start_dir):
        for file in files:
            if file.endswith(".html") and file not in excluded_files:
                # Get relative path from start_dir
                # e.g., if start_dir is "hkj-book/book" and file is "hkj-book/book/core-concepts.html",
                # relative_path will be "core-concepts.html"
                # if file is "hkj-book/book/subdir/page.html", relative_path will be "subdir/page.html"
                relative_path = os.path.relpath(os.path.join(root, file), start_dir)

                # Construct URL, ensuring no double slashes if relative_path is empty (for index.html at root)
                # os.path.join won't work directly for URLs, so we build carefully
                url_path_parts = relative_path.replace(os.path.sep, '/').split('/')
                # Filter out any empty parts that might result from an index.html at the root of a subdir scan
                # though os.path.relpath usually handles this well.
                # For sitemap, we want the actual .html file name

                url = base_url + "/".join(url_path_parts)

                # Unescape HTML entities that might be in filenames if any (less common)
                # url = html.unescape(url)

                urls.append(url)

    sitemap_content = '<?xml version="1.0" encoding="UTF-8"?>\n'
    sitemap_content += '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'

    # Use a fixed date for lastmod or get it from file metadata if desired (more complex)
    # Using current date for simplicity in this example
    today = datetime.now().strftime('%Y-%m-%d')

    for url in sorted(urls): # Sort URLs for consistent sitemap output
        sitemap_content += '  <url>\n'
        sitemap_content += f'    <loc>{html.escape(url)}</loc>\n' # Escape special characters in URL
        sitemap_content += f'    <lastmod>{today}</lastmod>\n'
        sitemap_content += '    <changefreq>weekly</changefreq>\n' # Default change frequency
        # Determine priority based on page importance
        # Priority 1.0: Main landing pages
        if url.endswith("index.html") or url.endswith("home.html") or url == base_url.rstrip('/'):
            sitemap_content += '    <priority>1.0</priority>\n'
        # Priority 0.9: Effect Path API and Optics intro pages (key selling points)
        elif any(p in url for p in ["effect/ch_intro.html", "effect/effect_path_overview.html",
                                     "optics/optics_intro.html", "optics/focus_dsl.html",
                                     "effect/focus_integration.html"]):
            sitemap_content += '    <priority>0.9</priority>\n'
        # Priority 0.8: Core documentation and tutorials intro
        elif any(p in url for p in ["core-concepts.html", "usage-guide.html", "hkt_introduction.html",
                                     "tutorials_intro.html", "spring_boot_integration.html"]):
            sitemap_content += '    <priority>0.8</priority>\n'
        # Priority 0.7: Other effect and optics pages
        elif "/effect/" in url or "/optics/" in url:
            sitemap_content += '    <priority>0.7</priority>\n'
        # Priority 0.6: Tutorial pages and examples
        elif "/tutorials/" in url or "/hkts/" in url:
            sitemap_content += '    <priority>0.6</priority>\n'
        else:
            sitemap_content += '    <priority>0.5</priority>\n' # Default priority
        sitemap_content += '  </url>\n'

    sitemap_content += '</urlset>\n'

    # Ensure the output directory exists
    os.makedirs(os.path.dirname(output_file), exist_ok=True)

    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(sitemap_content)
    print(f"Sitemap generated at {output_file} with {len(urls)} URLs.")

if __name__ == "__main__":
    # These values will be passed as environment variables in the GitHub Action
    # Default values are provided here for local testing if needed.
    book_output_directory = os.environ.get("MDBOOK_OUTPUT_DIR", "hkj-book/book")
    site_base_url = os.environ.get("MDBOOK_SITE_URL", "https://higher-kinded-j.github.io/")

    # The sitemap should be placed in the root of the book_output_directory
    sitemap_output_path = os.path.join(book_output_directory, "sitemap.xml")

    # Ensure the script is run from the repository root for consistent relative paths
    # Or adjust book_output_directory to be an absolute path if needed
    if not os.path.isabs(book_output_directory):
        # Assuming the script is in .github/scripts/ and repo root is two levels up
        # This might need adjustment based on where the action runs the script from
        # In GitHub Actions, the default working directory is usually the repo root.
        pass

    print(f"Starting sitemap generation...")
    print(f"Output directory for HTML files: {os.path.abspath(book_output_directory)}")
    print(f"Site base URL: {site_base_url}")
    print(f"Sitemap will be saved to: {os.path.abspath(sitemap_output_path)}")

    if not os.path.isdir(book_output_directory):
        print(f"Error: MDBOOK_OUTPUT_DIR '{book_output_directory}' does not exist or is not a directory.")
        print(f"Current PWD: {os.getcwd()}")
        # List contents of current dir and hkj-book
        print("Listing current directory contents:")
        for item in os.listdir("."):
            print(f"  - {item}")
        if os.path.exists("hkj-book"):
            print("Listing hkj-book directory contents:")
            for item in os.listdir("hkj-book"):
                print(f"  - hkj-book/{item}")
        exit(1)

    generate_sitemap(book_output_directory, site_base_url, sitemap_output_path)