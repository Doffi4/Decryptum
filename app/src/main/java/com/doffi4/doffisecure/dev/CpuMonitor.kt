package com.doffi4.doffisecure.dev

import java.io.File

/**
 * One-second snapshot of system health for the dev tools.
 *
 * @param cpuTempC Approximate CPU temperature in degrees Celsius, or null when
 *   the device does not expose readable thermal zones.
 * @param tempSource Human-readable hint of where [cpuTempC] came from
 *   ("CPU/SoC зоны", "макс. зона", ...).
 * @param cpuLoadPercent Overall CPU load over the last sample interval
 *   (0..100+), computed from /proc/stat deltas.
 * @param cpuFreqMhz Highest current core frequency in MHz, or null when the
 *   cpufreq governor is not readable.
 */
data class CpuStats(
    val cpuTempC: Float?,
    val tempSource: String,
    val cpuLoadPercent: Float,
    val cpuFreqMhz: Int?,
)

/**
 * Reads approximate CPU temperature, overall CPU load and current CPU
 * frequency straight from the Linux sysfs/proc filesystem.
 *
 * There is no official Android API for CPU temperature, so this follows the
 * de-facto convention used by every monitoring app:
 *  - temperature: /sys/class/thermal/thermal_zone directories expose
 *    {type,temp} files — the kernel reports millidegrees Celsius. Zones whose
 *    type mentions "cpu", "soc" or "tsens" are the SoC/CPU sensors, so the
 *    hottest of those is used; if none exists the hottest readable zone is a
 *    rough fallback.
 *  - load: /proc/stat line 1 holds cumulative jiffies; the delta of
 *    (total - idle) / total over the previous call gives the load.
 *  - frequency: /sys/devices/system/cpu/cpu*, subdir cpufreq/scaling_cur_freq.
 *
 * Reading a handful of one-line files once per second is negligible, so the
 * caller just calls [sample] from a slow coroutine ticker.
 */
class CpuMonitor {

    private var lastTotalJiffies = 0L
    private var lastIdleJiffies = 0L
    private var hasPreviousSample = false

    /**
     * Reads the current CPU stats. Safe to call repeatedly; load is computed
     * against the previous call, so the first call reports 0%.
     */
    fun sample(): CpuStats {
        val (tempC, source) = readCpuTemperature()
        val (totalJiffies, idleJiffies) = readCpuJiffies()
        val load = if (hasPreviousSample && totalJiffies > lastTotalJiffies) {
            val totalDelta = totalJiffies - lastTotalJiffies
            val idleDelta = idleJiffies - lastIdleJiffies
            100f * (totalDelta - idleDelta) / totalDelta
        } else {
            0f
        }
        lastTotalJiffies = totalJiffies
        lastIdleJiffies = idleJiffies
        hasPreviousSample = true

        return CpuStats(
            cpuTempC = tempC,
            tempSource = source,
            cpuLoadPercent = load,
            cpuFreqMhz = readCpuFrequencyMhz(),
        )
    }

    // ── Temperature ────────────────────────────────────────────────────────

    private val CPU_ZONE_HINTS = listOf("cpu", "soc", "tsens", "apc")

    private fun readCpuTemperature(): Pair<Float?, String> {
        val zones = readThermalZones()
        if (zones.isEmpty()) return null to "—"

        // Prefer CPU/SoC-related zones; otherwise fall back to the hottest zone.
        val cpuZones = zones.filter { (type, _) ->
            CPU_ZONE_HINTS.any { hint -> type.contains(hint, ignoreCase = true) }
        }
        if (cpuZones.isNotEmpty()) {
            return (cpuZones.maxOfOrNull { it.second }) to "cpu-zones"
        }
        return (zones.maxOfOrNull { it.second }) to "max-zone"
    }

    /** Reads every readable thermal zone as (type, millidegC). */
    private fun readThermalZones(): List<Pair<String, Float>> {
        val root = File("/sys/class/thermal")
        val dirs = root.listFiles { file -> file.name.startsWith("thermal_zone") } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val type = readText(File(dir, "type"))?.trim().orEmpty()
            val rawTemp = readText(File(dir, "temp"))?.trim()?.toFloatOrNull()
            if (rawTemp == null || rawTemp < 0) {
                null
            } else {
                // The kernel reports millidegrees Celsius for thermal zones.
                type to (rawTemp / 1000f)
            }
        }
    }

    // ── Load ───────────────────────────────────────────────────────────────

    /** Parses the aggregate "cpu " line of /proc/stat into (total, idle) jiffies. */
    private fun readCpuJiffies(): Pair<Long, Long> {
        val line = readText(File("/proc/stat"))?.lineSequence()?.firstOrNull()
            ?.takeIf { it.startsWith("cpu ") } ?: return 0L to 0L
        // cpu user nice system idle iowait irq softirq steal guest guest_nice
        val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 5) return 0L to 0L
        val idle = parts[3] + parts.getOrElse(4) { 0 } // idle + iowait
        val total = parts.take(8).sum()
        return total to idle
    }

    // ── Frequency ──────────────────────────────────────────────────────────

    /** Current frequency of the fastest core, in MHz, or null. */
    private fun readCpuFrequencyMhz(): Int? {
        val cpuRoot = File("/sys/devices/system/cpu")
        val cores = cpuRoot.listFiles { file ->
            file.name.startsWith("cpu") && file.name.drop(3).toIntOrNull() != null
        } ?: return null
        val maxKHz = cores.mapNotNull { core ->
            readText(File(core, "cpufreq/scaling_cur_freq"))?.trim()?.toLongOrNull()
        }.maxOrNull()
        return maxKHz?.let { (it / 1000).toInt() }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Safe single-file read; returns null when unreadable (permissions, absent). */
    private fun readText(file: File): String? = try {
        if (file.exists() && file.canRead()) file.readText() else null
    } catch (_: Exception) {
        null
    }
}