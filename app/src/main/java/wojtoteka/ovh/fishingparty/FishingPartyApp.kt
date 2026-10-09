package wojtoteka.ovh.fishingparty

import android.app.Application
import wojtoteka.ovh.fishingparty.audio.AudioEngine
import wojtoteka.ovh.fishingparty.data.GameRepository
import wojtoteka.ovh.fishingparty.gfx.Sprites

class FishingPartyApp : Application() {
    lateinit var repository: GameRepository
        private set
    lateinit var audio: AudioEngine
        private set

    override fun onCreate() {
        super.onCreate()
        Sprites.init(this)
        repository = GameRepository(this)
        audio = AudioEngine(this).apply {
            musicEnabled = repository.save.music
            sfxEnabled = repository.save.sfx
            vibrationEnabled = repository.save.vibration
        }
    }
}
