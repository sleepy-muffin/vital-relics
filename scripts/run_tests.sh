#!/bin/sh

# Run the Minecraft-independent unit tests and the GameTests for one module.
#
# Usage: scripts/run_tests.sh <module>
#
# Modules: forge-1.20.1 neoforge-1.21.1 neoforge-1.21.8 neoforge-26.2 neoforge-26.3

set -eu

module=${1:-}
if [ -z "$module" ]; then
	echo "usage: $0 <module>" >&2
	echo "modules: forge-1.20.1 neoforge-1.21.1 neoforge-1.21.8 neoforge-26.2 neoforge-26.3" >&2
	exit 2
fi

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

case "$module" in
	forge-1.20.1) major=17 ;;
	neoforge-1.21.1|neoforge-1.21.8) major=21 ;;
	neoforge-26.2|neoforge-26.3) major=25 ;;
	*)
		echo "unknown module: $module" >&2
		exit 2
		;;
esac

if [ ! -d "$root/$module" ]; then
	echo "module directory not found: $root/$module" >&2
	exit 2
fi

java_major() {
	"$1" -version 2>&1 |
		sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' |
		sed -n '1p'
}

# Print the path of a JDK with the requested major version, if one can be found.
find_jdk() {
	required=$1

	if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] &&
		[ "$(java_major "$JAVA_HOME/bin/java")" = "$required" ]; then
		printf '%s\n' "$JAVA_HOME"
		return 0
	fi

	for candidate in "$HOME"/opt/jdk-"$required"* /usr/lib/jvm/*"$required"*; do
		if [ -x "$candidate/bin/java" ] &&
			[ "$(java_major "$candidate/bin/java")" = "$required" ]; then
			printf '%s\n' "$candidate"
			return 0
		fi
	done

	return 1
}

module_java=$(find_jdk "$major" || true)
common_java=$(find_jdk 17 || true)

if [ -z "$module_java" ]; then
	path_java=$(command -v java || true)

	if [ -z "$path_java" ] || [ "$(java_major "$path_java")" != "$major" ]; then
		echo "Java $major is required for $module but no matching JDK was found." >&2
		echo "Set JAVA_HOME to a Java $major installation." >&2
		exit 1
	fi

	echo "Using Java $major from PATH: $path_java"
	module_java=
else
	echo "Using JDK $major for $module: $module_java"
fi

if [ -n "$common_java" ]; then
	echo "Using JDK 17 for common: $common_java"
	echo "==> Unit tests (common)"
	(cd "$root/common" &&
		JAVA_HOME="$common_java" PATH="$common_java/bin:$PATH" \
			./gradlew test --rerun-tasks)
else
	echo "==> Unit tests (common)"
	echo "No JDK 17 found; relying on Gradle toolchain detection." >&2
	(cd "$root/common" && ./gradlew test --rerun-tasks)
fi

echo "==> GameTests ($module)"
if [ -n "$module_java" ]; then
	(cd "$root/$module" &&
		JAVA_HOME="$module_java" PATH="$module_java/bin:$PATH" \
			./gradlew runGameTestServer)
else
	(cd "$root/$module" && ./gradlew runGameTestServer)
fi
