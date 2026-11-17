# ADR-017 — Empreinte rapide 128 bits : MurmurHash3 x64 plutôt que XXH3-128

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

La décision D9 fixe par défaut « XXH3-128 ou équivalent rapide 128 bits, implémentation Java pure compatible ART (pas de mémoire non sécurisée), à benchmarker » pour la détection de changement ; SHA-256 reste le hachage de confiance (artefacts, lockfile). XXH3-128 comporte de nombreuses branches selon la longueur (corps, queues de 1 à 240 octets, accumulateurs) : l'implémenter correctement en Java pur sans transcription erronée est un risque réel pour un gain non démontré dans notre contexte (fichiers de code et de ressources, blocs de 64 Ko).

## Décision

L'empreinte rapide est **MurmurHash3, variante 64 bits, sortie 128 bits** : « équivalent rapide 128 bits » au sens de D9. L'implémentation est Java pur, sans mémoire non sécurisée, avec un paramètre de graine par domaine (fichiers, arbres, classpaths, définitions de modules). Elle est vérifiée par des vecteurs de référence générés avec la mise en œuvre canonique publique de l'algorithme (liaison mmh3) et commités dans `HashingTest` ; l'égalité structurelle `hash(b"", 1) == hash(b"\x00", 0)` constatée pendant la vérification est une propriété de l'algorithme, pas un défaut.

Mesure sur la machine de build (JDK 21, un fil, tampon de 64 Mo en mémoire) : murmur128 ≈ 3,2 Go/s ; SHA-256 ≈ 1,3 Go/s. La détection de changement reste donc largement au-delà du besoin (cold-scan de 100 000 fichiers sans hachage de contenu : 557 ms).

## Options

| Option | Verdict | Raison |
|---|---|---|
| MurmurHash3 x64 128, Java pur (retenue) | retenue | algorithme public éprouvé, ~200 lignes vérifiables, vecteurs de référence disponibles, débit mesuré suffisant |
| XXH3-128 maison | écartée | complexité de transcription élevée, risque d'erreur non couvert par des vecteurs simples |
| SHA-256 partout | écartée | correct mais plus lent sur le chemin chaud de détection de changement |
| Bibliothèque de hachage tierce | écartée | dépendance nouvelle pour un besoin couvert par 200 lignes vérifiées |

## Conséquences

`jo.joide.platform.Hashing.fast128` est la seule empreinte de détection de changement du noyau ; les empreintes sont relocalisables (aucun chemin absolu haché) et les graines séparent les domaines. Si une mesure future sur appareil montre un goulot, un remplacement par XXH3 reste possible derrière la même signature, avec ses propres vecteurs.
