package jo.joide.agent;

import jo.joide.deps.json.JsonValue;

/**
 * La description d'un outil proposée au modèle : nom, description,
 * schéma JSON des arguments. Le marqueur {@code mutant} suit l'outil
 * partout (permissions, exécution séquentielle) et les bornes de
 * résultat sont portées CÔTÉ OUTIL (ADR-É9, leçon 10) : un résultat
 * déjà borné part une fois dans le cache, le réécrire après coup
 * invaliderait les tours suivants.
 */
public final class DescOutil {

    /** Le nom de l'outil (identifiant stable, snake_case). */
    public final String nom;
    /** La description pour le modèle. */
    public final String description;
    /** Le schéma JSON des arguments (objet, propriétés fermées). */
    public final JsonValue schema;
    /** Vrai si l'outil modifie l'espace de travail. */
    public final boolean mutant;
    /** La limite de caractères du résultat, portée par l'outil. */
    public final int limiteResultat;

    /** Construit la description (le schéma peut être null : sans arguments). */
    public DescOutil(String nom, String description, JsonValue schema,
                     boolean mutant, int limiteResultat) {
        this.nom = nom;
        this.description = description;
        this.schema = schema == null ? jo.joide.deps.json.JsonValue
                .ofNull() : schema;
        this.mutant = mutant;
        this.limiteResultat = limiteResultat;
    }

    @Override
    public String toString() {
        return "DescOutil[" + nom + (mutant ? ", mutant" : "") + "]";
    }
}
