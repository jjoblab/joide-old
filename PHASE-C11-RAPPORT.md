# PHASE C11 — le magasin de la signature s'ouvre, le PKCS12 redevient lisible sur l'appareil (2026-11-20)

**Mandat du propriétaire, mot pour mot.** « Le nouveau message du
build est : app:sign[debug] : signig failed — Unrecognized keystore
format please load with a specific type » (la coquille « signig » est
celle du terrain ; notre habillage rend « signing failed: <panne> »).
Le chantier du bouton build (C8 : le pack des classes de
l'assembleur ; C9 : les SIX dépendances runtime du POM ; C10 : le
manifeste portant sa version) avait fait franchir à la chaîne la
barrière du bundle — elle atteint maintenant l'étape de SIGNATURE, et
la dernière phrase du terrain change : ce n'est plus une classe
manquante ni un manifeste avare, c'est le fichier de clés lui-même
que la machine ne reconnaît pas.

## 1. Le diagnostic

`ApksigSigner.sign` ouvrait le keystore par le lecteur à SONDE du
JDK :

```java
KeyStore store = KeyStore.getInstance(request.keystore.toFile(),
        request.keystorePassword);
```

Cette voie exige qu'un fournisseur INSTALLÉ charge le fichier tout
seul. Or :

- l'APPAREIL n'installe que deux lecteurs de magasins : PKCS12 et
  BKS ;
- son lecteur PKCS12 ne DÉCHIFFRE pas un magasin moderne — PBES2/
  PBKDF2, la manière d'écriture de keytool depuis Java 12 (le
  JDK de l'appareil n'a pas de `SecretKeyFactory` PBES2) ;
- le keystore de débogage embarqué en asset (lot U/8) est écrit par
  le keytool du JDK de construction — moderne, donc.

Résultat du terrain : aucun fournisseur installé ne charge le
fichier, la sonde rend « Unrecognized keystore format, please load
with a specific type » — un message qui ne nomme NI la cause (le
format du fichier n'est pas le problème : son CHIFFREMENT l'est) NI
le remède. Sur les bancs, rien ne pouvait le voir : la machine
virtuelle du harnais déchiffre PBES2 sans broncher — c'est la
différence de lecteurs entre la machine de test et l'appareil.

## 2. Le correctif — trois volets du même terrain

### 2.1 La LECTURE : le magasin s'ouvre par type explicite

`ApksigSigner.ouvrirMagasin(Path, char[])` remplace la sonde :
chaque type se demande À PLAT, dans l'ordre — PKCS12, JKS, BKS du
lecteur installé (un type absent de la machine ne fait qu'une panne
notée), puis PKCS12 et BKS du fournisseur cryptographique
complémentaire chargé par RÉFLEXION
(`org.bouncycastle.jce.provider.BouncyCastleProvider` : présent sur
l'appareil au classpath de démarrage, absent du classpath de test —
aucun répertoire d'artefacts ne l'exige et son absence ne casse
rien). Ce fournisseur complémentaire SAIT déchiffrer le PKCS12
moderne là où le lecteur installé de l'appareil ne peut pas : même
un magasin moderne posé sur l'appareil devient signable.

L'erreur finale, honnête, liste les types essayés avec leurs pannes
ET le remède : « keystore unreadable: tried PKCS12, JKS, BKS … ;
Regenerate it as a legacy PKCS12 (SHA1/3DES): a keystore written by
a modern keytool (PBES2) cannot be decrypted on-device ». Un
fichier absent se dit absent avec son chemin.

Deux fidélités de plus dans le geste :

- la CLÉ se récupère avec le mot de passe de clé PUIS celui du
  magasin (le PKCS12 protège la clé par le mot de passe du magasin,
  keytool ignore un mot de passe distinct) — et jamais deux fois le
  MÊME candidat : le déverrouillage PKCS12 coûte des milliers de
  tours de dérivation sur l'appareil ;
- la CHAÎNE de certificats passe ENTIÈRE au signataire (avant :
  le certificat seul — une chaîne complète serait amputée).

### 2.2 La GÉNÉRATION : l'asset naît compatible

La tâche `genererKeystoreDebug` (lot U/8) passe maintenant
`-J-Dkeystore.pkcs12.legacy=true` au keytool : le magasin naît en
SHA1/3DES — lisible PARTOUT, machine de construction, machine
virtuelle de test ET appareil. Deux gardes aux OCTETS l'entourent
(`portePbes2`, l'OID `1.2.840.113549.1.5.13`, DER
`06 09 2A 86 48 86 F7 0D 01 05 0D`) :

- la garde d'à-jour : un keystore MODERNE resté d'un build ancien
  (le fichier n'est jamais commis mais survit dans le dossier de
  build) force la re-génération ;
- la vérification après génération : un keytool qui rendrait quand
  même un fichier moderne échoue le build, jamais la panique à la
  signature.

### 2.3 La COPIE : le fichier posé sur appareil suit l'asset

`PreparationChaine.assurerKeystoreDebug` copiait le keystore UNE
fois (« présent et non vide : ne rien refaire »). Or le terrain
précédent avait déjà posé le mauvais magasin (moderne) sur
l'appareil : une application mise à jour — avec un asset corrigé —
gardait la panne POUR TOUJOURS, le fichier présent interdisant la
recopie. La copie tient maintenant une MARQUE : l'empreinte SHA-256
de l'asset, posée à côté du fichier
(`filesDir/toolchain/debug.keystore.marque`). Empreinte changée
(APK mis à jour), marque absente ou muette — la copie se refait,
atomiquement (le fichier et la marque ensemble, écriture
temporaire + renommage, comme la discipline du dépôt). La lecture
de l'asset reste à la main (`readAllBytes`/`transferTo` exigent
l'API 33, minSdk 28 — même discipline que le lot C1). Le
comportement d'avant tient : asset absent (APK antérieur) — le
build continue non signé, l'écart se dira à la console, jamais un
crash.

## 3. Les tests — le terrain reproduit, puis assaini

Quatre nouveaux (`ApksigSignerMagasinTest`, source set de test du
module build-android — le classpath porte les adaptateurs) :

- `pkcs12ModerneSouvreParLeType` — keytool SANS option (la manière
  d'écriture actuelle : le terrain exact) : le magasin s'ouvre par
  le type PKCS12 installé, l'alias « joide » s'y trouve ;
- `jksSouvreParLeType` — un JKS s'ouvre par son type ;
- `fichierAlienDiagnostiqueEtRemedie` — un fichier aliéné rend
  l'erreur honnête : les types essayés se listent, le remède se
  dit — jamais le message muet du terrain ;
- `fichierAbsentSeDitAbsent` — avec son chemin.

La garde d'octets de la génération se vérifie LIVRÉE : l'APK C11
porte un `assets/toolchain/debug.keystore` de 2 509 octets, OID
PBES2 ABSENT, protections legacy (1.2.840.113549.1.12.1.x)
PRÉSENTES — `keytool -list` le lit (alias « joide »,
`PrivateKeyEntry`). Le pipeline d'acceptation
(`AndroidPipelineTest`) signe toujours son APK jusqu'au bundle
validé par bundletool — les lots C8-C10 tiennent.

## 4. Bilan

658 tests verts (403 app-android + 184 agent + 71 build-android,
dont 4 nouveaux). APK C11 livré
(`download/joide-build-c11.apk`, 16,1 Mo, arm64-v8a) — la variante
debug NON débogable, rétrécie, du lot VIII. La chaîne du bouton
build est complète sur le papier : compiler, dexer, packager,
SIGNER, assembler le bundle — chaque étape a son terrain corrigé
(C8, C9, C10, C11).

## 5. Hygiène du dépôt (en passant, le contrôle d'abord)

Le contrôle de politique de langue
(`scripts/check-language-policy`) restait ROUGE à la racine sur des
pannes préexistantes : un nom de méthode de test portait des
ACCENTS (identifiants non ASCII —
`un400EstUneRequeteRefuseePasUneReponseIllisible`, sans accents
maintenant) et les espaces sécables des rapports anciens (C9, C10,
entrées du CHANGES) se corrigeaient au mode `--fix` du contrôle
lui-même. Racine verte : 0 problème.
