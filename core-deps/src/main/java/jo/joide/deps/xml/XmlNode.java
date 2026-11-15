package jo.joide.deps.xml;

import java.util.ArrayList;
import java.util.List;

/**
 * Un élément XML analysé : nom, attributs, texte direct, enfants.
 * Les sections CDATA et le texte ordinaire sont fusionnés dans le
 * texte ; les commentaires et les instructions de traitement sont
 * ignorés. Immuable.
 */
public final class XmlNode {

    private final String name;
    private final List<Attr> attributes;
    private final String text;
    private final List<XmlNode> children;

    XmlNode(String name, List<Attr> attributes, String text,
            List<XmlNode> children) {
        this.name = name;
        this.attributes = List.copyOf(attributes);
        this.text = text;
        this.children = List.copyOf(children);
    }

    /** Un attribut : nom et valeur. */
    public static final class Attr {
        public final String name;
        public final String value;

        Attr(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /** @return le nom de l'élément, préfixe d'espace compris */
    public String name() {
        return name;
    }

    /** @return les attributs dans l'ordre du document */
    public List<Attr> attributes() {
        return attributes;
    }

    /** @return la valeur d'un attribut, ou {@code null} */
    public String attr(String attrName) {
        for (Attr a : attributes) {
            if (a.name.equals(attrName)) {
                return a.value;
            }
        }
        return null;
    }

    /** @return le texte direct, éventuellement vide */
    public String text() {
        return text;
    }

    /** @return les enfants */
    public List<XmlNode> children() {
        return children;
    }

    /**
     * @param childName nom cherché
     * @return le premier enfant de ce nom, ou {@code null}
     */
    public XmlNode child(String childName) {
        for (XmlNode c : children) {
            if (c.name.equals(childName)) {
                return c;
            }
        }
        return null;
    }

    /**
     * @param childName nom cherché
     * @return tous les enfants de ce nom
     */
    public List<XmlNode> children(String childName) {
        List<XmlNode> out = new ArrayList<>();
        for (XmlNode c : children) {
            if (c.name.equals(childName)) {
                out.add(c);
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return name + "[" + children.size() + "]";
    }
}
