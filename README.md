# SpotyDard (Minecraft HUD-мод)

Fabric-мод для Minecraft 26.3 (Fabric Loader 0.20.1): HUD-виджет с текущим треком Spotify — обложка, название, исполнители, полоса прогресса и время.

Требуется **Spotify Premium**.

## Сборка
```powershell
$env:JAVA_HOME='C:\Users\suvor\AppData\Local\Temp\opencode\jdk25\jdk-25.0.4.1+1'
.\gradlew.bat build
```
Готовый jar: `build\libs\spotifyplayer-1.0.0.jar`.

## Установка
1. Установите Fabric Loader 0.20.1 для Minecraft 26.3.
2. Положите `spotifyplayer-1.0.0.jar` и **Fabric API** (`fabric-api-0.164.0+26.3.jar`) в папку `.minecraft\mods`.
3. (Опционально) ModMenu 21.0.0 для входа в настройки через список модов.

## Настройка Spotify API
1. Зайдите на https://developer.spotify.com/dashboard → Create app.
2. Redirect URI: `http://127.0.0.1:5500/callback` (порт можно любой, задайте такой же в настройках мода).
3. API: Web API. Скопируйте **Client ID**.
4. В игре откройте настройки мода (клавиша U / ModMenu — кнопка «Настройки»), вставьте Client ID, нажмите **Войти в Spotify** — откроется браузер, подтвердите вход.

Настройки и токены хранятся в `.minecraft\config\spotifyplayer.json`.

## Управление (по умолчанию, настраивается)
| Действие | Клавиша |
|---|---|
| Пауза/продолжить | P |
| Следующий трек | N |
| Предыдущий трек | B |
| Шафл | I |
| Повтор | O |
| Громкость +/− | ↑ / ↓ |
| Настройки | U |

Позиция HUD: ниже/выше хотбара, слева/по центру/справа, рамка вкл/выкл, цветовая схема.
