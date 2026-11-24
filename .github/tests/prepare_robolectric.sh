#!/usr/bin/env bash
# Preload the exact framework versions requested by the existing SDK 33/34 tests.
# Use the same Maven Central coordinates; do not skip tests or modify the framework.
set -euo pipefail
dest="${1:?Destination directory required}"
mkdir -p "$dest"
versions=(13-robolectric-9030017-i7 14-robolectric-10818077-i7)
repos=(https://repo.maven.apache.org/maven2 https://repo1.maven.org/maven2 https://maven-central.storage-download.googleapis.com/maven2)
for version in "${versions[@]}"; do
    jar="android-all-instrumented-$version.jar"
    path="org/robolectric/android-all-instrumented/$version/$jar"
    if [[ -s "$dest/$jar" && -s "$dest/$jar.sha256" ]] &&
        (cd "$dest" && sha256sum --check "$jar.sha256"); then
        echo "Verified cached $jar"
        continue
    fi
    rm -f "$dest/$jar" "$dest/$jar.sha256"
    ok=0
    tmp="$(mktemp -d "$dest/.download.XXXXXX")"
    for repo in "${repos[@]}"; do
        echo "Resolving $jar from $repo"
        if curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' --connect-timeout 15 --max-time 180 --retry 2 --retry-max-time 30 "$repo/$path.sha256" -o "$tmp/hash" &&
           curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' --connect-timeout 15 --max-time 240 --retry 2 --retry-max-time 30 "$repo/$path" -o "$tmp/$jar"; then
            hash="$(awk '{print $1; exit}' "$tmp/hash")"
            if [[ "$hash" =~ ^[a-fA-F0-9]{64}$ ]]; then
                printf '%s  %s\n' "$hash" "$jar" > "$tmp/$jar.sha256"
                if (cd "$tmp" && sha256sum --check "$jar.sha256"); then
                    mv "$tmp/$jar" "$dest/$jar"
                    mv "$tmp/$jar.sha256" "$dest/$jar.sha256"
                    ok=1
                    break
                fi
            fi
        fi
    done
    rm -rf "$tmp"
    if [[ "$ok" != 1 ]]; then
        echo "Could not retrieve and verify $jar; tests must not be skipped." >&2
        exit 1
    fi
done
