package jo.joide.ide.apercu;

import java.util.List;
import java.util.TreeSet;

import jo.joide.deps.xml.Xml;
import jo.joide.deps.xml.XmlNode;
import jo.layoutlib.resources.ResourceResolverImpl;

/**
 * Les références de ressources DIRECTES d'un XML de mise en page que
 * le résolveur du projet ne résout pas (lot L, ADR-É7) : la brique
 * passe ces ressources au repli natif SANS le dire — la façade le
 * dit. Pur, testé JVM sur le VRAI résolveur de la brique (le pattern
 * du lot E) ; package-private : aucun type de brique ne quitte le
 * module (ADR-044).
 *
 * <p>Le XML est analysé par le parseur maison ({@code core-deps/xml},
 * ADR-028) : les références viennent des attributs de l'arbre, jamais
 * d'une regex sur le document (risque n° 11 de l'ADR-É9). Seules les
 * références des types que le résolveur sait interroger sont vérifiées
 * ({@code color}, {@code string}, {@code dimen}, {@code drawable},
 * {@code integer}, {@code bool}) ; les références natives
 * ({@code @android:…}), les identifiants ({@code @id/…},
 * {@code @+id/…}) et les attributs de thème ({@code ?attr/…}) ne
 * relèvent pas du résolveur — jamais de faux positif.</p>
 */
final class ReferencesManquantes {

    private ReferencesManquantes() {
    }

    /**
     * @param resolveur le résolveur du projet (jamais null)
     * @param xml       le XML de mise en page courant
     * @return les références non résolues, dédupliquées et triées
     */
    static List<String> de(ResourceResolverImpl resolveur, String xml) {
        if (xml == null) {
            return List.of();
        }
        Xml.Document document = Xml.parse(xml);
        if (document.root == null) {
            return List.of();
        }
        TreeSet<String> manquantes = new TreeSet<>();
        collecter(document.root, resolveur, manquantes);
        return List.copyOf(manquantes);
    }

    private static void collecter(XmlNode noeud,
                                  ResourceResolverImpl resolveur,
                                  TreeSet<String> manquantes) {
        for (XmlNode.Attr attribut : noeud.attributes()) {
            String valeur = attribut.value;
            if (valeur != null && valeur.length() > 1
                    && valeur.charAt(0) == '@'
                    && !valeur.startsWith("@android:")
                    && !valeur.startsWith("@+id/")
                    && !valeur.startsWith("@id/")
                    && valeur.indexOf(' ') < 0
                    && valeur.indexOf(',') < 0) {
                int barre = valeur.indexOf('/');
                if (barre > 1 && resoutPas(resolveur, valeur)) {
                    manquantes.add(valeur);
                }
            }
        }
        for (XmlNode enfant : noeud.children()) {
            collecter(enfant, resolveur, manquantes);
        }
    }

    private static boolean resoutPas(ResourceResolverImpl resolveur,
                                     String reference) {
        if (reference.startsWith("@color/")) {
            return resolveur.getColor(reference) == null;
        }
        if (reference.startsWith("@string/")) {
            return resolveur.getString(reference) == null;
        }
        if (reference.startsWith("@dimen/")
                || reference.startsWith("@dimension/")) {
            return resolveur.getDimension(reference) == null;
        }
        if (reference.startsWith("@drawable/")) {
            return resolveur.getDrawablePath(reference) == null;
        }
        if (reference.startsWith("@integer/")) {
            return resolveur.getInteger(reference) == null;
        }
        if (reference.startsWith("@bool/")) {
            return resolveur.getBoolean(reference) == null;
        }
        return false;
    }
}
