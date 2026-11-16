# ADR-047 — CI des APK signés : GitHub Actions, clé CI unique, briques republiées par le workflow

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

P8 est livrée et attend la validation du propriétaire — sa première
exécution réelle sur appareil. Il faut donc produire des APK
installables sans environnement local complet (JDK 21, SDK 36/37.2,
trois briques publiées en Maven Local). L'exigence explicite du
propriétaire du 2026-11-16 : les APK du CI doivent porter une
signature STABLE — installer une nouvelle génération ne doit jamais
exiger la désinstallation complète de la précédente (perte de données,
perte des projets importés dans l'application).

Deux obstacles : le keystore de débogage d'un exécutant est régénéré
à chaque exécution (signature différente à chaque fois — exactement
le problème à éviter) ; et les trois briques (celeris, code-lsp,
LayoutLib) sont des dépôts privés non publiés sur JitPack (ADR-44 :
consommation binaire depuis Maven Local).

## Décision

1. **GitHub Actions** (workflow `.github/workflows/apk.yml`), le dépôt
   étant sur GitHub avec Actions déjà activé. Déclencheurs : manuel
   (`workflow_dispatch`) et push sur `main`. Permissions minimales :
   lecture du contenu, écriture des actions (nettoyage des artefacts)
   — rien d'autre.
2. **Deux APK par exécution, non rétrécis** : debug (logcat et
   diagnostic — le premier test du propriétaire en aura besoin) et
   release (fidélité production), tous deux sans R8 (rétrécissement
   reporté au backlog P8 : les règles de conservation des
   ServiceLoader des moteurs exigent une passe dédiée).
3. **Une clé CI unique** (PKCS12, RSA 2048, validité 10 000 jours,
   alias `joide-ci`) signe les DEUX types : basculer entre debug et
   release conserve les données de l'application. La clé vit dans les
   secrets chiffrés du dépôt (`JOIDE_CI_KEYSTORE_B64`,
   `JOIDE_CI_KEYSTORE_PASSWORD`, `JOIDE_CI_KEY_ALIAS`), jamais dans le
   dépôt (convention « aucun secret, aucun jeton, aucun keystore dans
   le dépôt ») ; une copie de secours est détenue par le propriétaire
   hors du dépôt. Sa perte imposerait un jour la désinstallation
   complète sur chaque appareil : dès le premier APK installé, la clé
   est l'identité de l'application (et une candidate naturelle pour
   la clé de production future).
4. **Activation par l'environnement uniquement** : la configuration de
   signature ne s'applique que si les trois variables `JOIDE_CI_*`
   sont posées ; sans elles, comportement local inchangé (debug sur
   keystore de débogage local, release non signée) — le critère P0
   sans SDK et l'étalon incrémental équivalent au propre restent
   intacts.
5. **Briques republiées par le workflow** : clonage des refs épinglées
   (celeris `f9001fc`, code-lsp `657d787`, LayoutLib `720e85d`),
   publication en Maven Local exactement selon docs/dev.md (sans
   `:host` pour code-lsp), résultat mis en cache
   (`~/.m2/repository`, clé de cache = les trois refs) : les
   exécutions suivantes sautent la publication. Monter une version
   d'une brique change la ref dans le workflow ET dans
   `gradle/libs.versions.toml` — le cache s'invalide et republie.
6. **Accès aux dépôts privés** : le `GITHUB_TOKEN` du workflow
   n'atteint que joide ; un PAT du propriétaire (secret
   `JOIDE_BRIQUES_PAT`) clone les trois briques. Recommandation à
   terme : le remplacer par un PAT fin limité aux trois dépôts
   (lecture du contenu seulement, expiration courte).
7. **Nettoyage des artefacts précédents** avant chaque dépôt : deux
   APK représentent environ 165 Mo et le stockage des artefacts d'un
   dépôt privé est compté — seule la dernière génération reste
   disponible, rétention de 14 jours en filet de sécurité. Les
   exécutions consomment le quota de minutes du dépôt privé
   (environ 20 à 40 minutes à froid, 8 à 12 en cache).
8. **D-3 inchangée** : le dépôt privé rend les artefacts accessibles
   au seul propriétaire — c'est un test, pas une distribution ; la
   confirmation des binaires JetBrains (java-syntax) reste exigée
   avant TOUTE diffusion hors du dépôt privé (rapport P8, section 7).

## Conséquences

- Le propriétaire teste l'application sans construire localement :
  onglet Actions, dernière exécution, artefact « joide-apk-… ».
- La clé CI devient une pièce critique à sauvegarder par le
  propriétaire (copie hors dépôt, hors machine).
- Les exécutions dépendent de la disponibilité des refs épinglées et
  des secrets ; toute rotation se fait dans les secrets, sans
  modification du dépôt.
