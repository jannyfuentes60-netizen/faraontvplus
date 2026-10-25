# Faraón TV — TV en vivo

Interfaz independiente de streaming inspirada en plataformas de televisión, con menú lateral, buscador, favoritos, tarjetas de canales y reproductor adaptable a móvil y PC. No es un producto oficial de izzi y no utiliza sus recursos propietarios.

## Catálogo de canales
- México: https://iptv-org.github.io/iptv/countries/mx.m3u
- Respaldo internacional: https://iptv-org.github.io/iptv/index.m3u
- Proyecto del catálogo: https://github.com/iptv-org/iptv

Las señales pueden estar fuera de línea, restringidas por región o no ser compatibles con todos los navegadores.

## Publicar
Es una web estática: no requiere build ni variables de entorno. En GitHub entra a **Settings → Pages**, selecciona **Deploy from a branch**, la rama `main` y la carpeta `/(root)`.

Utiliza solo señales que tengas derecho a reproducir en tu región. Este proyecto no incluye suscripciones, credenciales ni acceso a canales de pago.


## Aplicación Android nativa

El proyecto Android está en la carpeta `android/` y usa Kotlin, Jetpack Compose y Media3/ExoPlayer. Incluye búsqueda, filtros, favoritos guardados en el dispositivo y reproducción de señales compatibles, con una interfaz adaptable a teléfonos y Android TV.

Cada cambio en `android/` ejecuta el workflow **Build Faraon TV Plus APK**. Cuando termine correctamente, abre **Actions → Build Faraon TV Plus APK → ejecución más reciente → Artifacts → Faraon-TV-Plus-APK** para descargar el APK instalable. También puedes ejecutar el workflow manualmente desde Actions.

La aplicación usa las mismas fuentes públicas M3U principales de la web; algunas señales pueden no funcionar en todos los dispositivos o regiones. Este APK inicial es de depuración, no una versión firmada para publicar en Play Store.
