#!/usr/bin/env bash
# Launcher for Articulect: runs the jar with the system Java.
# Builds the jar first (system Maven) if it does not exist yet.
# Output goes to launcher.log in this folder.
DIR="$(dirname "$(readlink -f "$0")")"
cd "$DIR" || exit 1
exec > "$DIR/launcher.log" 2>&1

export PATH="/run/current-system/sw/bin:$HOME/.nix-profile/bin:$PATH"

JAR="target/articulect-1.0.0.jar"
[ -f "$JAR" ] || mvn -q package
exec java -jar "$JAR"
