#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Génère la liste des signatures java.*/javax.* de l'API Android 28.

Entrée : le android.jar d'une platform 28 (ex. platform-28_r06.zip,
https://dl.google.com/android/repository/platform-28_r06.zip, entrée
android-9/android.jar). Sortie : un fichier texte gzippe, une entrée par
ligne, trois formes :

  C:<classe en notation / >                      (référence de classe)
  M:<classe>.<méthode>:<descripteur>              (méthode ou constructeur)
  F:<classe>.<champ>:<descripteur>                (champ)

Deux passes :

1. Membres déclarés : javap -s -p du JDK liste toutes les classes java/
   et javax/ du jar (y compris imbriquées) ; chaque membre déclaré est
   traduit en entrée M/F.
2. Signatures EFFECTIVES : javac émet le type statique du récepteur comme
   propriétaire de la référence (par ex. java/io/BufferedWriter.write
   alors que write(String) est déclaré dans Writer). La hiérarchie
   (super-classe, interfaces) est lue directement dans les en-têtes des
   fichiers de classe du jar, et chaque classe reçoit aussi les membres
   hérités de ses supertypes — fermeture transitive. C'est un sur-ensemble
   sûr : il n'autorise rien que la plateforme n'offre, il évite les faux
   rejets sur les appels hérités.

Aucune signature n'est écrite à la main. Régénération documentée dans
docs/dev.md ; la somme de contrôle du android.jar source est consignée
dans docs/toolchain.md.

Usage :
  python3 scripts/generate_api28_signatures.py <android.jar> <sortie.txt.gz>
"""
import gzip
import hashlib
import re
import struct
import subprocess
import sys
import zipfile

CLASS_DECL = re.compile(
    r"^((?:public|protected|private|abstract|final|static|sealed|"
    r"non-sealed|strictfp)\s+)*(class|interface|enum|record|@interface)"
    r"\s+([A-Za-z_$][\w.$]*)")
METHOD_NAME = re.compile(r"([A-Za-z_$][\w$]*)\s*\(")
IDENT_BEFORE_END = re.compile(r"([A-Za-z_$][\w$]*)\s*;")

# Taille des entrées du pool de constantes hors Utf8/Long/Double (u1 tag
# déjà lu) : tag -> octets suivants.
POOL_FIXED = {
    3: 4, 4: 4,            # Integer, Float
    7: 2, 8: 2,            # Class, String
    9: 4, 10: 4, 11: 4,    # Fieldref, Methodref, InterfaceMethodref
    12: 4,                 # NameAndType
    15: 3, 16: 2,          # MethodHandle, MethodType
    17: 4, 18: 4,          # Dynamic, InvokeDynamic
    19: 2, 20: 2,          # Module, Package
}


def list_classes(jar_path):
    """Classes java/ et javax/ du jar, imbriquées comprises."""
    with zipfile.ZipFile(jar_path) as zf:
        names = [n[:-len(".class")] for n in zf.namelist()
                 if n.endswith(".class")
                 and (n.startswith("java/") or n.startswith("javax/"))
                 and not n.endswith("module-info.class")
                 and not n.endswith("package-info.class")]
    return sorted(set(names))


def read_hierarchy(zf, class_name):
    """(super, interfaces) d'une classe du jar, depuis son en-tête."""
    with zf.open(class_name + ".class") as fh:
        data = fh.read()
    if struct.unpack(">I", data[:4])[0] != 0xCAFEBABE:
        raise ValueError("not a class file: " + class_name)
    pos = 8  # magic + minor + major
    count = struct.unpack(">H", data[pos:pos + 2])[0]
    pos += 2
    utf8 = {}
    class_entries = {}  # index CONSTANT_Class -> index Utf8 du nom
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack(">H", data[pos:pos + 2])[0]
            pos += 2
            utf8[i] = data[pos:pos + length].decode("utf-8", "replace")
            pos += length
        elif tag == 7:
            class_entries[i] = struct.unpack(">H", data[pos:pos + 2])[0]
            pos += 2
        elif tag in (5, 6):
            pos += 8
            i += 1
        elif tag in POOL_FIXED:
            pos += POOL_FIXED[tag]
        else:
            raise ValueError(f"tag {tag} inattendu dans {class_name}")
        i += 1

    def class_name_of(idx):
        return utf8[class_entries[idx]] if idx != 0 else None

    # access_flags, this_class, super_class
    pos += 2
    this = struct.unpack(">H", data[pos:pos + 2])[0]
    pos += 2
    super_idx = struct.unpack(">H", data[pos:pos + 2])[0]
    pos += 2
    interfaces_count = struct.unpack(">H", data[pos:pos + 2])[0]
    pos += 2
    interfaces = []
    for _ in range(interfaces_count):
        idx = struct.unpack(">H", data[pos:pos + 2])[0]
        pos += 2
        interfaces.append(class_name_of(idx))
    super_name = class_name_of(super_idx)
    if class_name_of(this) != class_name:
        raise ValueError(f"this_class inattendu dans {class_name}")
    return super_name, interfaces


def javap(javap_bin, jar_path, classes):
    """Sortie texte de javap -s -p sur toutes les classes (par lots)."""
    out = []
    batch = []
    for i, name in enumerate(classes):
        batch.append(name)
        if len(batch) == 500 or i == len(classes) - 1:
            res = subprocess.run(
                [javap_bin, "-s", "-p", "-classpath", jar_path] + batch,
                capture_output=True, text=True, check=True)
            out.append(res.stdout)
            batch = []
    return "\n".join(out)


def parse_declared(javap_text):
    """Membres déclarés par classe : {classe: {(genre, nom, desc)}}."""
    declared = {}
    current = None
    pending = None
    for line in javap_text.split("\n"):
        stripped = line.strip()
        m = CLASS_DECL.match(stripped)
        if m and not line[:1].isspace():
            current = m.group(3).replace(".", "/")
            declared.setdefault(current, set())
            pending = None
            continue
        if current is None or not line[:1].isspace():
            continue
        if stripped.startswith("descriptor:"):
            desc = stripped[len("descriptor:"):].strip()
            if pending is not None:
                kind, nom = pending
                if desc:
                    declared[current].add((kind, nom, desc))
                pending = None
            continue
        if not stripped.endswith(";") or stripped.startswith(("}", "{")):
            continue
        if stripped.startswith("static {};"):
            pending = ("M", "<clinit>")
        elif "(" in stripped:
            mm = METHOD_NAME.findall(stripped)
            if mm:
                nom = mm[-1]
                simple = current.rsplit("/", 1)[-1]
                simple = simple.rsplit("$", 1)[-1]
                if nom == simple:
                    nom = "<init>"
                pending = ("M", nom)
        else:
            fm = IDENT_BEFORE_END.findall(stripped)
            if fm:
                pending = ("F", fm[-1])
    return declared


def effective_signatures(declared, parents):
    """Fermeture : chaque classe hérite des membres de ses supertypes."""
    cache = {}

    def closure(name, stack):
        if name in cache:
            return cache[name]
        if name in stack:
            return set()  # cycle défensif : données externes
        members = set(declared.get(name, ()))
        super_name, interfaces = parents.get(name, (None, []))
        for sup in [super_name] + list(interfaces):
            if sup and sup in parents:
                members |= closure(sup, stack | {name})
        cache[name] = members
        return members

    lines = set()
    for name in declared:
        lines.add("C:" + name)
        for kind, nom, desc in closure(name, frozenset()):
            lines.add(f"{kind}:{name}.{nom}:{desc}")
    return lines


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    jar, out_path = sys.argv[1], sys.argv[2]
    with open(jar, "rb") as fh:
        digest = hashlib.sha256(fh.read()).hexdigest()
    classes = list_classes(jar)
    parents = {}
    with zipfile.ZipFile(jar) as zf:
        for name in classes:
            parents[name] = read_hierarchy(zf, name)
    javap_bin = "javap"  # JDK requis (docs/dev.md)
    declared = parse_declared(javap(javap_bin, jar, classes))
    lines = sorted(effective_signatures(declared, parents))
    with gzip.open(out_path, "wt", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    print(f"android.jar sha256 : {digest}")
    print(f"classes java/javax : {sum(1 for l in lines if l.startswith('C:'))}")
    print(f"entrees M (membres): {sum(1 for l in lines if l.startswith('M:'))}")
    print(f"entrees F (champs) : {sum(1 for l in lines if l.startswith('F:'))}")
    print(f"total lignes       : {len(lines)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
