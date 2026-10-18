# Validación de RadioIPEM388 2.2.2

Fecha: 18 de octubre de 2026. Código de versión: 5. Los cambios permanecen locales y no se han enviado a Google Play ni a GitHub.

## Comprobaciones aprobadas

- `assembleDebug`, `testDebugUnitTest` y `assembleDebugAndroidTest`: compilación satisfactoria. Cinco pruebas unitarias, cero fallos y cero errores.
- `bundleRelease` y `lintDebug`: compilación satisfactoria. Lint informa cero errores y 42 advertencias sobre versiones de bibliotecas, recursos y compatibilidad de atributos. No se hizo una migración general de dependencias.
- Instrumentación en teléfono físico Xiaomi con Android 14 / API 34: dos pruebas aprobadas, ejecución final de 32,715 segundos.
- Reproducción del MP3 incluido: estado real de ExoPlayer, pausa, reanudación y detención al cambiar entre grabación y emisión en vivo.
- El icono muestra Pausar durante la reproducción y Reproducir al pausar. El estado y el icono se mantienen al recrear la actividad mientras suena el audio.
- Cancelar la conexión en vivo mantiene la pausa tras esperar 19 segundos. No comienza reproducción tardía.
- Inspección visual de la captura de la propia app: fuentes, título de la grabación, controles, atribución y versión visibles.
- `git diff --check`: sin errores.

El MP3 se obtuvo del enlace de audio del RSS oficial de iVoox, con permiso confirmado por el titular. Se comprobó el tipo audio/mpeg, cabecera MP3, tamaño de 2.185.900 bytes y SHA-256 consignado en CONTENIDO-AUDIO.md.

## Alcance y pendientes

La app de prueba usa `ar.edu.ipem388.radio.preview`; se instaló junto a la original. El auxiliar de pruebas fue eliminado y la app de prueba quedó abierta sin reproducción automática. No se desinstaló ni modificó la aplicación original.

El primer intento de instrumentación agotó el tiempo de espera al iniciar las actividades desde las pruebas. Se adaptó el inicio mediante la misma intención de lanzamiento desde ADB. Las dos ejecuciones posteriores pasaron; la última incluye las comprobaciones adicionales del icono y de recreación.

No se asegura la disponibilidad del servidor de emisión en vivo. La grabación incluida permite escuchar contenido aun cuando ese servidor no responde. La prueba valida el estado del reproductor, no una evaluación humana del sonido.

El AAB de release generado está SIN FIRMAR, comprobado con jarsigner. Requiere la clave de carga registrada de RadioIPEM388 antes de subirlo a Google Play. No está listo para cargar tal como está.

No se probó esta versión en otros modelos, en todas las versiones de Android admitidas ni con lectores de pantalla. Queda pendiente una prueba manual de los controles de la notificación y de interrupciones por llamadas/auriculares. La actualización no garantiza por sí sola la eliminación del aviso de inactividad de la cuenta.
