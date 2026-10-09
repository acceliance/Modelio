#!/usr/bin/env python3
"""Sign the macOS native libraries that live INSIDE jar/zip files of an app bundle, in place.

Apple's notarization scans inside archives (jars included, nested ones too) and rejects any unsigned Mach-O
file. Modelio ships such libraries in jars (SWT, JNA, filesystem/security fragments, ...). For every jar that
contains a Mach-O file this tool:
  1. signs each Mach-O entry (by extracting it to a temp file and running codesign on it),
  2. rewrites the jar with the signed entries, keeping entry order, compression, timestamps and permissions,
  3. drops the jar's own signature files (META-INF/*.SF|RSA|DSA|EC) and the per-entry digests in the manifest,
     because the content changed and the old Java signature can no longer be valid.
Nested archives (a jar inside a jar) are handled recursively. Jars without Mach-O files are not touched.

  sign-jar-natives.py <app-or-dir> --codesign-args "--force --sign <identity> --options runtime --timestamp"
Set --codesign to use another command (tests use a stub). Prints a summary; exit code 1 on any signing error.
"""
import argparse
import io
import os
import shlex
import subprocess
import sys
import tempfile
import zipfile

MACHO_MAGIC = {
    b'\xcf\xfa\xed\xfe', b'\xce\xfa\xed\xfe',   # 64/32-bit little endian
    b'\xfe\xed\xfa\xcf', b'\xfe\xed\xfa\xce',   # big endian
    b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca',   # fat (0xcafebabe is also a Java .class: .class files are excluded)
}
NATIVE_EXT = ('.jnilib', '.dylib', '.so')
ARCHIVE_EXT = ('.jar', '.zip')
SIGNATURE_EXT = ('.SF', '.RSA', '.DSA', '.EC')

CR, LF = chr(13), chr(10)
CRLF = CR + LF

stats = {'jars_changed': 0, 'libs_signed': 0, 'errors': 0}


def is_native(name, head):
    return name.lower().endswith(NATIVE_EXT) and head in MACHO_MAGIC and not name.endswith('.class')


def is_digest(first_line):
    key = first_line.split(':', 1)[0]
    return key.endswith('-Digest') or key == 'Digest-Algorithms'


def strip_manifest_digests(raw):
    """Remove per-entry digest attributes (and sections that only carried a Name and digests) from a manifest."""
    text = raw.decode('utf-8', errors='surrogateescape')
    eol = CRLF if CRLF in text else LF
    blocks = text.replace(CRLF, LF).split(LF + LF)
    out = [blocks[0]]                                   # main section is kept as is
    for block in blocks[1:]:
        if not block.strip():
            continue
        # logical attributes = physical lines joined with their continuation lines (starting with one space)
        attrs, cur = [], None
        for ln in block.split(LF):
            if ln.startswith(' ') and cur is not None:
                cur.append(ln)
            else:
                cur = [ln]
                attrs.append(cur)
        kept = [a for a in attrs if not is_digest(a[0])]
        if any(not a[0].startswith('Name:') for a in kept):   # the section carries real attributes: keep it
            out.append(LF.join(l for a in kept for l in a))
    result = (LF + LF).join(out).rstrip(LF) + LF + LF
    return result.replace(LF, eol).encode('utf-8', errors='surrogateescape')


def sign_bytes(data, name, codesign_cmd, codesign_args):
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, os.path.basename(name))
        with open(path, 'wb') as f:
            f.write(data)
        r = subprocess.run([codesign_cmd] + codesign_args + [path], capture_output=True, text=True)
        if r.returncode != 0:
            stats['errors'] += 1
            print('ERROR signing %s: %s' % (name, (r.stderr or r.stdout).strip()), file=sys.stderr)
            return None
        with open(path, 'rb') as f:
            return f.read()


def process_zip(data, label, codesign_cmd, codesign_args):
    """Return new bytes if something inside was signed, else None."""
    try:
        zin = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return None
    replacements = {}
    for info in zin.infolist():
        if info.is_dir():
            continue
        name = info.filename
        low = name.lower()
        if low.endswith(ARCHIVE_EXT):
            blob = zin.read(info)
            if blob[:4] == b'PK\x03\x04':
                new = process_zip(blob, label + '!' + name, codesign_cmd, codesign_args)
                if new is not None:
                    replacements[name] = new
        elif low.endswith(NATIVE_EXT):
            blob = zin.read(info)
            if is_native(name, blob[:4]):
                signed = sign_bytes(blob, name, codesign_cmd, codesign_args)
                if signed is None:
                    return None
                replacements[name] = signed
                stats['libs_signed'] += 1
                print('  signed %s!%s' % (label, name))
    if not replacements:
        return None
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as zout:
        for info in zin.infolist():
            name = info.filename
            if name.upper().startswith('META-INF/') and name.upper().endswith(SIGNATURE_EXT):
                continue                                          # stale Java signature
            payload = b'' if info.is_dir() else zin.read(info)
            if name in replacements:
                payload = replacements[name]
            elif name.upper() == 'META-INF/MANIFEST.MF':
                payload = strip_manifest_digests(payload)
            ni = zipfile.ZipInfo(name, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.create_system = info.create_system
            ni.comment = info.comment
            zout.writestr(ni, payload)
    stats['jars_changed'] += 1
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('root', help='app bundle or directory to scan')
    ap.add_argument('--codesign', default='codesign', help='codesign command (default: codesign)')
    ap.add_argument('--codesign-args', required=True,
                    help='arguments placed before the file, e.g. "--force --sign ID --options runtime --timestamp"')
    args = ap.parse_args()
    cs_args = shlex.split(args.codesign_args)
    scanned = 0
    for dirpath, _, files in os.walk(args.root):
        for fn in files:
            if not fn.lower().endswith(ARCHIVE_EXT):
                continue
            path = os.path.join(dirpath, fn)
            if os.path.islink(path):
                continue
            with open(path, 'rb') as f:
                data = f.read()
            if data[:4] != b'PK\x03\x04':
                continue
            scanned += 1
            new = process_zip(data, os.path.relpath(path, args.root), args.codesign, cs_args)
            if new is not None:
                mode = os.stat(path).st_mode
                tmp = path + '.signing-tmp'
                with open(tmp, 'wb') as f:
                    f.write(new)
                os.chmod(tmp, mode)
                os.replace(tmp, path)
    print('scanned %d archives: %d rewritten, %d native libraries signed, %d errors' %
          (scanned, stats['jars_changed'], stats['libs_signed'], stats['errors']))
    return 1 if stats['errors'] else 0


if __name__ == '__main__':
    sys.exit(main())
