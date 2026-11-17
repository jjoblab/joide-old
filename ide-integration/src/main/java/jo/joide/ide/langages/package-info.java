/**
 * Moteurs de langage derrière interfaces JoIDE (lot C du câblage P8) :
 * les moteurs Java, Kotlin et XML de la brique code-lsp sont montés et
 * nourris par les instantanés du modèle de langage (lot B) — l'API
 * publique de ce paquet ne montre QUE des types JoIDE, jamais un type
 * de la brique (ADR-044).
 *
 * <p>Modèle de bout en bout : l'hôte synchronise
 * ({@link jo.joide.ide.langages.MoteursLangage#synchroniser}), ce qui
 * publie un nouvel instantané immuable PUIS re-nourrit les moteurs —
 * contextes par module du moteur Java, sessions Kotlin par module,
 * session XML de l'espace, index disque CIDX réutilisé (jamais d'index
 * JoIDE tant qu'aucune mesure ne montre que CIDX ne tient pas le
 * budget — §6.8.5). Les requêtes sont en tirage (pull) : l'hôte détient
 * les tampons et passe le texte complet à chaque appel.</p>
 */
package jo.joide.ide.langages;
