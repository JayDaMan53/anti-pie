# Anti Pie

Anti Pie is a Fabric server-side mod that stops certain hidden block entities from being sent to players. This keeps them from showing up on the debug pie chart, making it harder to use the pie chart to find hidden bases. Players do not need to install anything.

## How it works

- Removes selected block entities from chunk packets before they are sent to the client.
- Regularly checks hidden positions and sends the real block and block entity data as soon as they become visible.
- Uses a voxel raycast and counts full solid blocks as blockers. Transparent blocks and partial blocks do not hide a target.

## Performance

Most packets only do a few simple type checks. Visibility checks are limited by `visibilityChecksPerPass` (32 by default): four times per second while a player is moving and once per second while standing still. When chunks first load, visibility checks are also limited to 64 per player each tick.

Each visibility check first tests the center of the block. If needed, it checks the exposed faces. Most blocks only need one raycast, while the worst case is 31 raycasts. Hidden positions are stored as packed longs to keep memory usage low.

The client does not run any mod code. Initial chunk packets are the same size or smaller because hidden block entity data is removed. When a hidden block becomes visible, the server sends one replacement chunk packet for the affected chunk. This can briefly use a little more bandwidth and cause the client to rebuild the chunk, but normal gameplay is usually lighter because the client has fewer block entities to process.

## Configuration

Anti Pie creates an `anti-pie.json5` file in your server's `config` folder the first time it starts.

From this file you can:

- Choose which block entities should be hidden.
- Change how close players must be before hidden block entities are always shown.
- Adjust how often visibility checks run and how much work the server does each pass to balance performance and responsiveness.

The config uses the JSON5 format for comments to make it easier to read and customize.

The generated list is version-aware and covers every vanilla block-entity type with a client renderer in that Minecraft release. Newer-only entries such as shelves, copper golem statues, and test-instance blocks are omitted from older builds; beds are included on releases where they still use a block-entity renderer.

## Supported versions

The Stonecutter workspace builds separate Fabric jars for Minecraft 1.21.1, 1.21.11, 26.1, and 26.2 from one shared source tree. The 1.21 targets compile for Java 21, while 26.x compiles for Java 25. Fabric API is not required at runtime.

Run `./gradlew build` (or `./gradlew.bat build` on Windows) to build every target. Build one target with a command such as `./gradlew :1.21.11-fabric:build`. Artifacts are written below `versions/<minecraft>-fabric/build/libs/`.

## Publishing to Modrinth

Set the `MODRINTH_TOKEN` and `MODRINTH_ID` environment variables and run `./gradlew publishMods`. The configured publishing integration uploads each jar as a separate version-specific Fabric file under the same Modrinth project.

## License

CC0-1.0. The code is original, but the packet-filtering idea was inspired by [orbyfied/AntiPieRay](https://github.com/orbyfied/AntiPieRay).
