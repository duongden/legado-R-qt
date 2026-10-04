#!/usr/bin/env python3
"""Run translation JVM tests without an Android SDK; dependencies stay in a temp cache."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path,
                        default=Path(tempfile.gettempdir()) / "legado-translation-jvm")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    cache = args.cache_dir.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    dependencies = [
        ("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.3.10"),
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.10"),
        ("org.jetbrains.kotlin", "kotlin-script-runtime", "2.3.10"),
        ("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
        ("org.jetbrains.kotlin", "kotlin-daemon-embeddable", "2.3.10"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
        ("org.jetbrains", "annotations", "13.0"),
        ("junit", "junit", "4.13.2"),
        ("org.hamcrest", "hamcrest-core", "1.3"),
    ]
    jars = []
    for group, artifact, version in dependencies:
        jar = cache / f"{artifact}-{version}.jar"
        if not jar.exists():
            url = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{artifact}/{version}/{jar.name}"
            temp = jar.with_suffix(".download")
            try:
                with urllib.request.urlopen(url, timeout=120) as response, temp.open("wb") as output:
                    shutil.copyfileobj(response, output)
                temp.replace(jar)
            finally:
                temp.unlink(missing_ok=True)
        jars.append(str(jar))
    classpath = os.pathsep.join(jars)
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else shutil.which("java")
    if not java:
        raise SystemExit("Java is required; set JAVA_HOME to an installed JDK.")
    names = ["DoubleArrayTrie", "TranslationData", "TranslationEngine", "TranslationMarkup",
             "TranslationSegments", "DictionaryTextParser", "TranslationRules"]
    sources = [root / f"app/src/main/java/io/legado/app/model/{name}.kt" for name in names]
    sources.append(root / "app/src/main/java/io/legado/app/model/dictionary/ITrieDictionary.kt")
    tests = ["TranslationEngineTest", "DictionaryTextParserTest", "TranslationRulesTest"]
    sources.extend(root / f"app/src/test/java/io/legado/app/model/{name}.kt" for name in tests)
    classes = cache / "classes"
    subprocess.run([java, "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
                    "-d", str(classes), *map(str, sources)], check=True, cwd=root)
    subprocess.run([java, "-cp", str(classes) + os.pathsep + classpath, "org.junit.runner.JUnitCore",
                    *(f"io.legado.app.model.{name}" for name in tests)], check=True, cwd=root)


if __name__ == "__main__":
    main()
