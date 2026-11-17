# ADR-É2 — Système de design JoIDE : palette « Graphite », typographie, composants, implémentation

- **Statut** : Accepted (design — implémentation en lots futurs)
- **Date** : 2026-11-16

## Contexte

L'UI P8 laisse le cadre tel quel (`Theme.Material.Light.NoActionBar`,
vues empilées programmatiquement) : lisible mais brut, sans identité.
Le propriétaire exige un design moderne et professionnel. L'analyse
CodeAssist (ADR-É1) montre qu'un IDE sérieux repose sur un petit
système — palette sombre, typographie à deux familles, seuil
d'adaptation, composants stables — pas sur des écrans jetables.

## Décision

### Palette « Graphite » — sombre d'abord

Un IDE se lit des heures ; le sombre est la norme professionnelle
(VS Code, Android Studio, CodeAssist). Le clair suivra en backlog
avec les mêmes rôles — les jetons sont des rôles, pas des valeurs
câblées dans les vues.

| Rôle | Valeur | Usage |
|---|---|---|
| `fond` | `#0E1116` | fond d'application |
| `surface` | `#151920` | cartes, barres, tiroirs |
| `surfaceHaute` | `#1B212A` | menus, feuilles, survols |
| `puits` | `#0B0E12` | éditeur et console (le code s'enfonce) |
| `bord` | `#262D38` | bordures 1 dp |
| `texte` | `#E7ECF3` | texte principal |
| `texteSecondaire` | `#9AA6B5` | méta, légendes |
| `texteEstompe` | `64707F` (`#64707F`) | désactivé, sorties de build en cours |
| `accent` | `#82AAFF` | actions primaires, sélection, liens |
| `reussite` | `#3DDC84` | construire, builds réussis, l'exécuter |
| `alerte` | `#E5C07B` | avertissements honnêtes |
| `erreur` | `#E06C75` | diagnostics, suppressions |

Tuiles tonales par type de projet (conteneur à 12 % sur fond
`surface`) : Android `reussite`, console JVM `accent`, bibliothèque
`#C678DD` (violet), vide gris. Syntaxe de code dans l'éditeur et les
extraits : mot-clé `#C678DD`, type `#E5C07B`, fonction `#61AFEF`,
chaîne `#98C379`, nombre `#D19A66`, commentaire `7C8695` (`#7C8695`),
par défaut `#D6DAE3`.

### Typographie

Deux familles comme CodeAssist (`CaTypography`) : UI en sans système,
code en monospace — jamais l'un pour l'autre.

| Style | Taille/ligne | Emploi |
|---|---|---|
| légende | 12/16 | compteurs, méta de carte |
| note | 13/18 | second line, champs |
| corps | 15/22 | texte courant |
| sous-titre | 16/22 | noms de projet, lignes de liste |
| titre | 17/22 demi-gras | titres de barre |
| titre écran | 28/34 gras | titre d'accueil |
| code | 13,5/22 | éditeur, console, extraits |
| code compact | 12,5/20 | consoles denses |

### Forme, espace, mouvement

Rayons : cartes 16 dp, tuiles et boutons 12 dp, puces 999 dp.
Espace : grille de 4 dp (marges d'écran 20 dp, gouttières de liste
20 dp, tuile 46 dp). Mouvement : 180 ms standard, 240 ms entre
écrans ; entrées latérales directionnelles sur téléphone (axe X
partagé), fondu discret sur grand écran — la distinction
`ScreenTransition` de CodeAssist. Élévation en sombre : liseré
supérieur clair (1 dp, 5 % blanc) plus ombre douce ; jamais d'ombre
dure.

### Composants (le kit, dessiné une fois)

Carte de projet (tuile tonale + nom + une ligne de méta + ⋮), carte
de reprise, carte d'import (tonale, secondaire), carte de modèle
(galerie), puce, puce de catégorie, segment (2–3 états exclusifs),
bouton primaire/tonal/texte, champ (étiquette au-dessus), barre
d'onglets (indicateur 2 dp accent, point de modification), tiroir
poussé (360 dp max), dock de console (poignée repliable), FAB
construire (`reussite`), barre de phases, ligne de problème
cliquable, bannière de franchise (info/alerte), rangée de réglage
(valeur + switch/segment), pilule d'état d'index, pilule de build en
tâche de fond.

### Implémentation — vues du cadre uniquement, zéro AndroidX

| Option | Verdict | Raison |
|---|---|---|
| Vues du cadre, composants dessinés (retenue) | retenue | identité du dépôt : aucune dépendance UI tierce (NOTICE, licences), celeris est déjà une bibliothèque de vues dessinées ; le framework fournit canvas, dégradés, états |
| AndroidX + Material Components | écartée | ajoute une dépendance de marque et un style à surcharger ; le design Graphite serait contre les composants |
| Compose | écartée | changement de pile, pas de design ; deux runtimes dans l'APK avec celeris |

Le kit vit dans `app-android` : `jo/joide/app/android/ui/` (composants
dessinés, thème Graphite en constantes), `jo/joide/app/android/ecrans/`
(un paquet par écran). Chaque écran garde un **état d'écran pur**
(champs publics, testable JVM comme les 15 tests actuels
d'app-android) et une vue mince qui lit l'état — la doctrine « l'UI
reste mince » de P8 est reconduite ; aucun design ne déplace la
logique dans les vues. Le thème de l'application passe à
`Theme.Material.NoActionBar` (cadre) coloré par les constantes
Graphite ; le clair est un backlog qui réutilisera les mêmes rôles.

### Maquettes

`design/index.html` (preview principal : galerie des écrans + jetons)
et un HTML par écran — autonomes (aucune ressource externe), police
système, couleurs exactes des jetons ci-dessus. Les maquettes sont
contractuelles pour la structure (sections, états, franchise), pas
pour le pixel.

## Révision du 2026-11-16 (lot G)

Le plan ADR-É10 (accepté) avance
le clair au lot G : la palette claire, le mode nuit du
système et l'application du thème vivent dans l'ADR-048 ;
les rayons restent 16/12/999 (la maquette affiche 14 sur tuile et
bouton — écart pixel, É2 fait autorité).

## Conséquences

- Les ADR-É3 à É8 héritent des jetons et composants sans les
  redéfinir ; un changement de palette se fait ici, une fois.
- L'accessibilité : contrastes AA vérifiés sur les couples
  texte/`fond` et texte/`surface` (à contrôler à l'implémentation avec
  les tests d'écran) ; `texteEstompe` n'est jamais porteur d'information
  seul — il accompagne toujours une icône ou un libellé.
- Les valeurs de la palette dans les maquettes et le futur kit sont
  identiques par construction (les HTML reprennent les jetons
  textuellement).
