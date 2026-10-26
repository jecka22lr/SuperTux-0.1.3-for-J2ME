# SuperTux 0.1.3 for J2ME MIDP2
A rewrite of SuperTux 0.1.3 for J2ME devices. 240p, landscape or portrait. Not my own game - only rewrote it with the assistance of AI.

# Benchmarks?
Sony Ericsson Cedar - Full speed, no known issues. Virtually perfect.

Nokia E71 - Runs at full speed unless Sound is on due to some issues with 369MHz Symbian 9.2 phones. Triggering sound effects results in lag spikes.

LG t375 - 9-10 FPS on Landscape, 12 FPS on Portrait, 15 FPS with only Sound or Music turned on with strange audio effects if only Music is on.

J2ME Loader, Android: 33FPS, stable (Locked to 32FPS)

# What changed between the original port and this?
Brutal rewrite of all of the code. Like, pretty much every line of code was adapted to Java... but the entire game is made to be as much of a 1:1 recreation as possible.

# How to install?
Lite JAR for 1MB limitations in OSes like Nokia S40, otherwise use the regular JAR. JAD available if needed.

# Requirements
This has not yet been tested on real firmware and as such I have no real expectations right now, but I have bought some devices for testing.

# Issues
Lite version of this game has very shitty MIDIs for the soundtrack. I might automate something to make them chiptune versions of the soundtrack. If you wanna make some human-made stuff I would much prefer that!
On Symbian S60 platform phones with 369MHz CPUs, there are annoying lag spikes for every time a sound effect is played. This is not yet mitigated by different sound effect options except for OFF. This is a known issue unfortunately and allegedly some commercial J2ME games shipped without SFX for these.

# What's new in Beta Release 1.2
Community levels made for 0.1.3 under Bonus Levels: Tux Strikes Back by Christian Schaefers (19 levels and its story) and four classic CT's Domain levels by CT. da'Bomb and UnNamed, from the SuperTux add-on collection.

More options for slow phones:
| Option | What it does |
|---|---|
| Detail: Low | Plain sky colour instead of the background picture, no snow or clouds, level tiles drawn from a cached image (only coins and other animated tiles are drawn every frame), no HUD shadow, outlined touch buttons in landscape |
| Detail: Potato | Also: tiles drawn from a cached image (only newly scrolled columns get drawn), no tile, enemy, fireball or star animations, defeated enemies vanish, no flying scores, coins or brick pieces, no fades, menu slides or HUD text shadows, solid menu boxes |
| Music: MIDI | No MP3 decoding |
| Sound: Fast / MIDI / Nokia | Other ways of playing sound effects, for phones whose Java freezes when one starts |
| FPS cap | Max, 25, 20 or 15. Fewer frames leaves the phone idle time, which can smooth out sound and music |
| RAM saver | Memory instead of speed, with the same look: no tile cache or cached touch buttons, no stretched copy of the background picture (sky colour above it instead), and the logo, story and score pictures are freed before each level loads |

The MIDI versions of the tracker songs (lite jar, Music: MIDI, phones without MP3) now have a softer chiptune sound.

Options > Option Help explains every option on a few pages, like the Controls screen.

With Show touch on, a MENU box under the lives pauses the game; a stray tap on the level no longer does.

Adventure To The New World (ATTNW) by Miksy91 is supported by the converter, but it's not in the released jars since its music and many of its backgrounds come from other games. With the mod's files, `tools/convert.py` and `build.sh` build `SuperTux.jar` (everything) and `SuperTux-ATTNW.jar` (the mod alone) yourself.
