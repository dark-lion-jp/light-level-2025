<div style="display: inline">
  <img src="https://img.shields.io/badge/Minecraft-1.21.6-white">
  <img src="https://img.shields.io/badge/Fabric_Loader-0.16.14-white">
  <img src="https://img.shields.io/badge/Fabric_API-0.127.0%2B1.21.6-white">
  <img src="https://img.shields.io/github/actions/workflow/status/dark-lion-jp/light-level-2025/build.yml?branch=main">
</div>

# Light Level 2025

Press **F9** to see the block light levels.

Inspired by [LightLevel By parzivail](https://github.com/Parzivail-Modding-Team/LightLevel), with
some improvements implemented.

![Screenshot for Over World](https://raw.githubusercontent.com/dark-lion-jp/light-level-2025/refs/heads/main/src/main/resources/assets/light_level_2025/screenshot-for-overworld.png)

## Dependencies

You can find the dependencies in the description of each release.

- **Fabric Loader**  
  [Download](https://fabricmc.net/use/installer/) from fabricmc.net

- **Fabric API**  
  [Download](https://modrinth.com/mod/fabric-api) from modrinth  
  [View source](https://github.com/FabricMC/fabric) on GitHub

## Features

### Dimension-Specific Color Coding

The colors used to represent the light levels can be customized in `config/light-level-2025.yaml` as
hex color codes.

#### Overworld

- **Hostile mobs cannot spawn**
    - color: green
    - conditions:
        - block light >= 1
    - config field: `text.color.safe`

- **Hostile mobs can spawn at night**
    - color: yellow
    - conditions:
        - block light = 0
        - sky light >= 8
    - config field: `text.color.warning`

- **Hostile mobs can always potentially spawn**
    - color: red
    - conditions:
        - block light = 0
        - sky light <= 7
    - config field: `text.color.danger`

#### The Nether

- **Mobs cannot spawn**
    - color: green
    - conditions:
        - block light >= 12
    - config field: `text.color.safe`

- **Mobs can always potentially spawn**
    - color: red
    - conditions:
        - block light <= 11
    - config field: `text.color.danger`

#### The End

- **Mobs cannot spawn**
    - color: green
    - conditions:
        - block light >= 1
    - config field: `text.color.safe`

- **Mobs can always potentially spawn**
    - color: red
    - conditions:
        - block light = 0
    - config field: `text.color.danger`

### Adjustable Text Height

If a block that doesn't have a collision box is above the target block and its appearance
obstructs the text, the light
level will be shown above its visual.

### Debug Mode

Press **F3** to show both the block light and the sky light levels.

## Technical Features

### Block Exclusion Conditions

The rendering on a target block will be skipped if:

- The target block is one of the following (config field: `block.blacklist`):
    - `minecraft:air`
    - `minecraft:barrier`
    - `minecraft:bedrock`
    - `minecraft:chain_command_block`
    - `minecraft:command_block`
    - `minecraft:end_portal`
    - `minecraft:nether_portal`
    - `minecraft:repeating_command_block`

- The target block is not opaque and visually transparent.
    - Examples Excluded:
        - `minecraft:glass`
        - `minecraft:ice`
    - Examples Not Excluded:
        - `minecraft:glowstone`
        - `minecraft:packed_ice`

- The target block does not have a full square top surface.
    - Examples Excluded:
        - bottom slabs
        - right-side-up stairs
    - Examples Not Excluded:
        - top slabs
        - up-side-down stairs

- A block above the target block has a collision shape.
    - Examples Excluded:
        - `minecraft:ladder`
        - `minecraft:snow` (layers count >= 2)
    - Examples Not Excluded:
        - `minecraft:vine`
        - `minecraft:snow` (layers count = 1)

### Caching

This mod caches information about block positions and the text to be rendered, storing it in memory
for faster access.

By setting the cache update frequency in frames, the update rate automatically adjusts based on
FPS — updating more frequently in high-FPS environments and less frequently in low-FPS environments.

You can change the update frequency via `cache.update_interval_frames` in the config file.

### Rendering Culling

This mod does not render the light level for a target block that is invisible to the player.

## Configuration

You can customize the mod behavior via `config/light-level-2025.yaml`.

- `render_distance`
    - `horizontal`: Maximum horizontal distance for rendering
        - Default: `16`
    - `vertical`: Maximum vertical distance for rendering
        - Default: `4`

- `block`
    - `blacklist`: Blocks to exclude from rendering
        - Default:
            - `minecraft:air`
            - `minecraft:barrier`
            - `minecraft:bedrock`
            - `minecraft:chain_command_block`
            - `minecraft:command_block`
            - `minecraft:end_portal`
            - `minecraft:nether_portal`
            - `minecraft:repeating_command_block`
    - `whitelist`: Blocks to include in rendering
        - Default:
            - `minecraft:mud`
            - `minecraft:slime_block`
            - `minecraft:soul_sand`

- `text`
    - `color`:
        - `safe`: Light level where hostile mobs cannot spawn
            - Default: `40FF40`
        - `warning`: Light level where hostile mobs can spawn at night in Overworld
            - Default: `FFFF40`
        - `danger`: Light level where hostile mobs can always potentially spawn
            - Default: `FF4040`
        - `neutral`: Display color for unknown or unsupported dimensions
            - Default: `FFFFFF`
    - `scale`:
        - `normal`: Text size for normal state
            - Default: `0.03125`
        - `debug`: Text size for debug screen
            - Default: `0.020833334`
    - `offset_y_base`: Height to offset the text from the block
        - Default: `0.1`

- `cache`
    - `update_interval_frames`: Frequency in frames to update the block cache
        - Default: `20`

## Contribution

We welcome contributions from the community!

To contribute, please fork the repository and create a pull request on GitHub.
We appreciate your help in making this project even better!

Please note that when we use a LLM for code proofreading, aspects of the code might change.
This isn't a reflection of any dissatisfaction or shortcomings with your original code.
