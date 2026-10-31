import argparse
import hashlib
import html
import io
import json
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone
from pathlib import Path

MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"
VERSION_PATTERN = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
CHECKSUMS = ("md5", "sha1", "sha256", "sha512")


def command(*args):
    return subprocess.check_output(args)


def coordinates(pom):
    root = ET.fromstring(pom)
    values = tuple(root.findtext(f"{{{MAVEN_NAMESPACE}}}{name}") for name in ("groupId", "artifactId", "version"))
    group, artifact, version = values
    if not group or not re.fullmatch(r"[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*", group):
        raise ValueError("An explicit groupId is required")
    if not artifact or not re.fullmatch(r"[A-Za-z0-9_-]+", artifact):
        raise ValueError("An explicit artifactId is required")
    if not version or not VERSION_PATTERN.fullmatch(version):
        raise ValueError("Only stable three-part release versions are supported")
    if root.findtext(f"{{{MAVEN_NAMESPACE}}}packaging", "jar") != "jar":
        raise ValueError("The release must be a JAR project")
    return values


def source_archive(ref):
    paths = command("git", "ls-tree", "-r", "--name-only", ref, "--", "src/main/java").decode().splitlines()
    if not paths:
        raise ValueError("Release has no production Java sources")
    timestamp = int(command("git", "log", "-1", "--format=%ct", ref))
    date = time.gmtime(max(timestamp, 315532800))[:6]
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as archive:
        for path in sorted(paths):
            if not path.endswith(".java"):
                continue
            info = zipfile.ZipInfo(path.removeprefix("src/main/java/"), date)
            info.compress_type = zipfile.ZIP_STORED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, command("git", "show", f"{ref}:{path}"))
        license_data = command("git", "show", f"{ref}:LICENSE")
        info = zipfile.ZipInfo("META-INF/LICENSE", date)
        info.compress_type = zipfile.ZIP_STORED
        info.create_system = 3
        info.external_attr = 0o100644 << 16
        archive.writestr(info, license_data)
    return output.getvalue()


def verify_jar(jar, gav):
    group, artifact, version = gav
    with zipfile.ZipFile(io.BytesIO(jar)) as archive:
        raw = archive.read(f"META-INF/maven/{group}/{artifact}/pom.properties").decode()
        properties = dict(line.split("=", 1) for line in raw.splitlines() if line and not line.startswith("#") and "=" in line)
        if any(properties.get(key) != value for key, value in zip(("groupId", "artifactId", "version"), gav)):
            raise ValueError("Release JAR coordinates do not match its tag POM")
        if archive.testzip() is not None:
            raise ValueError("Release JAR contains a damaged entry")


def with_checksums(path, data):
    result = {path: data}
    for algorithm in CHECKSUMS:
        result[path + "." + algorithm] = (hashlib.new(algorithm, data).hexdigest() + "\n").encode()
    return result


def checked_path(site, relative):
    path = site / relative
    if not path.resolve().is_relative_to(site.resolve()):
        raise ValueError("Publication path escapes the site directory")
    return path


def publish(site, pom, jar, sources):
    gav = coordinates(pom)
    verify_jar(jar, gav)
    group, artifact, version = gav
    module_path = "maven/" + group.replace(".", "/") + "/" + artifact
    version_path = module_path + "/" + version
    basename = artifact + "-" + version
    payloads = {}
    for suffix, data in ((".pom", pom), (".jar", jar), ("-sources.jar", sources)):
        payloads.update(with_checksums(version_path + "/" + basename + suffix, data))
    # 先核对全部版本文件，再写入任何文件，防止冲突产生半个发布版本。
    for relative, data in payloads.items():
        target = checked_path(site, relative)
        if target.exists() and target.read_bytes() != data:
            raise ValueError(f"Refusing to overwrite a published release: {relative}")
    for relative, data in payloads.items():
        target = checked_path(site, relative)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    module = checked_path(site, module_path)
    versions = sorted((p.name for p in module.iterdir() if p.is_dir() and VERSION_PATTERN.fullmatch(p.name)),
                      key=lambda value: tuple(map(int, value.split("."))))
    metadata_path = module_path + "/maven-metadata.xml"
    previous = checked_path(site, metadata_path)
    same_versions = False
    if previous.exists():
        existing = ET.fromstring(previous.read_bytes())
        same_versions = (existing.findtext("groupId") == group and existing.findtext("artifactId") == artifact
                         and existing.findall("versioning/versions/version")
                         and [v.text for v in existing.findall("versioning/versions/version")] == versions
                         and existing.findtext("versioning/latest") == versions[-1]
                         and existing.findtext("versioning/release") == versions[-1])
    if not same_versions:
        metadata = ET.Element("metadata")
        ET.SubElement(metadata, "groupId").text = group
        ET.SubElement(metadata, "artifactId").text = artifact
        versioning = ET.SubElement(metadata, "versioning")
        for name in ("latest", "release"):
            ET.SubElement(versioning, name).text = versions[-1]
        listed = ET.SubElement(versioning, "versions")
        for value in versions:
            ET.SubElement(listed, "version").text = value
        ET.SubElement(versioning, "lastUpdated").text = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
        ET.indent(metadata)
        for relative, data in with_checksums(metadata_path, ET.tostring(metadata, encoding="utf-8", xml_declaration=True) + b"\n").items():
            checked_path(site, relative).write_bytes(data)
    links = "\n".join(f'<li><a href="{html.escape(value)}/{html.escape(artifact)}-{html.escape(value)}.pom">{html.escape(value)}</a></li>' for value in reversed(versions))
    module.joinpath("index.html").write_text(f'<!doctype html>\n<meta charset="utf-8">\n<title>{html.escape(group + ":" + artifact)}</title>\n<h1>{html.escape(group + ":" + artifact)}</h1>\n<ul>{links}</ul>\n', encoding="utf-8")
    site.joinpath("maven/index.html").write_text('<!doctype html>\n<meta charset="utf-8">\n<title>Long2 Maven Repository</title>\n<h1>Maven Repository</h1>\n<p><a href="com/inf/long2/Long2/">com.inf.long2:Long2</a></p>\n', encoding="utf-8")
    site.joinpath(".nojekyll").write_bytes(b"")
    site.joinpath("index.html").write_text('<!doctype html>\n<meta charset="utf-8">\n<title>Long2 Maven Repository</title>\n<h1>Long2 Maven Repository</h1>\n<p>Maven repository: <a href="maven/">maven/</a></p>\n<p>Coordinates: <code>com.inf.long2:Long2</code>. Java 25 or newer is required.</p>\n<p><a href="maven/com/inf/long2/Long2/">Available versions</a></p>\n', encoding="utf-8")
    print(f"Published {group}:{artifact}:{version}; available versions: {', '.join(versions)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--site", type=Path, required=True)
    args = parser.parse_args()
    if not args.tag.startswith("v") or not VERSION_PATTERN.fullmatch(args.tag[1:]):
        parser.error("Expected a stable release tag such as v0.1.2")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repository):
        parser.error("Expected an owner/repository name")
    if not args.site.resolve().is_relative_to(Path.cwd().resolve()):
        parser.error("Site directory must be inside the current workspace")
    ref = "refs/tags/" + args.tag
    pom = command("git", "show", f"{ref}:pom.xml")
    gav = coordinates(pom)
    if gav[2] != args.tag[1:]:
        raise ValueError("Tag and POM versions differ")
    release = json.loads(command("gh", "api", f"repos/{args.repository}/releases/tags/{args.tag}"))
    if release["draft"] or release["prerelease"]:
        raise ValueError("Only published stable releases can be deployed")
    name = f"{gav[1]}-{gav[2]}.jar"
    assets = [asset for asset in release["assets"] if asset["name"] == name]
    if len(assets) != 1:
        raise ValueError(f"Expected one release asset named {name}")
    args.site.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="maven-download-", dir=args.site.parent) as directory:
        command("gh", "release", "download", args.tag, "--repo", args.repository, "--pattern", name, "--dir", directory)
        jar = Path(directory, name).read_bytes()
    digest = assets[0].get("digest")
    if digest and digest != "sha256:" + hashlib.sha256(jar).hexdigest():
        raise ValueError("Downloaded JAR does not match the GitHub asset digest")
    publish(args.site, pom, jar, source_archive(ref))


if __name__ == "__main__":
    main()
