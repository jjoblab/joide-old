# ADR-044 — Mode de consommation des briques (P8)

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

§6.8 : JoIDE s'appuie sur les briques du propriétaire (celeris, code-lsp,
LayoutLib), jamais modifiées (règle 6 : tout écart passe par
`docs/integration/DEMANDES.md`). Les contrats réels sont consignés dans
`docs/integration/` après reverification sur les sources (constats du
prompt revérifiés — où ils étaient faux, le dépôt a raison).

## Décision

1. **Dépendances binaires épinglées par ref git du checkout** : les
   briques sont construites une fois depuis le checkout épinglé
   (celeris `f9001fc`, code-lsp `657d787`, LayoutLib `720e85d`) et
   publiées en Maven Local (procédure dans `docs/dev.md`). JoIDE
   consomme `jo.celeris:*:1.1.0`, `jo.codeide.lsp.*:1.15.0`,
   `com.github.jjoblab.LayoutLib:module-*:3.7.0`. Aucune brique dans le
   dépôt JoIDE, aucun build composite (l'épinglage par version publiée
   se vérifie ; la substitution composite ignore les versions, ce qui
   affaiblirait le contrat).
2. **code-lsp SANS `:host`** : l'AAR `jo.codeide.lsp.host` expose
   `jo.codeide.tooling.protocol.ClasspathResult` dans son API publique
   et dépend de `com.github.jjoblab.codeide-tooling:protocol` (licence
   non clarifiée — interdit §6.8 règle 2b) et de
   `com.github.jjoblab.code-editor:cel-lsp` (l'ancien éditeur). JoIDE
   consomme les moteurs et le socle (`:java`, `:kotlin`, `:xml`,
   `:model`, `:index`), qui n'ont aucune dépendance vers codeide-tooling.
   L'adaptateur JoIDE nourrit directement le contrat `ProjetLsp` v11 du
   socle `:model` (réécriture clean-room du protocole, embarquée) — la
   clarification de codeide-tooling devient inutile pour JoIDE
   (DEMANDES.md D-2 close).
3. **Le compagnon ecj-art (EPL-2.0) reste au classpath runtime du
   moteur Java uniquement** (jamais compilé contre) — la séparation de
   licences est celle de la brique, reportée dans le `NOTICE` de JoIDE.
4. **LayoutLib : livraison conditionnée à D-1** (LICENSE Apache-2.0 +
   NOTICICE à la racine — DEMANDES.md) : l'intégration est préparée et
   testable sur le checkout local ; la redistribution des artefacts
   (APK/AAB embarquant les AAR) reste bloquée tant que D-1 n'est pas
   satisfaite.
5. **Aucun type tiers ne fuit** : les briques vivent en
   `implementation` d'ide-integration ; l'API publique est
   `jo.joide.ide.*` exclusivement (ADR-010).

## Options

| Option | Verdict | Raison |
|---|---|---|
| GitHub Packages (joide.token read:packages) | écartée | authentification à jeton incertaine, reproductibilité faible hors CI |
| Build composite depuis les checkouts | écartée | la substitution ignore les versions publiées — l'épinglage se perd |
| Maven Local depuis checkout épinglé (retenue) | retenue | versions vérifiables, reproductible, aucune modification des briques |

## Conséquences

- `docs/dev.md` documente la publication (une fois par montée de
  version d'une brique ; les sommes de contrôle de celeris sont
  consignées par sa tâche `publicationChecksums`).
- Les incompatibilités futures entre briques et JoIDE se voient au
  premier build (version absente du Maven Local) — jamais en silence.
- `NOTICE` et `docs/third-party.md` consignent les artefacts embarqués
  et leurs licences.
