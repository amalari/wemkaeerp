#!/usr/bin/env bash
# Shared repo task selection. --print-task resolves without executing Gradle.
set -euo pipefail
kind=${1:?Expected compile or test}
input=${2:?Expected module or file}
mode=${3:-}
case "$mode" in ''|--print-task) ;; *) echo 'Unknown option' >&2; exit 2 ;; esac
project_root=$(git rev-parse --show-toplevel)
path=${input#"$project_root/"}
path=${path#./}
case "$path" in
  core/*|:core) module=:core ;;
  app/shared/*|:app:shared) module=:app:shared ;;
  server/*|:server) module=:server ;;
  app/androidApp/*|:app:androidApp) module=:app:androidApp ;;
  app/desktopApp/*|:app:desktopApp) module=:app:desktopApp ;;
  app/webApp/*|:app:webApp) module=:app:webApp ;;
  *) echo "UNAVAILABLE: no Gradle module for $input" >&2; exit 2 ;;
esac
case "$kind:$module" in
  compile::core|compile::app:shared) task=$module:compileKotlinJvm ;;
  compile::server|compile::app:desktopApp) task=$module:compileKotlin ;;
  compile::app:webApp) task=$module:compileKotlinJs ;;
  compile::app:androidApp) task=$module:compileDebugKotlin ;;
  test::core|test::app:shared) task=$module:jvmTest ;;
  test::server) task=$module:test ;;
  *) echo "UNAVAILABLE: $kind task not configured here for $module" >&2; exit 2 ;;
esac
if [ "$mode" = --print-task ]; then
  echo "$task"
else
  cd "$project_root"
  exec ./gradlew "$task" --console=plain
fi
