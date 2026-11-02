# SuperTux 0.1.3 for J2ME MIDP2
A rewrite of SuperTux 0.1.3 for J2ME devices. 240p, landscape or portrait. Not my own game - only rewrote it with the assistance of AI.

# Benchmarks?
Sony Ericsson Cedar - Full speed, no known issues. Virtually perfect.

Nokia E71 - Runs at full speed unless Sound is on due to some issues with 369MHz Symbian 9.2 phones. Triggering sound effects results in lag spikes. Nokia sound mode should fix it.

Sony Ericsson z780a - Runs well, though sound effects create very small lag spikes, like on the E71.

Samsung OmniaPRO4 - Full speed. Perfect. Optimal.

Nokia N82 - Nearly if not full speed on Low detail, pretty good on High detail, needs Nokia sound mode or turned off sound for optimal performance.

LG t375 - 9-10 FPS on Landscape, 12 FPS on Portrait, 15 FPS with only Sound or Music turned on with strange audio effects if only Music is on.

J2ME Loader, Android: 33FPS, stable (Locked to 32FPS)

Sony Ericsson Vivaz - Full speed. (Was centered and small before Stretch.)

Samsung Omnia - Full speed. (Was centered and small before Stretch.)

Nokia E75 - Full speed with sound and music on.

Nokia E72 - Full speed with sound and music on.

Nokia E52 - Full speed with sound and music on.

Sony Ericsson Aino - Full speed with music and sound on.

Sony Ericsson TXT Pro - Laggy on Full detail. Use Low detail (Normal Low and Lite Low start on it).

Samsung Wave 533 - 25 FPS on Low detail with sound and music off. Full detail with music and sound is laggy, 10+ FPS.

# What changed between the original port and this?
Brutal rewrite of all of the code. Like, pretty much every line of code was adapted to Java... but the entire game is made to be as much of a 1:1 recreation as possible.

# How to install?
Pick the Edition that fits your phone. Every Edition installs as its own app (SuperTux F, N, L, M, NL, LL...), so you can keep several side by side, each with its own saves. JADs are there if your phone needs them.

| Edition | File | For |
|---|---|---|
| Normal | SuperTux-Normal.jar | Most phones: everything in the game, MP3 music where the phone plays it |
| Lite | SuperTux-Lite.jar | Phones with a 1 MB jar limit (Nokia S40 and others): MIDI music, all Detail modes |
| Normal Low / Lite Low | SuperTux-Normal-Low.jar / SuperTux-Lite-Low.jar | Normal or Lite starting on Low detail, for slow phones (Sony Ericsson TXT Pro and such) |
| Minimal | SuperTux-Minimal.jar | Phones with about 2 MB of Java memory (Nokia 6600 fold and others): Max Potato only, pictures loaded a piece at a time |
| Full | SuperTux-Full.jar | Normal plus Adventure To The New World and I Wanna Be The SuperTux (separate ATTNW release) |
| Full ATTNW | SuperTux-Full-ATTNW.jar | ATTNW's own lineup at full quality (separate ATTNW release) |
| Minimal ATTNW | SuperTux-Minimal-ATTNW.jar | ATTNW's lineup for 2 MB phones (separate ATTNW release) |

# Requirements
This has not yet been tested on enough of real firmware and as such I have no real set requirements right now, but I have bought some devices for testing and some others will test it for me. See benchmarks for a realistic preview.

# Issues
Lite version of this game has very shitty MIDIs for the soundtrack. I might automate something to make them chiptune versions of the soundtrack. If you wanna make some human-made stuff I would much prefer that!
On Symbian S60 platform phones with 369MHz CPUs, there are annoying lag spikes for every time a sound effect is played. This is not yet mitigated by different sound effect options except for OFF. This is a known issue unfortunately and allegedly some commercial J2ME games shipped without SFX for these.

# What's new in Release 1.3.1 (hotfix)
Options > Stretch for screens bigger than the game (WVGA, nHD and such): Off keeps it at its size in the middle, Fit scales it up as far as it goes keeping its shape (the default), Fill covers the whole screen. Works sideways too, and taps land where you aim.

Keys set in Keyboard Setup (QWERTY letters and such) now work in the menus too.

Normal Low and Lite Low: the same as Normal and Lite, but starting on Low detail for slow phones. Each installs as its own app.

# What's new in Release 1.3
Editions: Full, Normal, Lite and Minimal (plus ATTNW versions of Full and Minimal), each installing as its own app.

Minimal Edition for phones with about 2 MB of Java memory, like the Nokia 6600 fold: the tile and sprite sheets come in small pages and only the ones the level, world map or editor shows are kept; the pages for parts of a level already played are let go as you go (levels that scroll back keep theirs). The level editor works there too, and Options > Animations brings back animated tiles, bouncing bricks and coins, floating scores and the walking title at no extra memory. Show FPS adds a line with free memory and how much the pictures take.

Lite Edition: tiles in pages as well; with RAM saver on only the level's are kept.

ATTNW's crowded levels: bad guys only check bad guys near them for collisions, with the same results as before, so levels with hundreds of them run far faster on slow phones.

Controls: separate key setups for Portrait and Landscape, landscape layout whenever the screen is wider than tall (no more black bars), the joystick read on its own so number keys don't move Tux, the last key the phone sent shown in Keyboard Setup, and Options > Toggle run.

Audio: SFX volume and Music volume (25 to 100%), and a reworked Nokia sound mode (the only lag-free one on some Symbian phones).

Physics: Tux moves in small steps, so slow phones jump as high and walk down stairs like fast ones; no more tiny hops at the end of a level on emulators that skip frames.

Fixes: waterfall tiles, the Max Potato menu, the editor pointer reaching its buttons, and more. The high score name entry is out for now.

# What's new in Beta Release 1.2
Community levels made for 0.1.3 under Bonus Levels: Tux Strikes Back by Christian Schaefers (19 levels and its story) and four classic CT's Domain levels by CT. da'Bomb and UnNamed, from the SuperTux add-on collection.

More options for slow phones:
| Option | What it does |
|---|---|
| Detail: Low | Plain sky colour instead of the background picture, no snow or clouds, level tiles drawn from a cached image (only coins and other animated tiles are drawn every frame), no HUD shadow, outlined touch buttons in landscape |
| Detail: Potato | Also: tiles drawn from a cached image (only newly scrolled columns get drawn), no tile, enemy, fireball or star animations, defeated enemies vanish, no flying scores, coins or brick pieces, no fades, menu slides or HUD text shadows, solid cached menu boxes, credits on an About screen instead of the title |
| Detail: Max Potato | For the slowest phones: Potato plus hard-edged sprites (no half-see-through pixels), a still title screen, and bad guys far off screen wait until Tux comes near |
| Draw cap | Max, 20, 15 or 10 drawn frames a second while the game keeps running at full speed: choppier, much less work |
| Music: MIDI | No MP3 decoding |
| Sound: Fast / MIDI / Tones / Nokia | Other ways of playing sound effects, for phones whose Java freezes when one starts (Tones: plain beeps that work on any phone) |
| FPS cap | Max, 25, 20 or 15. Fewer frames leaves the phone idle time, which can smooth out sound and music |
| RAM saver | Memory instead of speed, with the same look: no tile cache or cached touch buttons, no stretched copy of the background picture (sky colour above it instead), and the logo, story and score pictures are freed before each level loads |

The MIDI versions of the tracker songs (lite jar, Music: MIDI, phones without MP3) now have a softer chiptune sound.

Options > Option Help explains every option on a few pages, like the Controls screen.

With Show touch on, a MENU box under the lives pauses the game; a stray tap on the level no longer does.

Adventure To The New World (ATTNW) by Miksy91 is supported by the converter. Its music and many of its backgrounds come from other games, so the ATTNW Editions are only attached to a separate ATTNW release and their files aren't in this repository. With the mod's files, `tools/convert.py` and `build.sh` build them yourself.
