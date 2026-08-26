package jaydon.antipie.bukkit;

import org.bukkit.Location;
import org.bukkit.World;

final class VoxelVisibility {
    private static final double EPSILON = 1.0E-7;

    private VoxelVisibility() {
    }

    static boolean isVisible(World world, Location eye, BlockPosition target, double alwaysVisibleDistance) {
        double centerX = target.x() + 0.5;
        double centerY = target.y() + 0.5;
        double centerZ = target.z() + 0.5;
        double distanceX = eye.getX() - centerX;
        double distanceY = eye.getY() - centerY;
        double distanceZ = eye.getZ() - centerZ;
        if (distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ
                <= alwaysVisibleDistance * alwaysVisibleDistance) return true;
        if (clearRay(world, eye.getX(), eye.getY(), eye.getZ(), centerX, centerY, centerZ, target)) return true;

        for (Face face : Face.values()) {
            if (isOccluding(world, target.x() + face.x, target.y() + face.y, target.z() + face.z)) continue;
            if (clearFace(world, eye, target, face)) return true;
        }
        return false;
    }

    private static boolean clearFace(World world, Location eye, BlockPosition target, Face face) {
        double fixedX = face.x < 0 ? 0.02 : face.x > 0 ? 0.98 : 0.5;
        double fixedY = face.y < 0 ? 0.02 : face.y > 0 ? 0.98 : 0.5;
        double fixedZ = face.z < 0 ? 0.02 : face.z > 0 ? 0.98 : 0.5;
        if (clearRay(world, eye.getX(), eye.getY(), eye.getZ(), target.x() + fixedX,
                target.y() + fixedY, target.z() + fixedZ, target)) return true;

        for (int first = 0; first < 2; first++) {
            for (int second = 0; second < 2; second++) {
                double edgeA = first == 0 ? 0.02 : 0.98;
                double edgeB = second == 0 ? 0.02 : 0.98;
                double x = fixedX;
                double y = fixedY;
                double z = fixedZ;
                if (face.x != 0) {
                    y = edgeA;
                    z = edgeB;
                } else if (face.y != 0) {
                    x = edgeA;
                    z = edgeB;
                } else {
                    x = edgeA;
                    y = edgeB;
                }
                if (clearRay(world, eye.getX(), eye.getY(), eye.getZ(), target.x() + x,
                        target.y() + y, target.z() + z, target)) return true;
            }
        }
        return false;
    }

    private static boolean clearRay(World world, double startX, double startY, double startZ,
                                    double endXValue, double endYValue, double endZValue,
                                    BlockPosition target) {
        int x = floor(startX), y = floor(startY), z = floor(startZ);
        int endX = floor(endXValue), endY = floor(endYValue), endZ = floor(endZValue);
        double dx = endXValue - startX, dy = endYValue - startY, dz = endZValue - startZ;
        int stepX = Integer.compare(endX, x), stepY = Integer.compare(endY, y), stepZ = Integer.compare(endZ, z);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tMaxX = firstBoundary(startX, x, stepX, dx);
        double tMaxY = firstBoundary(startY, y, stepY, dy);
        double tMaxZ = firstBoundary(startZ, z, stepZ, dz);

        for (int steps = 0; steps < 1024; steps++) {
            if (x == endX && y == endY && z == endZ) return true;
            double nextBoundary = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (tMaxX <= nextBoundary + EPSILON) {
                x += stepX;
                tMaxX += tDeltaX;
            }
            if (tMaxY <= nextBoundary + EPSILON) {
                y += stepY;
                tMaxY += tDeltaY;
            }
            if (tMaxZ <= nextBoundary + EPSILON) {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == target.x() && y == target.y() && z == target.z()) return true;
            if (isOccluding(world, x, y, z)) return false;
        }
        return false;
    }

    private static boolean isOccluding(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return true;
        return world.getBlockAt(x, y, z).getType().isOccluding();
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double firstBoundary(double coordinate, int block, int step, double delta) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? block + 1.0 : block;
        return Math.max(0.0, (boundary - coordinate) / delta);
    }

    private enum Face {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

        final int x;
        final int y;
        final int z;

        Face(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
