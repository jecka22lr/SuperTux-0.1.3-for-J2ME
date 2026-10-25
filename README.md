# SuperTux 0.1.3 for J2ME

A Java ME (MIDP 2.0 / CLDC 1.1) remake of **SuperTux 0.1.3** for feature phones, built from 0.1.3's own
levels, graphics, sounds and music. The title screen, menus and level editor follow the
[Symbian S60v3 port](https://github.com/jecka22lr/SuperTux-for-Symbian-S60v3-FP1) (Final Release 1.1).

**Current version: Beta Release 1.1**

## Download

Get the jars from the [Releases](../../releases) page:

| File | For |
|---|---|
| `SuperTux-BetaRelease1.1.jar` + `.jad` | Most phones (MP3 music, 3.6 MB) |
| `SuperTux-BetaRelease1.1-lite.jar` + `.jad` | Phones with a 1 MB jar limit (MIDI music, 0.9 MB) |

Copy the jar (and the jad if your phone asks for one) to the phone. It also runs in
[FreeJ2ME-Plus](https://github.com/TASEmulators/freej2me-plus), J2ME Loader (Android) and KEmulator.
Made for 240x320; landscape 320x240 works too, and on an upright phone the picture is turned sideways.

## Features

- All of Icyisland plus Bonus Island I and II, on the world maps, with 5 save slots
- Title screen with the demo level, 0.1.3's menus, intro, extro and credits
- All bad guys, power-ups, sound effects and music (MP3 or MIDI)
- Level editor with the Symbian build's button bar and keypad pointer; edit the built-in levels or make your own level subsets
- Keyboard Setup (one key per action, like the Symbian build), touch controls, high score
- Portrait or landscape, Show FPS, sound and music toggles

## Keys (phone upright, defaults)

| Key | Action |
|---|---|
| Joystick left/right, 4 / 6 | walk |
| 1 / 3 | run left / right |
| Joystick up, 2, centre, 5 | jump (5 / centre also select) |
| 7 / 9 | jump while walking left / right |
| Joystick down, 8 | duck |
| 0 | run / fire / hold a Mr. Iceblock |
| Softkey, * or # | pause menu / back |

## Building

- `tools/convert.py` turns the 0.1.3 data (the half-size GP2X/Symbian data) into `res/`. It needs Python 3 with Pillow, plus ffmpeg (with libopenmpt) and ImageMagick.
- `build.sh` compiles with JDK 8 (`-source 1.3 -target 1.3`) against any MIDP 2.0 / CLDC 1.1 API jar, then preverifies and shrinks with ProGuard (`-microedition`). Edit the paths at the top of the script.
- `harness/` is a desktop stand-in for the phone APIs, used by `test.sh` for scripted screenshot tests.

## Credits

- SuperTux 0.1.3 by the SuperTux Devel Team
- Symbian port originally by Lars "Anotherguest"
- 2026 J2ME port by Jecka22LR

This is a fan port, not an official SuperTux release.

## Licence

GPL v2 or later, like SuperTux 0.1.3 and its data. See [COPYING](COPYING).
