package com.musicstats.app.service

import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.model.Moment
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentReleaseScheduler @Inject constructor(
    private val momentDao: MomentDao,
    private val eventDao: ListeningEventDao
) {
    private val mutex = Mutex()

    /**
     * Attempts to release the next highest-priority moment.
     * Returns the released moment (for notification decision), or null if nothing released.
     *
     * Rules:
     * 1. Total listening must be >= 5 hours (gate)
     * 2. Max 1 release per calendar day
     * 3. Pick highest priority (lowest tier number), then newest within tier
     * 4. Expire tier 3-5 moments older than 14 days unreleased
     */
    suspend fun releaseNext(): Moment? = mutex.withLock {
        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        if (totalMs < MomentPriority.GATE_MS) return@withLock null

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        if (momentDao.countReleasedInRange(todayStart, todayEnd) > 0) return@withLock null

        // Expire stale tier 3-5 moments
        val expiryCutoff = now - MomentPriority.EXPIRY_MS
        val unreleased = momentDao.getUnreleasedMoments()
        val expiredIds = unreleased
            .filter { it.triggeredAt < expiryCutoff && MomentPriority.canExpire(MomentPriority.tierOf(it.type)) }
            .map { it.id }
        if (expiredIds.isNotEmpty()) {
            momentDao.deleteByIds(expiredIds)
        }

        // Pick the best remaining unreleased moment
        val candidates = if (expiredIds.isEmpty()) unreleased else momentDao.getUnreleasedMoments()
        val best = candidates
            .sortedWith(compareBy<Moment> { MomentPriority.tierOf(it.type) }.thenByDescending { it.triggeredAt })
            .firstOrNull()
            ?: return@withLock null

        momentDao.setReleasedAt(best.id, now)
        best.copy(releasedAt = now)
    }
}
