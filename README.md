# Lecturas - Desafio practico 3

Proyecto Android creado para este desafio desde una estructura nueva. Usa Kotlin, Firebase Authentication y Firebase Realtime Database. Identificador Android: `com.example.lecturas`.

## Configurar Firebase

1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com/).
2. Crea Realtime Database.
3. Registra una app Android con el ID `com.example.lecturas`, descarga `google-services.json` y colocalo dentro de `app/`.
4. En Authentication > Metodo de acceso, activa Correo electronico/Contrasena.
5. En las reglas de Realtime Database, pega y publica el contenido de `database.rules.json`. Solo permite acceder a `/users/{uid}` al usuario autenticado con ese mismo UID.
6. Abre esta carpeta en Android Studio, sincroniza Gradle y ejecuta en un emulador con Google Play o un dispositivo conectado a internet.

El plugin Google Services se activa cuando encuentra `app/google-services.json`. Sin ese archivo la app puede importarse, pero Firebase no queda conectado.

## Datos guardados

- `users/{uid}/books/{bookId}`: libros y progreso.
- `users/{uid}/goals/{year}`: meta anual.
- `users/{uid}/annualPages/{year}`: variacion de paginas leidas al guardar cambios de progreso.

Incluye registro e inicio de sesion, persistencia de sesion, cierre de sesion, meta anual, alta/edicion/eliminacion de libros, biblioteca, progreso y estadisticas del año.

Las paginas anuales se calculan con los cambios de progreso guardados durante ese año; las cuentas de libros terminados y calificaciones usan su fecha de finalizacion.
