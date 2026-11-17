# ADR-007 — Dépendances reproductibles et vérifiées

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Constat 9 de la revue : aucune vérification de checksum ni lockfile dans l'outil analysé ; avec des plages de versions, les builds ne sont pas reproductibles et la chaîne d'approvisionnement n'est pas protégée.

## Décision

Résolveur Maven + Gradle Module Metadata (variantes AndroidX/KMP, POM parents, BOM, exclusions, contraintes, strictly) ; lockfile joide.lock (coordonnée + version + SHA-256 + dépôt d'origine) ; une résolution avec lockfile valide ne fait AUCUNE requête réseau ; vérification des checksums publiés, échec explicite sur divergence ; versions dynamiques et SNAPSHOT résolues une fois puis figées ; mode hors-ligne strict ; téléchargements parallèles plafonnés ; extraction AAR protégée zip-slip.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Cache sans lockfile (schéma analysé) | écartée | non reproductible, chaîne non vérifiée |
| Lockfile + checksums (retenue) | retenue | reproductible, vérifiable, hors-ligne possible |

## Conséquences

P4 livrera les preuves : zéro requête réseau avec lockfile (transport factice), checksum divergent = échec, lockfile identique octet pour octet sur deux résolutions.
