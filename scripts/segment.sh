#!/bin/sh
# Runs segment.swift with the app's own SentenceSplitter.
set -e
tmp=$(mktemp -d)
cat Mythos/Model/SentenceSplitter.swift scripts/segment.swift > "$tmp/main.swift"
swift "$tmp/main.swift" "$@"
rm -rf "$tmp"
