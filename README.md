# Light Level 2025

Press **F9** to see the block light levels.

Inspired by [LightLevel By parzivail](https://github.com/Parzivail-Modding-Team/LightLevel), with
some improvements implemented.

<div style="display: inline">
  <img src="https://img.shields.io/badge/Minecraft-1.21.5-white">
  <img src="https://img.shields.io/badge/Fabric_Loader-0.16.12-white">
  <img src="https://img.shields.io/github/actions/workflow/status/dark-lion-jp/light-level-2025/build.yml?branch=main">
</div>

## Features

### Dimension-Specific Colors

#### Overworld

- <span style="color: #40FF40;">GREEN</span>: Mobs cannot spawn
    - Block Light >= 1
- <span style="color: #FFFF40;">YELLOW</span>: Mobs can spawn at night
    - Block Light = 0
    - Sky Light >= 8
- <span style="color: #FF4040;">RED</span>: Mobs can always potentially spawn
    - Block Light = 0
    - Sky Light <= 7

#### The Nether

- <span style="color: #40FF40;">GREEN</span>: Mobs cannot spawn
    - Block Light >= 12
- <span style="color: #FF4040;">RED</span>: Mobs can always potentially spawn
    - Block Light <= 11

#### The End

- <span style="color: #40FF40;">GREEN</span>: Mobs cannot spawn
    - Block Light >= 1
- <span style="color: #FF4040;">RED</span>: Mobs can always potentially spawn
    - Block Light = 0

### Debug Mode

Press **F3** to show both the block light and the sky light levels.

## Technical Features

### Block Exclusion Conditions

The rendering on a target block will be excluded if:

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
        - `snow` (layer count = 1)

### Y Offset

If a non-colliding block is above the target block and its appearance obstructs the text, the light
level will be shown above the visual.

### Culling

This mod does not render the light level for a target block that is invisible to the player.