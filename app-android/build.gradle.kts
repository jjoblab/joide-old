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
        }
        release {
            // P8 : rétrécissement reporté (backlog) — les règles de
            // conservation des ServiceLoader des moteurs exigent une
            // passe dédiée ; jamais de règle approximative.
            isMinifyEnabled = false
            if (signatureCi) signingConfig = signingConfigs.getByName("ci")
        }
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
    }
    // Lot U (ADR-066) : la chaîne Android EMBARQUÉE — les adaptations
    // d'outils du source set tools (D8/R8 en processus, apksig,
    // bundletool) et les jars des outils eux-mêmes vivent dans l'APK :
    // « d8 et autre non embarqué » ne se dit plus, le pipeline Android
    // complet tourne sur l'appareil.
    //
    // CHAÎNE DIFFÉRÉE (2026-11-17) — APK allégé : les packs
    // r8-<version>.dex.zip et bundletool-<version>.dex.zip générés
    // par :dexPacksOutils peuvent REMPLACER libs.r8, libs.bundletool
    // et libs.guava ci-dessous (et le BuildRunner chargera les packs
    // téléchargés à la première construction, voir
    // build-android/.../toolchain/PacksOutils.java). Trois étapes :
    //   1. publier build/distribution/*.dex.zip dans une release
    //      GitHub « toolchain-v1 » du dépôt ;
    //   2. retirer les trois lignes implementation(libs.r8/bundletool/
    //      guava) ci-dessous (GARDER toolsApi : les adaptateurs
    //      restent dans l'APK) ;
    //   3. reconstruire — l'APK perd ~20-35 Mo, la première
    //      construction sur appareil télécharge le pack UNE fois.
    implementation(project(mapOf("path" to ":build-android",
            "configuration" to "toolsApi")))
    implementation(libs.r8)
    implementation(libs.apksig)
    implementation(libs.bundletool)
    implementation(libs.guava)
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
dependencies {
    jarsOutils(libs.r8)
    jarsOutils(libs.bundletool)
    jarsOutils(libs.guava)
    jarAdaptateurs(project(mapOf("path" to ":build-android",
            "configuration" to "toolsApi")))
}

val dexPacksOutils = tasks.register("dexPacksOutils") {
    description = "Génère les packs dex différés (r8, bundletool+guava) dans build/distribution — à publier dans la release toolchain-v1."
    group = "build setup"
    // le jar des adaptateurs doit ÊTRE construit (la résolution
    // d'une configuration de projet ne porte pas la tâche productrice
    // quand elle se fait à l'exécution)
    dependsOn(":build-android:toolsJar")
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

        fun dexter(dossier: File, vararg entrees: File): List<File> {
            dossier.deleteRecursively()
            dossier.mkdirs()
            val commande = mutableListOf(
                    javaBin.absolutePath, "-cp", r8Jar.absolutePath,
                    "com.android.tools.r8.D8", "--release",
                    "--min-api", "28",
                    "--lib", androidJar.absolutePath,
                    "--output", dossier.absolutePath)
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
        // pack doit reproduire l'empreinte épinglée de PacksOutils)
        fun empaqueter(cible: File, dexes: List<File>) {
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

        empaqueter(packR8, dexter(File(atelier, "r8"),
                r8Propre,
                *extraire("jo/joide/build/android/tools/InProcessDexer",
                        File(atelier, "adaptateurs-r8")).toTypedArray()))
        empaqueter(packBundletool, dexter(File(atelier, "bundletool"),
                bundletoolPropre, guavaPropre,
                *extraire("jo/joide/build/android/tools/BundletoolAssembler",
                        File(atelier, "adaptateurs-bt")).toTypedArray()))

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
                + "${packBundletool.absolutePath} (${packBundletool.length()} o)")
    }
}
