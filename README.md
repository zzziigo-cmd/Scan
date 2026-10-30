# Scan

Application Android de scan de sécurité pour site web.

## Prérequis

- Android Studio Hedgehog ou plus récent
- JDK 17
- Android SDK 35
- Un émulateur Android ou un appareil physique

## Configuration locale

Copiez le fichier `local.properties.example` en `local.properties` et adaptez le chemin du SDK Android :

```properties
sdk.dir=/Chemin/Vers/Android/Sdk
```

Exemples courants :

- macOS : `/Users/<votre_nom>/Library/Android/sdk`
- Linux : `/home/<votre_nom>/Android/Sdk`
- Windows : `C:\Users\<votre_nom>\AppData\Local\Android\Sdk`

## Build

Depuis la racine du projet :

```bash
./gradlew assembleDebug
```

Pour lancer l’application :

```bash
./gradlew installDebug
```

Ou dans Android Studio :

- Ouvrir le projet `Scan`
- Attendre la synchronisation Gradle
- Choisir un device
- Cliquer sur Run

## Projets Android généré

Le dépôt contient désormais la configuration Gradle minimale suivante :

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`

Cela permet de synchroniser le projet Android et de le compiler avec Gradle.

## Dépannage courant

### SDK Android introuvable
Vérifiez que `local.properties` pointe vers un SDK valide.

### Erreur de version Java
Assurez-vous d’utiliser Java 17.

```bash
java -version
```

### Erreur de dépendances
Dans Android Studio, faites :

- File > Sync Project with Gradle Files
- Build > Clean Project
- Build > Rebuild Project

## Notes

Le dépôt contient encore des dépendances et des classes Android avancées qui peuvent nécessiter des ajustements selon la version du SDK ou des plugins installés localement.
