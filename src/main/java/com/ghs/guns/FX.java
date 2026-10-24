package com.ghs.guns;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/** Particle shape helpers: lines, helixes, rings and spheres. */
final class FX {
    private FX() {}

    static final Color[] RAINBOW = {
            Color.fromRGB(255, 40, 40), Color.fromRGB(255, 150, 0), Color.fromRGB(255, 240, 0),
            Color.fromRGB(60, 255, 60), Color.fromRGB(40, 200, 255), Color.fromRGB(90, 80, 255), Color.fromRGB(220, 60, 255)
    };

    static Particle.DustOptions dust(Color color, float size) {
        return new Particle.DustOptions(color, size);
    }

    static Particle.DustOptions dust(int r, int g, int b, float size) {
        return new Particle.DustOptions(Color.fromRGB(r, g, b), size);
    }

    static Particle.DustTransition fade(Color from, Color to, float size) {
        return new Particle.DustTransition(from, to, size);
    }

    static void dustAt(Location at, Particle.DustOptions dust, int count, double spread) {
        at.getWorld().spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, dust);
    }

    static void at(Location at, Particle particle, int count, double spread, double speed) {
        at.getWorld().spawnParticle(particle, at, count, spread, spread, spread, speed);
    }

    /** Points along a straight line from a to b. */
    static void line(Location a, Location b, double step, Consumer<Location> draw) {
        Vector diff = b.toVector().subtract(a.toVector());
        double len = diff.length();
        if (len < 0.001) return;
        diff.multiply(1 / len);
        for (double d = 0; d <= len; d += step) {
            draw.accept(a.clone().add(diff.getX() * d, diff.getY() * d, diff.getZ() * d));
        }
    }

    /** Two vectors perpendicular to dir (and to each other), for drawing circles around a direction. */
    static Vector[] basis(Vector dir) {
        Vector d = dir.clone().normalize();
        Vector up = Math.abs(d.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector u = d.clone().crossProduct(up).normalize();
        Vector v = d.clone().crossProduct(u).normalize();
        return new Vector[]{u, v};
    }

    /** A spiral wrapped around a beam. */
    static void helix(Location start, Vector dir, double length, double radius, double turnsPerBlock, double step,
                      double phase, Consumer<Location> draw) {
        Vector d = dir.clone().normalize();
        Vector[] b = basis(d);
        for (double t = 0; t <= length; t += step) {
            double a = phase + t * turnsPerBlock * Math.PI * 2;
            Vector off = b[0].clone().multiply(Math.cos(a) * radius).add(b[1].clone().multiply(Math.sin(a) * radius));
            draw.accept(start.clone().add(d.clone().multiply(t)).add(off));
        }
    }

    /** A circle facing along dir (dir = up gives a flat ground ring). */
    static void ring(Location center, Vector dir, double radius, int points, Consumer<Location> draw) {
        Vector[] b = basis(dir);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            draw.accept(center.clone().add(b[0].clone().multiply(Math.cos(a) * radius)).add(b[1].clone().multiply(Math.sin(a) * radius)));
        }
    }

    static void ring(Location center, double radius, int points, Consumer<Location> draw) {
        ring(center, new Vector(0, 1, 0), radius, points, draw);
    }

    /** Evenly spread points on a sphere surface. */
    static void sphere(Location center, double radius, int points, Consumer<Location> draw) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = golden * i;
            draw.accept(center.clone().add(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius));
        }
    }

    /** Particles shooting outward from a point in every direction (directional particles only). */
    static void burst(Location center, Particle particle, int count, double speed) {
        World w = center.getWorld();
        sphere(center, 1, count, p -> {
            Vector v = p.toVector().subtract(center.toVector());
            w.spawnParticle(particle, center, 0, v.getX(), v.getY(), v.getZ(), speed);
        });
    }

    /** Particles flowing inward to a point from a sphere around it (directional particles only). */
    static void implode(Location center, Particle particle, double radius, int count, double speed) {
        World w = center.getWorld();
        sphere(center, radius, count, p -> {
            Vector v = center.toVector().subtract(p.toVector());
            w.spawnParticle(particle, p, 0, v.getX(), v.getY(), v.getZ(), speed);
        });
    }

    /** A jagged lightning bolt between two points. */
    static void zap(Location a, Location b, Consumer<Location> draw) {
        Vector diff = b.toVector().subtract(a.toVector());
        int segments = Math.max(3, (int) (diff.length() / 1.2));
        Location prev = a.clone();
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 1; i <= segments; i++) {
            Location next = a.clone().add(diff.clone().multiply(i / (double) segments));
            if (i < segments) next.add(r.nextDouble(-0.5, 0.5), r.nextDouble(-0.5, 0.5), r.nextDouble(-0.5, 0.5));
            line(prev, next, 0.2, draw);
            prev = next;
        }
    }
}
