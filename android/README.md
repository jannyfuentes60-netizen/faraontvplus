# Faraón TV Plus — Android nativo

Aplicación Android nativa en Kotlin + Jetpack Compose, con reproducción mediante AndroidX Media3/ExoPlayer.

## Funciones incluidas
- Catálogo M3U público con varias fuentes y respaldo internacional.
- Búsqueda y filtros por categoría.
- Lista de favoritos durante la sesión.
- Reproducción de señales compatibles, incluidos streams HLS compatibles con ExoPlayer.
- Controles de reproducción y modo de pantalla completa.
- Interfaz adaptable a teléfonos y Android TV, con soporte de navegación por control remoto.

La disponibilidad, los derechos de emisión y la compatibilidad dependen de cada proveedor de señal. La app no incorpora credenciales ni desbloquea canales de pago.

## Generar el APK
El workflow "Build Faraon TV Plus APK" de GitHub Actions compila app-debug.apk cuando cambia la carpeta android/ o al ejecutarse manualmente. Descarga el artefacto "Faraon-TV-Plus-APK" desde la ejecución completada.

Para compilar localmente con JDK 17 y Gradle 8.9:
    cd android
    gradle assembleDebug

APK de depuración: android/app/build/outputs/apk/debug/app-debug.apk.
