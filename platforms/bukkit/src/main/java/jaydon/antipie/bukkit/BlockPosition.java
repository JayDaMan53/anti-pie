package jaydon.antipie.bukkit;

record BlockPosition(int x, int y, int z) {
    long chunkKey() {
        return chunkKey(x >> 4, z >> 4);
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX & 0xffffffffL | ((long) chunkZ & 0xffffffffL) << 32;
    }
}
