SuperTux for J2ME (MIDP 2.0 / CLDC 1.1, 240x320) - work in progress
A Java remake of SuperTux 0.1.3 for feature phones, built from 0.1.3's levels and graphics, with the
title screen and menus of Jecka's 320x240 Symbian build.

Install: copy the jar (and the jad if the phone wants it) to the phone. Releases:
SuperTux-BetaRelease1.2.jar (MP3 music) and SuperTux-BetaRelease1.2-lite.jar for phones with a
1 MB JAR limit (MIDI music instead of MP3).
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
Bonus Levels also hold community levels made for 0.1.3: Tux Strikes Back by Christian Schaefers
(19 levels, with its story) and four classic CT's Domain levels by CT. da'Bomb and UnNamed, from the
SuperTux add-on collection (GPL like all SuperTux levels). convert.py takes the collection's levels
folder as a fourth argument.
Adventure To The New World (ATTNW) by Miksy91 (convert.py's fifth argument: the folder holding ADV 1-3):
its three adventures are picked under Start Game, each with its own save slots, and Bonus Levels
adds Bonus Island III, the Islands of Illusion, the hard adventure and I Wanna Be The SuperTux. The
full jar has it next to Icyisland; SuperTux-ATTNW.jar has only the mod; SuperTux-noattnw.jar
(released as SuperTux-BetaRelease1.2.jar) and the lite jar leave it out. Its music and many
backgrounds come from other games and sources, not covered by the GPL, so it isn't released.
Options for slow phones: Detail Low (plain sky, no snow or clouds) or Potato (also no tile
animations, flying scores, coins or brick pieces, fades or menu slides), Music MIDI (no MP3
decoding) and the Sound modes.
Beta Release 1.1: touch screens (Options > Show touch: arrows, FIRE and JUMP buttons, tap menus,
tap Tux on the map to enter, tap where to walk; the editor pointer follows the finger), icon.
Beta Release 1.2: community levels, ATTNW support (build it yourself, see above), Music MIDI, Sound
Fast/MIDI/Nokia, Detail Low/Potato, FPS cap, RAM saver. Potato draws the tiles from a cached image
(only columns scrolling into view are drawn), freezes enemy, fireball and star animations, makes
defeated enemies vanish and drops the HUD's text shadow. HUD numbers line up; option values line up.

Layout
  src\      the game (Java 1.3 source level, CLDC 1.1 APIs only)
  tools\    convert.py: 0.1.3 data -> res\ (atlases, tables, levels, world maps, texts, sounds, music)
            mod2mid.py + xm.py: 0.1.3's XM songs -> General MIDI
  harness\  desktop stand-in for the phone APIs, used for screenshot tests while developing
  dist\     SuperTux.jar, SuperTux-noattnw.jar, SuperTux-lite.jar, SuperTux-ATTNW.jar (+ .jad each)

Licence: GPL v2 or later, like SuperTux 0.1.3 and its graphics.
