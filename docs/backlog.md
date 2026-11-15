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
