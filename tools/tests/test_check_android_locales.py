"""Regression checks for missing English fallback labels and indexed choices."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "check_android_locales", Path(__file__).resolve().parents[1] / "check_android_locales.py"
)
locales = importlib.util.module_from_spec(spec)
spec.loader.exec_module(locales)


class AndroidLocalesTest(unittest.TestCase):
    def validate(self, default, vi=""):
        # Keep test-created files within the project, like other session artifacts.
        artifact_root = Path(__file__).resolve().parents[2] / "build/tmp/android-locales-tests"
        artifact_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=artifact_root) as directory:
            root = Path(directory)
            for name, content in (("values", default), ("values-vi", vi)):
                (root / name).mkdir()
                (root / name / "strings.xml").write_text(
                    f"<resources>{content}</resources>", encoding="utf-8"
                )
            return locales.validate(root)[0]

    def test_missing_english_label_is_an_error(self):
        self.assertEqual(self.validate('<string name="action">Run task</string>'),
                         ["Missing Vietnamese: action"])

    def test_missing_chinese_label_is_an_error(self):
        self.assertEqual(self.validate('<string name="action">运行</string>'),
                         ["Missing Vietnamese: action"])

    def test_non_translatable_code_is_exempt(self):
        self.assertEqual(self.validate('<string name="code" translatable="false">key</string>'), [])

    def test_format_arguments_must_be_preserved(self):
        self.assertEqual(self.validate('<string name="count">%1$d books</string>',
                                       '<string name="count">%1$s sách</string>'),
                         ["Format arguments differ: count"])

    def test_format_reordering_is_allowed(self):
        self.assertEqual(self.validate('<string name="pair">%1$s %2$d</string>',
                                       '<string name="pair">%2$d %1$s</string>'), [])

    def test_literal_choices_need_translation(self):
        self.assertEqual(self.validate('<string-array name="choices"><item>Default</item></string-array>'),
                         ["Missing Vietnamese array: choices"])

    def test_array_indexes_must_stay_aligned(self):
        self.assertEqual(self.validate(
            '<string-array name="choices"><item>Text</item><item>Video</item></string-array>',
            '<string-array name="choices"><item>Văn bản</item></string-array>'),
            ["Array lengths differ: choices"])

    def test_icon_names_are_display_labels(self):
        self.assertEqual(self.validate(
            '<string-array name="icon_names"><item>iconMain</item></string-array>'),
            ["Missing Vietnamese array: icon_names"])

    def test_resource_references_and_stored_ids_inherit(self):
        self.assertEqual(self.validate(
            '<string-array name="entries"><item>@string/label</item></string-array>'
            '<string-array name="engine_values"><item>native</item></string-array>'
            '<string-array name="icons"><item>ic_launcher</item></string-array>'), [])


if __name__ == "__main__":
    unittest.main()
