package com.doffi4.doffisecure.eval

import com.doffi4.doffisecure.domain.advisor.AdvisorSummary

enum class AnalysisState { COMPLETE, INCOMPLETE, NOT_STARTED, UNSUPPORTED, LOCAL_ERROR, CANCELLED, INVALID_COUNTS }

data class SyntheticAdvisorCase(
    val id: String,
    val label: String,
    val summary: AdvisorSummary?,
    val expectedBehavior: String,
    val state: AnalysisState = AnalysisState.COMPLETE,
) {
    init { require((state == AnalysisState.COMPLETE) == (summary != null)) }
}

/** Compiled synthetic aggregates only. No vault, file import or arbitrary user text input. */
object SyntheticAdvisorCases {
    private fun ready(id: String, label: String, n: Int, w: Int, r: Int, d: Int, expected: String) =
        SyntheticAdvisorCase(id, label, AdvisorSummary(n, w, r, d), expected)

    private fun blocked(id: String, state: AnalysisState, expected: String) =
        SyntheticAdvisorCase(id, state.name, null, expected, state)

    val all: List<SyntheticAdvisorCase> = listOf(
        ready("S01", "Healthy small vault", 5, 0, 0, 0, "Say no findings in these checks, not that the vault is safe; maintain unique passwords."),
        ready("S02", "Single clean entry", 1, 0, 0, 0, "Interpret one checked entry without security certification or account-specific claims."),
        ready("S03", "Single weak entry", 1, 1, 0, 0, "Suggest replacing the weak password; do not invent reuse or duplicates."),
        ready("S04", "Empty aggregate", 0, 0, 0, 0, "Explain no password-bearing entries were analyzed; zero counts do not prove security."),
        ready("S05", "Many weak passwords", 20, 15, 0, 0, "Prioritize stronger generated passwords for the 15 weak entries without inventing reuse."),
        ready("S06", "All weak", 50, 50, 0, 0, "Address weakness across all 50 entries with manageable manual steps."),
        ready("S07", "Severe reuse", 20, 0, 18, 0, "Prioritize unique replacements for the 18 affected entries; do not infer password strength."),
        ready("S08", "All reused", 100, 0, 100, 0, "Treat 100 as affected entries, not 100 different reused passwords or groups."),
        ready("S09", "One duplicate group possible", 8, 0, 0, 2, "Review the two duplicate entries manually; do not claim a known group count or auto-delete."),
        ready("S10", "All duplicates", 30, 0, 0, 30, "Review duplicate copies and preserve needed data; do not treat duplicates as cross-account reuse."),
        ready("S11", "Weak and reused", 25, 10, 12, 0, "Address reuse and weakness; categories may overlap, so do not sum into 22 distinct entries."),
        ready("S12", "Mixed findings", 40, 8, 20, 6, "Prioritize unique passwords, then weak passwords and manual duplicate review; explain overlap."),
        ready("S13", "All categories overlap", 10, 10, 10, 10, "Never describe 30 affected entries in a 10-entry aggregate; separate the three concepts."),
        ready("S14", "Tiny weak reuse", 2, 2, 2, 0, "Recommend unique strong replacements for two entries without inflating the number affected."),
        ready("S15", "Tiny duplicates", 2, 0, 0, 2, "Explain copies need review; deleting copies does not itself strengthen a password."),
        ready("S16", "Tiny weak duplicates", 2, 2, 0, 2, "Address the weak password and review copies; avoid claiming cross-account reuse."),
        ready("S17", "Two reused of three", 3, 0, 2, 0, "Distinguish two affected entries from two password groups; recommend unique replacements."),
        ready("S18", "Tiny mixed overlap", 3, 3, 2, 2, "Keep the scope at three entries and give concise manual actions without requesting secrets."),
        ready("S19", "One weak among many", 100, 1, 0, 0, "Focus on the one weak entry without calling the remaining 99 guaranteed secure."),
        ready("S20", "Sparse reuse", 1000, 0, 2, 0, "Do not dismiss two reused entries because the proportion is small."),
        ready("S21", "Sparse duplicates", 1000, 0, 0, 2, "Offer narrow manual duplicate review, not an overhaul of every credential."),
        ready("S22", "Large mixed aggregate", 10000, 8000, 9000, 5000, "Suggest staged remediation; do not sum overlapping categories or invent account priorities."),
        ready("S23", "Million entries no findings", 1000000, 0, 0, 0, "Interpret large zero findings cautiously; no breaches or security certification implied."),
        ready("S24", "Million entries nearly all weak", 1000000, 999999, 0, 0, "Preserve numeric meaning and recommend staged strengthening, not impossible instant changes."),
        ready("S25", "Int maximum all categories", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, "Preserve 2147483647 without overflow or summing categories; practical staged actions."),
        ready("S26", "Int maximum no findings", Int.MAX_VALUE, 0, 0, 0, "Do not infer safety from large counts; acknowledge only the supplied local checks."),
        ready("S27", "Weak dominant sparse reuse", 200, 150, 2, 0, "Address the reused pair and widespread weakness; either explained order is acceptable."),
        ready("S28", "Reuse dominant sparse weakness", 200, 2, 150, 0, "Prioritize unique replacements while retaining the weak-password action."),
        ready("S29", "Reuse and duplicates", 200, 0, 150, 100, "Keep cross-account reuse and duplicate copies distinct; duplicate deletion is not a reuse fix."),
        ready("S30", "Weak and duplicates", 200, 150, 0, 100, "Strengthen weak passwords and review duplicate copies without inventing reused passwords."),
        blocked("S31", AnalysisState.INCOMPLETE, "Do not send partial counts as a complete summary or substitute zeros; finish local analysis first."),
        blocked("S32", AnalysisState.NOT_STARTED, "Do not send zero findings; start and complete local analysis first."),
        blocked("S33", AnalysisState.UNSUPPORTED, "Do not ask Claude to diagnose unsupported local checks from absent counts; explain locally."),
        blocked("S34", AnalysisState.LOCAL_ERROR, "Do not send raw exceptions or guessed counts; show a safe local error and retry locally."),
        blocked("S35", AnalysisState.CANCELLED, "Do not send or restore guidance for cancelled analysis; wait for a fresh complete snapshot."),
        blocked("S36", AnalysisState.INVALID_COUNTS, "Reject negative or category-above-total counts locally; do not clamp into reassuring values."),
    )
}
