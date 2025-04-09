# Light Level 2025

<div style="display: inline">
  <img src="https://img.shields.io/badge/Minecraft-25w15a-white">
  <img src="https://img.shields.io/badge/Fabric_Loader-0.16.13-white">
  <img src="https://img.shields.io/badge/Fabric_API-0.119.10%2B1.21.6-white">
  <img src="https://img.shields.io/github/actions/workflow/status/dark-lion-jp/light-level-2025/build.yml?branch=main">
</div>

Press **F9** to see the block light levels.

Inspired by [LightLevel By parzivail](https://github.com/Parzivail-Modding-Team/LightLevel), with
some improvements implemented.

![Screenshot for Over World](https://raw.githubusercontent.com/dark-lion-jp/light-level-2025/refs/heads/main/src/main/resources/assets/light_level_2025/screenshot-for-overworld.png)

## Dependencies

You can find the versions in the description of each file.

- Fabric Loader
    - [Download it](https://fabricmc.net/use/installer/) on fabricmc.net

- Fabric API
    - [Download it](https://modrinth.com/mod/fabric-api) on modrinth
    - [View source](https://github.com/FabricMC/fabric) on GitHub

## Features

### Dimension-Specific Color Coding

#### Overworld

- GREEN: Mobs cannot spawn
    - Block Light >= 1
- YELLOW: Mobs can spawn at night
    - Block Light = 0
    - Sky Light >= 8
- RED: Mobs can always potentially spawn
    - Block Light = 0
    - Sky Light <= 7

#### The Nether

- GREEN: Mobs cannot spawn
    - Block Light >= 12
- RED: Mobs can always potentially spawn
    - Block Light <= 11

#### The End

- GREEN: Mobs cannot spawn
    - Block Light >= 1
- RED: Mobs can always potentially spawn
    - Block Light = 0

### Debug Mode

Press **F3** to show both the block light and the sky light levels.

## Technical Features

### Block Exclusion Conditions

The rendering on a target block will be skipped if:

- The target block is one of the following:
    - `air`
    - `barrier`
    - `bedrock`
    - `chain_command_block`
    - `command_block`
    - `repeating_command_block`
- The target block is not opaque (visually transparent).
    - **Examples Excluded**:
        - `water`
        - `glass`
    - **Examples Not Excluded**:
        - slabs
        - stairs
- The target block does not have a full square top surface.
    - **Examples Excluded**:
        - right-side-up slabs
        - right-side-up stairs
    - **Examples Not Excluded**:
        - up-side-down slabs
        - up-side-down stairs
- A block above the target block has a collision shape.
    - **Examples Excluded**:
        - `ladder`
        - `snow` (layers count >= 2)
    - **Examples Not Excluded**:
        - `vine`
        - `snow` (layers count = 1)

### Y Offset

If a non-colliding block is above the target block and its appearance obstructs the text, the light
level will be shown above the visual.

### Culling

This mod does not render the light level for a target block that is invisible to the player.