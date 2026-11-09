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

ALIASES = {"id": "in", "he": "iw", "zh-Hans": "b+zh+Hans", "zh-Hant": "b+zh+Hant"}
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


def text_resources(directory: Path) -> dict[tuple[str, str], ET.Element]:
    resources = {}
    for path in sorted(directory.glob("*.xml")):
        for element in ET.parse(path).getroot():
            if (
                element.tag in {"string", "plurals"}
                and element.get("translatable") != "false"
            ):
                resources[element.tag, element.attrib["name"]] = element
    return resources


def resource_arguments(element: ET.Element) -> dict[str, Counter[tuple[int, str]]]:
    if element.tag == "string":
        return {"": arguments("".join(element.itertext()))}
    return {
        item.attrib["quantity"]: arguments("".join(item.itertext()))
        for item in element.findall("item")
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
    for base in sorted(root.glob("**/src/main/res/values")):
        defaults = text_resources(base)
        if not defaults:
            continue
        for tag in languages:
            if tag == "en":
                continue
            localized = base.parent / f"values-{ALIASES.get(tag, tag)}"
            translated = text_resources(localized)
            location = localized.relative_to(root)
            for (kind, name), default in defaults.items():
                key = f"{kind}/{name}"
                translation = translated.get((kind, name))
                if translation is None:
                    errors.append(f"{location}: missing {key}")
                    continue
                expected = resource_arguments(default)
                actual = resource_arguments(translation)
                if kind == "plurals" and "other" not in (expected.keys() & actual.keys()):
                    missing_in = base.relative_to(root) if "other" not in expected else location
                    errors.append(f"{missing_in}: missing {key}[other]")
                    continue
                for quantity, found in actual.items():
                    if found != expected.get(quantity, expected.get("other")):
                        item = f"{key}[{quantity}]" if quantity else key
                        errors.append(f"{location}: format arguments differ for {item}")
    return errors


if __name__ == "__main__":
    findings = validate(Path(__file__).resolve().parents[1])
    if findings:
        print("\n".join(findings), file=sys.stderr)
        sys.exit(1)
    print("Localization consistency checks passed.")
