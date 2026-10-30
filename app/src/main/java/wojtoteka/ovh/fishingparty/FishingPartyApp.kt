package wojtoteka.ovh.fishingparty

import android.app.Application
import wojtoteka.ovh.fishingparty.audio.AudioEngine
import wojtoteka.ovh.fishingparty.data.GameRepository
import wojtoteka.ovh.fishingparty.data.LevelDrafts
import wojtoteka.ovh.fishingparty.gfx.Sprites
import wojtoteka.ovh.fishingparty.online.OnlineManager

class FishingPartyApp : Application() {
    lateinit var repository: GameRepository
        private set
    lateinit var audio: AudioEngine
        private set
    lateinit var online: OnlineManager
        private set

    /** Szkice z kreatora poziomów. */
    lateinit var drafts: LevelDrafts
        private set

    /** Czy gra jest teraz na ekranie (wtedy przypomnienia o passie nie są pokazywane). */
    var inForeground = false

    override fun onCreate() {
        super.onCreate()
        Sprites.init(this)
        repository = GameRepository(this)
        drafts = LevelDrafts(filesDir)
        audio = AudioEngine(this).apply {
            musicEnabled = repository.save.music
            sfxEnabled = repository.save.sfx
            vibrationEnabled = repository.save.vibration
        }
        online = OnlineManager(this, repository) {
            // Zapis z konta niesie też ustawienia dźwięku.
            audio.musicEnabled = repository.save.music
            audio.sfxEnabled = repository.save.sfx
            audio.vibrationEnabled = repository.save.vibration
        }
    }
}
