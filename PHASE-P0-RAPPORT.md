# PHASE P0 — RAPPORT

| | |
|---|---|
| Statut | Livré — **arrêt pour validation du propriétaire** (§11.4) |
| Date | 2026-11-15 |
| Machine d'exécution | Sandbox Linux (conteneur partagé) : 2 cœurs, 4 Go de RAM, stockage overlay — **inférieure à l'appareil de référence D12** (8 cœurs, 6 Go) ; conséquences sur les mesures temporelles au §4 |
| Outils | Temurin JDK 21.0.13+9 (javac), Gradle 9.8.2 (wrapper, somme de contrôle épinglée), Python 3.12, platform-28_r06 (android.jar sha256 `d20853d289a5161d9cf1c1268d7923076487d4fa68c970b0733e2d5a0ccfc3c5`) |
| Dépôt | `jjoblab/joide` (privé, branche `main`), 116 fichiers versionnés, 3 197 lignes Java, 340 lignes de conventions Gradle, 541 lignes de documentation |
| Journal clean-room | **Vide — aucune consultation de CodeAssist** (§4.1). Le travail repose sur le prompt, le rapport d'analyse joint et la documentation publique des outils. |

---

## 1. Ce qui est fait

- **Squelette des 14 modules** (§5.1) avec règle de dépendances appliquée à
  chaque build et testée en échec volontaire (TestKit branché au vrai
  build-logic).
- **Contrôle de la politique de langue** (script adapté de celeris, Apache-2.0,
  ADR-012) : identifiants ASCII, messages d'exception anglais, Javadoc des
  classes publiques, typographie française. Câblé dans `check`.
- **Contrôle de compatibilité API 28** sans SDK Android (ADR-013) : 63 020
  signatures effectives (fermeture d'héritage incluse) générées
  mécaniquement depuis l'android.jar de platform-28, scanner de pool de
  constantes en build-logic, appliqué aux 12 modules produit.
- **tools-bench** : générateur déterministe des tiers S/M/L/XL, harnais de
  mesure (temps mur, temps CPU, pic de tas, hash d'arbre Merkle), baselines
  S/M commitées, tâches `bench`/`regressionTest*`/`ciLocal`.
- **16 ADR** (001–010 reprises du rapport, 011–016 nouvelles), documentation
  complète (`toolchain`, `third-party`, `dev`, `backlog`, `JOURNAL`
  clean-room), `NOTICE`, `CHANGES.md`.

## 2. Critères d'acceptation P0, un par un

| # | Critère (§7 P0) | Statut | Preuve (commande + sortie) |
|---|---|---|---|
| 1 | Génération reproductible (mêmes octets à graine égale) | ✅ | `ReproducibilityTest` (tools-bench) : S généré 2× dans 2 répertoires → racines Merkle **identiques** ; idem M. En outre `BenchMain` revérifie à chaque exécution que ses 3 runs produisent la même racine (échec sinon) : jamais déclenché en ~20 invocations. Hash stables de bout en session : S `b75757f3aece…`, M `a765434928db…`, L `442470af208c…`, XL `9dbcae776a11…` |
| 2 | La CI locale exécute tests + bench S/M | ✅ | `./gradlew ciLocal` → **BUILD SUCCESSFUL**, 44 tâches : tous les tests (12, 0 échec — détail au §3), bench S et M mesurés avec porte de déterminisme intégrée |
| 3 | Règle de dépendances testée en échec volontaire | ✅ | `DependencyRuleKitTest` : mini-projet Gradle (TestKit, **vrai** build-logic inclus) déclare l'arête remontante `a -> :b` interdite par son graphe → la configuration échoue avec « dependency rule violation(s) in module 'a' … a -> :b (configuration 'implementation') is not allowed ». Le contrôle tourne en plus à **chaque** build de chaque module (plugin build-logic) ; les données du graphe sont validées par `ModuleGraphFileTest` (exactitude §5.1, acyclicité, ordre descendant) |
| 4 | Contrôle API 28 échoue sur violation volontaire | ✅ | `Api28KitTest` : module de fixture utilisant `java.util.HexFormat` (compilable avec --release 17, **absent** de l'API 28) → `:bad:checkApi28` échoue avec « API 28 check failed … reference outside the Android 28 API: M:java/util/HexFormat.of… » ; le module conforme passe. Le scan tourne en continu sur les 12 modules produit à chaque `check` |
| 5 | Politique de langue (adoptée, ADR-012) échoue sur violation volontaire | ✅ | `LanguagePolicyScriptTest` : la fixture de violations (identifiant accentué `largeurRéelle`, message d'exception « fichier introuvable », classe publique sans Javadoc, espace sécable en Markdown) fait échouer le script (code 1, chaque catégorie signalée) ; la fixture conforme passe (code 0). Sur le dépôt entier : `politique de langue : 0 problème(s)` |
| 6 | `./gradlew check` vert sur les modules purs **sans SDK Android** | ✅ | `ANDROID_HOME` et `ANDROID_SDK_ROOT` **non définis** pendant toute la session → `./gradlew check` **BUILD SUCCESSFUL** (42 tâches) |

Détail des tests (JUnit, sortie des rapports XML) :

| Test | Module | Tests | Échecs | Temps |
|---|---|---|---|---|
| ReproducibilityTest | tools-bench | 3 | 0 | 3,7 s |
| ModuleGraphPlannerTest | tools-bench | 3 | 0 | 0,5 s |
| LanguagePolicyScriptTest | tools-test | 2 | 0 | 0,1 s |
| ModuleGraphFileTest | tools-test | 2 | 0 | 0,04 s |
| DependencyRuleKitTest (TestKit) | tools-test | 1 | 0 | 4,6 s |
| Api28KitTest (TestKit) | tools-test | 1 | 0 | 22,8 s |
| **Total** | | **12** | **0** | ~32 s |

Preuve complémentaire (non exigée, apportée) : **le Java généré compile
réellement** — `javac --release 17` sur la totalité des sources Java des
projets synthétiques : S (255 fichiers), M (2 550), L (15 200) → code
retour 0 dans les trois cas.

## 3. Mesures de performance (génération + harnais, machine 2 cœurs)

Les cibles du §8 concernent le moteur (P2+) : P0 mesure le **générateur et
le harnais**. Machine inférieure à D12 ; les valeurs temporelles sont des
ordres de grandeur, les valeurs déterministes sont exactes partout.

| Tier | Fichiers | Modules | Octets | Racine Merkle (12 premiers) | Temps mur moy. | Temps CPU moy. | Pic de tas |
|---|---|---|---|---|---|---|---|
| S | 500 | 5 | 1 128 853 | `b75757f3aece` | 103,7 ms | 140,0 ms | 43,0 Mo |
| M | 5 000 | 50 | 12 709 935 | `a765434928db` | 914,0 ms | 1 133,3 ms | 161,0 Mo |
| L | 30 000 | 200 | 77 221 972 | `442470af208c` | 7 328 ms | 7 696,7 ms | 175,7 Mo |
| XL | 100 000 | 500 | 260 481 340 | `9dbcae776a11` | 22 267 ms | 22 703,3 ms | 252,8 Mo |

Baselines commitées : `tools-bench/baselines/s.toml` et `m.toml`
(`joide.baseline/1`, sans horodatage). `regressionTest*` compare l'exact
(hash, comptages, octets — échec à toute divergence) et le temporel
(temps CPU, ±10 %).

## 4. Écarts par rapport au prompt (et pourquoi)

1. **Kotlin généré non compilé en P0** (risque R3 du plan, assumé) : kotlinc
   est hors périmètre P0. La validité est assurée par gabarits à grammaire
   restreinte et par échantillonnage manuel ; la compilation arrive en P3
   (le test différentiel la rendra obligatoire).
2. **Scan API 28 sur le source set principal uniquement** : D4 vise le code
   « exécuté sur Android » ; les tests unitaires tournent sur JVM de bureau.
   Précision par rapport au plan (qui écrivait « main + test »).
3. **Exemption de `tools-bench`/`tools-test`** du scan API 28 (ADR-013
   ajusté) : modules « test-only » (§5.1 du prompt), jamais exécutés sur
   Android ; l'exemption (visible et commentée dans la convention) autorise
   le harnais à mesurer le temps CPU via les API de la JVM de bureau.
4. **Métrique temporelle de régression = temps CPU** (ADR-014), temps mur
   informatif : sur la machine de développement partagée, le temps mur varie
   du simple au triple (107 ms → 504 ms pour un travail identique, hash
   identiques) selon les vagues de fréquence/charge. Une boucle de
   calibration a été expérimentée puis **écartée** (ratio non convergent :
   32-66 selon les fenêtres). Le mécanisme de comparaison est néanmoins
   **prouvé fonctionnel** : `regressionTest[M] : OK (cpu 1486,7 vs
   baseline 1486,7)` en fenêtre calme ; `regressionTest[S]` a produit un
   faux positif temporel (déterministe exact partout, seule la valeur
   temporelle dérivait). **Action propriétaire** : exécuter
   `regressionTestS/M` sur la machine de référence D12 et y régénérer les
   baselines (`docs/dev.md`).
5. **Hygiène de mesure découverte** : `/tmp` peut résider sur une couche de
   stockage distincte du répertoire projet (mesure non comparable) — le
   répertoire de travail de la comparaison vit désormais sur le même
   système de fichiers que la capture (ADR-014).
6. **Baselines L/XL mesurées mais non commitées** (coût de génération sur
   machine partagée ; à committer depuis D12 — backlog).
7. **Incident d'environnement** : bridage 429 intermittent de Maven Central
   pendant le développement (phénomène déjà documenté par celeris le
   2026-11-08) ; contourné **temporairement** en développement par un miroir
   — **aucune trace dans le dépôt livré** (settings vérifiés propres).
8. **JUnit 5.14.4** (dernière 5.x stable, vérifiée sur Maven Central le
   2026-11-15) conformément au prompt §10 — pas JUnit 6 (question Q2,
   défaut appliqué).

Aucune exigence du prompt n'a été contournée en silence ; les ajustements
ci-dessus sont tous consignés en ADR ou au présent rapport (§11.6).

## 5. Dette et risques ouverts

| # | Risque | État |
|---|---|---|
| R1 | Déterminisme | **Couvert** : porte exacte à chaque bench + tests dédiés ; jamais pris en défaut |
| R2 | Sous-ensemble TOML figé (joide.workspace/1, joide.module/1) | Ouvert — validation en P1 quand core-model lit les projets synthétiques |
| R3 | Kotlin généré non compilé | Ouvert — P3 (test différentiel) |
| R4 | Fraîcheur de la liste API 28 | Couvert pour P0 (platform-28_r06, somme de contrôle consignée, script de régénération) ; à rafraîchir si le niveau visé change |
| R5 | Coût du bench dans `check` | Absorbé : bench S/M vivent dans `ciLocal`, pas dans `check` (~30 s au total sur 2 cœurs) |
| R6 | TestKit | Stable (2 tests, ~27 s au total, jamais flaky sur ~10 exécutions) |
| R7 | Temps CPU indisponible sur certaines JVM | Repli documenté sur temps mur + 50 ms (BaselineCompare) |
| Nouveau | Faux positif temporel de `regressionTest` sur ordonnanceur partagé | Documenté (§4.4) ; validation sur D12 requise |
| Nouveau | Démon Kotlin du build-logic consomme ~1,3 Go en arrière-plan | Constat d'environnement (développement) ; sans effet sur le dépôt livré |

## 6. ADR ajoutés

001 moteur natif · 002 TOML déclaratif · 003 infrastructure incrémentale ·
004 ABI + graphe classe-classe · 005 daemon :build · 006 index segmenté
(Proposed) · 007 dépendances vérifiées · 008 Apache-2.0 clean-room ·
009 Java 17 / minSdk 28 · 010 intégration des briques (Proposed) ·
011 règle de dépendances et squelette · 012 politique de langue ·
013 contrôle API 28 sans SDK · 014 générateur et harnais · 015 chaîne de
build P0 · 016 modèle de concurrence. Les 001-010 reprennent les décisions
du rapport d'analyse ; 011-016 documentent les choix propres à P0, y compris
les ajustements d'exécution du §4.

## 7. Questions pour le propriétaire

1. **P0 vous convient-elle ?** (critères §2, écarts §4) — le « go » pour P1
   (modèle, VFS, store) débloque la phase suivante (§7 P1).
2. **Q1 (D12)** : confirmer la RAM minimale visée (4 Go supposés) et la
   machine de référence pour `regressionTest` + baselines L/XL.
3. **Validation temporelle** : exécuter `./gradlew
   :tools-bench:regressionTestS :tools-bench:regressionTestM` sur D12 puis
   régénérer les baselines (`docs/dev.md`) — le déterministe doit rester
   vert partout, seul le temporel dépend de la machine.
4. Q2-Q7 du plan : défauts appliqués faute de réponse (JUnit 5, script
   celeris adapté, baselines bloquantes P2+, format natif seul,
   `joide`/`jo.joide`, arbitrage bench) — dites si l'un doit être revu.

## 8. Reproduction

```bash
export JAVA_HOME=<jdk-21>        # javac exigé (docs/dev.md)
./gradlew check                  # critère 6 (sans ANDROID_HOME)
./gradlew ciLocal                # critères 2 (tests + bench S/M)
./gradlew :tools-bench:bench -Ptier=XL   # mesure XL (~25 s/run sur 2 cœurs)
python3 scripts/generate_api28_signatures.py <android.jar> out.gz  # signatures
```

Archive livrée : `joide-p0.zip` (fichiers complets, arborescence prête à
copier, `CHANGES.md` à l'intérieur). Dépôt poussé : `jjoblab/joide` (privé,
`main`, 8 commits).
