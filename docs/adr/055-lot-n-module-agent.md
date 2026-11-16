# ADR-055 — Lot N : le module `agent` dans le graphe — un noyau pur à SPI neutre

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot N (ADR-É10) livre le noyau de l'agent IA analysé en ADR-É9 :
un module PUR, pair de `core-tasks`, testable sans SDK Android. Les
lots O (outils, espace de travail) et P (UI, intégration app)
s'appuieront dessus sans le modifier. Cette ADR consigne la place du
module dans le graphe et le contrat de son modèle neutre.

## Décision

### 1. Le module et ses arêtes

`agent` entre dans le graphe entre `core-deps` et `build-jvm` :

```
agent -> core-platform core-deps
```

- `core-platform` : exécuteurs nommés (`WorkerPools`), `CancelToken`,
  `Clock`, `AtomicFiles` — la concurrence explicite d'ADR-016, jamais
  de fil créé à la main ;
- `core-deps` : le parseur JSON maison (ADR-028) et sa nouvelle
  écriture compacte `JsonEcrire` — ajoutée à côté du parseur parce
  qu'un deuxième sérialiseur ailleurs (le démon en a un privé) serait
  une duplication ; l'aller-retour lecture/écriture des `JsonValue`
  est garanti par construction ;
- JAMAIS Android, JAMAIS les briques : celeris/code-lsp/LayoutLib
  n'apparaîtront que dans l'implémentation du port (lot O, côté
  ide-integration). Le contrôle API 28 s'applique au module comme aux
  autres modules purs (le noyau tournera sur appareil au lot P) : ni
  `java.net.http` (absent du niveau 28 — c'est aussi pourquoi D-5
  tranche le transport), ni records ni sealed (rejetés par le scan
  jusqu'à validation sur appareil), ni `Files.readString`/
  `readAllBytes` de flux (niveau 33).

### 2. Le modèle neutre à signatures opaques

`PartieContenu` (quatre genres fermés : texte, raisonnement, appel
d'outil, résultat d'outil), `MessageAgent` (rôles utilisateur/
assistant — les résultats d'outils voyagent en message utilisateur,
chaque adaptateur les remappe à son dialecte), `RequeteLlm` (le
prompt coupé par volatilité : préfixe système stable + historique +
note de fin), `EvenementFlux` (deltas vivants, fin de message,
usage). Chaque partie RAISONNEMENT porte une SIGNATURE OPAQUE que le
fournisseur remplit et relit : Anthropic ses `signature_delta`,
OpenAI son `reasoning_content` tagué `openai:*` — jamais confondues,
jamais interprétées par le noyau ; l'aller-retour
conversation-fournisseur ne perd rien (leçon 2 de l'ADR-É9).

`RequeteLlm.empreinteStable()` sérialise la partie stable (préfixe
système + historique, hors la note volatile qui avance par
conception) : c'est la pièce du garde-fou « préfixe stable » — la
requête N+1 d'une boucle d'outils doit contenir la requête N octet
pour octet en préfixe ; une dérive silencieuse ne se voit que sur la
facture, le test échoue à sa place (leçon 3).

### 3. La surface publique du lot N

Le noyau publie : le modèle neutre, `BoucleAgent` (+ `Config`),
`EvenementAgent`/`EcouteurAgent`, `ErreursLlm` (taxonomie),
`Permissions`, `HistoriqueAgent`, `ReparationHistorique`,
`CompacteurHistorique`, `ExecuteurOutils` (+ `PortOutils`),
`SessionsAgent`, `SurfaceOutils`, `TransportHttp` (+ `TransportOkHttp`),
`FournisseurLlm` (+ les deux adaptateurs), `ParseurSse`. Les outils
v1 et le port `EspaceTravailAgent` sont le lot O.

## Conséquences

- `settings.gradle.kts` inclut le module ; `gradle/module-graph.txt`
  et le test du graphe (ADR-011) portent l'arête ; le test exige
  exactement les arêtes déclarées — l'arrivée du module se voit.
- 61 tests JVM hors ligne : trames SSE rejouées par le faux
  transport, modèle scripté jugeant l'état final (outils reçus,
  historique, usage sommé), garde-fous préfixe stable et budget de
  surface, réparation sur quatre scénarios dont la conversation
  cassée par une ancienne version, compaction à hystérésis,
  permissions au défaut sûr.
- Le `README.md` du module dit le périmètre et les règles (une
  dépendance tierce, zéro Android, zéro clé dans le code).
