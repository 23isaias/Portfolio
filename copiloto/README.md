# Copiloto

App Android para conductores novatos: graba tus viajes automáticamente cuando el
móvil se conecta a Android Auto. Más adelante puntuará tu conducción y mostrará
una fogata animada en la pantalla del coche cuando estés aparcado.

## Estado: etapa 2 de 6

| Etapa | Qué añade | Estado |
|---|---|---|
| 1 | Detectar la conexión al coche y guardar cada viaje (fecha y duración) | Hecha |
| 2 | Distancia y ruta con GPS, coste estimado del viaje | Hecha |
| 3 | Puntuación de conducción con el acelerómetro | Pendiente |
| 4 | Pantalla en Android Auto (nota del último viaje, racha) | Pendiente |
| 5 | Medallas y rachas | Pendiente |
| 6 | Fogata animada en la pantalla del coche, solo aparcado | Pendiente |

## Cómo abrirla y probarla

1. Instala [Android Studio](https://developer.android.com/studio) (gratis).
2. En Android Studio: **File > Open** y elige la carpeta `copiloto` de este repositorio.
3. Espera a que termine la sincronización (la primera vez descarga bastantes cosas).
4. Conecta tu móvil por USB con la *depuración USB* activada, o crea un emulador
   en **Device Manager**.
5. Pulsa el botón verde **Run**.

En la app:

- **Simular viaje** inicia un viaje de prueba sin necesidad de coche; **Terminar
  viaje** lo guarda en el historial.
- **Detección automática** deja un servicio vigilando: al conectar el móvil a
  Android Auto empieza un viaje y al desconectarlo lo termina. Tras reiniciar el
  móvil hay que volver a activarla. Al activarla pide permiso de ubicación: con
  él, cada viaje mide la distancia y guarda la ruta con el GPS.
- **Mi coche** guarda el precio del litro y el consumo de tu coche; con ellos se
  estima el coste de cada viaje.
- Toca un viaje del **historial** para ver el dibujo de su ruta.

Para probar el GPS sin moverte, usa un emulador: en sus controles extendidos
(**⋮ > Location > Routes**) puedes reproducir una ruta mientras hay un viaje en
curso con la detección automática activada.

## Cómo está organizado el código

Todo está en `app/src/main/java/com/copiloto/novato/`:

| Archivo | Para qué sirve |
|---|---|
| `Trip.kt` | Qué datos tiene un viaje |
| `CarSettings.kt` | Precio del combustible y consumo del coche |
| `TripStore.kt` | Guarda y lee los viajes (archivo `trips.json` dentro de la app) |
| `CarWatcherService.kt` | Servicio que detecta la conexión con Android Auto y escucha el GPS |
| `MainActivity.kt` | La pantalla del móvil |
| `CopilotoApp.kt` | Arranque de la app |
