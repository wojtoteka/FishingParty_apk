package wojtoteka.ovh.fishingparty.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.game.Sfx
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Odtwarzanie dźwięku. Efekty i muzyka są renderowane raz (tym samym
 * syntezatorem co w oryginale) do plików WAV w cache, potem grają przez
 * SoundPool / AudioTrack bez opóźnień.
 */
class AudioEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
    private val soundIds = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile
    private var music: AudioTrack? = null
    @Volatile
    private var musicWanted = false

    var sfxEnabled = true
    var vibrationEnabled = true
    var musicEnabled = true
        set(value) {
            field = value
            refreshMusic()
        }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        scope.launch { prepare() }
    }

    private fun prepare() {
        try {
            val dir = File(appContext.cacheDir, "sfx_v$VERSION").apply { mkdirs() }
            for ((name, lines) in SoundScript.SFX) {
                val file = File(dir, "$name.wav")
                if (!file.exists()) writeWav(file, BeepSynth.toPcm16(SoundScript.render(lines, SAMPLE_RATE)))
                soundIds[name] = pool.load(file.path, 1)
            }
            val samples = SoundScript.render(SoundScript.MUSIC, SAMPLE_RATE, SoundScript.MUSIC_SECONDS)
            val pcm = BeepSynth.toPcm16(samples)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, pcm.size, -1)
            track.setVolume(MUSIC_VOLUME)
            music = track
            refreshMusic()
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się przygotować dźwięku", e)
        }
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val tmp = File(file.path + ".tmp")
        DataOutputStream(FileOutputStream(tmp).buffered()).use { out ->
            val dataSize = pcm.size * 2
            out.writeBytes("RIFF")
            out.writeIntLe(36 + dataSize)
            out.writeBytes("WAVEfmt ")
            out.writeIntLe(16)
            out.writeShortLe(1)
            out.writeShortLe(1)
            out.writeIntLe(SAMPLE_RATE)
            out.writeIntLe(SAMPLE_RATE * 2)
            out.writeShortLe(2)
            out.writeShortLe(16)
            out.writeBytes("data")
            out.writeIntLe(dataSize)
            for (s in pcm) out.writeShortLe(s.toInt())
        }
        tmp.renameTo(file)
    }

    private fun DataOutputStream.writeIntLe(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLe(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }

    fun play(sfx: Sfx) {
        if (!sfxEnabled) return
        val name = when (sfx) {
            Sfx.FISH -> "fish"
            Sfx.FISH_GOLD -> "fish_gold"
            Sfx.COMBO -> "combo"
            Sfx.HIT -> "hit"
            Sfx.LOST -> "lost"
            Sfx.START -> "start"
            Sfx.BUMP -> "bump"
            Sfx.GATE_OPEN -> "gate"
            Sfx.STAGE -> "stage"
            Sfx.WIN -> "win"
            Sfx.CLICK -> "click"
            Sfx.COIN -> "coin"
            Sfx.ACHIEVEMENT -> "achievement"
            Sfx.SHIELD -> "shield"
            Sfx.STAR -> "star"
        }
        val id = soundIds[name] ?: return
        pool.play(id, SFX_VOLUME, SFX_VOLUME, 1, 0, 1f)
    }

    fun vibrate(millis: Long) {
        if (!vibrationEnabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    /** Wywoływane z cyklu życia aktywności. */
    fun setForeground(foreground: Boolean) {
        musicWanted = foreground
        refreshMusic()
    }

    private fun refreshMusic() {
        val track = music ?: return
        try {
            if (musicWanted && musicEnabled) {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
            } else if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Muzyka niedostępna", e)
        }
    }

    companion object {
        private const val TAG = "FishingParty"
        private const val VERSION = 1
        const val SAMPLE_RATE = 48000
        private const val SFX_VOLUME = 0.9f
        private const val MUSIC_VOLUME = 0.7f
    }
}
