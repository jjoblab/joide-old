package jo.joide.ide.apercu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jo.layoutlib.resources.ResourceResolverImpl;

/**
 * La détection des ressources manquantes du lot L (ADR-É7) sur le
 * VRAI résolveur de la brique : les références directes du XML que le
 * résolveur ne résout pas sont celles qui passent au repli natif —
 * la brique le fait silencieusement, la façade le DIT. Les alias
 * suivis ne sont jamais signalés, les références natives et les
 * identifiants ne relèvent pas du résolveur.
 */
final class ReferencesManquantesTest {

    @TempDir
    Path tmp;

    private Path res;
    private ResourceResolverImpl resolveur;

    @BeforeEach
    void preparer() throws IOException {
        res = Files.createDirectories(tmp.resolve("app/src/main/res"));
        Path values = Files.createDirectories(res.resolve("values"));
        Files.writeString(values.resolve("colors.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <color name="primaire">#FF6750A4</color>
                    <color name="lien">@color/primaire</color>
                </resources>
                """);
        Files.writeString(values.resolve("strings.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="continuer">Continuer</string>
                </resources>
                """);
        Files.writeString(values.resolve("dimens.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <dimen name="marge">16dp</dimen>
                </resources>
                """);
        Files.writeString(values.resolve("integers.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <integer name="max">10</integer>
                </resources>
                """);
        resolveur = new ResourceResolverImpl(res.toString());
    }

    @Test
    void referencesResoluesNeSontPasSignalees() {
        List<String> manquantes = ReferencesManquantes.de(resolveur, """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:padding="@dimen/marge">
                    <TextView android:text="@string/continuer"
                        android:textColor="@color/primaire" />
                    <ImageView android:src="@drawable/logo" />
                </LinearLayout>
                """);
        assertEquals(List.of("@drawable/logo"), manquantes,
                "seule la référence absente est signalée");
    }

    @Test
    void aliasSuiviNestJamaisUnFauxPositif() {
        List<String> manquantes = ReferencesManquantes.de(resolveur, """
                <Button xmlns:android="http://schemas.android.com/apk/res/android"
                    android:background="@color/lien" />
                """);
        assertTrue(manquantes.isEmpty(),
                "@color/lien suit l'alias vers @color/primaire : résolu");
    }

    @Test
    void referencesNativesEtIdentifiantsNeRelventPasDuResolveur() {
        List<String> manquantes = ReferencesManquantes.de(resolveur, """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/racine"
                    android:background="@android:color/white"
                    android:theme="?attr/monTheme">
                    <View android:id="@id/racine" />
                </LinearLayout>
                """);
        assertTrue(manquantes.isEmpty(),
                "@android:, @id/, @+id/ et ?attr/ ne sont pas jugés");
    }

    @Test
    void manquantesDedupliqueesEtTrieesTousTypes() {
        List<String> manquantes = ReferencesManquantes.de(resolveur, """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
                    <TextView android:text="@string/absente"
                        android:textColor="@color/absente"
                        android:textSize="@dimen/absente" />
                    <Button android:maxLines="@integer/absent" />
                    <TextView android:text="@string/absente" />
                </LinearLayout>
                """);
        assertEquals(List.of("@color/absente", "@dimen/absente",
                "@integer/absent", "@string/absente"), manquantes,
                "dédupliquées et triées — jamais deux fois la même");
    }

    @Test
    void xmlEnFrappeEtVideNeSignalentRien() {
        assertTrue(ReferencesManquantes.de(resolveur,
                "<LinearLayout").isEmpty(),
                "un XML en frappe n'a pas de racine analysable : rien");
        assertTrue(ReferencesManquantes.de(resolveur, "").isEmpty());
        assertTrue(ReferencesManquantes.de(resolveur, null).isEmpty());
    }

    @Test
    void attributsEtEnfantsSontParcourusEnProfondeur() {
        List<String> manquantes = ReferencesManquantes.de(resolveur, """
                <?xml version="1.0" encoding="utf-8"?>
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android">
                    <LinearLayout android:padding="@dimen/marge">
                        <LinearLayout>
                            <LinearLayout>
                                <TextView android:text="@string/perdue" />
                            </LinearLayout>
                        </LinearLayout>
                    </LinearLayout>
                </FrameLayout>
                """);
        assertEquals(List.of("@string/perdue"), manquantes,
                "la référence au quatrième niveau d'imbrication est"
                        + " trouvée — l'arbre est parcouru en profondeur");
    }
}
