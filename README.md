# RadioIPEM388 — actualización 2.2.2 en preparación

Aplicación Android de Radio Escolar 388. Código Kotlin y Jetpack Compose. Conserva la identidad y los enlaces del proyecto original.

## Reproducción

La pantalla permite elegir entre **Programa grabado** y **En vivo**. Al abrir una instalación nueva queda seleccionado el programa grabado «El Molle de beber», incluido en la app con permiso del titular. Se puede escuchar sin internet. El título, la atribución y el enlace a iVoox están visibles. Consultar `CONTENIDO-AUDIO.md` para la procedencia y los derechos del archivo.

La opción En vivo busca el stream en el portal de la emisora. El enlace fijo obsoleto dejó de usarse. Si el servidor devuelve una página HTML en lugar de audio, se informa que la emisión no está disponible. La app realiza como máximo tres reintentos por interrupción, con esperas de 2, 5 y 10 segundos. Nunca cambia automáticamente a una grabación ni a otra emisora.

El botón Reproducir envía una sola orden. La pantalla muestra Conectando, Reproduciendo, En pausa, Detenido, Programa finalizado o un error. Pausar y cambiar de fuente cancelan la resolución y los reintentos pendientes. Consultar el estado al abrir la pantalla no crea un servicio ni inicia una conexión.

El servicio publica su notificación de reproducción antes de resolver la dirección del stream. Los controles de la notificación y la sesión multimedia permiten reproducir, pausar y detener. ExoPlayer gestiona el foco de audio y la desconexión de auriculares.

## Abrir y compilar

Abrir esta carpeta en Android Studio con JDK 21 y Android SDK 36. El proyecto conserva Gradle 8.13, AGP 8.12.3 y Kotlin 2.0.21. Android mínimo: API 21. Android objetivo: API 36. Android Studio crea `local.properties` con la ruta del SDK de cada equipo.

- Release: `ar.edu.ipem388.radio`, código 5, versión 2.2.2. Requiere la clave de carga de esta aplicación antes de subir a Google Play.
- Debug: `ar.edu.ipem388.radio.preview`, nombre RadioIPEM388 · Prueba. Se instala junto a la app original sin reemplazarla.

Comandos habituales desde la raíz:

```text
gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest
gradlew.bat connectedDebugAndroidTest
gradlew.bat bundleRelease lintDebug
```

## Pruebas

Las pruebas unitarias comprueban el presupuesto de reconexión, la selección de fuentes, la extracción de URLs del proveedor y el rechazo de respuestas HTML. Las pruebas de dispositivo comprueban reproducción, pausa y reanudación del MP3 real, detención al cambiar de fuente, cambio del icono, conservación del estado al recrear la pantalla y cancelación de una conexión en vivo. Guardan una captura de la app en `files/radio_preview.png`.

La instrumentación usa el paquete de prueba `.preview`. En Android 13 o posterior concede a esa app el permiso de notificaciones para verificar sus controles. No modifica permisos de la app original ni ajustes globales del teléfono.

Consultar `VALIDACION.md` para los resultados realmente ejecutados. La preparación del código no equivale a una prueba aprobada ni a una publicación en Google Play.

## Firma y publicación

No guardar claves ni contraseñas en el repositorio. No usar la clave de AquaVuelo para reemplazar la clave de carga registrada de RadioIPEM388. Comprobar el certificado esperado en Play Console antes de firmar una actualización.

La versión permanece en preparación local. La carga a GitHub, el envío de una consulta a soporte y la publicación en Google Play requieren la confirmación del titular.

Código con licencia MIT. La grabación conserva los derechos de sus autores según `CONTENIDO-AUDIO.md`.
