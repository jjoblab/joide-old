# ADR-035 — Outillage Android épinglé, téléchargé par le transport vérifié

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

aapt2 est un binaire natif distribué comme artefact Maven à
classifieur de plateforme ; l'android.jar de compilation n'est pas un
artefact Maven mais une archive des dépôts du SDK. Le moteur doit les
obtenir sans SDK préinstallé (critère P0 : constructible et testable
sans SDK Android), sans jamais exécuter un binaire non vérifié.

## Décision

Un localisateur résout l'outillage dans cet ordre : variables
d'environnement explicites (`JOIDE_AAPT2`, `JOIDE_ANDROID_JAR`,
`JOIDE_R8_JAR`), jar de D8/R8 présent sur le classpath d'exécution,
puis téléchargement épinglé dans un cache local partagé entre
projets. Chaque téléchargement porte une URL, une version et un
SHA-256 fixes dans le code ; il passe par le transport HTTPS du
résolveur de dépendances (P4 : https seul, TLS vérifié, plafonds) ;
une empreinte divergente est un échec explicite. L'extraction des
archives suit la politique zip-slip de l'extracteur d'AAR (noms
normalisés, plafonds d'entrées et de volume, zone d'attente et
déplacement atomique, idempotence par marqueur). La plateforme
téléchargée n'est jamais redistribuée par JoIDE : c'est une donnée
d'entrée locale, soumise aux conditions du SDK Android.

## Options

| Option | Verdict | Raison |
|---|---|---|
| SDK Android préinstallé exigé | écartée | contredit le critère « sans SDK » et l'usage sur appareil |
| Téléchargement sans épinglage | écartée | chaîne d'approvisionnement non vérifiée, exactement le constat 9 |
| Épinglage SHA-256 + transport vérifié (retenue) | retenue | reproductible, vérifiable, hors-ligne au second passage |

## Conséquences

Le premier lancement des tests télécharge aapt2 (2,4 Mio) et la
plateforme (61 Mio) une fois ; les passages suivants sont hors-ligne.
Une montée de version d'outil = changer la constante + son SHA-256,
l'empreinte des tâches change et le build se réexécute.
