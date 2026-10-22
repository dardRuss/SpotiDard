# SpotyDard (Minecraft HUD mod)

A Fabric mod for Minecraft 26.3 (Fabric Loader 0.20.1): a HUD widget showing the current Spotify track — album art, title, artists, progress bar and time.

**Spotify Premium** is required.

## Building
```powershell
$env:JAVA_HOME='<path to JDK 25>'
.\gradlew.bat build
```
The resulting jar: `build\libs\spotifyplayer-1.0.0.jar`.

## Installation
1. Install Fabric Loader 0.20.1 for Minecraft 26.3.
2. Put `spotifyplayer-1.0.0.jar` and **Fabric API** (`fabric-api-0.164.0+26.3.jar`) into the `.minecraft\mods` folder.
3. (Optional) ModMenu 21.0.0 to access the settings from the mod list.

## Spotify API setup
1. Go to https://developer.spotify.com/dashboard → Create app.
2. Redirect URI: `http://127.0.0.1:5500/callback` (the port can be any, use the same one in the mod settings).
3. API: Web API. Copy the **Client ID**.
4. In game, open the mod settings (U key / ModMenu — "Settings" button), paste the Client ID, click **Log in** — a browser will open, confirm the sign-in.

Settings and tokens are stored in `.minecraft\config\spotifyplayer.json`.

## Controls (default, configurable)
| Action | Key |
|---|---|
| Pause / play | P |
| Next track | N |
| Previous track | B |
| Shuffle | I |
| Repeat | O |
| Volume +/− | ↑ / ↓ |
| Settings | U |

HUD position: below/above the hotbar, left/center/right, frame on/off, color scheme.

The mod supports English and Russian languages (selected automatically based on the game language).
