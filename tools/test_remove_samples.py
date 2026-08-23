import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent


class RemoveSamplesTest(unittest.TestCase):
    def test_remove_samples_after_project_rename(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture_root = Path(directory) / "project"
            shutil.copytree(
                PROJECT_ROOT,
                fixture_root,
                ignore=shutil.ignore_patterns(
                    ".git",
                    ".gradle",
                    ".idea",
                    ".kotlin",
                    ".Codex",
                    "build",
                    "local.properties",
                ),
            )

            rename = self.run_tool(
                fixture_root,
                "rename_project.py",
                "--project-name",
                "Fixture App",
                "--package-name",
                "com.acme.fixture",
                "--apply",
            )
            self.assertEqual(0, rename.returncode, rename.stderr)

            settings_before = (fixture_root / "settings.gradle.kts").read_text(encoding="utf-8")
            preview = self.run_tool(fixture_root, "remove_samples.py")
            self.assertEqual(0, preview.returncode, preview.stderr)
            self.assertEqual(
                settings_before,
                (fixture_root / "settings.gradle.kts").read_text(encoding="utf-8"),
            )
            self.assertIn("미리보기만 완료했습니다", preview.stdout)

            applied = self.run_tool(fixture_root, "remove_samples.py", "--apply")
            self.assertEqual(0, applied.returncode, applied.stderr)
            self.assertFalse((fixture_root / "feature/sample").exists())
            self.assertFalse((fixture_root / "core/data/remote").exists())
            self.assertFalse((fixture_root / "core/database").exists())
            self.assertFalse((fixture_root / "core/test-fixture").exists())
            self.assertTrue((fixture_root / "feature/home").is_dir())
            self.assertTrue((fixture_root / "feature/settings").is_dir())

            app_chrome = list((fixture_root / "presentation/src/main/kotlin").rglob("AppChrome.kt"))
            self.assertEqual(1, len(app_chrome))
            self.assertFalse(list((fixture_root / "presentation/src/main/kotlin").rglob("AppUiPolicy.kt")))

            settings = (fixture_root / "settings.gradle.kts").read_text(encoding="utf-8")
            self.assertNotIn('include(":feature:sample")', settings)
            self.assertIn('include(":feature:home")', settings)
            self.assertIn('include(":feature:settings")', settings)

    @staticmethod
    def run_tool(root: Path, tool: str, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(root / "tools" / tool), *arguments],
            cwd=root,
            check=False,
            capture_output=True,
            text=True,
        )


if __name__ == "__main__":
    unittest.main()
