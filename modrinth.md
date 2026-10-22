# SpotyDard

SpotyDard is a Fabric mod for Minecraft that shows a Spotify HUD right in the game: the current track with album art, title, artists, a progress bar, lyrics and playback controls.

Requires **Spotify Premium**.

## Features
- Spotify track HUD: album art, title, artists, progress bar and time
- Controls right in your inventory: play/pause, next/previous, volume slider, shuffle and repeat
- Adjustable HUD position, scale and visibility
- Song lyrics display
- Hotkeys for everything
- English and Russian languages (chosen by the game language)

## How to install
1. Install **Fabric Loader 0.20.1** for Minecraft 26.3.
2. Download the **SpotyDard** mod jar and put it into your `mods` folder:
   `C:\Users\YOUR_USER\AppData\Roaming\.minecraft\mods`
3. Download **Fabric API** (`fabric-api-0.164.0+26.3.jar`) and put it in the same `mods` folder.
4. (Optional) Add **ModMenu** to open the mod settings from the mod list.
5. Launch the game and open the mod settings (key **U**), paste your Spotify **Client ID** from [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard), then press **Log in** and confirm in the browser.

## Spotify Client ID setup
1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard).
2. Create an app (API: **Web API**).
3. Add a Redirect URI: `http://127.0.0.1:5500/callback` (use the same port in the mod settings).
4. Copy the **Client ID** and paste it into the mod settings in the game.

## Default hotkeys
| Action | Key |
|---|---|
| Pause / play | P |
| Next track | N |
| Previous track | B |
| Shuffle | I |
| Repeat | O |
| Volume +/− | Up / Down |
| Settings | U |

All hotkeys can be changed in Minecraft controls settings.
