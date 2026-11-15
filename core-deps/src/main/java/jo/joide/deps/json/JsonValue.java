package jo.joide.deps.json;

import java.util.ArrayList;
import java.util.List;

/**
 * Une valeur JSON : objet, tableau, chaîne, nombre, booléen ou
 * null. Les nombres sont conservés en texte d'origine (les tailles
 * et versions GMM n'ont pas besoin d'arithmétique). Immuable.
 */
public final class JsonValue {

    /** Le type de la valeur. */
    public enum Type {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }

    private final Type type;
    private final String text;          // STRING, NUMBER
    private final Boolean flag;         // BOOLEAN
    private final List<String> keys;    // OBJECT
    private final List<JsonValue> values; // OBJECT members, ARRAY items

    private JsonValue(Type type, String text, Boolean flag,
                      List<String> keys, List<JsonValue> values) {
        this.type = type;
        this.text = text;
        this.flag = flag;
        this.keys = keys;
        this.values = values;
    }

    /** @return une valeur null */
    public static JsonValue ofNull() {
        return new JsonValue(Type.NULL, null, null, null, null);
    }

    /** @param value booléen @return la valeur */
    public static JsonValue of(boolean value) {
        return new JsonValue(Type.BOOLEAN, null, value, null, null);
    }

    /** @param value texte @return la valeur */
    public static JsonValue of(String value) {
        return new JsonValue(Type.STRING, value, null, null, null);
    }

    static JsonValue ofNumber(String raw) {
        return new JsonValue(Type.NUMBER, raw, null, null, null);
    }

    static JsonValue object(List<String> keys, List<JsonValue> members) {
        return new JsonValue(Type.OBJECT, null, null, keys, members);
    }

    static JsonValue array(List<JsonValue> items) {
        return new JsonValue(Type.ARRAY, null, null, null, items);
    }

    /** @return le type */
    public Type type() {
        return type;
    }

    /** @return vrai si la valeur est null */
    public boolean isNull() {
        return type == Type.NULL;
    }

    /** @return la chaîne, ou {@code null} si pas une chaîne */
    public String asString() {
        return type == Type.STRING ? text : null;
    }

    /** @return le booléen, ou {@code null} si pas un booléen */
    public Boolean asBoolean() {
        return type == Type.BOOLEAN ? flag : null;
    }

    /** @return le nombre en texte d'origine, ou {@code null} */
    public String asNumber() {
        return type == Type.NUMBER ? text : null;
    }

    /**
     * @return la valeur entière du nombre, ou -1 si absent ou non
     * entier (usage : tailles de fichiers GMM)
     */
    public long asLong() {
        if (type != Type.NUMBER || text == null) {
            return -1L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(text);
                return (long) d;
            } catch (NumberFormatException e2) {
                return -1L;
            }
        }
    }

    /** @return les clés de l'objet dans l'ordre, ou liste vide */
    public List<String> keys() {
        return keys == null ? List.of() : keys;
    }

    /**
     * @param key clé cherchée
     * @return le membre d'un objet, ou une valeur null
     */
    public JsonValue member(String key) {
        if (type != Type.OBJECT) {
            return ofNull();
        }
        for (int k = 0; k < keys.size(); k++) {
            if (keys.get(k).equals(key)) {
                return values.get(k);
            }
        }
        return ofNull();
    }

    /** @return les éléments d'un tableau, ou liste vide */
    public List<JsonValue> items() {
        return values != null && type == Type.ARRAY ? values : List.of();
    }

    @Override
    public String toString() {
        switch (type) {
            case NULL:
                return "null";
            case STRING:
                return "\"" + text + "\"";
            case NUMBER:
                return text;
            case BOOLEAN:
                return flag.toString();
            case OBJECT:
                return "{...}";
            case ARRAY:
                return "[...]";
            default:
                return "?";
        }
    }
}
