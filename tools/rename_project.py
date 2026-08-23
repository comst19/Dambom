#!/usr/bin/env python3

import argparse
import fnmatch
import os
import re
import shutil
import sys
from pathlib import Path


TEMPLATE_DISPLAY_NAME = "Android" + " Init"
TEMPLATE_CODE_NAME = "Android" + "Init"
TEMPLATE_PACKAGE = ".".join(("com", "example", "androidinit"))
TEMPLATE_PLUGIN_PREFIX = "android" + "-init"
TEMPLATE_SCHEME = "android" + "init"

IGNORED_DIRECTORIES = {
    ".git",
    ".gradle",
    ".idea",
    ".kotlin",
    "build",
}
SENSITIVE_PATTERNS = {
    ".env",
    ".env.*",
    "*.jks",
    "*.keystore",
    "GoogleService-Info.plist",
    "credentials*.json",
    "google-services.json",
    "keystore.properties",
    "local.properties",
    "secrets.properties",
    "service-account*.json",
}
TEXT_FILE_NAMES = {
    ".gitignore",
    "gradlew",
}
TEXT_SUFFIXES = {
    ".gradle",
    ".json",
    ".kt",
    ".kts",
    ".md",
    ".pro",
    ".properties",
    ".py",
    ".toml",
    ".xml",
    ".yaml",
    ".yml",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Android Init 템플릿의 프로젝트 식별자를 일괄 변경합니다.",
    )
    parser.add_argument("--project-name", required=True, help="사용자에게 표시할 앱 이름")
    parser.add_argument("--package-name", required=True, help="applicationId와 기본 package")
    parser.add_argument(
        "--code-name",
        help="Kotlin 심볼과 Gradle rootProject 이름. 생략하면 project-name에서 생성",
    )
    parser.add_argument(
        "--scheme",
        help="딥링크 scheme. 생략하면 code-name의 소문자 값 사용",
    )
    parser.add_argument(
        "--plugin-prefix",
        help="convention plugin ID 접두어. 생략하면 project-name의 kebab-case 값 사용",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="실제 파일을 변경합니다. 생략하면 변경 예정 목록만 출력합니다.",
    )
    parser.add_argument(
        "--keep-build-cache",
        action="store_true",
        help="적용 후 프로젝트 내부 Gradle 및 build 생성물을 유지합니다.",
    )
    return parser.parse_args()


def ascii_words(value: str) -> list[str]:
    return re.findall(r"[A-Za-z0-9]+", value)


def default_code_name(project_name: str) -> str:
    return "".join(word[:1].upper() + word[1:] for word in ascii_words(project_name))


def default_plugin_prefix(project_name: str) -> str:
    return "-".join(word.lower() for word in ascii_words(project_name))


def validate_inputs(
    project_name: str,
    package_name: str,
    code_name: str,
    scheme: str,
    plugin_prefix: str,
) -> None:
    if not project_name.strip():
        raise ValueError("project-name은 비어 있을 수 없습니다.")
    if not re.fullmatch(r"[A-Z][A-Za-z0-9]*", code_name):
        raise ValueError("code-name은 영문 대문자로 시작하는 영문/숫자 PascalCase여야 합니다.")
    package_segment = r"[a-z][a-z0-9_]*"
    if not re.fullmatch(rf"{package_segment}(\.{package_segment})+", package_name):
        raise ValueError("package-name은 com.example.app 형식의 소문자 패키지여야 합니다.")
    if not re.fullmatch(r"[a-z][a-z0-9+.-]*", scheme):
        raise ValueError("scheme은 영문 소문자로 시작하고 소문자, 숫자, '+', '-', '.'만 사용할 수 있습니다.")
    if not re.fullmatch(r"[a-z][a-z0-9.-]*", plugin_prefix):
        raise ValueError("plugin-prefix는 영문 소문자로 시작하고 소문자, 숫자, '-', '.'만 사용할 수 있습니다.")


def is_sensitive(path: Path) -> bool:
    return any(fnmatch.fnmatch(path.name, pattern) for pattern in SENSITIVE_PATTERNS)


def is_ignored(path: Path, root: Path) -> bool:
    relative_parts = path.relative_to(root).parts
    return any(part in IGNORED_DIRECTORIES for part in relative_parts)


def text_files(root: Path) -> list[Path]:
    files = []
    for current_root, directories, names in os.walk(root):
        directories[:] = [name for name in directories if name not in IGNORED_DIRECTORIES]
        current_path = Path(current_root)
        for name in names:
            path = current_path / name
            if path.resolve() == Path(__file__).resolve():
                continue
            if is_sensitive(path):
                continue
            if name in TEXT_FILE_NAMES or path.suffix in TEXT_SUFFIXES:
                files.append(path)
    return sorted(files)


def replacement_function(replacements: dict[str, str]):
    pattern = re.compile(
        "|".join(re.escape(value) for value in sorted(replacements, key=len, reverse=True)),
    )
    return pattern, lambda match: replacements[match.group(0)]


def changed_text_files(
    root: Path,
    replacements: dict[str, str],
) -> list[tuple[Path, str]]:
    pattern, replace = replacement_function(replacements)
    changes = []
    for path in text_files(root):
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        updated = pattern.sub(replace, content)
        if path.suffix == ".kt":
            updated = sort_kotlin_imports(updated)
        if updated != content:
            changes.append((path, updated))
    return changes


def sort_kotlin_imports(content: str) -> str:
    lines = content.splitlines(keepends=True)
    import_indexes = [index for index, line in enumerate(lines) if line.startswith("import ")]
    if not import_indexes:
        return content

    first = import_indexes[0]
    last = import_indexes[-1]
    imports = [line for line in lines[first : last + 1] if line.startswith("import ")]

    def import_key(line: str) -> tuple[bool, bool, str]:
        target = line.removeprefix("import ").strip()
        has_alias = " as " in target
        is_java_or_kotlin = target.startswith(("java.", "javax.", "kotlin."))
        return has_alias, is_java_or_kotlin, target

    sorted_imports = sorted(imports, key=import_key)
    return "".join(lines[:first] + sorted_imports + lines[last + 1 :])


def package_directories(root: Path) -> list[Path]:
    old_parts = tuple(TEMPLATE_PACKAGE.split("."))
    matches = []
    for current_root, directories, _ in os.walk(root):
        directories[:] = [name for name in directories if name not in IGNORED_DIRECTORIES]
        current_path = Path(current_root)
        candidate = current_path.joinpath(*old_parts)
        if candidate.is_dir() and not is_ignored(candidate, root):
            matches.append(candidate)
            directories[:] = [name for name in directories if name != old_parts[0]]
    return sorted(set(matches), key=lambda path: len(path.parts), reverse=True)


def package_move_target(path: Path, package_name: str) -> tuple[Path, Path]:
    anchor = path.parents[len(TEMPLATE_PACKAGE.split(".")) - 1]
    return anchor, anchor.joinpath(*package_name.split("."))


def branded_paths(root: Path, replacements: dict[str, str]) -> list[tuple[Path, Path]]:
    pattern, replace = replacement_function(replacements)
    changes = []
    for current_root, directories, names in os.walk(root, topdown=False):
        current_path = Path(current_root)
        if is_ignored(current_path, root):
            continue
        for name in names + directories:
            updated_name = pattern.sub(replace, name)
            if updated_name != name:
                source = current_path / name
                changes.append((source, current_path / updated_name))
    return changes


def generated_directories(root: Path) -> list[Path]:
    generated = []
    for current_root, directories, _ in os.walk(root):
        current_path = Path(current_root)
        for name in list(directories):
            if name == "build" or name in {".gradle", ".kotlin"}:
                generated.append(current_path / name)
                directories.remove(name)
    return sorted(generated, key=lambda path: len(path.parts), reverse=True)


def prune_empty_parents(start: Path, stop: Path) -> None:
    current = start
    while current != stop and current.is_dir():
        try:
            current.rmdir()
        except OSError:
            break
        current = current.parent


def verify_no_template_identifiers(root: Path, replacements: dict[str, str]) -> list[Path]:
    pattern, _ = replacement_function(replacements)
    remaining = []
    for path in text_files(root):
        try:
            if pattern.search(path.read_text(encoding="utf-8")):
                remaining.append(path)
        except UnicodeDecodeError:
            continue
    return remaining


def print_preview(
    text_changes: list[tuple[Path, str]],
    package_moves: list[tuple[Path, Path]],
    path_changes: list[tuple[Path, Path]],
    root: Path,
) -> None:
    print(f"텍스트 변경: {len(text_changes)}개 파일")
    print(f"패키지 경로 이동: {len(package_moves)}개")
    print(f"파일/디렉터리 이름 변경: {len(path_changes)}개")
    for source, target in package_moves + path_changes:
        print(f"  {source.relative_to(root)} -> {target.relative_to(root)}")


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parent.parent
    settings_file = root / "settings.gradle.kts"
    template_marker = f'rootProject.name = "{TEMPLATE_CODE_NAME}"'
    if not settings_file.is_file() or template_marker not in settings_file.read_text(encoding="utf-8"):
        print(
            "오류: Android Init 원본 템플릿이 아니거나 이미 초기화된 프로젝트입니다.",
            file=sys.stderr,
        )
        return 5

    code_name = args.code_name or default_code_name(args.project_name)
    scheme = args.scheme or code_name.lower()
    plugin_prefix = args.plugin_prefix or default_plugin_prefix(args.project_name)

    try:
        validate_inputs(
            args.project_name,
            args.package_name,
            code_name,
            scheme,
            plugin_prefix,
        )
    except ValueError as error:
        print(f"오류: {error}", file=sys.stderr)
        return 2

    replacements = {
        TEMPLATE_DISPLAY_NAME: args.project_name,
        TEMPLATE_CODE_NAME: code_name,
        TEMPLATE_PACKAGE: args.package_name,
        TEMPLATE_PLUGIN_PREFIX: plugin_prefix,
        TEMPLATE_SCHEME: scheme,
    }
    path_replacements = {
        TEMPLATE_CODE_NAME: code_name,
        TEMPLATE_PACKAGE: args.package_name,
        TEMPLATE_PLUGIN_PREFIX: plugin_prefix,
    }
    text_changes = changed_text_files(root, replacements)
    package_moves = [
        (path, package_move_target(path, args.package_name)[1])
        for path in package_directories(root)
        if path != package_move_target(path, args.package_name)[1]
    ]
    path_changes = branded_paths(root, path_replacements)

    for source, target in package_moves + path_changes:
        if target.exists() and source != target:
            print(f"오류: 변경 대상 경로가 이미 존재합니다: {target}", file=sys.stderr)
            return 3

    print_preview(text_changes, package_moves, path_changes, root)
    if not args.apply:
        print("미리보기만 완료했습니다. 실제 적용하려면 --apply를 추가하세요.")
        return 0

    for path, updated in text_changes:
        path.write_text(updated, encoding="utf-8")

    for source, target in package_moves:
        anchor, _ = package_move_target(source, args.package_name)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(source), str(target))
        prune_empty_parents(source.parent, anchor)

    for source, target in branded_paths(root, path_replacements):
        if not source.exists():
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        source.rename(target)

    if not args.keep_build_cache:
        for path in generated_directories(root):
            shutil.rmtree(path)

    remaining = verify_no_template_identifiers(root, replacements)
    if remaining:
        print("오류: 이전 템플릿 식별자가 남아 있습니다.", file=sys.stderr)
        for path in remaining:
            print(f"  {path.relative_to(root)}", file=sys.stderr)
        return 4

    print("프로젝트 식별자 변경을 완료했습니다.")
    print(f"  project: {code_name}")
    print(f"  app name: {args.project_name}")
    print(f"  package: {args.package_name}")
    print(f"  deep link: {scheme}://")
    print(f"  convention plugin prefix: {plugin_prefix}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
