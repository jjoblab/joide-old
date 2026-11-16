// app-android — l'application hôte (§6.7/§6.10, P8) : UI, console de
// build, service de premier plan :build, installation, logcat. Le code
// de l'application ne voit QUE jo.joide.ide (interfaces) et les modules
// JoIDE — jamais une brique directement (ADR-010).
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
val aapt2Abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

val fetchAapt2 = tasks.register("fetchAapt2") {
    description = "Télécharge l'aapt2 préconstruit dans src/main/jniLibs/<abi>/libaapt2.so."
    group = "build setup"
    val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs").asFile
    val tag = aapt2ReleaseTag
    val abis = aapt2Abis
    outputs.upToDateWhen {
        // à jour : le marqueur porte la source — un changement de tag relance
        abis.all { abi ->
            val marqueur = File(File(jniLibsDir, abi), ".aapt2-source")
            marqueur.takeIf { it.exists() }?.readText()?.trim()
                    == "revanced-$tag"
        }
    }
    doLast {
        for (abi in abis) {
            val dossierAbi = File(jniLibsDir, abi)
            val aapt2 = File(dossierAbi, "libaapt2.so")
            val marqueur = File(dossierAbi, ".aapt2-source")
            if (aapt2.exists() && aapt2.length() > 0L
                    && marqueur.readText().trim() == "revanced-$tag") {
                continue
            }
            dossierAbi.mkdirs()
            val url = "https://github.com/ReVanced/aapt2/releases/download/$tag/aapt2-$abi"
            val temporaire = File.createTempFile("aapt2-$abi", ".bin")
            try {
                java.net.URL(url).openStream().use { entree ->
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

tasks.named("preBuild") {
    dependsOn(fetchAapt2)
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
    }

    sourceSets.getByName("main") {
        // lot M : LICENSE et NOTICE de la racine dans les assets.
        // AGP 9 refuse un Provider ici (généré contre statique
        // indécidable) : le dossier est posé en statique et la tâche
        // de copie est câblée aux fusions d'assets ci-dessous.
        assets.srcDir(layout.buildDirectory
                .dir("generated/assets/licences").get().asFile)
    }
}

// Les tâches qui lisent le dossier d'assets généré dépendent de la
// copie des licences (lot M) — Gradle 9 exige la dépendance
// EXPLICITE : la fusion des assets ET toutes les tâches lint (le
// lint vital de release construit son modèle et analyse les assets
// sans connaître la tâche de copie — la validation du CI l'a prouvé
// rouge depuis le lot M ; un dependsOn sur une copie à jour est
// sans coût).
tasks.matching {
    it.name.contains("Lint") || it.name.startsWith("lint")
            || (it.name.startsWith("merge")
            && it.name.endsWith("Assets"))
}.configureEach {
    dependsOn(copierLicences)
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
    implementation(project(mapOf("path" to ":build-android",
            "configuration" to "toolsApi")))
    implementation(libs.r8)
    implementation(libs.apksig)
    implementation(libs.bundletool)
    implementation(libs.guava)
}
