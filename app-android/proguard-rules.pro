# JoIDE — règles de conservation R8 (lot VIII, ADR-070)
#
# La passe de rétrécissement DÉDIÉE annoncée par le backlog P8 :
# « les règles de conservation des ServiceLoader des moteurs exigent
# une passe dédiée ; jamais de règle approximative ». Chaque règle
# ci-dessous porte sa RAISON ; une règle sans raison se supprime.

# --- Sans optimiseur : la machine de build a 4 Go -------------------
# Le noyau a tué le démon Gradle (tas 2,2 Go) pendant la phase
# d'optimisation de R8 sur les 41 Mo de classes du lot VII — la voie
# officielle AGP quand l'optimiseur ne tient pas : -dontoptimize dans
# les règles maison. Le rétrécissement (retrait du code mort) et
# l'obfuscation (noms courts) restent actifs : l'essentiel du gain.
-dontoptimize

# --- Le mur de chargeurs : ce qui traverse la frontière APK ↔ pack ---
# Les packs différés (r8, bundletool) portent leurs PROPRES
# adaptateurs, dexés contre CES interfaces ; renommer un type ou un
# membre casserait la liaison côté pack — l'adaptateur s'y charge PAR
# NOM (Class.forName dans BuildRunner), jamais par ServiceLoader.
-keep class jo.joide.build.android.dex.** { *; }
-keep class jo.joide.build.android.bundle.** { *; }
-keep class jo.joide.build.android.sign.** { *; }
# L'écrivain d'archives alignées fait aussi partie du mur (lot XXI) :
# l'adaptateur bundletool DU PACK l'appelle PAR NOM pour écrire la
# charge du module de base — le terrain du 2026-11-18 : « bundle
# [debug] : NoClassDefFoundError: Failed resolution of:
# Ljo/joide/build/android/packaging/ApkWriter; » — la classe vivait
# bien dans l'APK (PackageTask l'atteint) mais R8 l'avait RENOMMÉE :
# le pack, lui, lie le nom original à travers le chargeur parent.
# MurEtancheTest (build-android) vérifie que les imports du jeu de
# sources tools restent dans cette surface gardée.
-keep class jo.joide.build.android.packaging.** { *; }

# Le pont Android des packs (ChargeurDexAndroid, DexClassLoader) :
# découvert par ServiceLoader — le nom de l'implémentation et du
# service voyagent dans META-INF/services (R8 modélise le fichier,
# la règle ceinture le baudrier).
-keep class jo.joide.app.android.ChargeurDexAndroid { *; }

# L'adaptateur de signature se charge PAR NOM depuis BuildRunner
# (Class.forName("jo.joide.build.android.tools.ApksigSigner")) : le
# nom ET le constructeur sans argument se gardent — le reste de la
# classe vit derrière l'interface ApkSignerService.
-keep class jo.joide.build.android.tools.ApksigSigner {
    <init>();
}

# --- lsp4j sérialise ses messages LSP par réflexion Gson ----------
# (lsp4j instancie et remplit ses types de messages via Gson : tout
# renommage casse la sérialisation JSON du protocole.)
-keep class org.eclipse.lsp4j.** { *; }

# --- ecj compagnon : les gabarits de messages vivent en réflexion --
# Messages.loadMessagesProperties remplit les champs statiques String
# de org.eclipse.jdt.internal.compiler.util.Messages DEPUIS le bundle
# messages.properties PAR NOM DE CHAMP (Field.getName → clé du
# properties) — le terrain du 2026-11-18 : en RELEASE obfusquée, R8
# renomme les champs (compilation_request → « t ») et le chargeur
# lève « ExceptionInInitializerError: Missing message: t in:
# org.eclipse.jdt.internal.compiler.messages ». La même garde que
# l'IDE de référence (proguard-rules.pro:61) : l'ecj compagnon ne s'obfusque
# pas — mêmes classes, shims ART en plus.
-keep class org.eclipse.jdt.** { *; }

# --- kotlinx.serialization (moteurs code-lsp) ----------------------
# Le lookup réflexif du serializer compagnon des classes @Serializable.
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class jo.**$$serializer { *; }

# --- Les métadonnées Kotlin -----------------------------------------
# L'ABI KotlinFacts lit @Metadata des classes utilisateur à l'analyse
# (kotlin-metadata-jvm embarqué, décision du lot VII) : les
# annotations, signatures génériques et classes internes se gardent.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,AnnotationDefault
-keep class kotlin.Metadata { *; }

# --- Les tables de codage joni/jcodings ----------------------------
# Les tables/Transcoder_*.bin (encodages CJK des grammaires de
# surbrillance) sont des ressources RACINE du jar — le rétrécissement
# des RESSOURCES (res/, resources.arsc) ne les touche pas ; cette note
# documente pourquoi aucune règle n'est nécessaire : ne pas en ajouter.

# --- LayoutLib (aperçu de layouts) ----------------------------------
# Le moteur de rendu de mises en page charge une partie de sa
# machinerie par réflexion (Class.forName internes sur ses propres
# classes) : le paquet entier se garde — 0,5 Mo de jar, le risque
# n'en vaut pas l'économie.
-keep class jo.layoutlib.** { *; }

# --- Attentes connues -----------------------------------------------
# -dontwarn uniquement pour les références optionnelles que les
# bibliothèques déclarent mais n'exigent pas ; chaque ligne ajoutée
# ci-dessous doit nommer la bibliothèque fautive et la classe absente.

# syntax-api JetBrains (métamodule Kotlin multiplatform) : l'« actual »
# JVM de fleet.util manque du jar publié — le chemin fautif
# (fabrication de pile relative, diagnostic IntelliJ) ne charge pas
# sur l'appareil.
-dontwarn fleet.util.multiplatform.Actual

# ecj compagnon : javax.tools/javac et javax.lang.model n'existent pas
# sur ART (le batch FileSystem et l'outil EclipseCompiler d'ecj les
# référencent) — ces chemins ne chargent jamais sur l'appareil.
-dontwarn javax.tools.JavaCompiler
-dontwarn javax.lang.model.SourceVersion

# StAX (javax.xml.stream) absent d'ART : la lecture XML de
# BaseApiAndroid (inspections Java de code-lsp) reste au repli —
# le lecteur du ressort s'en sert, jamais ce chemin-là.
-dontwarn javax.xml.stream.XMLInputFactory
-dontwarn javax.xml.stream.XMLStreamReader

# KotlinCompilerDriver (pilote embarqué du BUREAU) référence le
# compilateur Kotlin — exclu de l'APK depuis le lot VII : sur
# l'appareil, la sonde BuildRunner.kotlinDriver() choisit le pilote
# FORKÉ, cette classe ne charge jamais. Liste complète produite par
# R8 (missing_rules.txt) — arguments, collecteur, compilateur et
# services de configuration.
-dontwarn org.jetbrains.kotlin.cli.common.ExitCode
-dontwarn org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
-dontwarn org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
-dontwarn org.jetbrains.kotlin.cli.common.messages.MessageCollector
-dontwarn org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
-dontwarn org.jetbrains.kotlin.config.Services

# Les feuilles manquantes du compagnon ecj gardé (la règle -keep ci-
# dessus racine-référence TOUT org.eclipse.jdt) : l'adaptateur ANT
# (org.apache.tools.ant — jamais invoqué sur ART) et deux API JDK de
# compilation (Elements$DocCommentKind, PreviewFeature) — les lignes
# générées par R8 (missing_rules.txt), la voie AGP standard.
-dontwarn javax.lang.model.util.Elements$DocCommentKind
-dontwarn jdk.internal.javac.PreviewFeature
-dontwarn org.apache.tools.ant.BuildException
-dontwarn org.apache.tools.ant.Project
-dontwarn org.apache.tools.ant.taskdefs.Javac
-dontwarn org.apache.tools.ant.taskdefs.compilers.DefaultCompilerAdapter
-dontwarn org.apache.tools.ant.types.Commandline$Argument
-dontwarn org.apache.tools.ant.types.Commandline
-dontwarn org.apache.tools.ant.types.Path
-dontwarn org.apache.tools.ant.util.JavaEnvUtils
