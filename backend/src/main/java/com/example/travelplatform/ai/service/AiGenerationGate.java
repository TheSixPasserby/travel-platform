package com.example.travelplatform.ai.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Per-user AI generation admission gate.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>At most {@value #MAX_SUCCESSES_PER_WINDOW} successful {@link #acquire(long)} calls per user
 *       in any rolling {@value #WINDOW_MILLIS} ms window.</li>
 *   <li>At most {@value #MAX_CONCURRENT_PER_USER} in-flight permit per user.</li>
 *   <li>At most {@value #MAX_CONCURRENT_GLOBAL} in-flight permits across all users.</li>
 *   <li>{@code acquire} never blocks or queues: every refusal throws {@link Rejected} immediately.
 *       A permit {@code close()} only frees a concurrency slot; it never refunds window credit.</li>
 *   <li>Tracked user state is bounded by {@value #MAX_USERS}; expired users with no in-flight permit
 *       are reclaimed on demand. If reclamation frees nothing, the call is rejected.</li>
 *   <li>If the clock jumps backwards, the current window is never reopened, so no extra
 *       generations can be admitted.</li>
 * </ul>
 *
 * <p>Thread-safe; all state changes happen under one private monitor.</p>
 */
public final class AiGenerationGate {

    /** Rolling window length in milliseconds. */
    public static final long WINDOW_MILLIS = 60_000L;

    /** Maximum successful acquires per user per rolling window. */
    public static final int MAX_SUCCESSES_PER_WINDOW = 2;

    /** Maximum in-flight permits for a single user. */
    public static final int MAX_CONCURRENT_PER_USER = 1;

    /** Maximum in-flight permits across all users. */
    public static final int MAX_CONCURRENT_GLOBAL = 2;

    /** Maximum number of tracked users. */
    public static final int MAX_USERS = 10_000;

    private final Clock clock;
    private final Object lock = new Object();
    private final Map<Long, UserState> users = new HashMap<>();

    private int globalActive;
    private long latestNow = Long.MIN_VALUE;

    /** Creates a gate driven by {@link Clock#systemUTC()}. */
    public AiGenerationGate() {
        this(Clock.systemUTC());
    }

    /** Creates a gate using the supplied clock; a fixed clock makes tests deterministic. */
    public AiGenerationGate(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Tries to admit one AI generation for the user.
     *
     * <p>Outside tests it should be used as a resource:
     * {@code try (var permit = gate.acquire(userId)) { ...generate... }}. The permit must be closed
     * when generation finishes; closing it more than once is harmless.</p>
     *
     * @param userId external user id
     * @return a permit that must be closed to release the concurrency slot
     * @throws Rejected if the user is over their rolling window, already has a generation running,
     *     the service is at the global concurrency cap, or the bounded user table cannot make room
     */
    public Permit acquire(long userId) {
        synchronized (lock) {
            long now = Math.max(latestNow, clock.millis());
            latestNow = now;
            UserState state = users.get(userId);
            if (state == null) {
                if (users.size() >= MAX_USERS) {
                    reclaimExpired(now);
                    if (users.size() >= MAX_USERS) {
                        throw new Rejected("AI 生成服务当前繁忙，请稍后重试");
                    }
                }
                state = new UserState();
                users.put(userId, state);
            }
            expireAdmissions(state, now);
            if (state.admissions.size() >= MAX_SUCCESSES_PER_WINDOW) {
                throw new Rejected("AI generation rate limit reached");
            }
            if (state.activePermits >= MAX_CONCURRENT_PER_USER) {
                throw new Rejected("上一次 AI 生成还在进行中，请稍后重试");
            }
            if (globalActive >= MAX_CONCURRENT_GLOBAL) {
                throw new Rejected("AI 生成服务当前繁忙，请稍后重试");
            }
            state.admissions.addLast(now);
            state.activePermits++;
            globalActive++;
            return new Permit(this, state);
        }
    }

    /** Drops every expired idle user. Must be called while holding {@link #lock}. */
    private void reclaimExpired(long now) {
        Iterator<Map.Entry<Long, UserState>> iterator = users.entrySet().iterator();
        while (iterator.hasNext()) {
            UserState state = iterator.next().getValue();
            expireAdmissions(state, now);
            if (state.activePermits == 0 && state.admissions.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void expireAdmissions(UserState state, long now) {
        while (!state.admissions.isEmpty()) {
            long first = state.admissions.getFirst();
            if (now < first || now - first < WINDOW_MILLIS) {
                return;
            }
            state.admissions.removeFirst();
        }
    }

    /** Returned by {@link #acquire(long)}; closing it releases the slot exactly once. */
    public static final class Permit implements AutoCloseable {

        private final AiGenerationGate gate;
        private UserState state;

        private Permit(AiGenerationGate gate, UserState state) {
            this.gate = gate;
            this.state = state;
        }

        /** Releases the concurrency slot; repeated calls are harmless. */
        @Override
        public void close() {
            UserState released;
            synchronized (gate.lock) {
                released = state;
                state = null;
                if (released != null) {
                    released.activePermits--;
                    gate.globalActive--;
                }
            }
        }
    }

    /** Refusal thrown by {@link #acquire(long)}; carries only a user-facing message. */
    public static final class Rejected extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** Creates the refusal with a user-facing message. */
        public Rejected(String message) {
            super(message);
        }
    }

    /** Mutable per-user bookkeeping; all access is serialized by the gate monitor. */
    private static final class UserState {

        private final ArrayDeque<Long> admissions = new ArrayDeque<>(MAX_SUCCESSES_PER_WINDOW);
        private int activePermits;
    }
}
