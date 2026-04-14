package endgame.plugin.ui.snake;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Snake minigame page — arcade machine easter egg.
 * Features: body gradient, progressive speed, bonus food, food pulse, high score.
 */
public class SnakeGamePage extends InteractiveCustomUIPage<SnakeEventData> {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "EndgameQoL-Snake");
                t.setDaemon(true);
                return t;
            });

    private static final long START_DELAY_MS = 2000;

    private final SnakeGame game = new SnakeGame();
    private volatile ScheduledFuture<?> tickTask;
    private volatile boolean started;
    private volatile int lastProcessedLength;
    private volatile int highScore;

    public SnakeGamePage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, SnakeEventData.CODEC);
    }

    // --- Color engine ---

    private static final String HEAD_COLOR = "#55ffaa";
    private static final String DEATH_COLOR = "#881111";
    private static final String EAT_FLASH_COLOR = "#ffffff";
    private static final String GRID_BG = "#111118";

    /** Snake body gradient: head is distinct bright cyan-green, body fades to dark green. */
    private static String bodyColor(int index, int length) {
        if (index == 0) return HEAD_COLOR;
        float ratio = (float) index / Math.max(1, length - 1);
        int g = (int) (0xff * (1f - ratio) + 0x44 * ratio);
        int b = (int) (0x41 * (1f - ratio) + 0x11 * ratio);
        return String.format("#00%02x%02x", g, b);
    }

    /** Food color pulses between red shades. */
    private static String foodColor(int tick) {
        return (tick % 4 < 2) ? "#ff3333" : "#ff5555";
    }

    /** Bonus food pulses gold/yellow. */
    private static String bonusFoodColor(int tick) {
        return (tick % 4 < 2) ? "#ffaa00" : "#ffdd33";
    }

    /** Speed level from tick delay. */
    private static int speedLevel(long delay) {
        if (delay >= 130) return 1;
        if (delay >= 110) return 2;
        if (delay >= 90) return 3;
        if (delay >= 70) return 4;
        return 5;
    }

    // --- Animation state ---
    private volatile boolean deathAnimating;
    private volatile int deathAnimFrame;
    private List<int[]> deathPositions;
    private volatile int lastRenderedLength;

    // --- Page lifecycle ---

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/SnakeGame.ui");

        // Set initial grid colors
        int[][] grid = game.getGrid();
        for (int y = 0; y < SnakeGame.GRID_SIZE; y++) {
            for (int x = 0; x < SnakeGame.GRID_SIZE; x++) {
                int cell = grid[y][x];
                String color = switch (cell) {
                    case SnakeGame.FOOD -> "#ff3333";
                    default -> "#111118";
                };
                cmd.set("#Cx" + x + "y" + y + ".Background", color);
            }
        }

        // Render initial snake with gradient
        List<int[]> positions = game.getSnakePositions();
        for (int i = 0; i < positions.size(); i++) {
            int[] pos = positions.get(i);
            cmd.set("#Cx" + pos[0] + "y" + pos[1] + ".Background", bodyColor(i, positions.size()));
        }

        cmd.set("#Score.Text", "Score: 0  |  Best: " + highScore);

        // "GET READY" countdown
        cmd.set("#GameOverLabel.Text", "GET READY...");
        cmd.set("#GameOverLabel.Visible", true);

        // Single TextField captures all input (WASD + R) — no buttons to steal focus
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#KeyInput",
                EventData.of("@KeyCode", "#KeyInput.Value"), false);

        lastProcessedLength = 0;
        lastRenderedLength = 0;
        deathAnimating = false;
        startGameLoop();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull SnakeEventData data) {
        synchronized (game) {
            // All input via TextField — WASD for direction, R for retry
            if (data.keyCode != null && data.keyCode.length() > lastProcessedLength) {
                for (int i = lastProcessedLength; i < data.keyCode.length(); i++) {
                    char c = Character.toLowerCase(data.keyCode.charAt(i));
                    switch (c) {
                        case 'w' -> game.setDirection(SnakeGame.UP);
                        case 's' -> game.setDirection(SnakeGame.DOWN);
                        case 'a' -> game.setDirection(SnakeGame.LEFT);
                        case 'd' -> game.setDirection(SnakeGame.RIGHT);
                        case 'r' -> {
                            if (game.isGameOver()) {
                                game.reset();
                                started = false;
                                lastProcessedLength = data.keyCode.length();
                                rebuild();
                                startGameLoop();
                                return;
                            }
                        }
                    }
                }
                lastProcessedLength = data.keyCode.length();
            }
        }
    }

    // --- Game loop with progressive speed ---

    private void startGameLoop() {
        cancelGameLoop();
        started = false;
        tickTask = SCHEDULER.schedule(this::gameTick, START_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void scheduleNextTick() {
        long delay;
        synchronized (game) {
            delay = game.getTickDelay();
        }
        tickTask = SCHEDULER.schedule(this::gameTick, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelGameLoop() {
        ScheduledFuture<?> task = tickTask;
        if (task != null) {
            task.cancel(false);
            tickTask = null;
        }
    }

    private void gameTick() {
        try {
            // First tick: hide GET READY
            if (!started) {
                started = true;
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#GameOverLabel.Visible", false);
                sendUpdate(cmd, null, false);
                scheduleNextTick();
                return;
            }

            List<int[]> changes;
            boolean isOver;
            int score;
            int tick;
            List<int[]> snakePositions;
            boolean hasBonusFood;
            int bonusX = 0, bonusY = 0;

            synchronized (game) {
                changes = game.tick();
                isOver = game.isGameOver();
                score = game.getScore();
                tick = game.getTickCount();
                snakePositions = game.getSnakePositions();
                hasBonusFood = game.hasBonusFood();
                if (hasBonusFood) {
                    bonusX = game.getBonusFoodX();
                    bonusY = game.getBonusFoodY();
                }
            }

            UICommandBuilder cmd = new UICommandBuilder();

            // --- Death animation phase ---
            if (deathAnimating) {
                if (deathAnimFrame < deathPositions.size()) {
                    // Turn 2 cells red per frame for a fast domino effect
                    for (int j = 0; j < 2 && deathAnimFrame < deathPositions.size(); j++, deathAnimFrame++) {
                        int[] pos = deathPositions.get(deathAnimFrame);
                        cmd.set("#Cx" + pos[0] + "y" + pos[1] + ".Background", DEATH_COLOR);
                    }
                    sendUpdate(cmd, null, false);
                    tickTask = SCHEDULER.schedule(this::gameTick, 50, TimeUnit.MILLISECONDS);
                    return;
                } else {
                    // Animation done — show game over text
                    deathAnimating = false;
                    String msg = score > 0 && score >= highScore
                            ? "NEW BEST! " + score + " - Press R"
                            : "GAME OVER " + score + " - Press R";
                    cmd.set("#GameOverLabel.Text", msg);
                    cmd.set("#GameOverLabel.Visible", true);
                    sendUpdate(cmd, null, false);
                    return;
                }
            }

            if (isOver) {
                if (score > highScore) highScore = score;
                cmd.set("#Score.Text", "Score: " + score + "  |  Best: " + highScore);

                // Start death animation — cells turn red head→tail
                deathPositions = snakePositions;
                deathAnimFrame = 0;
                deathAnimating = true;
                sendUpdate(cmd, null, false);
                tickTask = SCHEDULER.schedule(this::gameTick, 200, TimeUnit.MILLISECONDS);
                return;
            }

            // --- Normal game rendering ---

            // Detect if food was eaten this tick (for eat flash)
            boolean ateFood = false;
            int eatX = 0, eatY = 0;
            for (int[] change : changes) {
                if (change[2] == SnakeGame.FOOD || change[2] == SnakeGame.BONUS_FOOD) {
                    // New food spawned = old food was eaten at head position
                    ateFood = true;
                    int[] head = snakePositions.getFirst();
                    eatX = head[0]; eatY = head[1];
                }
            }

            // Clear removed cells (tail, expired bonus food)
            for (int[] change : changes) {
                if (change[2] == SnakeGame.EMPTY) {
                    cmd.set("#Cx" + change[0] + "y" + change[1] + ".Background", GRID_BG);
                }
            }

            // Render food (new spawns + pulse existing)
            for (int[] change : changes) {
                if (change[2] == SnakeGame.FOOD) {
                    cmd.set("#Cx" + change[0] + "y" + change[1] + ".Background", foodColor(tick));
                }
                if (change[2] == SnakeGame.BONUS_FOOD) {
                    cmd.set("#Cx" + change[0] + "y" + change[1] + ".Background", bonusFoodColor(tick));
                }
            }

            // Pulse existing bonus food
            if (hasBonusFood) {
                cmd.set("#Cx" + bonusX + "y" + bonusY + ".Background", bonusFoodColor(tick));
            }

            // Render snake — only head (new), neck (was head), and tail (removed)
            // Full gradient re-render only when length changes (growth)
            int len = snakePositions.size();
            if (len != lastRenderedLength) {
                // Length changed (ate food) — full gradient re-render
                for (int i = 0; i < len; i++) {
                    int[] pos = snakePositions.get(i);
                    cmd.set("#Cx" + pos[0] + "y" + pos[1] + ".Background", bodyColor(i, len));
                }
                lastRenderedLength = len;
            } else {
                // Length unchanged — only update head (bright) and old head→body
                if (len > 0) {
                    int[] head = snakePositions.get(0);
                    cmd.set("#Cx" + head[0] + "y" + head[1] + ".Background", HEAD_COLOR);
                }
                if (len > 1) {
                    int[] neck = snakePositions.get(1);
                    cmd.set("#Cx" + neck[0] + "y" + neck[1] + ".Background", bodyColor(1, len));
                }
            }

            // Eat flash — briefly white-flash the head when food is eaten
            if (ateFood) {
                cmd.set("#Cx" + eatX + "y" + eatY + ".Background", EAT_FLASH_COLOR);
            }

            // Speed indicator + score
            long delay;
            synchronized (game) { delay = game.getTickDelay(); }
            cmd.set("#Score.Text", "Score: " + score + "  Speed: " + speedLevel(delay) + "  Best: " + highScore);

            sendUpdate(cmd, null, false);

            // Schedule next tick with progressive speed
            if (!isOver) {
                scheduleNextTick();
            }
        } catch (Exception ignored) {
            // Prevent scheduler death
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        cancelGameLoop();
    }
}
