package jo.joide.build.android.tasks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jo.joide.build.android.sign.ApkSignerService;
import jo.joide.platform.Problem;
import jo.joide.platform.RootedPath;
import jo.joide.tasks.ResourceClaim;
import jo.joide.tasks.TaskContext;
import jo.joide.tasks.TaskDefinition;
import jo.joide.tasks.TaskExecution;
import jo.joide.tasks.TaskInputs;
import jo.joide.tasks.TaskOutputs;

/**
 * Tâche de signature d'APK : signe l'APK packagé avec les schémas
 * demandés par la variante, puis vérifie la signature produite — un
 * APK signé non vérifiable est un échec, jamais un avertissement.
 */
public final class SignTask implements TaskDefinition {

    private final String moduleName;
    private final String variantName;
    private final RootedPath inputApk;
    private final RootedPath outputApk;
    private final Path keystore;
    private final String alias;
    private final char[] keystorePassword;
    private final char[] keyPassword;
    private final List<String> schemes;
    private final int minSdk;
    private final ApkSignerService signer;

    /**
     * @param moduleName        nom du module
     * @param variantName       nom de la variante
     * @param inputApk          APK non signé
     * @param outputApk         APK signé
     * @param keystore          fichier de clés
     * @param alias             alias de clé
     * @param keystorePassword  mot de passe du fichier de clés
     * @param keyPassword       mot de passe de la clé
     * @param schemes           schémas activés (« v1 », « v2 », « v3 »)
     * @param minSdk            niveau d'API minimum
     * @param signer            service de signature
     */
    public SignTask(String moduleName, String variantName,
                    RootedPath inputApk, RootedPath outputApk, Path keystore,
                    String alias, char[] keystorePassword, char[] keyPassword,
                    List<String> schemes, int minSdk,
                    ApkSignerService signer) {
        this.moduleName = Objects.requireNonNull(moduleName);
        this.variantName = Objects.requireNonNull(variantName);
        this.inputApk = Objects.requireNonNull(inputApk);
        this.outputApk = Objects.requireNonNull(outputApk);
        this.keystore = Objects.requireNonNull(keystore);
        this.alias = Objects.requireNonNull(alias);
        this.keystorePassword = keystorePassword;
        this.keyPassword = keyPassword;
        this.schemes = List.copyOf(schemes);
        this.minSdk = minSdk;
        this.signer = Objects.requireNonNull(signer);
    }

    @Override
    public String name() {
        return moduleName + ":sign[" + variantName + "]";
    }

    @Override
    public String taskType() {
        return "sign";
    }

    @Override
    public TaskInputs inputs() {
        return TaskInputs.builder()
                .file(inputApk)
                .property("joide.sign.schemes", String.join(",", schemes))
                .property("joide.sign.alias", alias)
                .build();
    }

    @Override
    public TaskOutputs outputs() {
        return TaskOutputs.builder().file(outputApk).build();
    }

    @Override
    public String toolVersion() {
        return signer.toolVersion();
    }

    @Override
    public String optionsFingerprint() {
        return "sign-v1:" + String.join(",", schemes) + ":" + alias;
    }

    @Override
    public ResourceClaim resourceClaim() {
        return new ResourceClaim(1, 128, false);
    }

    @Override
    public TaskExecution execute(TaskContext context)
            throws InterruptedException {
        try {
            signer.sign(new ApkSignerService.SigningRequest(
                    context.resolve(inputApk), context.resolve(outputApk),
                    keystore, keystorePassword, alias, keyPassword,
                    schemes, minSdk));
            ApkSignerService.Verification verification = signer.verify(
                    context.resolve(outputApk), minSdk);
            if (!verification.verified) {
                List<Problem> errors = new ArrayList<>();
                errors.add(Problem.error("sign.verify",
                        "signature not verified: "
                                + String.join("; ", verification.errors)));
                return TaskExecution.failed(errors);
            }
            return TaskExecution.ok(List.of(Problem.info("sign.ok",
                    "signed and verified ("
                            + schemesOf(verification) + ")")));
        } catch (Exception e) {
            return TaskExecution.failed(List.of(Problem.error("sign.failed",
                    e.getMessage())));
        }
    }

    private static String schemesOf(ApkSignerService.Verification v) {
        List<String> ok = new ArrayList<>();
        if (v.v1) {
            ok.add("v1");
        }
        if (v.v2) {
            ok.add("v2");
        }
        if (v.v3) {
            ok.add("v3");
        }
        return String.join("+", ok);
    }
}
