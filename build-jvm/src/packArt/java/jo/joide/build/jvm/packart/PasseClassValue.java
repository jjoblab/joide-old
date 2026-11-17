package jo.joide.build.jvm.packart;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Relocalise TOUTE référence à {@code java.lang.ClassValue} du pack
 * vers le shim ART {@code ClassValueCompat}.
 *
 * <p>{@code java.lang.ClassValue} (un type JDK-7) est ABSENT d'ART ;
 * ART résout les références de classe à EAGER au chargement — une
 * classe qui le touche (super-classe, type de champ, {@code new},
 * {@code invokevirtual ClassValue.get}) jette {@code
 * NoClassDefFoundError} dès son chargement. Le scan du jar épinglé
 * trouve SEPT utilisateurs : cinq classes IntelliJ relocalisées
 * ({@code MethodHandleCache} — le cache du MessageBus, {@code
 * PropertyCollector}, {@code XmlSerializerPropertyCollectorList
 * ClassValue}), {@code kotlin.reflect.jvm.internal.Computable
 * ClassValue} (kotlin-reflect est sur le classpath de l'ouvrière)
 * et {@code kotlinx.coroutines.internal.ClassValueCtorCache$cache$1}
 * (coroutines, employées par le compilateur K2).</p>
 *
 * <p>Détection LARGE : les octets bruts citent-ils {@code
 * java/lang/ClassValue} ? Un jar qui gagne un utilisateur se couvre
 * seul ; une fausse positive (une chaîne littérale) reste
 * inoffensive — le {@code ClassRemapper} ne déplace que les
 * références de TYPE, uniformément (super-classe, champs,
 * signatures, descripteurs, instructions, frames). La cible
 * {@code java.*} ne peut pas être livrée sous son propre nom sur
 * ART ; le shim rejoue {@code get}/{@code computeValue}/{@code
 * remove} avec une mémoïsation {@code ConcurrentHashMap}.</p>
 */
public final class PasseClassValue implements Passe {

    static final String TYPE = "java/lang/ClassValue";
    static final String SHIM
            = "jo/joide/build/jvm/artcompat/ClassValueCompat";

    @Override
    public String nom() {
        return "class-value-shim";
    }

    @Override
    public boolean cible(String fqn, byte[] octets) {
        // jamais le shim lui-même (il ne référence pas le type de
        // toute façon)
        return PatcheurJar.contient(octets, TYPE);
    }

    @Override
    public ClassVisitor visiteur(String fqn, ClassVisitor suivant) {
        return new ClassRemapper(suivant, new Remapper() {
            @Override
            public String map(String interne) {
                return TYPE.equals(interne) ? SHIM : interne;
            }
        });
    }
}
