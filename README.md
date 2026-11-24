# Bing Háttér

Kis Android app, amely a Bing napi képét automatikusan beállítja kezdő- és/vagy zárolt képernyő háttérnek.

- Kotlin, Jetpack Compose (Material 3), WorkManager; minSdk 26, target/compileSdk 37
- Build: `./gradlew assembleDebug` (JDK 17+, Android SDK), tesztek: `./gradlew testDebugUnitTest`
- Képforrás: nem hivatalos Bing `HPImageArchive.aspx` végpont (hu-HU), először UHD, hiba esetén 1920x1080

## V2

- Új: „Csúsztatható háttér" opció. A kezdőképernyő képe szélesebb a kijelzőnél (max. 3 képernyőszélesség), a zárolt képernyő a megszokott vágást kapja.
