# ADR-012 — Politique de langue : reprise du script celeris, adapté

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

D3 : identifiants et messages d'exception en anglais ASCII, Javadoc, commentaires et documentation en français. celeris maintient déjà un contrôle de politique de langue (script Python sans dépendance, Apache-2.0) ; le §10 du prompt autorise sa reprise si elle reste légère.

## Décision

Le script scripts/check-language-policy est repris de celeris@f9001fc et adapté : quatre contrôles — identifiants non ASCII, messages d'exception anglais (contrôle lexical : ASCII + petit lexique français), Javadoc des classes publiques, typographie française des Markdown avec mode --fix. La parité values/values-fr (ADR-008 celeris) reste un point d'extension inactif jusqu'à P8. Les données de test (src/test/resources, y compris les fixtures Java volontairement fausses) sont exclues : ce sont des données. Provenance consignée dans NOTICE.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Reprise adaptée (retenue) | retenue | éprouvée, légère, même politique que celeris |
| Réécriture complète | écartée | coût sans bénéfice |
| Aucun contrôle | écartée | D3 sans garde-fou |

## Conséquences

Divergences futures d'avec celeris : chaque divergence sera un ADR. Prérequis python3 documenté dans docs/dev.md, comme dans celeris.
