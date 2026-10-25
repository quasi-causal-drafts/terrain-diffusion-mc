# Terrain Diffusion Mod [[Modrinth]](https://modrinth.com/mod/terrain-diffusion)

#### UPDATE: The research behind this mod has been accepted to SIGGRAPH 2026, the world's premier graphics conference! That means the research was officially peer reviewed and recognized as a significant contribution to the field. Enjoy the mod!

This is a Minecraft multiplateform mod integrating [Terrain Diffusion](https://github.com/xandergos/terrain-diffusion).

## Which version should I use?

Three builds are available on the [Releases](https://github.com/xandergos/terrain-diffusion-mc/releases) page:

**The CPU build is slow unless you are on MacOS.**

| Build                     | Supports                    | Setup required                          |
|---------------------------| --------------------------- | --------------------------------------- |
| **Windows** (recommended) | Windows with any modern GPU | None                                    |
| **CUDA**                  | NVIDIA GPUs                 | [CUDA + cuDNN install](CUDA_INSTALL.md) |
| **CPU**                   | Everything else             | None                                    |

> **Mac users:** the CPU build automatically uses CoreML for hardware acceleration on Apple Silicon. No extra setup is needed.

Use the `-cuda` build only if you are on Linux, or have an NVIDIA GPU and prefer CUDA (may improve performance).

## Requirements

- Windows with a GPU OR Linux with an NVIDIA GPU is strongly recommended. CPU inference works but is very slow.
- VRAM (GPU RAM) needed: 1.5GB
- RAM needed: 2.5GB (May need to increase Minecraft's RAM allocation)

One of the following:
- Minecraft with [Fabric](https://fabricmc.net/) and the [Fabric API Mod](https://modrinth.com/mod/fabric-api) installed
- Minecraft with [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/) installed

## Usage

**If using CUDA build:** First see [CUDA_INSTALL.md](CUDA_INSTALL.md).

1. Download the mod jar from [Releases](https://github.com/xandergos/terrain-diffusion-mc/releases) for your Minecraft version and place it in your Minecraft `mods/` folder. Make sure the Minecraft version matches.
2. Launch Minecraft, at least once online to download the models (~2.5GB).
3. Create a world, and select the **Terrain Diffusion** world type. Click **Customize** to set the `World Scale` (see [Per-world settings](#per-world-settings) below).
4. The mod will search for a land spawn point near the world origin automatically. If the area around (0, 0) is entirely ocean, it may take a moment to find land. Use `/td-explore` (see below) to scout the world further.

## Exploring the World

The mod includes a built-in terrain explorer web UI. Run the `/td-explore` command in-game; it will print a clickable link (e.g. `http://localhost:19801`) that opens an interactive map in your browser. Click the map on the left to open a "detailed view". Click the detailed view to get coordinates in the bottom left. You can also filter for certain climates.

Use the explorer to scout continents, mountains, islands, and other interesting terrain before venturing out in Minecraft.

### Locating terrain and biomes

Both commands search the coarse terrain map (one cell is 256 × world scale blocks across) instead of generating every chunk, so they are fast but approximate: they report the centre of the nearest matching area, and the exact spot inside it may differ.

- `/td-locate <mountains|highest|flatlands|ocean|land> [radius]` finds terrain features (default radius 8000 blocks). `mountains` means an average elevation of at least 2000 m or more than 600 m of relief; `flatlands` means land below 300 m with less than 60 m of relief. Click the coordinates to get a teleport command aimed above the terrain.
- `/locate biome <biome>` works in Terrain Diffusion worlds, including biome tags such as `#minecraft:is_mountain`. Peaks are found in rugged areas whose steep ground would produce them. Note that Terrain Diffusion uses its own sparse forest and taiga variants (for example `terrain-diffusion-mc:taiga_sparse`).

### Terralith biomes

If [Terralith](https://modrinth.com/mod/terralith) is installed, Terrain Diffusion worlds use 49 Terralith biomes alongside vanilla ones; without it, worlds generate exactly as before. Each Terralith biome stands in for the vanilla biome Terrain Diffusion would otherwise place (for example shield or birch taiga for taiga, steppe or shrubland for dry grassland, white or granite cliffs for steep coasts), chosen by altitude, temperature, rainfall, slope and large-scale regions. Rare themed regions (moonlight, lavender, sakura) span neighbouring forests and meadows.

- Terrain shape and ground blocks stay Terrain Diffusion's: Terralith biomes bring their trees, plants, colors and mobs, and use the same ground blocks as the vanilla biome they replace. Terralith biomes defined mainly by their own terrain or surface blocks (spires, canyons, chasms, volcanic areas, sky islands) are not used.
- Terralith 2.5.x for 1.20.1 overrides the overworld dimension, which would otherwise replace a Terrain Diffusion world; Terrain Diffusion worlds keep their own overworld. Other world types still get Terralith's.
- Worlds created with Terralith installed before this support existed already have Terralith's overworld saved, so create a new world.

### Choosing biomes

`biomes.allowed` in `config/terrain-diffusion-mc.properties` lists the biomes Terrain Diffusion worlds may use. Leave it empty for the default set.

```
biomes.allowed=default, biomesoplenty:*, -moonlight_grove, -moonlight_valley
```

| Entry | Meaning |
|---|---|
| `default` | Everything Terrain Diffusion places out of the box: the vanilla biomes below, its own `forest_sparse`, `taiga_sparse` and `snowy_taiga_sparse`, and Terralith's 49 biomes when Terralith is installed |
| `minecraft:birch_forest` | One biome. Vanilla, Terrain Diffusion and Terralith biomes can drop the `namespace:` (`birch_forest`, `shield`) |
| `biomesoplenty:*` | Every overworld surface biome from a mod. Caves, rivers, beaches, mushroom islands and Nether or End biomes are left out |
| `-moonlight_grove` | Remove a biome (`-terralith:*` removes a whole mod). Removals apply last |

How the list is used:

- **Biomes Terrain Diffusion has rules for** (the vanilla biomes it places and the 49 Terralith biomes) go where the classifier puts them.
- **Any other biome** (other vanilla biomes such as `cherry_grove`, or biomes from other mods) is matched to the classifier biome with the closest climate and character, using its temperature, rainfall and tags, and shares that area with it in large regions. For example Biomes O' Plenty's `bayou` and `marsh` share swamp land, `maple_woods` shares taiga. Biomes their mod tags as rare or magical get about a third of a normal share. The log lists where each one went.
- **A vanilla biome that isn't listed** is replaced by the Terralith biomes that stand in for it (if listed), or else by the nearest listed biome along a fallback chain (swamp falls back to forest, desert to badlands, then savanna, and so on). Oceans only ever become other oceans.
- Ground blocks follow Terrain Diffusion's surface rules, so biomes it has no rules for get grass and dirt (or sand, snow and stone in the areas where it places those).
- Read at startup; only newly generated chunks change. Misspelled names and mods that aren't installed are reported in the log.

Vanilla biomes Terrain Diffusion places by default: `plains`, `snowy_plains`, `desert`, `swamp`, `forest`, `taiga`, `snowy_taiga`, `savanna`, `windswept_hills`, `jungle`, `grove`, `snowy_slopes`, `frozen_peaks`, `stony_peaks`, `warm_ocean`, `ocean`, `cold_ocean`, `frozen_ocean`.

Older settings still work: `terralith.disabled_biomes` and `vanilla.disabled_biomes` are applied as removals (`-name`).

Terralith biomes and the vanilla biomes they stand in for:

| Terralith biome | Replaces |
|---|---|
| `alpine_grove` | snowy taiga, snowy sparse taiga (mountains) |
| `alpine_highlands` | plains (mountains), grove |
| `amethyst_rainforest` | jungle (rare) |
| `ancient_sands` | desert |
| `ashen_savanna` | savanna |
| `basalt_cliffs` | stony peaks (hot coasts) |
| `birch_taiga` | forest, taiga |
| `blooming_plateau` | plains (mountains) |
| `blooming_valley` | plains, forest |
| `cloud_forest` | taiga (wet mountains) |
| `cold_shrubland` | plains, grove |
| `desert_oasis` | desert (rare) |
| `forested_highlands` | forest, taiga |
| `frozen_cliffs` | frozen peaks (coasts) |
| `granite_cliffs` | stony peaks (temperate coasts) |
| `haze_mountain` | sparse taiga (mountains) |
| `highlands` | plains, sparse forest |
| `hot_shrubland` | sparse forest |
| `ice_marsh` | snowy plains |
| `jungle_mountains` | jungle |
| `lavender_forest` | forest, sparse forest (rare) |
| `lavender_valley` | plains (rare) |
| `lush_desert` | desert |
| `lush_valley` | taiga |
| `moonlight_grove` | forest, sparse forest (rare) |
| `moonlight_valley` | plains (rare) |
| `orchid_swamp` | swamp |
| `rocky_jungle` | jungle |
| `rocky_mountains` | stony peaks, windswept hills |
| `rocky_shrubland` | plains, grove |
| `sakura_grove` | forest, sparse forest (rare) |
| `sakura_valley` | plains (rare) |
| `sandstone_valley` | desert |
| `savanna_badlands` | savanna |
| `savanna_slopes` | savanna |
| `shield` | taiga, sparse taiga |
| `shield_clearing` | sparse taiga |
| `shrubland` | savanna |
| `siberian_taiga` | snowy taiga |
| `snowy_cherry_grove` | snowy sparse taiga (rare) |
| `snowy_maple_forest` | snowy taiga |
| `snowy_shield` | snowy sparse taiga |
| `steppe` | grove |
| `temperate_highlands` | forest |
| `tropical_jungle` | jungle |
| `valley_clearing` | plains |
| `white_cliffs` | stony peaks (cold coasts) |
| `wintry_forest` | snowy taiga |
| `yosemite_lowlands` | sparse forest |

## Configuration

Edit `config/terrain-diffusion-mc.properties` (created automatically on first launch):

```
# Terrain Diffusion MC configuration

# Inference device: "cpu", "gpu", or "auto" (try GPU first then fall back to CPU).
# "gpu" uses DirectML on the -windows build, or CUDA on the -cuda build.
# GPU builds default to "gpu" so startup fails loudly if no GPU is detected.
# CPU build defaults to "auto": uses CoreML on macOS, otherwise CPU.
inference.device=gpu

# Offload inactive models from VRAM between pipeline stages.
# Keeps peak VRAM to ~1.5-2 GB. Set to false if you have ~2.5+ GB free for slightly
# faster generation.
inference.offload_models=true

# Validate SHA-256 for pre-existing files in .minecraft/terrain-diffusion-models.
# Set to false if you want to provide custom models/config files without hash checks.
validate_model=true

# Port for the local terrain explorer web UI (/td-explore).
explorer.port=19801

# Spawn search: coarse-pixel region sizes for finding a land spawn near (0, 0).
# Starts at initial_size x initial_size and expands by 8 each step up to max_size x max_size.
# Each coarse pixel covers a large area (hundreds of blocks), so 16–128 is typically sufficient.
spawn_search.initial_size=16
spawn_search.max_size=128
```

### Per-world settings

For Terrain Diffusion worlds, click **Customize** in world creation and set:

- `World Scale` (integer `1..6`)

This value is saved with the world save and affects:

- how many real-world meters each block represents (`scale=1` => `30m/block`, `scale=2` => `15m/block`, etc.)
- world max height for newly created worlds (assumes tallest point is 10000 real-world meters)
- 2 is recommended for a good balance of scale and playability. Use 1 for smaller, more compressed worlds.
- Lower values put more stress on the GPU (Terrain Diffusion runs more often), while higher values put more stress on the CPU (larger world height). Most modern GPUs will be bottlenecked by the CPU around scale 2 or 3.

## Common Issues

**A dynamic link library (DLL) initialization routine failed**

This can happen for some older Java versions. Please update to the most recent version of Java 17 or higher. The [latest Microsoft OpenJDK 17](https://learn.microsoft.com/en-us/java/openjdk/download) version is known to work.

**LoadLibrary failed with error 126** *(CUDA build only)*

This is typically due to an improper CUDA or cuDNN installation. See [CUDA_INSTALL.md](CUDA_INSTALL.md) for troubleshooting steps.

**java.lang.IllegalStateException: Failed to load terrain-diffusion models**

This typically indicates an "out of memory" error (the logs should show this as well).
Terrain Diffusion's models take up about 2.5GB of RAM, so make sure to allocate enough RAM to account for this.

**If your issue is still not resolved, please [raise it here](https://github.com/xandergos/terrain-diffusion-mc/issues/new).**

## Building from Source

An internet connection is required during the build to fetch the pinned model manifest metadata from Hugging Face.

The `-windows` build requires `libs/onnxruntime-dml.jar`, which is provided as part of the repo. See [Building onnxruntime with DirectML](#building-onnxruntime-with-directml) to build from source. 

Build all loaders for Windows (DirectML):
```
./gradlew buildDml
# or: ./gradlew build -PuseDml=true
```

Build all loaders for CUDA:
```
./gradlew buildCuda
# or: ./gradlew build -PuseCuda=true
```

Build all loaders for CPU (also handles macOS/CoreML automatically):
```
./gradlew buildCpu
# or: ./gradlew build -PuseCpu=true
```

Build every variant for Fabric and Forge:
```
./gradlew buildAll
```

You can also build one loader/variant directly, for example:
```
./gradlew :fabric:buildDml
./gradlew :forge:buildCuda
```

### Building onnxruntime with DirectML

**Requirements**

- [Windows 10 SDK (10.0.17134.0)](https://developer.microsoft.com/en-us/windows/downloads/sdk-archive/index-legacy) — for Windows 10 version 1803 or newer
- Visual Studio 2017 toolchain — install *Desktop development with C++* from the VS Installer
- Visual Studio 2022 toolchain — same as above
- Python 3.10+: [https://python.org/](https://python.org/)
- CMake 3.28 or higher

Keep both VS toolchains up to date. Full details at the [ONNX Runtime build docs](https://onnxruntime.ai/docs/build/inferencing.html) and the [DirectML EP requirements](https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html#build).

**Steps**

Run all commands from the **Developer Command Prompt for VS 2022**.

```
git clone --recursive https://github.com/Microsoft/onnxruntime.git
cd onnxruntime
.\build.bat --config RelWithDebInfo --build_shared_lib --parallel --compile_no_warning_as_error --skip_submodule_sync --use_dml --build_java --build
```

The built jar appears in `java/build/`. Rename it to `onnxruntime-dml.jar` and place it in `libs/` in this repository.

## Note For Mod Developers

While modifying the AI terrain itself is quite complex, the integration with Minecraft biomes is extremely simple. The model outputs elevation + 4 climate variables, and this is converted to Minecraft biomes with hand-written rules. This is the most immediate way to improve the quality of the terrain and is relatively easy, but takes time to get realistic. The entire biome classifier is [only 250 lines](https://github.com/xandergos/terrain-diffusion-mc/blob/master/src/main/java/com/github/xandergos/terraindiffusionmc/pipeline/BiomeClassifier.java).

The terrain diversity far outpaces the biome diversity and there's a real opportunity to close that gap. I'm hoping someone goes crazy with it.
