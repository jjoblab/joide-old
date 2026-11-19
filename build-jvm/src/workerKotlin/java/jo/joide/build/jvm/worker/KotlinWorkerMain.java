package jo.joide.build.jvm.worker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;

/**
 * Ouvrier du compilateur Kotlin en machine virtuelle FORKÉE (lot VII,
 * la manière éprouvée {@code ForkedKotlinCompiler}) : l'APK allégé
 * ne porte plus les 43 Mo de dex du compilateur — il vit dans un pack
 * de dex différé, chargé par une VM non-zygote dont le tas dépasse le
 * plafond {@code largeHeap} de l'application.
 *
 * <p>L'ouvrier est AUTO-CONTENU : aucune classe jo.joide.* — son
 * chargeur ne porte que le pack (compilateur + stdlib + métadonnées).
 * Il tourne identiquement sur ART ({@code dalvikvm64}) et sur JVM de
 * bureau (les essais bout-en-bout du protocole).</p>
 *
 * <p>Protocole ligne (UTF-8), la VM est RÉUTILISÉE entre les lots —
 * un démarrage de compilateur ne se paie qu'une fois :
 * <pre>
 *   entrée  : ligne 1 = nombre d'arguments, puis un argument par ligne
 *   sortie  : ligne 1 = OK | ERR
 *             ligne 2 = nombre de diagnostics
 *             puis un diagnostic par ligne :
 *             SEVERITE \t code \t chemin:ligne:colonne \t message-échappé
 * </pre>
 * Le message échappe l'antislash, la tabulation et les sauts de ligne
 * ({@code \\} {@code \t} {@code \n} {@code \r}) : une ligne transport
 * toujours QUATRE champs. La fermeture de l'entrée termine l'ouvrier
 * (le parent meurt, l'ouvrier meurt).</p>
 */
public final class KotlinWorkerMain {

    private KotlinWorkerMain() {
    }

    /**
     * @param argv inutilisé (tout passe par l'entrée standard)
     * @throws Exception si la boucle se rompt — le parent le lit comme
     *                   une mort de l'ouvrier
     */
    public static void main(String[] argv) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(
                System.in, StandardCharsets.UTF_8));
        Writer out = new BufferedWriter(new OutputStreamWriter(
                System.out, StandardCharsets.UTF_8));
        String ligne;
        while ((ligne = in.readLine()) != null) {
            int nombre;
            try {
                nombre = Integer.parseInt(ligne.trim());
            } catch (NumberFormatException e) {
                // flux corrompu : rien de récupérable, mourir net
                return;
            }
            String[] args = new String[nombre];
            for (int i = 0; i < nombre; i++) {
                args[i] = in.readLine();
                if (args[i] == null) {
                    return;
                }
            }
            compiler(args, out);
            out.flush();
        }
    }

    /**
     * @param args arguments du compilateur (construits côté parent par
     *             KotlinArgs — les mêmes que le pilote embarqué)
     * @param out  la sortie protocolaire
     * @throws IOException si l'écriture échoue (parent mort)
     */
    static void compiler(String[] args, Writer out) throws IOException {
        List<String> diagnostiques = new ArrayList<>();
        MessageCollector collecteur = new MessageCollector() {
            @Override
            public void clear() {
                // rien : les messages partent au fil de l'eau
            }

            @Override
            public void report(CompilerMessageSeverity severite,
                               String message,
                               CompilerMessageSourceLocation endroit) {
                if (CompilerMessageSeverity.ERROR.equals(severite)
                        || CompilerMessageSeverity.EXCEPTION.equals(
                        severite)) {
                    diagnostiques.add(ligne("ERROR", "kotlin.error",
                            message, endroit));
                } else if (CompilerMessageSeverity.STRONG_WARNING.equals(
                        severite) || CompilerMessageSeverity.WARNING.equals(
                        severite)) {
                    diagnostiques.add(ligne("WARNING", "kotlin.warning",
                            message, endroit));
                }
            }

            @Override
            public boolean hasErrors() {
                return false;
            }
        };
        K2JVMCompiler compilateur = new K2JVMCompiler();
        K2JVMCompilerArguments analyses = compilateur.createArguments();
        compilateur.parseArguments(args, analyses);
        ExitCode code = compilateur.exec(collecteur, Services.EMPTY,
                analyses);
        out.write(ExitCode.OK.equals(code) ? "OK\n" : "ERR\n");
        out.write(Integer.toString(diagnostiques.size()));
        out.write('\n');
        for (String diagnostique : diagnostiques) {
            out.write(diagnostique);
            out.write('\n');
        }
    }

    /** Une ligne de diagnostic : quatre champs séparés par tabulation. */
    private static String ligne(String severite, String code,
                                String message,
                                CompilerMessageSourceLocation endroit) {
        String place = "";
        if (endroit != null && endroit.getLine() > 0) {
            place = endroit.getPath() + ":" + endroit.getLine() + ":"
                    + Math.max(endroit.getColumn(), 1);
        }
        return severite + "\t" + code + "\t" + place + "\t"
                + echapper(message);
    }

    /** Échappe l'antislash, la tabulation et les sauts de ligne. */
    static String echapper(String texte) {
        if (texte == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(texte.length() + 16);
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
