#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
mod="$repo/minecraft-mod"
java="${MC_JAVA:-$repo/.tools/jdk/jdk-25.0.4.1+1/bin/java}"
cd "$mod/run"
exec "$java" --enable-native-access=ALL-UNNAMED -Xmx${MC_HEAP:-1500M} -Dmax.bg.threads=${MC_BG_THREADS:-4} \
	-Dblpassthrough.debug=true \
	-Dfabric.dli.config="$mod/.gradle/loom-cache/launch.cfg" \
	-Dfabric.dli.env=client \
	-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient \
	"@$mod/build/loom-cache/argFiles/runClient" \
	net.fabricmc.devlaunchinjector.Main "$@"
