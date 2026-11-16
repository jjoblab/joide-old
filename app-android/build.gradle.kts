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

// Le vidage/merge des assets dépend de la copie des licences (lot M)
tasks.matching {
    it.name.startsWith("merge") && it.name.endsWith("Assets")
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
}
