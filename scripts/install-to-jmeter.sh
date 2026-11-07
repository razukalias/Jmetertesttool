#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JMETER_HOME="${1:-}"
if [[ -z "$JMETER_HOME" || ! -d "$JMETER_HOME/lib/ext" ]]; then
  echo "Usage: $0 /path/to/apache-jmeter" >&2
  exit 2
fi
JAR="$ROOT/target/jmeter-dataengine-sampler-0.1.0-SNAPSHOT-all.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Build first with: mvn package" >&2
  exit 2
fi
cp "$JAR" "$JMETER_HOME/lib/ext/"
echo "Installed $(basename "$JAR") into $JMETER_HOME/lib/ext"
