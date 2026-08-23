#!/usr/bin/env python3
# noqa: SIZE_OK — dry-run과 apply가 동일 removal plan과 변환 함수를 공유해야 합니다.

import argparse
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path


SAMPLE_MODULES = (
    "feature/sample",
    "core/data/remote",
    "core/database",
    "core/test-fixture",
)

SAMPLE_FILES = (
    ("core/data/repository", "DefaultSampleRepository.kt"),
    ("core/data/repository", "RemoteErrorMapping.kt"),
    ("core/data/repository", "SampleMappers.kt"),
    ("core/data/repository", "DefaultSampleRepositoryTest.kt"),
    ("core/domain", "Sample.kt"),
    ("core/domain", "SampleRepository.kt"),
    ("core/domain", "ObserveSampleUseCase.kt"),
    ("core/domain", "ObserveSamplesUseCase.kt"),
    ("core/domain", "RefreshSamplesUseCase.kt"),
    ("core/domain", "ObserveSamplesUseCaseTest.kt"),
)

SAMPLE_MARKERS = (
    "feature.sample",
    "feature:sample",
    "SampleMvvmKey",
    "SampleMviKey",
    "SampleDetailKey",
    "SampleMatchingGraph",
    "SampleMatchingKey",
    "SampleMatchingDetailKey",
    "SampleMatchingProfileEditKey",
    "SampleProfileGraph",
    "SampleProfileKey",
    "SampleProfileEditKey",
    "sampleEntries",
    "DefaultSampleRepository",
    "SampleRepository",
    "SampleRemoteDataSource",
    "SampleEntity",
    "SampleDao",
)


class PlanError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class TextChange:
    path: Path
    content: str


@dataclass(frozen=True, slots=True)
class RemovalPlan:
    deletions: tuple[Path, ...]
    text_changes: tuple[TextChange, ...]
    renames: tuple[tuple[Path, Path], ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sample 수직 슬라이스를 제거하고 Home과 Settings만 유지합니다.",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="실제로 파일을 변경합니다. 생략하면 변경 예정 목록만 출력합니다.",
    )
    return parser.parse_args()


def replace_once(content: str, old: str, new: str, label: str) -> str:
    count = content.count(old)
    if count != 1:
        raise PlanError(f"{label}: 예상한 코드가 {count}개 발견되었습니다.")
    return content.replace(old, new, 1)


def remove_line_once(content: str, fragment: str, label: str) -> str:
    matching = [line for line in content.splitlines(keepends=True) if fragment in line]
    if len(matching) != 1:
        raise PlanError(f"{label}: '{fragment}' 줄이 {len(matching)}개 발견되었습니다.")
    return content.replace(matching[0], "", 1)


def find_unique_file(root: Path, module: str, name: str) -> Path:
    matches = sorted((root / module).rglob(name))
    if len(matches) != 1:
        raise PlanError(f"{module}/{name}: 파일이 {len(matches)}개 발견되었습니다.")
    return matches[0]


def find_unique_kotlin_containing(root: Path, module: str, marker: str) -> Path:
    matches = []
    for path in sorted((root / module).rglob("*.kt")):
        if marker in path.read_text(encoding="utf-8"):
            matches.append(path)
    if len(matches) != 1:
        raise PlanError(f"{module}: '{marker}'를 포함한 Kotlin 파일이 {len(matches)}개 발견되었습니다.")
    return matches[0]


def edit(path: Path, transform) -> TextChange:
    original = path.read_text(encoding="utf-8")
    updated = transform(original)
    if updated == original:
        raise PlanError(f"{path}: 변경 결과가 없습니다.")
    return TextChange(path, updated)


def simplify_app_ui_policy(content: str) -> str:
    package_line = content.splitlines()[0]
    if not package_line.startswith("package ") or "SampleDetailKey" not in content:
        raise PlanError("AppUiPolicy.kt: 예상한 Sample 전체 화면 정책을 찾지 못했습니다.")
    return f"""{package_line}

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.navigation3.runtime.NavKey

@Immutable
internal data class AppChrome(
    val showBottomBar: Boolean,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean,
)

@Composable
internal fun appChrome(currentKey: NavKey): AppChrome {{
    val showBottomBar = currentKey in AppNavigationConfig.bottomBarKeys
    val statusBarBackground = MaterialTheme.colorScheme.surface
    val navigationBarBackground =
        if (showBottomBar) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface

    return AppChrome(
        showBottomBar = showBottomBar,
        useDarkStatusBarIcons = statusBarBackground.isLightBackground(),
        useDarkNavigationBarIcons = navigationBarBackground.isLightBackground(),
    )
}}

private fun Color.isLightBackground(): Boolean = luminance() > LIGHT_THRESHOLD

private const val LIGHT_THRESHOLD = 0.5f
"""


def update_app_nav_key(content: str) -> str:
    sample_keys = """
    /** Home stack에 표시하는 MVVM Sample destination입니다. */
    @Serializable
    data object SampleMvvmKey : HomeGraph

    /** Home stack에 표시하는 MVI Sample destination입니다. */
    @Serializable
    data object SampleMviKey : HomeGraph

    /** Home stack의 Sample 상세 destination이며 [id]를 Navigation 인자로 전달합니다. */
    @Serializable
    data class SampleDetailKey(
        val id: Long,
    ) : HomeGraph
}

sealed interface SettingsGraph : AppNavKey {
    /** Settings 독립 stack의 root destination입니다. */
    @Serializable
    data object SettingsKey : SettingsGraph, TopLevelNavKey
}

sealed interface SampleMatchingGraph : AppNavKey {
    /** Sample Matching 독립 stack의 root destination입니다. */
    @Serializable
    data object SampleMatchingKey : SampleMatchingGraph, TopLevelNavKey

    /** Sample Matching stack의 상세 destination입니다. */
    @Serializable
    data object SampleMatchingDetailKey : SampleMatchingGraph

    /** Profile Edit UI를 Matching stack에 표시해 Back 시 Matching Detail로 복귀하는 destination입니다. */
    @Serializable
    data object SampleMatchingProfileEditKey : SampleMatchingGraph
}

sealed interface SampleProfileGraph : AppNavKey {
    /** Sample Profile 독립 stack의 root destination입니다. */
    @Serializable
    data object SampleProfileKey : SampleProfileGraph, TopLevelNavKey

    /** Sample Profile stack의 Edit destination이며 Back 시 Profile root로 이동합니다. */
    @Serializable
    data object SampleProfileEditKey : SampleProfileGraph
}
"""
    replacement = """
}

sealed interface SettingsGraph : AppNavKey {
    /** Settings 독립 stack의 root destination입니다. */
    @Serializable
    data object SettingsKey : SettingsGraph, TopLevelNavKey
}
"""
    return replace_once(content, sample_keys, replacement, "AppDestinationKey.kt")


def update_app_navigation_config(content: str) -> str:
    for fragment in (
        ".core.navigation.contract.SampleMatchingGraph.SampleMatchingKey",
        ".core.navigation.contract.SampleProfileGraph.SampleProfileKey",
        "AppTopLevelDestination(SampleMatchingKey",
        "AppTopLevelDestination(SampleProfileKey",
    ):
        content = remove_line_once(content, fragment, "AppNavigationConfig.kt")
    return content


def update_android_app(content: str) -> str:
    content = remove_line_once(content, ".feature.sample.navigation.sampleEntries", "App composable")
    return remove_line_once(content, "sampleEntries()", "App composable entries")


def update_home_screen(content: str) -> str:
    for fragment in (
        "        onOpenMvvm = viewModel::openMvvmSample,\n",
        "        onOpenMvi = viewModel::openMviSample,\n",
        "    onOpenMvvm: () -> Unit,\n",
        "    onOpenMvi: () -> Unit,\n",
        '        Button(onClick = onOpenMvvm) { Text("Sample MVVM") }\n',
        '        Button(onClick = onOpenMvi) { Text("Sample MVI") }\n',
        "            onOpenMvvm = {},\n",
        "            onOpenMvi = {},\n",
    ):
        content = replace_once(content, fragment, "", "HomeScreen.kt")
    return content


def update_home_view_model(content: str) -> str:
    content = remove_line_once(content, ".core.navigation.contract.HomeGraph.SampleMviKey", "HomeViewModel.kt")
    content = remove_line_once(content, ".core.navigation.contract.HomeGraph.SampleMvvmKey", "HomeViewModel.kt")
    for block in (
        """        fun openMvvmSample() {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(SampleMvvmKey))
            }
        }

""",
        """        fun openMviSample() {
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(SampleMviKey))
            }
        }

""",
    ):
        content = replace_once(content, block, "", "HomeViewModel.kt")
    return content


def update_repository_module(content: str) -> str:
    content = remove_line_once(content, ".repository.SampleRepository", "RepositoryModule.kt")
    return replace_once(
        content,
        """    @Binds
    @Singleton
    abstract fun bindSampleRepository(implementation: DefaultSampleRepository): SampleRepository

""",
        "",
        "RepositoryModule Sample binding",
    )


def update_theme(content: str) -> str:
    content = remove_line_once(content, "import androidx.compose.material3.ColorScheme", "Theme")
    content = remove_line_once(content, "import androidx.compose.ui.graphics.Color", "Theme")
    return replace_once(
        content,
        """val ColorScheme.sampleDetailBackground: Color
    get() = primaryContainer

""",
        "",
        "Theme Sample detail color",
    )


def build_plan(root: Path) -> RemovalPlan:
    missing_modules = [path for path in SAMPLE_MODULES if not (root / path).is_dir()]
    if missing_modules:
        raise PlanError(f"이미 제거됐거나 찾을 수 없는 Sample 모듈: {', '.join(missing_modules)}")

    deletions = [root / path for path in SAMPLE_MODULES]
    deletions.extend(find_unique_file(root, module, name) for module, name in SAMPLE_FILES)

    app_composable = find_unique_kotlin_containing(root, "presentation/src/main/kotlin", "sampleEntries()")
    theme = find_unique_kotlin_containing(root, "core/designsystem/src/main/kotlin", "sampleDetailBackground")

    text_changes = (
        edit(
            root / "settings.gradle.kts",
            lambda value: remove_line_once(
                remove_line_once(
                    remove_line_once(
                        remove_line_once(value, 'include(":feature:sample")', "settings.gradle.kts"),
                        'include(":core:data:remote")',
                        "settings.gradle.kts",
                    ),
                    'include(":core:database")',
                    "settings.gradle.kts",
                ),
                'include(":core:test-fixture")',
                "settings.gradle.kts",
            ),
        ),
        edit(
            root / "presentation/build.gradle.kts",
            lambda value: remove_line_once(value, "implementation(projects.feature.sample)", "presentation build"),
        ),
        edit(
            root / "app/build.gradle.kts",
            lambda value: remove_line_once(value, "implementation(projects.core.database)", "app build"),
        ),
        edit(
            root / "core/data/repository/build.gradle.kts",
            lambda value: remove_line_once(
                remove_line_once(value, "implementation(projects.core.data.remote)", "repository build"),
                "implementation(projects.core.database)",
                "repository build",
            ),
        ),
        edit(
            root / "core/domain/build.gradle.kts",
            lambda value: remove_line_once(value, "testImplementation(projects.core.testFixture)", "domain build"),
        ),
        edit(find_unique_file(root, "core/navigation-contract", "AppDestinationKey.kt"), update_app_nav_key),
        edit(
            find_unique_file(root, "presentation", "AppNavigationConfig.kt"),
            update_app_navigation_config,
        ),
        edit(app_composable, update_android_app),
        edit(find_unique_file(root, "presentation", "AppUiPolicy.kt"), simplify_app_ui_policy),
        edit(find_unique_file(root, "feature/home", "HomeScreen.kt"), update_home_screen),
        edit(find_unique_file(root, "feature/home", "HomeViewModel.kt"), update_home_view_model),
        edit(find_unique_file(root, "core/data/repository", "RepositoryModule.kt"), update_repository_module),
        edit(theme, update_theme),
    )
    app_ui_policy = find_unique_file(root, "presentation", "AppUiPolicy.kt")
    app_chrome = app_ui_policy.with_name("AppChrome.kt")
    if app_chrome.exists():
        raise PlanError(f"변경 대상 경로가 이미 존재합니다: {app_chrome}")
    return RemovalPlan(tuple(deletions), text_changes, ((app_ui_policy, app_chrome),))


def print_preview(plan: RemovalPlan, root: Path) -> None:
    print(f"삭제: {len(plan.deletions)}개 파일/디렉터리")
    for path in plan.deletions:
        print(f"  {path.relative_to(root)}")
    print(f"수정: {len(plan.text_changes)}개 파일")
    for change in plan.text_changes:
        print(f"  {change.path.relative_to(root)}")
    print(f"이름 변경: {len(plan.renames)}개 파일")
    for source, target in plan.renames:
        print(f"  {source.relative_to(root)} -> {target.relative_to(root)}")
    print("유지: feature/home, feature/settings, 공통 Navigation 및 UI 테스트")


def verify_no_sample_references(root: Path) -> list[tuple[Path, str]]:
    remaining = []
    checked_roots = ("app", "presentation", "feature", "core", "navigation")
    for relative in checked_roots:
        base = root / relative
        if not base.exists():
            continue
        for path in sorted(base.rglob("*")):
            if not path.is_file() or "build" in path.parts:
                continue
            if path.suffix not in {".kt", ".kts", ".xml"}:
                continue
            content = path.read_text(encoding="utf-8")
            for marker in SAMPLE_MARKERS:
                if marker in content:
                    remaining.append((path, marker))
    return remaining


def apply_plan(plan: RemovalPlan) -> None:
    for change in plan.text_changes:
        change.path.write_text(change.content, encoding="utf-8")
    for source, target in plan.renames:
        source.rename(target)
    for path in sorted(plan.deletions, key=lambda value: len(value.parts), reverse=True):
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        plan = build_plan(root)
    except PlanError as error:
        print(f"오류: {error}", file=sys.stderr)
        return 2

    print_preview(plan, root)
    if not args.apply:
        print("미리보기만 완료했습니다. 실제 적용하려면 --apply를 추가하세요.")
        return 0

    apply_plan(plan)
    remaining = verify_no_sample_references(root)
    if remaining:
        print("오류: Sample 코드 참조가 남아 있습니다.", file=sys.stderr)
        for path, marker in remaining:
            print(f"  {path.relative_to(root)}: {marker}", file=sys.stderr)
        return 3

    print("Sample 수직 슬라이스 제거를 완료했습니다.")
    print("Home과 Settings 및 공통 인프라는 유지했습니다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
