SuperTux for J2ME (MIDP 2.0 / CLDC 1.1, 240x320) - work in progress
A Java remake of SuperTux 0.1.3 for feature phones, built from 0.1.3's levels and graphics, with the
title screen and menus of Jecka's 320x240 Symbian build.

Install: copy dist\SuperTux.jar (and SuperTux.jad if the phone wants it) to the phone.
SuperTux-lite.jar is for phones with a 1 MB JAR limit (MIDI music instead of MP3).
Also runs in J2ME Loader (Android), KEmulator or FreeJ2ME (Windows).

Keys (phone upright)
  Joystick left/right, 4/6   walk
  1 / 3                      run left / right
  Joystick up, 2             jump
  Centre key, 5              jump / select
  7 / 9                      jump while walking left / right
  Joystick down, 8           duck (big Tux)
  0                          run / fire / hold a flat Mr. Iceblock
  Softkey, * or #            pause menu / back
  C (clear) on a save slot   delete the slot
In landscape on an upright phone the picture is turned sideways (hold the phone with the keypad
on the right) and the keys turn with it. Options > Landscape switches.

Level editor (main menu): the Symbian build's editor on the keypad. Joystick / 2468 move the
pointer, 5 or centre clicks (hold to paint), 1 / 3 scroll, # held while moving drags the level,
0 grid, * help, softkey the editor menu. Levels and subsets are saved on the phone; your own
subsets show up under Bonus Levels.
Options > Keyboard Setup: select a field, then press the key. Like the Symbian build each action
has one key, so the old key stops working (joystick and 1/3/7/9 stay). Upright and sideways
have separate setups; Default Keys resets.
A new high score asks for a name after game over (centre key / Space in FreeJ2ME confirms).

Build 3: title screen with the demo level, logo and menus as in the Symbian build (Start Game with
5 save slots, Bonus Levels, Options, Controls, Credits, Quit), the Icyisland world map and both Bonus
Island maps with saving, level intro after every death, game over back to the title, the intro,
extro and credits texts, sound effects, Show FPS.
Build 4: level editor, Keyboard Setup, high score, fades between screens.
Build 5: Symbian-style key rebinding, GPL notice at start, music pauses during calls.
Beta Release 1.1: touch screens (Options > Show touch: arrows, FIRE and JUMP buttons, tap menus,
tap Tux on the map to enter, tap where to walk; the editor pointer follows the finger), icon.

Layout
  src\      the game (Java 1.3 source level, CLDC 1.1 APIs only)
  tools\    convert.py: 0.1.3 data -> res\ (atlases, tables, levels, world maps, texts, sounds, music)
            mod2mid.py + xm.py: 0.1.3's XM songs -> General MIDI
  harness\  desktop stand-in for the phone APIs, used for screenshot tests while developing
  dist\     SuperTux.jar + SuperTux.jad, SuperTux-lite.jar + .jad

Licence: GPL v2 or later, like SuperTux 0.1.3 and its graphics.
