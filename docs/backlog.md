# Backlog (P0)

Entrées ouvertes — aucune n'est un TODO orphelin dans le code (§10) ;
chaque entrée sera fermée ou reportée explicitement dans un rapport de
phase.

- [ ] P1 : valider le sous-ensemble TOML figé (ADR-014/R2) quand
      core-model lit les projets synthétiques.
- [ ] P3 (ouvert) : compiler les sources Kotlin générés — le SPI
      `SourceGenerator` ordonne la génération avant les compilations,
      la validation sur un générateur réel reste à faire (validité
      constatée à la main en P0, R3). Le spike API Build Tools est
      TRANCHÉ (ADR-027) : incrémental maison retenu, révisable à P9.
- [ ] P3 (ouvert) : éditions différentielles dédiées `sealed` et
      `@JvmInline` (la liste scellée est déjà dans l'ABI, ADR-025) ;
      `const` et `inline` sont couverts.
- [ ] P3/P5 : vérifier le désucrage D8 de records/sealed avant tout
      usage dans le code exécuté sur Android (ADR-009/013).
- [ ] P2 : D8/D9 — benchmarker le hash 128 bits (D9) contre SHA-256 et
      trancher par ADR.
- [ ] P2 : ADR store alternatif journal append-only (D8, interface
      BuildStore).
- [ ] P6/P8 : appliquer le conditionnel Android (ADR-015) à l'arrivée
      du premier module Android, avec JOIDE_SKIP_ANDROID.
- [ ] P8 : réactiver la parité values/values-fr dans le script de
      langue (ADR-012) au premier module UI.
- [ ] Bench : surveiller le coût des tests TestKit ; si instabilité,
      bug à corriger, pas à relancer (§9 du prompt).
- [ ] Bench : valider la comparaison temporelle (regressionTest S/M) sur
      la machine de référence (D12) — le déterministe est déjà une porte
      exacte ; le temporel a produit un faux positif sur l'ordonnanceur
      partagé du développement (preuves au rapport P0).
- [ ] Bench : baselines L/XL à committer quand une machine de référence
      sera désignée (coût de génération documenté dans le rapport P0).


---

# Backlog (après P4)

Entrées ouvertes à l'issue de P4 :

- [ ] P5 : consommer `ResolvedArtifact.file` et `aarContents` dans le
      pipeline Android (dex des `classes.jar` + `libs/*.jar`) — la
      surface existe, le câblage arrive avec build-android.
- [ ] P4/P7 : authentification des dépôts privés (jetons, en-têtes) —
      le transport refuse le clair et n'envoie aucun identifiant ;
      aucune exigence du prompt ne la demande à P4.
- [ ] P4 : éviction du cache de dépendances (quota d'espace) — même
      travail que l'éviction du cache de sorties (ADR-024), à faire
      avec la gestion de l'espace du daemon (P6).
- [ ] P4 : classifieurs (`sources`, `javadoc`) résolus sur demande
      explicite — les entrées du lockfile existent (`g:a:v:c@ext`),
      aucune demande ne les produit aujourd'hui.
- [ ] P4 : unions de plages multiples (`[1,2),[3,4)`) — rejetées à
      l'analyse ; aucune occurrence dans les projets synthétiques.
- [ ] P4/P9 : benchmarker la relecture chaude (un hachage par artefact
      sur `verifyInCache`) sur un graphe de centaines de dépendances ;
      si le coût gêne, mémoïser par (taille, date) comme le fait le
      VFS (ADR-023 de celeris, même esprit que les signaux de P2).
- [ ] P4 : `HttpTransport` contre un miroir public réel (test hors
      harnais, à exécuter sur la machine de référence) — le harnais
      prouve la politique (clair, redirections, certificat rejeté),
      pas la latence du monde réel.
