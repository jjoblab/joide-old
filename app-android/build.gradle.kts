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

android {
    defaultConfig {
        applicationId = "jo.joide.app"
        versionCode = 1
        versionName = "0.1.0"
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
