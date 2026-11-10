# Mise — app de cocina para Android

Mise consta de una app principal para teléfono Android y una app complementaria para Wear OS. La web ya no forma parte del proyecto.

## APK

- `mise-mobile.apk`: cliente para teléfonos Android, identificador `com.mise.app`.
- `mise-wear.apk`: cliente para relojes Wear OS. Comparte el identificador `com.mise.app` con la app móvil para sincronizarse mediante Wear OS Data Layer API.

Instala ambos APK en un teléfono Android y un reloj Wear OS emparejado entre sí. Ambos dispositivos deben tener Google Play Services y las dos apps deben estar firmadas con el mismo certificado. Abre Mise en el teléfono para publicar el recetario; el reloj lo recibe al conectarse y actualiza la lista abierta. La sincronización se almacena en Wear OS y se reintenta cuando los dispositivos vuelven a conectarse.

El móvil es el origen de las recetas. El reloj está destinado a consultar las recetas sincronizadas y continuar la cocción; las recetas guardadas en el reloj no se sincronizan de vuelta al teléfono. Si tenías instalada una versión anterior del reloj con el paquete `com.mise.app.wear`, desinstálala e instala el nuevo `mise-wear.apk`.

Las búsquedas por voz de recetas usan la API en español de Wikilibros. Requieren conexión a internet y no necesitan credenciales. El contenido importado conserva la atribución y el enlace a Wikilibros (CC BY-SA 4.0).

## Reconstruir en Windows

Con Android SDK y JDK 17 instalados, ejecuta:

```powershell
powershell -ExecutionPolicy Bypass -File .\android\build-apks.ps1
```

El script descarga las dependencias de Google Play Services necesarias para Data Layer API, compila y firma ambos APK. Guarda un keystore de depuración en `android/build/mise-debug.keystore`; no lo uses para publicar en Google Play.
