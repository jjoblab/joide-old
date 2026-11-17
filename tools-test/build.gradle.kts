// tools-test — fixtures et tests des contrôles du dépôt.
// Module test-only : aucune dépendance vers un module JoIDE ; le kit
// TestKit vient du Gradle en cours d'exécution (aucune version tierce).
plugins {
    id("joide.java-library-conventions")
}

dependencies {
    "testImplementation"(gradleTestKit())
}
