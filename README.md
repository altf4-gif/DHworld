# MrHgame

Мини-игра: плоская плита 60×60 и ходячие 3D-модели из `../MrH`.

## Что нужно
- `DHsim-main/` рядом (там же, где `MrH/`)
- `MrH/` с паками моделей (`.obj + .mtl + .png` в подпапках + `rig.txt`)
- Интернет при первой сборке (build.bat сам качает LWJGL + JOML в `DHsim-main\lib`)

## Запуск
1. `build.bat` — сам скачивает LWJGL/JOML в `..\DHsim-main\lib` (если их нет),
   копирует 5 классов движка и компилирует.
2. `run.bat` — запускает игру.

`setup.bat` из `DHsim-main` теперь **не обязателен** для MrHgame — build.bat его заменяет.
(Но если ты хочешь ещё и сам DHsim-main запускать, setup.bat всё равно нужен.)

## Если интернет закрыт / curl не работает
Запусти `DHsim-main\setup.bat` один раз — он скачает те же jar'ы в `DHsim-main\lib`.
Или вручную скачай 9 jar'ов с `https://repo1.maven.org/maven2/` по путям:
```

org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3.jar
org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar
org/lwjgl/lwjgl-glfw/3.4.3/lwjgl-glfw-3.4.3.jar
org/lwjgl/lwjgl-glfw/3.4.3/lwjgl-glfw-3.4.3-natives-windows.jar
org/lwjgl/lwjgl-opengl/3.4.3/lwjgl-opengl-3.4.3.jar
org/lwjgl/lwjgl-opengl/3.4.3/lwjgl-opengl-3.4.3-natives-windows.jar
org/lwjgl/lwjgl-stb/3.4.3/lwjgl-stb-3.4.3.jar
org/lwjgl/lwjgl-stb/3.4.3/lwjgl-stb-3.4.3-natives-windows.jar
org/joml/joml/1.10.8/joml-1.10.8.jar

```
и положи их в `DHsim-main\lib`.

## Управление
- **WASD + мышь** — летать
- **Space / Shift** — вверх / вниз
- **1..9, 0, TAB** — выбрать модель
- **E / ЛКМ** — заспавнить
- **R** — убрать всех
- **ESC** — выход

## Добавить модель
Кинь папку с `.obj + .mtl + .png` в `MrH/`. Перезапусти игру — пересобирать не надо.

## Параметры походки
`mesh/MrH/rig.txt` (копируется из `MrH/rig.txt` при первой сборке).
