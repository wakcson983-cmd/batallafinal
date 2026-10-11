# HYDRA (Fabric 1.21.1)

Dragon gigante negro y morado de 3 cabezas. 600 de vida (3x el Ender Dragon), fuego morado,
rugidos, musica de batalla final y 2 fases.

## Como se juega
1. Gira la **ruleta-colores** en color **MORADO** con el titulo **HYDRA**.
   (Si la deteccion fallara: `/hydra invocar`.)
2. HYDRA cae del cielo y espera (invulnerable).
3. Un OP escribe `/batalla final iniciar` -> empieza la musica, los ataques y los eventos.
   - Cada 2 minutos se activa un evento que dura 1 minuto (barra roja con el tiempo).
   - Fase 1: dragon con 3 cabezas. Al "morir" se levanta como 3 cabezas flotantes (fase 2).
   - Al matar las 3 cabezas: victoria y botin.
4. `/batalla final detener` cancela todo. `/hydra evento` fuerza un evento (para probar).

## Eventos
Propios: Dificultad Pesadilla, Tregua, PvP forzado, Teletransporte, Tormenta electrica, Oscuridad,
Lluvia de fuego morado.
Si estan instalados: momentoshardcore (acido, magma, creepers, vida oculta) y ruletaazul (sus 8 eventos).
`ruletahardcore` es de Forge, no se puede cargar junto a un mod Fabric.

## Compilar el .jar con GitHub
1. Crea un repositorio nuevo en GitHub y sube TODO el contenido de esta carpeta (incluida `.github`).
2. Pestana **Actions** -> espera a que termine "build".
3. Pestana **Releases** -> descarga `hydra-1.0.0.jar` y ponlo en `mods` (cliente y servidor).

Tip: `/gamerule mobGriefing false` evita que las explosiones rompan bloques.
