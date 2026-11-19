// app-android — l'application hôte (§6.7/§6.10, P8) : UI, console de
// build, service de premier plan :build, installation, logcat. Le code
// de l'application ne voit QUE jo.joide.ide (interfaces) et les modules
// JoIDE — jamais une brique directement (ADR-010).
// Import explicite : dans le DSL Kotlin, « java » est masqué par
// l'accesseur java { } — « java.net.URL » non qualifié d'un import ne
// résout pas (la CI rouge l'a prouvé).
import java.net.URL
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("joide.android-application-conventions")
}

// Signature CI stable (ADR-047) : la clé vit dans les secrets du dépôt,
// jamais dans le dépôt (convention docs/dev.md) ; le workflow la dépose
// en fichier et la désigne par l'environnement. Sans ces variables, le
// comportement est inchangé : debug sur le keystore de débogage local,
// release non signée. La MÊME clé signe debug et release — installer
// l'un sur l'autre conserve les données de l'application, jamais de
// désinstallation forcée (exigence du propriétaire du 2026-11-16).
val cleCi = providers.environmentVariable("JOIDE_CI_KEYSTORE").orNull
val motDePasseCi = providers.environmentVariable("JOIDE_CI_KEYSTORE_PASSWORD").orNull
val aliasCi = providers.environmentVariable("JOIDE_CI_KEY_ALIAS").orNull
val signatureCi = cleCi != null && motDePasseCi != null && aliasCi != null

// SHA court du commit courant (lot M) : la rangée Version des
// Paramètres l'affiche ; « dev » hors dépôt git — jamais un SHA deviné.
val shaCourt = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short=7", "HEAD")
    }.standardOutput.asText.get().trim()
}.getOrDefault("dev")

// --- aapt2 embarqué (lot U, ADR-066) -------------------------------------------------
// ART n'exécute les binaires QUE depuis nativeLibraryDir et Google ne publie
// PAS d'aapt2 pour les ABI Android : l'APK embarque un aapt2 préconstruit en
// libaapt2.so par ABI (la même approche que les IDE sur appareil). La source
// est ReVanced/aapt2 — un build dont les segments LOAD sont alignés 16 Ko, ce
// qui charge sur les appareils 4 Ko COMME 16 Ko. L'outillage de build
// (ToolchainLocator.aapt2Embarque) le trouve par joide.native.dir.
val aapt2ReleaseTag = "v1.1.0"
// ABI UNIQUE (demande du propriétaire, 2026-11-17) : arm64-v8a seul —
// armeabi-v7a, x86 et x86_64 quittaient ~14 Mo d'un APK déjà lourd ; les
// émulateurs x86_64 restent servis par le keystore CI en debug si besoin,
// le pied téléphonique d'aujourd'hui est arm64.
val aapt2Abis = listOf("arm64-v8a")

val fetchAapt2 = tasks.register("fetchAapt2") {
    description = "Télécharge l'aapt2 préconstruit dans src/main/jniLibs/<abi>/libaapt2.so."
    group = "build setup"
    val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs").asFile
    val tag = aapt2ReleaseTag
    val abis = aapt2Abis
    outputs.upToDateWhen {
        // à jour : le marqueur porte la source — un changement de tag relance
        // (la comparaison tient sur UNE ligne : le Kotlin du DSL Gradle refuse
        // un retour à la ligne AVANT « == » — la CI rouge l'a prouvé)
        abis.all { abi ->
            val marqueur = File(File(jniLibsDir, abi), ".aapt2-source")
            val source = marqueur.takeIf { it.exists() }?.readText()?.trim()
            source == "revanced-$tag"
        }
    }
    doLast {
        // arm64 seul : les dossiers d'ABI retirés de la liste PARTENT du
        // disque — un jniLibs local d'un build 4-ABI ne doit pas
        // regonfler l'APK (les fichiers ne sont jamais commis, la
        // purge reste la seule vérité)
        if (jniLibsDir.isDirectory) {
            jniLibsDir.listFiles()?.forEach { enfant ->
                if (enfant.isDirectory && enfant.name !in abis) {
                    enfant.deleteRecursively()
                }
            }
        }
        for (abi in abis) {
            val dossierAbi = File(jniLibsDir, abi)
            val aapt2 = File(dossierAbi, "libaapt2.so")
            val marqueur = File(dossierAbi, ".aapt2-source")
            val dejaTelecharge = aapt2.exists() && aapt2.length() > 0L
                    && marqueur.takeIf { it.exists() }?.readText()?.trim() == "revanced-$tag"
            if (dejaTelecharge) {
                continue
            }
            dossierAbi.mkdirs()
            val url = "https://github.com/ReVanced/aapt2/releases/download/$tag/aapt2-$abi"
            val temporaire = File.createTempFile("aapt2-$abi", ".bin")
            try {
                URL(url).openStream().use { entree ->
                    temporaire.outputStream().use { entree.copyTo(it) }
                }
                // garde-fou : jamais une page d'erreur GitHub en binaire
                val magique = temporaire.inputStream().use { it.readNBytes(4) }
                val estElf = magique.size == 4 && magique[0] == 0x7F.toByte()
                        && magique[1] == 'E'.code.toByte()
                        && magique[2] == 'L'.code.toByte()
                        && magique[3] == 'F'.code.toByte()
                if (!estElf || temporaire.length() < 100_000L) {
                    throw GradleException(
                            "L'aapt2 téléchargé ($abi) n'est pas un binaire ELF "
                                    + "(${temporaire.length()} octets de $url)")
                }
                temporaire.copyTo(aapt2, overwrite = true)
                aapt2.setExecutable(true)
                marqueur.writeText("revanced-$tag")
            } finally {
                temporaire.delete()
            }
        }
    }
}

// Keystore de débogage (lot U/8) : la variante debug des projets
// UTILISATEUR se signe sur l'appareil (avant : APK non signé,
// ininstallable — la tâche de signature ne s'enregistrait que sur un
// keystore posé). Généré ici par keytool du JDK de construction,
// PKCS12, alias « joide », mot de passe « joidepass » (celui du
// BuildRunner) — JAMAIS COMMIS (convention docs/dev.md : aucun
// keystore dans le dépôt ; .gitignore), régénéré à la main si
// effacé. Le moteur d'une clé debug locale par machine est celui
// d'Android Studio (~/.android/debug.keystore).
val genererKeystoreDebug = tasks.register("genererKeystoreDebug") {
    description = "Génère le keystore de débogage embarqué en asset (toolchain/debug.keystore)."
    group = "build setup"
    val dossier = File(projectDir, "src/main/assets/toolchain")
    val cible = File(dossier, "debug.keystore")
    outputs.file(cible)
    outputs.upToDateWhen { cible.isFile && cible.length() > 1_000L }
    doLast {
        if (cible.isFile && cible.length() > 1_000L) {
            return@doLast
        }
        dossier.mkdirs()
        val keytool = File(System.getProperty("java.home"), "bin/keytool")
        val dname = "CN=JoIDE Debug, OU=Build, O=JoIDE, C=FR"
        val commande = listOf(
            keytool.absolutePath, "-genkeypair",
            "-keystore", cible.absolutePath,
            "-storetype", "PKCS12",
            "-alias", "joide",
            "-storepass", "joidepass",
            "-keypass", "joidepass",
            "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10950",
            "-dname", dname)
        val processus = ProcessBuilder(commande)
            .redirectErrorStream(true)
            .start()
        val journal = processus.inputStream.readBytes().decodeToString()
        if (processus.waitFor() != 0
                || !cible.isFile || cible.length() < 1_000L) {
            cible.delete()
            throw GradleException(
                "keytool a échoué (code ${processus.exitValue()}) :\n$journal")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(fetchAapt2)
    dependsOn(genererKeystoreDebug)
    // lot XX : l'asset du jar du plugin Compose, avant tout empaquetage
    dependsOn(stagerPluginCompose)
}

// Licences et NOTICE empaquetées (lot M, ADR-É8 « écran texte ») :
// les fichiers de la racine sont copiés dans les assets par une tâche
// — jamais de copie à la main qui vieillirait mal.
val copierLicences = tasks.register<Copy>("copierLicences") {
    from(rootProject.rootDir) {
        include("LICENSE", "NOTICE")
    }
    into(layout.buildDirectory.dir("generated/assets/licences"))
}

android {
    defaultConfig {
        applicationId = "jo.joide.app"
        versionCode = 1
        versionName = "0.1.0"
        // ABI UNIQUE (2026-11-17) : arm64-v8a seul — l'aapt2 des
        // autres ABI (3 × ~4-5 Mo) gonflait l'APK pour des appareils
        // hors cible ; le filtre protège aussi un jniLibs local
        // d'un ancien build 4-ABI
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        // Lot M (ADR-É8/054) : la rangée « Version » porte le nom et
        // le SHA COURT du build — injecté à la construction, « dev »
        // hors dépôt git. Jamais de version inventée.
        buildConfigField("String", "JOIDE_SHA", "\"$shaCourt\"")
    }
    buildFeatures {
        // lot M : la classe BuildConfig porte le SHA du build
        buildConfig = true
        // Lot L1 de l'atelier v2 (ADR-083) : ViewBinding — la SEULE
        // dépendance AndroidX tolérée (androidx.databinding:viewbinding,
        // Apache-2.0, NOTICE + docs/third-party.md). Aucun layout pour
        // l'instant : aucune classe *Binding générée, aucun risque ;
        // les lots L2-L6 gonflent les écrans depuis res/layout/.
        viewBinding = true
    }
    if (signatureCi) {
        signingConfigs {
            create("ci") {
                storeFile = file(requireNotNull(cleCi))
                storePassword = requireNotNull(motDePasseCi)
                keyAlias = requireNotNull(aliasCi)
                // PKCS12 : un seul mot de passe pour le magasin et la clé.
                keyPassword = requireNotNull(motDePasseCi)
            }
        }
    }
    buildTypes {
        debug {
            if (signatureCi) signingConfig = signingConfigs.getByName("ci")
            // Lot VIII (ADR-070) : NON débogable — la variante debug EST
            // le livrable distribué (keystore de débogage, arm64), et
            // AGP DÉSACTIVE le rétrécissement R8 d'une construction
            // débogable (« All code optimizations and obfuscation are
            // disabled for debuggable builds »). Le débogage local se
            // fait avec -Pdebuggable si besoin (ci-dessous).
            isDebuggable = providers.gradleProperty("debuggable")
                    .map { it.toBoolean() }.getOrElse(false)
            // Lot VIII (ADR-070) : la passe de rétrécissement DÉDIÉE du
            // backlog P8 — R8 sur les DEUX variantes. Les règles de
            // conservation (proguard-rules.pro) gardent : les SPI qui
            // traversent le mur de chargeurs vers les packs différés,
            // le chargement PAR NOM d'ApksigSigner, la réflexion
            // lsp4j/gson et les métadonnées Kotlin. La tâche
            // verifierOmbreApk surveille les régressions d'ombre.
            isMinifyEnabled = true
            isShrinkResources = true
            // « proguard-android-optimize » avec -dontoptimize dans nos
            // règles : la machine de construction a 4 Go et le noyau a
            // tué le démon R8 en mode optimiseur (-Xmx2200m, lot
            // VIII) — le rétrécissement (retrait du code mort) et
            // l'obfuscation (noms courts) portent l'essentiel du gain ;
            // la carte mapping.txt accompagne chaque construction
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro")
            if (signatureCi) signingConfig = signingConfigs.getByName("ci")
        }
    }
    lint {
        // Lot VIII (ADR-070) : le debug NON débogable hérite des tâches
        // VITALES de lint (AGP les planifie sur les constructions non
        // débogables) — R8 et lint chargeraient alors CHACUN le modèle
        // complet de l'application dans le même démon : le noyau a tué
        // le démon sur la machine de build (4 Go, rapport du lot VIII).
        // Le contrôle vit de lintDebug/lintRelease À LA DEMANDE ; la
        // discipline ADR-013 tient dans les modules (conventions).
        checkReleaseBuilds = false
    }
    packaging {
        // Le moteur embarque le compilateur Kotlin (build-jvm) ET la
        // stdlib (moteurs code-lsp) : deux copies des métadonnées de
        // builtins identiques (annotation, collections, ranges…) — la
        // première suffit, jamais de perte (même version 2.3.0).
        resources.pickFirsts += "**/*.kotlin_builtins"
        // Lot U : D8/R8, apksig et bundletool vivent DANS l'APK (le
        // build Android tourne sur l'appareil) — leurs méta-fichiers
        // se fusionnent au premier, jamais de doublon fatal.
        resources.excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/module-info.class",
                "module-info.class")
        // les dex de l'app : stockés (l'APK du build embarque assez —
        // les propres dex de JoIDE restent compressés par défaut)
        // l'aapt2 embarqué ne passe JAMAIS au dépouilleur : le binaire
        // préconstruit doit rester tel quel (CodeAssist a montré la
        // voie — un dépouillage n'améliore pas un ELF déjà lié)
        jniLibs.keepDebugSymbols += "**/libaapt2.so"
        // lot U/8 : l'aapt2 doit ÊTRE EXTRAIT vers nativeLibraryDir —
        // seul répertoire où ART permet exec. Sans extraction (défaut
        // AGP pour minSdk ≥ 23 : les .so restent mappés dans l'APK),
        // libaapt2.so n'EXISTE PAS sur le disque et le build sur
        // appareil échouait silencieusement (CodeAssist force la
        // même extraction pour son aapt2)
        jniLibs.useLegacyPackaging = true
    }

    sourceSets.getByName("main") {
        // lot M : LICENSE et NOTICE de la racine dans les assets.
        // AGP 9 refuse un Provider ici (généré contre statique
        // indécidable) : le dossier est posé en statique et la tâche
        // de copie est câblée aux fusions d'assets ci-dessous.
        // srcDir est dépréciée (niveau erreur) en AGP 9 : le passage
        // se fait par la liste mutable « directories » — chemin ABSOLU
        // pour lever toute ambiguïté de résolution relative.
        assets.directories.add(layout.buildDirectory
                .dir("generated/assets/licences").get().asFile.absolutePath)
    }
}

// Les tâches qui lisent le dossier d'assets généré dépendent de la
// copie des licences (lot M) — Gradle 9 exige la dépendance
// EXPLICITE : la fusion des assets ET toutes les tâches lint (le
// lint vital de release construit son modèle et analyse les assets
// sans connaître la tâche de copie — la validation du CI l'a prouvé
// rouge depuis le lot M ; un dependsOn sur une copie à jour est
// sans coût). Le keystore de débogage (lot U/8) suit la MÊME
// discipline : la fusion des assets doit voir l'asset généré.
tasks.matching {
    it.name.contains("Lint") || it.name.startsWith("lint")
            || (it.name.startsWith("merge")
            && it.name.endsWith("Assets"))
}.configureEach {
    dependsOn(copierLicences)
    dependsOn(genererKeystoreDebug)
}

dependencies {
    api(project(":ide-integration")) {
        // Une seule copie des classes ecj dans l'APK : le compagnon
        // patché ART (EPL-2.0, runtimeOnly d'ide-integration) remplace
        // l'ecj vanille du pipeline — mêmes classes, shims ART en plus.
        // Sans cet exclusion, D8 refuse les classes en double.
        exclude(group = "org.eclipse.jdt", module = "ecj")
        // Lot VII (VM forkée) : le compilateur Kotlin ne vit plus dans
        // l'APK — 43 Mo de dex partent au pack différé, chargé par la
        // VM ouvrière non-zygote (KotlinForkedDriver, ADR-069).
        // kotlin-metadata-jvm (ABI) et kotlin-stdlib restent embarqués.
        exclude(group = "org.jetbrains.kotlin",
                module = "kotlin-compiler-embeddable")
    }
    // Lot U (ADR-066) : la chaîne Android EMBARQUÉE — le pipeline
    // Android complet tourne sur l'appareil, sans ordinateur.
    //
    // CHAÎNE DIFFÉRÉE (lot VI) : la release GitHub « toolchain-v1 »
    // porte les packs r8-9.5.32.dex.zip, bundletool-1.18.3.dex.zip et
    // kotlin-compiler-2.3.0.dex.zip (épinglés taille + SHA-256 dans
    // PacksOutils.java) ; les jars r8, bundletool, guava et le
    // compilateur Kotlin ne vivent PLUS dans l'APK — la première
    // construction sur appareil télécharge chaque pack UNE fois
    // (BuildRunner.outil : embarqué d'abord, pack ensuite).
    //
    // Lot VIII (ADR-070) : les ADAPTATEURS des outils packés partent
    // de l'APK aussi (compileOnly) — leur copie embarquée empoisonnait
    // le pack : la délégation parent-d'abord du DexClassLoader
    // servait l'adaptateur de l'APK (sans ses classes d'outil) et
    // JAMAIS celui du pack (« pack différé r8 indisponible
    // (InvocationTargetException) », rapport du 2026-11-17). La
    // manière CodeAssist : AUCUNE classe d'outil dans l'app, tout
    // arrive déjà dexé. Seul ApksigSigner reste embarqué (apksig trop
    // léger pour un pack, décision du lot VI) par le jar dédié
    // signatureApi ; le bureau garde les adaptateurs sur son classpath
    // (toolsApi en compileOnly ici, implementation dans build-daemon).
    compileOnly(project(mapOf("path" to ":build-android",
            "configuration" to "toolsApi")))
    implementation(project(mapOf("path" to ":build-android",
            "configuration" to "signatureApi")))
    implementation(libs.apksig)
    // Lot XIX (la manière CodeAssist, android/art-compat :
    // java-compiler.jar committé et dexé dans l'APK) : l'API
    // javax.lang.model + javax.annotation.processing + javax.tools
    // — ABSENTE des bibliothèques cœur d'ART, référencée par ecj au
    // chargement (batch/FileSystem → SourceVersion ; le terrain :
    // « NoClassDefFoundError: Ljavax/lang/model/SourceVersion; » sur
    // app:generateRJar). 144 classes extraites du JDK 21 (scripts/
    // jar-javax-api.sh) ; ART accepte les classes javax.* de l'app.
    implementation(files("libs/java-compiler.jar"))
    // Lot XX (la manière CodeAssist) : le plugin compilateur Compose
    // vit dans l'APK — ses CLASSES sont dexées par AGP comme tout le
    // reste, le classpath du fork Kotlin porte l'APK lui-même (le
    // registrar se charge dans la VM ouvrière), et le JAR ORIGINAL
    // part en asset (stagerPluginCompose) pour le -Xplugin de kotlinc
    // (META-INF/services). Le pack de la release reste immuable.
    implementation(libs.compose.compiler.plugin) {
        // la stdlib est déjà embarquée — ne rien tirer d'autre
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    // ADR-085 (D1/D2, directives du propriétaire du 2026-11-18) : le socle
    // UI de l'application devient AndroidX + Material 3, habillé Graphite.
    // Quatre artefacts seulement au lot A1 — EXACTEMENT ceux dont le code
    // importe une classe (discipline de l'ADR : appcompat porte la coque
    // AppCompatActivity d'ActiviteJoide, core porte WindowInsetsCompat
    // (correctif C5 : l'API 30 directe d'hier) et ViewCompat, activity
    // porte OnBackPressedDispatcher et les contrats ActivityResult d'Ecrans,
    // material porte les thèmes Theme.Material3.* et les composants D2).
    // Versions STABLES épinglées au catalogue (règle §10, jamais en dur
    // ici) ; les suivants (recyclerview A2, constraintlayout/coordinatorlayout/
    // drawerlayout A6) entrent aux lots qui les importent. Jetpack Compose
    // reste EXCLU (application Java). Les règles R8 des bibliothèques se
    // consomment seules (consumer rules) ; verifierOmbreApk et
    // MurEtancheTest gardent le mur de chargeurs SANS interdit AndroidX
    // (vérifié en A0) — aucun amendement nécessaire.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.material)
}

// lot XX : le JAR ORIGINAL du plugin Compose en ASSET — résolu du
// catalogue, copié dans src/main/assets/toolchain/ (jamais committé,
// même règle que le jniLibs), la fraîcheur tenant au contenu
val composePluginAsset = configurations.create("composePluginAsset") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    composePluginAsset(libs.compose.compiler.plugin)
}
// lot XX : le JAR ORIGINAL du plugin Compose en ASSET — résolu du
// catalogue, copié dans src/main/assets/toolchain/ (jamais committé,
// même règle que le jniLibs). Tâche Copy NATIVE : la configuration
// cache refuse les références d'objets de script dans les actions
// (le terrain CI du 2026-11-17 : « cannot serialize Gradle script
// object references » sur la tâche doLast d'origine) — from/into,
// eux, s'enregistrent comme ENTRÉES de la tâche.
val stagerPluginCompose = tasks.register<Copy>("stagerPluginCompose") {
    description = "Copie le jar du plugin Compose en asset de l'APK (src/main/assets/toolchain)."
    group = "build setup"
    from(composePluginAsset)
    rename { "kotlin-compose-compiler-plugin.jar" }
    into(layout.projectDirectory.dir("src/main/assets/toolchain"))
}

// --- les packs d'outils DIFFÉRÉS (chaîne allégée, 2026-11-17) -------------------------
// ART ne charge pas des jars .class : D8/R8 et bundletool partent en
// packs de DEX pré-construits (la manière CodeAssist : r8.dex.zip),
// téléchargeables une fois à la première construction sur appareil.
// La tâche dexée les jars du catalogue + les adaptateurs du source
// set tools (seuls ceux du pack : le mur de chargeurs reste étanche),
// assemble des zips DÉTERMINISTES (horodatages fixes) et vérifie les
// empreintes contre les constantes épinglées de PacksOutils.java —
// une divergence échoue la tâche, jamais un pack non vérifié.
val jarsOutils = configurations.create("jarsOutils") {
    isCanBeConsumed = false
}
val jarAdaptateurs = configurations.create("jarAdaptateurs") {
    isCanBeConsumed = false
}
val jarsKotlin = configurations.create("jarsKotlin") {
    isCanBeConsumed = false
}
val jarKotlinWorker = configurations.create("jarKotlinWorker") {
    isCanBeConsumed = false
}
val jarPatchArt = configurations.create("jarPatchArt") {
    isCanBeConsumed = false
}
val jarShimsArt = configurations.create("jarShimsArt") {
    isCanBeConsumed = false
}
dependencies {
    jarsOutils(libs.r8)
    jarsOutils(libs.bundletool)
    jarsOutils(libs.guava)
    jarAdaptateurs(project(mapOf("path" to ":build-android",
            "configuration" to "toolsApi")))
    // Lot VII : le compilateur Kotlin et sa FERMETURE RUNTIME (les
    // dépendances de son POM : stdlib, script-runtime, reflect,
    // daemon-embeddable, coroutines — résolution transitive) +
    // l'OUVRIER (source set workerKotlin de build-jvm) partent
    // ensemble au dexage du pack kotlin-compiler.dex.zip
    jarsKotlin(libs.kotlin.compiler.embeddable)
    // lot XX : le plugin Compose ne vit PLUS dans le pack — le pack
    // de la release est IMMUTABLE (le terrain du 2026-11-17 : une
    // empreinte mutée sans remplacement du fichier publié =
    // « pack size mismatch » sur l'appareil). Les CLASSES du plugin
    // sont dexées dans l'APK (implementation ci-dessous) et son jar
    // ORIGINAL part en ASSET — la manière CodeAssist.
    // Lot XV — la manière CodeAssist (« StAX on ART (which ships
    // none) ») : l'API StAX (javax.xml.stream, le compilateur la
    // référence mais le shade de l'embeddable ne l'embarque PAS — le
    // bureau la reçoit du JDK, ART n'a rien), aalto comme
    // implémentation COMPLÈTE unshadée (la copie shadée de
    // l'embeddable n'a ni usines ni fichier de services) et
    // stax2-api pour aalto. Les META-INF/services d'aalto (les
    // usines XMLInputFactory/XMLOutputFactory/XMLEventFactory) partent
    // avec les ressources non-class du pack : ServiceLoader de la VM
    // ouvrière les trouve dans l'archive du pack.
    jarsKotlin(libs.stax.api)
    jarsKotlin(libs.stax2.api)
    jarsKotlin(libs.aalto.xml)
    jarKotlinWorker(project(mapOf("path" to ":build-android",
            "configuration" to "kotlinWorkerApi")))
    // Lot XI : le patcheur ART et sa fermeture ASM, les shims ART —
    // transités par build-android (ADR-011 : jamais build-jvm direct)
    jarPatchArt(project(mapOf("path" to ":build-android",
            "configuration" to "packArtApi")))
    jarShimsArt(project(mapOf("path" to ":build-android",
            "configuration" to "shimsArtApi")))
}

val dexPacksOutils = tasks.register("dexPacksOutils") {
    description = "Génère les packs dex différés (r8, bundletool+guava, kotlin) dans build/distribution — à publier dans la release toolchain-v1."
    group = "build setup"
    // le jar des adaptateurs doit ÊTRE construit (la résolution
    // d'une configuration de projet ne porte pas la tâche productrice
    // quand elle se fait à l'exécution) — idem pour l'ouvrier Kotlin
    dependsOn(":build-android:toolsJar")
    dependsOn(":build-jvm:workerKotlinJar")
    // Lot XI : le patcheur ART et les shims se construisent avant
    dependsOn(":build-jvm:packArtJar")
    dependsOn(":build-jvm:shimsArtJar")
    val sortie = layout.buildDirectory.dir("distribution")
    outputs.dir(sortie)
    // lus à la CONFIGURATION (cache de configuration : jamais
    // d'extension AGP à l'exécution) — la résolution du SDK suit
    // settings.gradle.kts à l'identique (ANDROID_HOME puis
    // local.properties) : sdkComponents d'AGP refuse l'accès eager
    val sdkRepertoire = run {
        val env = System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
        if (!env.isNullOrBlank()) {
            File(env)
        } else {
            val proprietes = Properties()
            val fichier = rootDir.resolve("local.properties")
            if (fichier.isFile) {
                fichier.inputStream().use { proprietes.load(it) }
            }
            File(proprietes.getProperty("sdk.dir")
                    ?: error("SDK Android introuvable : ANDROID_HOME "
                            + "ou local.properties/sdk.dir"))
        }
    }
    doLast {
        val javaBin = File(System.getProperty("java.home"), "bin/java")
        val jars = jarsOutils.resolve()
        val r8Jar = jars.first { it.name.startsWith("r8-") }
        val bundletoolJar = jars.first { it.name.startsWith("bundletool-") }
        val guavaJar = jars.first { it.name.startsWith("guava-") }
        val adaptateursJar = jarAdaptateurs.resolve().single()
        val androidJar = sdkRepertoire.resolve("platforms")
                .listFiles { f: File -> f.name.startsWith("android-") }
                ?.maxByOrNull { it.name }?.resolve("android.jar")
                ?: throw GradleException(
                        "android.jar introuvable sous $sdkRepertoire/platforms")

        // l'extraction ciblée des adaptateurs du source set tools :
        // SEULS ceux du pack partent au dexage (le mur de chargeurs
        // reste étanche — aucun META-INF/services dans le pack, le
        // BuildRunner charge la classe de l'adaptateur PAR NOM)
        fun extraire(prefixe: String, dossier: File): List<File> {
            dossier.deleteRecursively()
            dossier.mkdirs()
            val fichiers = mutableListOf<File>()
            ZipFile(adaptateursJar).use { zip ->
                zip.entries().asSequence()
                        .filter { it.name.startsWith(prefixe) }
                        .forEach { entree ->
                            val cible = dossier.resolve(entree.name)
                            cible.parentFile.mkdirs()
                            zip.getInputStream(entree).use { entree2 ->
                                cible.outputStream().use { entree2.copyTo(it) }
                            }
                            fichiers.add(cible)
                        }
            }
            if (fichiers.isEmpty()) {
                throw GradleException(
                        "adaptateur absent du jar tools : $prefixe")
            }
            return fichiers
        }

        // le dexage : D8 du JAR RÉSOLU (il se dexé lui-même — un jar
        // d'outil ne se compile jamais contre le classpath de l'app).
        // Les jars d'outils peuvent porter un classes.dex EMBARQUÉ
        // (bundletool en livre un pour Android) : D8 refuse une
        // archive mêlant dex et bytecode Java — le jar s'assainit
        // d'abord (dex, module-info et variantes multi-release hors)
        fun assainir(jar: File, cible: File): File {
            if (cible.isFile && cible.length() > 0L) {
                return cible
            }
            cible.parentFile.mkdirs()
            ZipFile(jar).use { source ->
                ZipOutputStream(cible.outputStream().buffered())
                        .use { sortie ->
                            source.entries().asSequence()
                                    .filter { e ->
                                        !e.name.endsWith(".dex")
                                                && e.name != "module-info.class"
                                                && !e.name.startsWith(
                                                        "META-INF/versions/")
                                    }
                                    .sortedBy { it.name }
                                    .forEach { e ->
                                        val entree = ZipEntry(e.name)
                                        entree.time = 946684800000L
                                        sortie.putNextEntry(entree)
                                        source.getInputStream(e).use {
                                            it.copyTo(sortie)
                                        }
                                        sortie.closeEntry()
                                    }
                        }
            }
            return cible
        }

        fun dexter(dossier: File, tasMax: String?,
                 vararg entrees: File): List<File> {
            dossier.deleteRecursively()
            dossier.mkdirs()
            val commande = mutableListOf(javaBin.absolutePath)
            if (tasMax != null) {
                // le dexage du compilateur Kotlin (60 Mo de bytecode)
                // dépasse le tas par défaut du lanceur
                commande.add("-Xmx$tasMax")
            }
            commande.addAll(listOf("-cp", r8Jar.absolutePath,
                    "com.android.tools.r8.D8", "--release",
                    "--min-api", "28",
                    "--lib", androidJar.absolutePath,
                    "--output", dossier.absolutePath))
            entrees.forEach { commande.add(it.absolutePath) }
            val processus = ProcessBuilder(commande)
                    .redirectErrorStream(true).start()
            val journal = processus.inputStream.readBytes()
                    .decodeToString()
            if (processus.waitFor() != 0) {
                throw GradleException("D8 a échoué pour ${dossier.name} :\n$journal")
            }
            return dossier.listFiles { f: File -> f.name.endsWith(".dex") }
                    ?.sortedBy { it.name }
                    ?: throw GradleException(
                            "aucun dex produit dans ${dossier.name}")
        }

        // l'archive déterministe : horodatages FIXES (re-générer un
        // pack doit reproduire l'empreinte épinglée de PacksOutils).
        // Les pack d'outils n'ont QUE des dex ; le pack Kotlin porte
        // AUSSI les ressources des jars (les .kotlin_module notamment)
        // que le dexage seul ne transporte pas.
        fun empaqueter(cible: File, dexes: List<File>,
                       ressources: Map<String, File> = emptyMap()) {
            cible.parentFile.mkdirs()
            ZipOutputStream(cible.outputStream().buffered())
                    .use { zip ->
                        dexes.forEach { dex ->
                            val entree = ZipEntry(dex.name)
                            entree.time = 946684800000L // 2000-01-01
                            zip.putNextEntry(entree)
                            dex.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                        for ((nom, fichier) in ressources) {
                            val entree = ZipEntry(nom)
                            entree.time = 946684800000L // 2000-01-01
                            zip.putNextEntry(entree)
                            fichier.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
        }

        val entreprise = layout.buildDirectory.dir("distribution")
                .get().asFile
        val atelier = File(temporaryDir, "atelier")
        val packR8 = File(entreprise, r8Jar.name.removeSuffix(".jar")
                + ".dex.zip")
        val packBundletool = File(entreprise,
                bundletoolJar.name.removeSuffix(".jar") + ".dex.zip")

        // les jars assainis (dex embarqué de bundletool, multi-release
        // de guava) : le pack reste DÉTERMINISTE — mêmes entrées, même
        // ordre, mêmes horodatages
        val r8Propre = assainir(r8Jar, File(atelier, "r8-san.jar"))
        val bundletoolPropre = assainir(bundletoolJar,
                File(atelier, "bundletool-san.jar"))
        val guavaPropre = assainir(guavaJar, File(atelier, "guava-san.jar"))

        empaqueter(packR8, dexter(File(atelier, "r8"), null,
                r8Propre,
                *extraire("jo/joide/build/android/tools/InProcessDexer",
                        File(atelier, "adaptateurs-r8")).toTypedArray()))
        empaqueter(packBundletool, dexter(File(atelier, "bundletool"),
                null, bundletoolPropre, guavaPropre,
                *extraire("jo/joide/build/android/tools/BundletoolAssembler",
                        File(atelier, "adaptateurs-bt")).toTypedArray()))

        // Lot VII : le pack du compilateur Kotlin — l'OUVRIER
        // (workerKotlin) + le compilateur + sa FERMETURE RUNTIME
        // (stdlib, script-runtime, reflect, daemon-embeddable,
        // coroutines). Le nom du pack suit la convention de
        // PacksOutils.KOTLIN (« kotlin-compiler-<version>.dex.zip »).
        val jarsK = jarsKotlin.resolve().sortedBy { it.name }
        val kceJar = jarsK.first {
            it.name.startsWith("kotlin-compiler-embeddable-") }
        val ouvrierJar = jarKotlinWorker.resolve().single()
        val versionKotlin = kceJar.name
                .removePrefix("kotlin-compiler-embeddable-")
                .removeSuffix(".jar")
        val packKotlin = File(entreprise,
                "kotlin-compiler-$versionKotlin.dex.zip")
        // chaque jar du pack passe d'abord par le PATCHEUR ART
        // (lot XI, la manière CodeAssist, ses passes kotlinc-art) :
        // les classes hostiles à ART se réécrivent AVANT le dexage
        // (management absent, Unsafe tolérant, ClassValue, fastutil
        // dispatch, Introspector, EDT, PathUtil) ; les shims ART
        // partent dexés à part, à côté du compilateur patché
        // le classpath du patcheur : son jar, ses jars ASM, puis
        // TOUS les jars du pack — le calcul des frames d'ASM résout
        // les super-classes communes en chargeant les types réels
        val patcheurCp = (jarPatchArt.resolve().sortedBy { it.name }
                + jarsK).map { it.absolutePath }
                .joinToString(File.pathSeparator)
        fun patcherArt(jar: File): File {
            val cible = File(atelier,
                    "kotlin-${jar.name.removeSuffix(".jar")}-art.jar")
            cible.parentFile.mkdirs()
            val commande = listOf(javaBin.absolutePath, "-Xmx1500m",
                    "-cp", patcheurCp,
                    "jo.joide.build.jvm.packart.PatcheurJar",
                    jar.absolutePath, cible.absolutePath)
            val processus = ProcessBuilder(commande)
                    .redirectErrorStream(true).start()
            val journal = processus.inputStream.readBytes()
                    .decodeToString()
            if (processus.waitFor() != 0) {
                throw GradleException("le patcheur ART a échoué pour "
                        + "${jar.name} :\n$journal")
            }
            return cible
        }
        val jarsKPatches = jarsK.map { patcherArt(it) }
        val shimsJar = jarShimsArt.resolve().single()

        // chaque jar assaini séparément (le suffixe de version garde
        // le cache juste quand le catalogue change de compilateur)
        val jarsKPropres = jarsKPatches.map { jar ->
            assainir(jar, File(atelier,
                    "kotlin-${jar.name.removeSuffix(".jar")}-san.jar"))
        }

        // les ressources non-class de la fermeture (dédupe par nom,
        // ordre stable) : le chargeur de la VM ouvrière les lit dans
        // l'archive du pack
        val dossierRessources = File(atelier, "kotlin-ressources")
        dossierRessources.deleteRecursively()
        val ressources = sortedMapOf<String, File>()
        for (jarPropre in jarsKPropres) {
            ZipFile(jarPropre).use { zip ->
                zip.entries().asSequence()
                        .filter { e -> !e.isDirectory
                                && !e.name.endsWith(".class")
                                && e.name != "META-INF/MANIFEST.MF" }
                        .sortedBy { it.name }
                        .forEach { e ->
                            val cible = ressources.putIfAbsent(
                                    e.name, dossierRessources.resolve(e.name))
                            if (cible == null) {
                                val fichier = ressources[e.name]!!
                                fichier.parentFile.mkdirs()
                                zip.getInputStream(e).use { entree ->
                                    fichier.outputStream().use {
                                        entree.copyTo(it) }
                                }
                            }
                        }
            }
        }
        // Lot XVI : le JAR ORIGINAL de la bibliothèque standard part
        // comme RESSOURCE du pack (« jars/kotlin-stdlib-<v>.jar ») :
        // le classpath de compilation du compilateur exige des jar
        // RÉELS (son JAR FS ne lit pas les dex du pack) et sur
        // l'appareil il n'existe AUCUN kotlin-stdlib.jar sur disque —
        // le terrain : « Cannot access built-in declaration
        // 'kotlin.Any' ». homeOuvriere (KotlinForkedDriver) extrait
        // déjà les ressources non-dex du pack dans kotlin-home/ et le
        // pilote pose le jar en tête du classpath (-no-stdlib). La
        // manière CodeAssist : LEUR outil télécharge le jar réel ;
        // ici le pack le porte, épinglé par son SHA.
        val stdlibJar = jarsK.first {
            it.name.startsWith("kotlin-stdlib-")
                    && !it.name.contains("sources")
        }
        val cibleStdlib = dossierRessources.resolve("jars")
                .resolve(stdlibJar.name)
        cibleStdlib.parentFile.mkdirs()
        stdlibJar.copyTo(cibleStdlib, overwrite = true)
        ressources["jars/" + stdlibJar.name] = cibleStdlib
        // lot XX : le jar du plugin Compose ne part PLUS dans le pack
        // (pack immuable — voir le commentaire jarsKotlin ci-dessus)
        // le dexage PAR LOTS (lot VII) : le compilateur Kotlin (60 Mo
        // de bytecode) ne tient pas dans une seule machine virtuelle
        // D8 d'une machine modeste — chaque lot se dexé seul (min-api
        // 28 : aucun désucrage inter-lots), les dex produits se
        // renumérotent en séquence (classes.dex, classes2.dex, …).
        // La composition des lots est DÉTERMINISTE (entrées triées,
        // taille fixe) : re-générer reproduit l'empreinte épinglée.
        val TAILLE_LOT_CLASSES = 6000

        fun lotsDuJar(jar: File, dossier: File): List<List<File>> {
            // petit jar : un seul lot, entier
            if (jar.length() < 12L * 1024 * 1024) {
                return listOf(listOf(jar))
            }
            // gros jar : les classes triées, coupées en tranches
            dossier.deleteRecursively()
            dossier.mkdirs()
            val fichiers = mutableListOf<File>()
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                        .filter { e -> !e.isDirectory
                                && e.name.endsWith(".class") }
                        .sortedBy { it.name }
                        .forEach { e ->
                            val cible = dossier.resolve(e.name)
                            cible.parentFile.mkdirs()
                            zip.getInputStream(e).use { entree ->
                                cible.outputStream().use {
                                    entree.copyTo(it) }
                            }
                            fichiers.add(cible)
                        }
            }
            return fichiers.chunked(TAILLE_LOT_CLASSES)
        }

        fun dexterParLots(dossier: File, tasMax: String,
                          lots: List<List<File>>): List<File> {
            dossier.deleteRecursively()
            dossier.mkdirs()
            val dexes = mutableListOf<File>()
            for ((index, lot) in lots.withIndex()) {
                val sortieLot = File(dossier, "lot-$index")
                sortieLot.mkdirs()
                val commande = mutableListOf(javaBin.absolutePath,
                        "-Xmx$tasMax",
                        "-cp", r8Jar.absolutePath,
                        "com.android.tools.r8.D8", "--release",
                        "--min-api", "28",
                        "--lib", androidJar.absolutePath,
                        "--output", sortieLot.absolutePath)
                lot.forEach { commande.add(it.absolutePath) }
                val processus = ProcessBuilder(commande)
                        .redirectErrorStream(true).start()
                val journal = processus.inputStream.readBytes()
                        .decodeToString()
                if (processus.waitFor() != 0) {
                    throw GradleException("D8 a échoué pour "
                            + "${dossier.name}/lot-$index :\n$journal")
                }
                dexes.addAll(sortieLot
                        .listFiles { f: File -> f.name.endsWith(".dex") }
                        ?.sortedBy { it.name } ?: emptyList())
            }
            // renumérotation en séquence : le premier dex garde
            // classes.dex, les suivants classes2.dex, classes3.dex…
            val cible = File(dossier, "renumero")
            cible.deleteRecursively()
            cible.mkdirs()
            val renumerotes = mutableListOf<File>()
            dexes.forEachIndexed { index, dex ->
                val nom = if (index == 0) "classes.dex"
                        else "classes${index + 1}.dex"
                val fichier = File(cible, nom)
                dex.copyTo(fichier, overwrite = true)
                renumerotes.add(fichier)
            }
            return renumerotes
        }

        // les lots de la fermeture : les petits jars entiers, le
        // compilateur en tranches, les shims ART puis l'ouvrier en
        // dernier
        val lotsKotlin = mutableListOf<List<File>>()
        for (jarPropre in jarsKPropres) {
            lotsKotlin.addAll(lotsDuJar(jarPropre,
                    File(atelier, "kotlin-lots-" + jarPropre.name)))
        }
        lotsKotlin.add(listOf(shimsJar))
        lotsKotlin.add(listOf(ouvrierJar))
        empaqueter(packKotlin,
                dexterParLots(File(atelier, "kotlin"), "1800m",
                        lotsKotlin),
                ressources)

        // le manifeste + la vérification contre les constantes épinglées
        fun empreinte(fichier: File): String {
            val resume = MessageDigest.getInstance("SHA-256")
            fichier.inputStream().use { entree ->
                val tampon = ByteArray(1 shl 16)
                while (true) {
                    val lus = entree.read(tampon)
                    if (lus < 0) break
                    resume.update(tampon, 0, lus)
                }
            }
            return resume.digest().joinToString("") {
                "%02x".format(it)
            }
        }
        val manifeste = File(entreprise, "manifeste-packs.txt")
        manifeste.writeText(buildString {
            append("# Packs d'outils différés — publier CES FICHIERS dans la")
            append(" release GitHub « toolchain-v1 » du dépôt.\n")
            append("# Les constantes de build-android/.../toolchain/")
            append("PacksOutils.java doivent porter CES valeurs.\n")
            append("r8 ${r8Jar.name.removeSuffix(".jar")} ${packR8.name} ")
            append("${packR8.length()} ${empreinte(packR8)}\n")
            append("bundletool ${bundletoolJar.name.removeSuffix(".jar")} ")
            append("${packBundletool.name} ${packBundletool.length()} ")
            append("${empreinte(packBundletool)}\n")
            append("kotlin kotlin-compiler-$versionKotlin ")
            append("${packKotlin.name} ${packKotlin.length()} ")
            append("${empreinte(packKotlin)}\n")
        })

        // vérification des constantes épinglées : une divergence est
        // un échec EXPLICITE avec les valeurs à reporter
        val sourcePacks = File(rootDir, "build-android/src/main/java"
                + "/jo/joide/build/android/toolchain/PacksOutils.java")
        val texte = sourcePacks.readText()
        fun constante(nom: String): String {
            val trouvee = Regex("$nom\\s*=\\s*(?:\"([^\"]*)\"|([\\d_]+))L?")
                    .find(texte)?.groupValues ?: emptyList()
            return trouvee.getOrNull(1)?.takeIf { it.isNotEmpty() }
                    // les littéraux Java portent des séparateurs _
                    ?: trouvee.getOrNull(2)?.replace("_", "") ?: ""
        }
        val divergences = mutableListOf<String>()
        if (constante("TAILLE_R8") != packR8.length().toString()) {
            divergences.add("TAILLE_R8 = ${packR8.length()}L")
        }
        if (constante("SHA_R8") != empreinte(packR8)) {
            divergences.add("SHA_R8 = \"${empreinte(packR8)}\"")
        }
        if (constante("TAILLE_BUNDLETOOL")
                != packBundletool.length().toString()) {
            divergences.add("TAILLE_BUNDLETOOL = ${packBundletool.length()}L")
        }
        if (constante("SHA_BUNDLETOOL") != empreinte(packBundletool)) {
            divergences.add("SHA_BUNDLETOOL = \"${empreinte(packBundletool)}\"")
        }
        if (constante("TAILLE_KOTLIN")
                != packKotlin.length().toString()) {
            divergences.add("TAILLE_KOTLIN = ${packKotlin.length()}L")
        }
        if (constante("SHA_KOTLIN") != empreinte(packKotlin)) {
            divergences.add("SHA_KOTLIN = \"${empreinte(packKotlin)}\"")
        }
        if (divergences.isNotEmpty()) {
            throw GradleException(buildString {
                append("Empreintes des packs divergent des constantes ")
                append("épinglées (PacksOutils.java) — reportez :\n")
                divergences.forEach { append("    ").append(it).append('\n') }
                append("Puis régénérez la vérification : ./gradlew ")
                append(":app-android:dexPacksOutils")
            })
        }
        println("packs prêts : ${packR8.absolutePath} (${packR8.length()} o), "
                + "${packBundletool.absolutePath} "
                + "(${packBundletool.length()} o), "
                + "${packKotlin.absolutePath} (${packKotlin.length()} o)")
    }
}

// --- Garde anti-ombre (lot VIII, ADR-070) ------------------------------------
// La panne du 2026-11-17 : une copie embarquée des adaptateurs packés
// (InProcessDexer, BundletoolAssembler) dans l'APK fait que la
// délégation parent-d'abord du DexClassLoader charge la copie de
// l'APK — sans ses classes d'outil — et JAMAIS celle du pack. Cette
// tâche LIT l'APK construit et ÉCHOUE si l'ombre revient, ou si un
// élément vital disparaît (adaptateur de signature, pont des packs,
// SPI). Les descripteurs de classes (avec « L… ; ») se cherchent dans
// les octets des dex : la constante texte de BuildRunner (dotted)
// ne fausse pas la sonde.
val verifierOmbreApk = tasks.register("verifierOmbreApk") {
    group = "verification"
    description = "Vérifie que l'APK ne porte ni ombre des adaptateurs packés ni perte d'un élément vital (lot VIII)."
    dependsOn("packageDebug")
    // lot IX : le chemin de l'APK se résout à la CONFIGURATION et se
    // capture comme File (sérialisable) — « layout » vit dans le
    // doLast, le cache de configuration refusait de stocker la tâche
    // (« cannot serialize Gradle script object references », run CI
    // 95cec9e du 2026-11-17 : les DEUX APK étaient construits, le
    // rouge ne venait que de ce stockage)
    val apk = layout.buildDirectory.file(
            "outputs/apk/debug/app-android-debug.apk").get().asFile
    doLast {
        if (!apk.isFile) {
            throw GradleException("APK introuvable : $apk")
        }
        val interdits = listOf(
                "Ljo/joide/build/android/tools/InProcessDexer;",
                "Ljo/joide/build/android/tools/BundletoolAssembler;",
                "META-INF/services/jo.joide.build.android.dex.Dexer",
                "META-INF/services/jo.joide.build.android.bundle.BundleAssembler")
        val requis = listOf(
                "Ljo/joide/build/android/tools/ApksigSigner;",
                "Ljo/joide/app/android/ChargeurDexAndroid;",
                "Ljo/joide/build/android/dex/Dexer;",
                // lot XXI — le pack bundletool lie ces types PAR NOM à
                // travers le mur de chargeurs (son adaptateur écrit la
                // charge du module via packaging.ApkWriter, et compile
                // contre l'interface BundleAssembler + son BaseModule) :
                // un renommage R8 les rend introuvables au pack —
                // « Failed resolution of: …ApkWriter » (terrain du
                // 2026-11-18). MurEtancheTest garde les imports du jeu
                // tools dans la surface gardée ; ces sondes prouvent
                // l'APK construit.
                "Ljo/joide/build/android/packaging/ApkWriter;",
                "Ljo/joide/build/android/packaging/ApkWriter\$Entry;",
                "Ljo/joide/build/android/bundle/BundleAssembler;",
                "Ljo/joide/build/android/bundle/BundleAssembler\$BaseModule;")
        val fautes = mutableListOf<String>()
        ZipFile(apk).use { zip ->
            // ISO-8859-1 : correspondance bijective octet ↔ caractère —
            // chercher un descripteur ASCII dans le texte révèle tout
            // descripteur de classe réellement présent dans le dex
            val textesDex = zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") }
                    .map { nom ->
                        String(zip.getInputStream(nom).readBytes(),
                                Charsets.ISO_8859_1) }
                    .toList()
            // Le fichier de services du pont peut porter un nom
            // OBFUSQUÉ par R8 (renommé avec l'interface, cohérent au
            // sein de l'APK) : c'est le CONTENU qui prouve que le pont
            // reste enregistré — l'implémentation est gardée PAR NOM.
            val pontEnregistre = zip.entries().asSequence()
                    .filter { it.name.startsWith("META-INF/services/") }
                    .any { nom ->
                        zip.getInputStream(nom).readBytes()
                                .toString(Charsets.ISO_8859_1)
                                .contains("jo.joide.app.android" +
                                        ".ChargeurDexAndroid") }
            if (!pontEnregistre) {
                fautes.add("PERDU de l'APK : aucun fichier de services" +
                        " n'enregistre le pont des packs" +
                        " (ChargeurDexAndroid)")
            }
            val noms = zip.entries().asSequence().map { it.name }.toSet()
            for (sonde in interdits) {
                if (sonde.startsWith("L")) {
                    if (textesDex.any { it.contains(sonde) }) {
                        fautes.add("OMBRE dans l'APK : classe $sonde "
                                + "— le pack ne chargera jamais")
                    }
                } else if (sonde in noms) {
                    fautes.add("OMBRE dans l'APK : service $sonde "
                            + "— le ServiceLoader cacherait le pack")
                }
            }
            for (sonde in requis) {
                if (sonde.startsWith("L")) {
                    if (textesDex.none { it.contains(sonde) }) {
                        fautes.add("PERDU de l'APK : classe $sonde "
                                + "absente — un keep R8 manque")
                    }
                } else if (sonde !in noms) {
                    fautes.add("PERDU de l'APK : entrée $sonde absente "
                            + "— un keep R8 manque")
                }
            }
        }
        if (fautes.isNotEmpty()) {
            throw GradleException(fautes.joinToString("\n"))
        }
        println("verifierOmbreApk : APK sans ombre, éléments vitaux "
                + "présents (${apk.length()} o)")
    }
}

// AGP enregistre ses tâches de variante APRÈS ce script : l'appariement
// paresseux (matching/configureEach) attend l'enregistrement au lieu
// de l'exiger à la configuration (tasks.named échouait)
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(verifierOmbreApk)
}
