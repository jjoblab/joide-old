# core-deps

Résolution Maven + Gradle Module Metadata, lockfile `joide.lock`,
checksums vérifiés, cache disque partagé adressé par contenu,
extraction AAR protégée zip-slip, transport HTTPS vérifié TLS.

| | |
|---|---|
| Paquet | `jo.joide.deps` |
| Nature | JVM pure |
| Dépendances JoIDE | core-platform |
| Phase de remplissage | P4 (faite) |

## Contenu

- **Coordonnées et versions** — `Coordinate` (forme Maven
  canonique, ordre), `Version` (ordre maison documenté, ADR-029),
  `VersionRange` (bornes incluses/exclues, pincement `[a]`),
  `VersionRequirement` (littéral, plage, `1.0+`, `+`,
  `latest.release`, `latest.integration`, strict réservé au lockfile),
  `DependencyRequest` avec `Exposure` (écho des portées du modèle
  sans en dépendre — les ponts traduisent).
- **Métadonnées** — `xml.Xml`/`XmlNode` et `json.Json`/`JsonValue`
  (parseurs maison, ADR-028) ; `pom.PomParser` : chaîne de parents
  suivie, propriétés interpolées (héritées puis propres), gestion de
  dépendances fusionnée avec importation de nomenclatures (`scope
  import` sur `type pom`), exclusions, portées ; `gmm.GmmParser` :
  variantes (attributs, fichiers avec checksums incorporés,
  dépendances, `available-at` suivi en trois sauts), version objet
  (`strictly`/`requires`/`prefers`) ; `repo.MavenMetadataParser` :
  versions publiées, `latest`/`release`, état d'instantané
  (horodatage, numéro, `snapshotVersions`).
- **Dépôts et transport** — `repo.Repository` (`Local` sur disque,
  `Remote` sur `Transport`), disposition Maven 2 dans `repo.Layout`
  (chemins d'artefacts, POM, `.module`, métadonnées, checksums,
  composition d'instantanés), `repo.HttpTransport` (ADR-031) :
  https seul, TLS vérifié jamais désactivé, redirections sans
  rétrogradation, plafonds, 404 = introuvable.
- **Cache** — `cache.DependencyCache` (ADR-030) : `files/xx/<sha256>`
  immuables, écriture atomique, relecture revérifiée, zone
  d'attente nettoyée, `aars/xx/<sha256>/`.
- **Lockfile** — `lock.Lockfile`/`LockfileEntry`/`LockfileEdge`
  (ADR-033) : sérialisation déterministe triée, lecture tolérante,
  `EdgeKind` (`api`, `runtime`, `compile` non transmise).
- **Résolveur** — `resolve.Resolver` : phase de couverture par
  lockfile (zéro requête, gel des instantanés et sélecteurs), point
  fixe des versions (« la plus récente gagne », strict s'impose),
  fermeture avec exclusions par sous-arbre, téléchargements
  parallèles plafonnés, vérification des checksums publiés
  (GMM incorporée, `.sha256`, `.sha1` — la première source fait
  foi, divergence = échec explicite), écriture du lockfile inchangé
  octet pour octet si le contenu est le même ; hors-ligne strict
  (`offlineStrict`) ; `AarExtractor` (ADR-032).
- **Empreintes** — `Checksums` : SHA-256/SHA-1 hexadécimaux minuscules
  (conversion maison, `HexFormat` hors niveau 28).

## Utilisation

```java
DependencyCache cache = new DependencyCache(Path.of(".cache/joide"));
Resolver resolver = new Resolver(new ResolverConfig.Builder()
        .repository(new Repository.Remote("central",
                "https://repo.example/maven2", new HttpTransport()))
        .repository(new Repository.Local("interne", Path.of("m2")))
        .cache(cache)
        .lockfile(Path.of("joide.lock"))
        .maxParallelDownloads(4)
        .build());

ResolutionResult result = resolver.resolve(List.of(
        new DependencyRequest("org.example", "lib",
                VersionRequirement.parse("[1.0,2.0)"), Exposure.API)));
for (ResolvedArtifact a : result.artifacts) {
    // a.file : octets au cache ; a.aarContents si AAR ;
    // a.edges : api / runtime / compile pour assembler les classpaths.
}
```

Avec un `joide.lock` valide et le cache chaud :
`result.lockfileFollowed` est vrai, `result.stats.requests` vaut
zéro — aucune requête, même pour un `-SNAPSHOT` (gel, ADR-033).

## Tests

`./gradlew :core-deps:test` — 72 cas : ordre des versions (20 cas
en série), plages, coordonnées ; XML et JSON (entités, CDATA,
échappements, tolérance, documents brisés) ; POM (parents,
propriétés héritées interpolées, nomenclatures, exclusions,
portées, packaging `aar`) ; GMM (variantes, `available-at`,
`strictly`, checksums incorporés) ; maven-metadata (versions,
instantanés) ; résolveur (fermeture par portées, conflits « la plus
récente gagne », strict, nomenclatures, exclusions par sous-arbre,
nomenclature en racine refusée, introuvable explicite) ;
`LockfileDeterminismTest` (deux résolutions indépendantes →
lockfiles identiques octet pour octet ; transport hostile → zéro
requête) ; `ChecksumDivergenceTest` (`.sha256` divergent = échec,
`.sha1` seul accepté, GMM fausse = échec, aucune source publiée =
accepté avec empreinte calculée) ; `SnapshotFreezeTest` (gel malgré
l'avancée du dépôt ; sans lockfile, la nouvelle forme est prise) ;
`OfflineStrictTest` ; `ParallelCapTest` (concurrence réelle ≤ plafond) ;
cache (idempotence, corruption retirée, écritures concurrentes) ;
extraction AAR (contenu, idempotence, sept formes de zip-slip
refusées) ; transport (clair refusé d'entrée, politique de
redirection, certificat auto-signé rejeté — TLS vérifié).
