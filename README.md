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

## License

CC0-1.0. The code is original, but the packet-filtering idea was inspired by [orbyfied/AntiPieRay](https://github.com/orbyfied/AntiPieRay).