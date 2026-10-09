package com.doffi4.doffisecure.dev

import com.doffi4.doffisecure.domain.model.Password
import kotlin.random.Random

data class SyntheticServiceAllocation(val service: String, val accountCount: Int)

// Construct only through plan(): validates both limits and the complete allocation.
class SyntheticVaultPlan internal constructor(val allocations: List<SyntheticServiceAllocation>) {
    val entryCount: Int get() = allocations.sumOf { it.accountCount }
    val serviceCount: Int get() = allocations.size
}

sealed interface TestDatasetRequest {
    data class Realistic(val plan: SyntheticVaultPlan) : TestDatasetRequest
    data object SecurityCheck : TestDatasetRequest
    data object Totp : TestDatasetRequest
}

sealed interface TestDataSeedState {
    data object Idle : TestDataSeedState
    data object Running : TestDataSeedState
    data class Saved(val inserted: Int, val requested: Int) : TestDataSeedState
    data object Failed : TestDataSeedState
    data object Locked : TestDataSeedState
    data object Disabled : TestDataSeedState
}

/** Public synthetic values only. No vault read, network, filesystem or account creation. */
object SyntheticVaultGenerator {
    const val MAX_ENTRIES = 5000
    const val MAX_SERVICES = 200

    // Illustrative service labels, not an authoritative recipient/trust registry.
    private val services = """
        google.com apple.com github.com netflix.com spotify.com amazon.com microsoft.com
        facebook.com instagram.com whatsapp.com telegram.org discord.com reddit.com x.com
        linkedin.com tiktok.com youtube.com twitch.tv pinterest.com snapchat.com threads.net
        bluesky.social mastodon.social tumblr.com quora.com medium.com substack.com patreon.com
        buymeacoffee.com ko-fi.com gumroad.com kickstarter.com indiegogo.com change.org
        wikipedia.org wikimedia.org stackoverflow.com stackexchange.com gitlab.com bitbucket.org
        sourceforge.net codeberg.org gitea.com npmjs.com pypi.org docker.com hub.docker.com
        kubernetes.io cloudflare.com vercel.com netlify.com render.com railway.com fly.io
        heroku.com digitalocean.com linode.com vultr.com hetzner.com ovhcloud.com aws.amazon.com
        azure.com cloud.google.com oracle.com ibm.com salesforce.com hubspot.com zoho.com
        notion.so evernote.com onenote.com obsidian.md trello.com asana.com monday.com clickup.com
        linear.app airtable.com todoist.com ticktick.com basecamp.com wrike.com smartsheet.com
        slack.com teams.microsoft.com zoom.us meet.google.com webex.com whereby.com miro.com
        figma.com canva.com adobe.com behance.net dribbble.com sketch.com framer.com webflow.com
        wordpress.com wix.com squarespace.com shopify.com bigcommerce.com etsy.com ebay.com
        aliexpress.com walmart.com target.com bestbuy.com costco.com ikea.com zara.com hm.com
        uniqlo.com nike.com adidas.com puma.com reebok.com newbalance.com asos.com zalando.com
        shein.com temu.com rozetka.com.ua prom.ua olx.ua epicentrk.ua allo.ua comfy.ua foxtrot.com.ua
        steamcommunity.com steampowered.com epicgames.com gog.com ea.com ubisoft.com battle.net
        riotgames.com playstation.com xbox.com nintendo.com roblox.com minecraft.net itch.io
        humblebundle.com fanatical.com nexusmods.com modrinth.com curseforge.com chess.com lichess.org
        duolingo.com coursera.org edx.org udemy.com khanacademy.org skillshare.com brilliant.org
        codecademy.com freecodecamp.org leetcode.com hackerrank.com codewars.com datacamp.com
        kaggle.com huggingface.co openai.com anthropic.com perplexity.ai runwayml.com midjourney.com
        stability.ai replicate.com elevenlabs.io deepl.com grammarly.com languagetool.org
        dropbox.com box.com drive.google.com mega.nz pcloud.com sync.com proton.me tutanota.com
        fastmail.com mail.com yahoo.com aol.com icloud.com outlook.com gmx.com hey.com
        soundcloud.com bandcamp.com deezer.com tidal.com qobuz.com last.fm pandora.com
    """.trimIndent().split(Regex("\\s+")).also {
        check(it.size == MAX_SERVICES && it.distinct().size == MAX_SERVICES)
    }

    /** Stable preview: changing inputs changes only the allocation, never stored data. */
    fun plan(entryCount: Int, serviceCount: Int): SyntheticVaultPlan {
        require(entryCount in 1..MAX_ENTRIES)
        require(serviceCount in 1..minOf(MAX_SERVICES, entryCount))
        val selected = services.take(serviceCount)
        val counts = IntArray(serviceCount) { 1 }
        val weights = intArrayOf(1, 1, 1, 2, 3, 5, 7, 12, 20)
        val tickets = selected.indices.flatMap { index -> List(weights[index % weights.size]) { index } }
        val random = Random(208)
        repeat(entryCount - serviceCount) { counts[tickets[random.nextInt(tickets.size)]]++ }
        return SyntheticVaultPlan(selected.mapIndexed { i, name -> SyntheticServiceAllocation(name, counts[i]) })
    }

    fun generate(plan: SyntheticVaultPlan, batchId: String, now: Long): List<Password> {
        require(batchId.matches(Regex("[A-Za-z0-9-]{1,64}")))
        val random = Random(batchId.hashCode())
        val characters = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!#@%"
        var index = 0
        return plan.allocations.flatMap { allocation ->
            List(allocation.accountCount) { account ->
                val i = index++
                val secret = when {
                    i % 13 == 0 -> "password123"
                    i % 11 == 0 -> "NOT_REAL_SHARED_7z!V9m#2"
                    else -> "NOT_REAL_" + List(18) { characters[random.nextInt(characters.length)] }.joinToString("")
                }
                Password(
                    id = 0,
                    service = allocation.service,
                    username = "test-${batchId}-${i + 1}-${account + 1}@example.invalid",
                    password = secret,
                    url = "https://${allocation.service}",
                    createdAt = now - random.nextLong(365L * 24 * 60 * 60 * 1000)
                )
            }
        }
    }

    /** Same four rows as the tester instructions; duplicates intentionally preserved. */
    fun securityFixture(now: Long): List<Password> = listOf(
        Password(0, "Demo Alpha", "demo-user-a", "password123", null, now),
        Password(0, "Demo Beta", "demo-user-b", "password123", null, now),
        Password(0, "Demo Copy", "demo-user-c", "NOT_REAL_TEST_ONLY_7z!V9m#2", null, now),
        Password(0, "Demo Copy", "demo-user-c", "NOT_REAL_TEST_ONLY_7z!V9m#2", null, now)
    )

    /** RFC 6238 Appendix B public SHA-1 fixture, shown with the app's 6-digit UI. */
    fun totpFixture(now: Long) = Password(
        0, "Demo TOTP", "demo-totp-user", "", null, now,
        "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    )
}
