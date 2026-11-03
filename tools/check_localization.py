# /// script
# requires-python = ">=3.10"
# dependencies = []
# ///
"""Check supported locales, translated keys, and Android format arguments offline."""

import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ALIASES = {"id": "in", "he": "iw"}
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])")


def arguments(text: str) -> Counter[tuple[int, str]]:
    result: Counter[tuple[int, str]] = Counter()
    implicit = 0
    for match in FORMAT.finditer(text):
        conversion = match.group(2)
        if conversion in {"%", "n"}:
            continue
        if match.group(1):
            position = int(match.group(1))
        else:
            implicit += 1
            position = implicit
        result[position, conversion] += 1
    return result


def strings(path: Path) -> dict[str, str]:
    return {
        element.attrib["name"]: "".join(element.itertext())
        for element in ET.parse(path).getroot().findall("string")
        if element.attrib.get("translatable") != "false"
    }


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    language_source = (
        root
        / "core/common/src/main/kotlin/com/comst19/dambom/core/common/locale/AppLanguage.kt"
    )
    entries = re.findall(
        r'^\s+[A-Z_]+\("([^\"]*)"(?:, "([^\"]*)")?\),',
        language_source.read_text(),
        re.MULTILINE,
    )
    languages = {tag: script for tag, script in entries if tag}
    if not languages:
        return ["No supported languages found in AppLanguage.kt"]
    configured = {
        element.attrib["{http://schemas.android.com/apk/res/android}name"]
        for element in ET.parse(
            root / "app/src/main/res/xml/locales_config.xml"
        ).getroot()
    }
    expected_locales = {
        f"{tag}-{script}" if script else tag for tag, script in languages.items()
    }
    if configured != expected_locales:
        errors.append(
            f"locales_config mismatch: {sorted(configured ^ expected_locales)}"
        )
    build = (root / "app/build.gradle.kts").read_text()
    configuration = re.search(
        r"resourceConfigurations\s*\+=\s*listOf\((.*?)\)", build, re.DOTALL
    )
    resource_languages = (
        set(re.findall(r'"([^\"]+)"', configuration.group(1)))
        if configuration
        else set()
    )
    expected_resources = {ALIASES.get(tag, tag) for tag in languages}
    if resource_languages != expected_resources:
        errors.append(
            f"resourceConfigurations mismatch: {sorted(resource_languages ^ expected_resources)}"
        )
    labels = (
        root
        / "feature/settings/src/main/kotlin/com/comst19/dambom/feature/settings/language/LanguageLabels.kt"
    ).read_text()
    enum_names = set(re.findall(r'^\s+([A-Z_]+)\("', language_source.read_text(), re.MULTILINE))
    if set(re.findall(r"AppLanguage\.([A-Z_]+)\s*->", labels)) != enum_names:
        errors.append(
            "LanguageLabels does not match the selectable AppLanguage entries"
        )
    for base in sorted(root.glob("**/src/main/res/values/strings.xml")):
        defaults = strings(base)
        if not defaults:
            continue
        for tag in languages:
            if tag == "en":
                continue
            localized = (
                base.parent.parent / f"values-{ALIASES.get(tag, tag)}" / base.name
            )
            if not localized.is_file():
                errors.append(
                    f"Missing translation file: {localized.relative_to(root)}"
                )
                continue
            translated = strings(localized)
            for key, text in defaults.items():
                if key not in translated:
                    errors.append(f"{localized.relative_to(root)}: missing {key}")
                elif arguments(text) != arguments(translated[key]):
                    errors.append(
                        f"{localized.relative_to(root)}: format arguments differ for {key}"
                    )
    return errors


if __name__ == "__main__":
    findings = validate(Path(__file__).resolve().parents[1])
    if findings:
        print("\n".join(findings), file=sys.stderr)
        sys.exit(1)
    print("Localization consistency checks passed.")
