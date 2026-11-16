package jo.joide.agent;

import java.util.List;

/**
 * Le budget de la surface d'outils (ADR-É9, économie) : 16 000
 * caractères de noms + descriptions + schémas — mesuré, annoncé. Le
 * chargement différé des outils PERDRAIT (un préfixe caché coûte
 * ~0,1× par requête contre un aller-retour entier dès qu'un outil
 * invisible manque) : la surface v1 reste entière, sous budget, et
 * le garde-fou du test l'exige.
 */
public final class SurfaceOutils {

    /** Le budget total de la surface (caractères). */
    public static final int BUDGET = 16_000;

    private SurfaceOutils() {
    }

    /**
     * Mesure la surface offerte au modèle.
     *
     * @param outils les descriptions
     * @return les caractères de noms + descriptions + schémas
     */
    public static int mesure(List<DescOutil> outils) {
        int total = 0;
        for (DescOutil outil : outils) {
            total += outil.nom.length();
            total += outil.description.length();
            if (outil.schema != null
                    && !outil.schema.isNull()) {
                total += jo.joide.deps.json.JsonEcrire
                        .valeur(outil.schema).length();
            }
        }
        return total;
    }

    /**
     * @param outils les descriptions
     * @return vrai si la surface tient dans le budget
     */
    public static boolean dansLeBudget(List<DescOutil> outils) {
        return mesure(outils) <= BUDGET;
    }
}
