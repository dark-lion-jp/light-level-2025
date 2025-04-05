# Light Level 2025

Press **F9** to see the block-light level.

Inspired by [LightLevel By parzivail](https://github.com/Parzivail-Modding-Team/LightLevel), with
some improvements implemented.

<div style="display: inline">
  <img src="https://img.shields.io/badge/Minecraft-1.21.5-white">
  <img src="https://img.shields.io/badge/Fabric_Loader-0.16.12-white">
  <img src="https://img.shields.io/github/actions/workflow/status/dark-lion-jp/light-level-2025/build.yml?branch=main">
</div>

## Features

### Dimension-Specific Color Coding

- **OVERWORLD**
    - <span style="color: #40FF40;">GREEN</span>: Mobs cannot spawn
        - Block >= 1
    - <span style="color: #FFFF40;">YELLOW</span>: Mobs can spawn at night
        - Block = 0
        - Sky >= 8
    - <span style="color: #FF4040;">RED</span>: Mobs can always potentially spawn
        - Block = 0
        - Sky <= 7
- **THE NETHER**
    - <span style="color: #40FF40;">GREEN</span>: Mobs cannot spawn
        - Block >= 12
    - <span style="color: #FF4040;">RED</span>: Mobs can always potentially spawn
        - Block <= 11
- **THE END**
    - <span style="color: #40FF40;">GREEN</span>: Mobs cannot spawn
        - Block >= 1
    - <span style="color: #FF4040;">RED</span>: Mobs can always potentially spawn
        - Block = 0

### Debug Mode

- Press **F3** to show both the block-light and the sky-light.