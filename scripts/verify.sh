#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
classes_dir="$repo_dir/build/classes"
rm -rf "$classes_dir"
mkdir -p "$classes_dir"
javac --release 17 -d "$classes_dir" $(find "$repo_dir/src/main/java" "$repo_dir/src/test/java" -name '*.java' -print)
java -cp "$classes_dir" education.store.export.OrderExportDecisionTest
