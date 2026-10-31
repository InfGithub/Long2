import hashlib
import importlib.util
import io
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("publish_maven", ROOT / "scripts/publish_maven.py")
PUBLISHER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PUBLISHER)


def pom(version="0.1.1"):
    return f'<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.inf.long2</groupId><artifactId>Long2</artifactId><version>{version}</version></project>'.encode()


def jar(version="0.1.1"):
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w") as archive:
        archive.writestr("META-INF/maven/com.inf.long2/Long2/pom.properties", f"groupId=com.inf.long2\nartifactId=Long2\nversion={version}\n")
    return data.getvalue()


class MavenRepositoryTest(unittest.TestCase):
    def setUp(self):
        (ROOT / "debug").mkdir(exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(prefix="maven-test-", dir=ROOT / "debug")
        self.addCleanup(self.directory.cleanup)
        self.site = Path(self.directory.name)

    def snapshot(self):
        return {str(path.relative_to(self.site)): path.read_bytes() for path in self.site.rglob("*") if path.is_file()}

    def test_standard_layout_preserves_release_bytes_and_all_checksums(self):
        binary = jar()
        PUBLISHER.publish(self.site, pom(), binary, b"sources")
        version = self.site / "maven/com/inf/long2/Long2/0.1.1"
        self.assertEqual(binary, (version / "Long2-0.1.1.jar").read_bytes())
        self.assertEqual(pom(), (version / "Long2-0.1.1.pom").read_bytes())
        for path in version.iterdir():
            if path.suffix in (".pom", ".jar"):
                for algorithm in PUBLISHER.CHECKSUMS:
                    self.assertEqual(hashlib.new(algorithm, path.read_bytes()).hexdigest(), Path(str(path) + "." + algorithm).read_text().strip())

    def test_repeat_publication_is_byte_identical(self):
        binary = jar()
        PUBLISHER.publish(self.site, pom(), binary, b"sources")
        before = self.snapshot()
        PUBLISHER.publish(self.site, pom(), binary, b"sources")
        self.assertEqual(before, self.snapshot())

    def test_rejects_changed_release_before_writing_any_payload(self):
        binary = jar()
        PUBLISHER.publish(self.site, pom(), binary, b"sources")
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, "Refusing to overwrite"):
            PUBLISHER.publish(self.site, pom(), binary, b"changed sources")
        self.assertEqual(before, self.snapshot())

    def test_preserves_versions_and_orders_them_numerically(self):
        binaries = {version: jar(version) for version in ("0.1.2", "0.1.10", "0.1.3")}
        for version, binary in binaries.items():
            PUBLISHER.publish(self.site, pom(version), binary, b"sources")
        metadata = ET.parse(self.site / "maven/com/inf/long2/Long2/maven-metadata.xml")
        self.assertEqual(["0.1.2", "0.1.3", "0.1.10"], [v.text for v in metadata.findall("versioning/versions/version")])
        self.assertEqual("0.1.10", metadata.findtext("versioning/latest"))
        for version, binary in binaries.items():
            self.assertEqual(binary, (self.site / f"maven/com/inf/long2/Long2/{version}/Long2-{version}.jar").read_bytes())

    def test_rejects_jar_with_wrong_coordinates(self):
        with self.assertRaisesRegex(ValueError, "coordinates"):
            PUBLISHER.publish(self.site, pom(), jar("0.1.2"), b"sources")
        self.assertEqual({}, self.snapshot())

    def test_rejects_snapshot_and_unsafe_coordinates(self):
        for version in ("../0.1.1", "0.1.1-SNAPSHOT", "0.1.1-rc1", "0.01.1"):
            with self.assertRaises(ValueError):
                PUBLISHER.coordinates(pom(version))
        with self.assertRaises(ValueError):
            PUBLISHER.coordinates(pom().replace(b"Long2", b"../Long2"))

    def test_metadata_checksums_match(self):
        PUBLISHER.publish(self.site, pom(), jar(), b"sources")
        path = self.site / "maven/com/inf/long2/Long2/maven-metadata.xml"
        for algorithm in PUBLISHER.CHECKSUMS:
            self.assertEqual(hashlib.new(algorithm, path.read_bytes()).hexdigest(), Path(str(path) + "." + algorithm).read_text().strip())

    def test_source_archive_matches_tag_sources_and_is_reproducible(self):
        first = PUBLISHER.source_archive("HEAD")
        self.assertEqual(first, PUBLISHER.source_archive("HEAD"))
        with zipfile.ZipFile(io.BytesIO(first)) as archive:
            source_path = "com/inf/long2/binaries/Binary256.java"
            self.assertEqual(PUBLISHER.command("git", "show", "HEAD:src/main/java/" + source_path), archive.read(source_path))
            self.assertIn("META-INF/LICENSE", archive.namelist())
            self.assertFalse(any(name.endswith(".class") for name in archive.namelist()))
            self.assertTrue(all(info.compress_type == zipfile.ZIP_STORED for info in archive.infolist()))


if __name__ == "__main__":
    unittest.main()
