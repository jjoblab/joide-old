# ADR-042 — Échappatoire : build Gradle déporté (conception, hors noyau)

- **Statut** : Accepted (conception — l'implémentation est hors du
  noyau P7, à la demande du prompt §6.9)
- **Date** : 2026-11-15

## Contexte

Le prompt §6.9 demande une « échappatoire optionnelle : build Gradle
déporté (machine/CI) hors appareil — hors du noyau, à concevoir en P7
comme extension ». Le besoin est réel : un projet importé que JoIDE ne
saurait pas construire (greffons non modélisés, tests instrumentés,
génération de code kapt/ksp) doit pouvoir être construit par un vrai
Gradle, ailleurs que sur l'appareil — l'appareil n'a ni le SDK
complet, ni les ressources pour une chaîne Gradle.

Le noyau fournit déjà les deux briques : l'export Gradle déterministe
(ADR-041) produit un projet constructible ; le démon de build (P6,
ADR-038) parle par cadres JSON et exécute des descriptions de build.
L'échappatoire les assemble.

## Décision (conception)

1. **L'appareil exporte, la machine exécute.** L'échappatoire est un
   dialogue entre l'application (P8) et un exécutant Gradle distant :

   - `EXPORT` : l'application exporte l'espace de travail vers un
     projet Gradle (ADR-041), l'empaquette (zip déterministe, sans
     sorties), le pousse à l'exécutant (HTTP(S) authentifié ou
     transfert de fichiers).
   - `BUILD` : l'exécutant lance `gradle assembleDebug` (ou les tâches
     demandées) sur une machine outillée — SDK Android, JDK, réseau
     Maven. Il diffuse les événements (lignes de sortie, tâches
     démarrées/finies) vers l'appareil, puis renvoie les artefacts
     (APK/AAB) et le bilan.
   - `SYNC` : la même exportation sert de diagnostic — l'exécutant
     peut exécuter `gradle help` et renvoyer les erreurs
     d'évaluation que l'import statique ne peut pas deviner, pour
     enrichir le SyncReport côté appareil.

2. **Contrat de l'exécutant, pas de protocole imposé.** L'exécutant
   est défini par un contrat (reçoit un zip + une requête JSON,
   rend des événements + des artefacts), pas par un protocole lié à
   JoIDE : un runner CI standard (GitHub Actions, machine de
   l'utilisateur) suffit. Aucune brique JoIDE n'est exigée côté
   exécutant — c'est la garantie d'entretien minimale.

3. **Jamais dans le noyau.** Les interfaces vivront derrière
   l'adaptateur d'IDE (P8) : l'application parle à un
   `BuildEscapeHatch` (interface du paquet ide-integration), avec une
   implémentation « déporté » et rien d'autre. Le démon `:build`
   n'est pas impliqué : le build déporté est un Gradle complet, pas
   le moteur JoIDE.

4. **Sécurité.** L'appareil n'exécute jamais ce qu'il reçoit : les
   artefacts sont vérifiés (sommes transmises par le contrat), la
   connexion authentifiée. L'exécutant, lui, exécute un projet
   exporté par JoIDE — le risque est celui de tout build distant,
   à la charge de la machine exécutante.

## Alternatives rejetées

- **Gradle embarqué sur l'appareil** : le runtime Gradle complet et
  le SDK ne tiennent ni dans le budget mémoire ni dans les
  restrictions d'exécution d'Android 10+ (§4 du prompt).
- **Un mode « Gradle partiel » dans le démon** : le démon exécuterait
  Gradle dans son fork à gros tas — mêmes objections, plus la
  complexité d'un couplage que rien ne justifie : le démon JoIDE
  construit avec le moteur JoIDE.

## Conséquences

- Rien n'est implémenté en P7 au-delà de l'export (qui existe) : la
  conception est ce livrable, les interfaces arrivent en P8 derrière
  l'adaptateur d'IDE.
- Le chemin de diagnostic `SYNC` ferme une lacune de l'import
  statique (ADR-039 ne peut pas deviner une erreur d'évaluation
  Gradle) sans jamais exécuter quoi que ce soit sur l'appareil.
