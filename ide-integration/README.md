# ide-integration

Adaptateurs vers les briques existantes (éditeur, serveurs de langage,
aperçu) derrière des interfaces JoIDE — P8.

| | |
|---|---|
| Paquet | `jo.joide.ide` |
| Nature | Bibliothèque Android (ADR-045) |
| Dépendances JoIDE | core-model, build-daemon |
| Phase de remplissage | P8 |

## Invariants

- **Aucun type tiers ne fuit dans l'API publique** (ADR-044) : les
  briques vivent en `implementation`, l'API publique est
  `jo.joide.ide.*` exclusivement. La vue brute des instantanés
  (`LanguageModelSnapshot.raw()`) n'est pas compilable hors du module.
- **Jamais de mutation sous les pieds d'un lecteur** (§6.8 règle 3) :
  les instantanés sont immuables, remplacés par publication.
- **CIDX réutilisé, jamais réécrit** (§6.8.5) : l'index disque de
  code-lsp indexe l'union des classpaths du projet.
- Aucune modification des briques : tout écart passe par
  `docs/integration/DEMANDES.md`.

## Contenu (état du lot C)

- **`sync`** (lot B) — synchronisation du modèle de langage : le
  workspace JoIDE (core-model) et la résolution des externes (résolveur
  P4) deviennent un instantané « ProjetLsp » v11 immuable par le
  convertisseur de la brique ; empreintes, ordre canonique, avertissements
  — jamais rien en silence. `LanguageModelSync`, `ProjetLspAdapter`,
  `LanguageModelSnapshot` (vue JoIDE), `ExternalResolution`,
  `AndroidPlatform`, `ModuleDirectories`.
- **`langages`** (lot C) — les moteurs de langage derrière une façade
  JoIDE : `MoteursLangage` monte le moteur Java (contextes par module
  nourris à chaque sync, routeur fichier→module par racine la plus
  profonde, rejeu d'époque « zéro réponse périmée »), les sessions
  Kotlin (une session étanche PAR module) et la session XML (espace),
  plus l'index disque CIDX (union indexable des jars + plateformes,
  budget auto max(30 Mo, 512 Kio/entrée)). Requêtes en tirage :
  `diagnostiquer`, `survol`, `completer`, `definition` — l'hôte détient
  les tampons et passe le texte complet à chaque appel. Types publics :
  `DiagnosticIde`, `SurvolIde`, `PropositionCompletion`,
  `CibleNavigation`, `EtatIndex`.
- **`editeur`** (lot D) — l'éditeur celeris derrière la scène JoIDE :
  `SceneEditeur` monte la vue d'éditeur et la couche de popups
  (exposées comme `android.view.View` — aucun type de brique ne
  quitte le module), détecte le langage du nom de fichier (registre
  de la brique : analyseurs écrits Java/Kotlin/KTS/Groovy/XML/JSON +
  grammaires TextMate compilées pour les autres, fabrique
  core-syntax-grammars) ; `SessionLangageCeleris` (interne) implémente
  le contrat de session de l'éditeur AU-DESSUS des moteurs du lot C —
  complétion/survol/signature par futurs, diagnostics POUSSÉS après
  chaque édition (fusion des frappes, le dernier texte gagne), analyse
  hors fil UI ; `CoachJoide` (interne) : repli mots réservés + badges.
  La couverture protocole non servie en P8 (symboles, plis,
  formatage, renommage, correctifs, indices, jetons, références) rend
  ses vides honnêtes — backlog.
- **`apercu`** (lot E) — l'aperçu de layouts LayoutLib derrière la
  façade JoIDE : `ApercuLayouts` monte le service de rendu de la
  brique (arbre de vues VIVANT, mesuré et layouté, rendu sur le fil
  principal, débouncé 400 ms) et l'expose sous
  `android.view.View` uniquement ; `ConfigurationApercu`
  porte les ressources et le thème du projet (dossier `res/`,
  contenus des thèmes/styles/attrs lus du disque, dimensions cibles,
  mode nuit), `ConfigurationsApercu.depuisSnapshot` la constitue du
  modèle de langage (facette Android du lot B — le nom du thème
  n'est JAMAIS deviné : null, repli hôte de la brique) ;
  honnêteté du rendu (contrat de la brique) : XML en frappe =
  dernier rendu valide conservé, classe inconnue = MockView sans
  échec, ressource manquante = repli aux ressources natives.

## Ce qui n'y est pas (encore)

- Lot F : rien — le module sera complet.

## Mode de consommation des briques

Binaire, depuis le Maven Local des checkouts épinglés (ADR-044) :
celeris 1.1.0@f9001fc, code-lsp 1.15.0@657d787 (SANS l'AAR `:host`,
ADR-044), LayoutLib 3.7.0@720e85d. Le compagnon ecj patché ART
(EPL-2.0) reste au classpath runtime uniquement — jamais compilé
contre.
