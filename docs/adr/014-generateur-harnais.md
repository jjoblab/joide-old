# ADR-014 — Générateur synthétique déterministe et harnais de mesure

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

§7 P0 exige des projets synthétiques S/M/L/XL reproductibles (mêmes octets à graine égale), un graphe réaliste (quelques modules centraux très dépendants, mélange Java/Kotlin, ressources Android) et des baselines commitées.

## Décision

Tiers exacts : S 5/500, M 50/5 000, L 200/30 000, XL 500/100 000 fichiers ; 85 % de sources (60/40 Java/Kotlin), 15 % de ressources et fichiers de structure ; couches 3/5/6/7 ; hubs 1/4/10/20 ; lignes par fichier 30-400. Déterminisme : budget exact par module (répartition stable), générateur pseudo-aléatoire semé par (graine du tier, chemin relatif) — le contenu d'un fichier ne dépend jamais de l'ordre d'itération —, itérations sur des listes triées, LF, UTF-8, Locale.ROOT. Code Java compilable : un fichier = un type public au contrat fixe (process/counter/accessors/enum), références croisées limitées aux dépendances directes importées. Kotlin à grammaire restreinte (non compilé en P0, écart documenté). Harnais : temps mur ET temps CPU du processus (3 exécutions minimum), pic de tas par sondage Runtime, comptages, octets, hash d'arbre Merkle — le bench vérifie lui-même que toutes les exécutions produisent la même racine (porte de déterminisme de ciLocal). Baselines TOML commitées (schema joide.baseline/1), sans horodatage ; regressionTest compare exact (déterministe) et ±10 % (temporel). Constats d'exécution (P0, machine de développement partagée) : le temps mur varie du simple au triple selon les vagues de charge et de fréquence de l'ordonnanceur ; /tmp peut résider sur une couche de stockage distincte du répertoire projet (mesure non comparable). Décisions d'hygiène qui en découlent : (1) tas fixe des JVM de mesure (-Xms256m -Xmx512m) ; (2) répertoire de travail de la comparaison sur le MÊME système de fichiers que la capture de baseline ; (3) critère temporel = temps CPU du processus, le temps mur restant informatif ; (4) la comparaison temporelle (regressionTest<S|M|L|XL>) s'exécute sur une machine de performance stable — la machine de référence (D12) ; ciLocal, conformément au critère P0 (« la CI locale exécute tests + bench S/M »), lance le bench avec sa porte de déterminisme intégrée. Une boucle de calibration a été expérimentée puis écartée : le ratio ne convergeait pas sous une fréquence CPU variable au cœur d'une mesure (preuves au rapport de phase).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Générateur maison déterministe (retenue) | retenue | contrôlé, extensible, prouvable |
| Projets réels seulement | écartée | non reproductibles à volonté, volumétrie non maîtrisée (les projets réels restent des candidats d'évaluation via import Gradle, §8 du prompt) |

## Conséquences

Risques consignés : sous-ensemble TOML figé à valider par P1 (R2) ; Kotlin non compilé avant P3 (R3). Baselines S/M commitées ; L/XL générées à la demande.
