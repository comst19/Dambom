#!/usr/bin/env bash
set -euo pipefail

output_file=${1:?Release notes output path is required}
requested_ref=${RELEASE_NOTES_FROM_REF:-}
event_before=${RELEASE_EVENT_BEFORE:-}
zero_sha=0000000000000000000000000000000000000000

if [[ -n "$requested_ref" ]]; then
  from_ref=$requested_ref
elif [[ -n "$event_before" && "$event_before" != "$zero_sha" ]]; then
  from_ref=$event_before
elif latest_tag=$(git describe --tags --abbrev=0 2>/dev/null); then
  from_ref=$latest_tag
elif git rev-parse --verify HEAD^ >/dev/null 2>&1; then
  from_ref=HEAD^
else
  from_ref=HEAD
fi

if ! from_sha=$(git rev-parse --verify --end-of-options "$from_ref^{commit}" 2>/dev/null); then
  printf 'Invalid release notes starting ref: %s\n' "$from_ref" >&2
  exit 1
fi

version_name=$(sed -nE 's/.*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts | head -1)
version_code=$(sed -nE 's/.*versionCode = ([0-9]+).*/\1/p' app/build.gradle.kts | head -1)
if [[ -z "$version_name" || -z "$version_code" ]]; then
  printf 'Unable to read version information from app/build.gradle.kts.\n' >&2
  exit 1
fi

features=()
fixes=()
performance=()
refactors=()
other=()

while IFS= read -r subject; do
  [[ -n "$subject" ]] || continue
  case "$subject" in
    feat:*|feat\(*\):*) features+=("$subject") ;;
    fix:*|fix\(*\):*) fixes+=("$subject") ;;
    perf:*|perf\(*\):*) performance+=("$subject") ;;
    refactor:*|refactor\(*\):*) refactors+=("$subject") ;;
    *) other+=("$subject") ;;
  esac
done < <(git log --no-merges --pretty=format:%s "$from_sha..HEAD")

write_section() {
  local title=$1
  shift
  printf '\n%s\n' "$title" >> "$output_file"
  if (( $# == 0 )); then
    printf -- '- 없음\n' >> "$output_file"
    return
  fi
  local item
  for item in "$@"; do
    printf -- '- %s\n' "$item" >> "$output_file"
  done
}

printf 'Dambom %s (%s)\n' "$version_name" "$version_code" > "$output_file"
printf 'Range: %s..%s\n' "$(git rev-parse --short "$from_sha")" "$(git rev-parse --short HEAD)" >> "$output_file"
write_section '새 기능' "${features[@]}"
write_section '버그 수정' "${fixes[@]}"
write_section '성능 개선' "${performance[@]}"
write_section '내부 구조 개선' "${refactors[@]}"
write_section '기타 변경' "${other[@]}"
