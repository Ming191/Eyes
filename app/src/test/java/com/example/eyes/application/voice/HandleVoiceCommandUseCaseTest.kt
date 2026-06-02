package com.example.eyes.application.voice

import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.application.ports.VoiceCommandRepository
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.domain.voice.VoiceCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class HandleVoiceCommandUseCaseTest {
    private val repository = FakeVoiceCommandRepository()
    private val speechOutput = FakeSpeechOutput()
    private val text = VoiceCommandText(
        readText = "read",
        describeScene = "scene",
        recognizeCurrency = "currency",
        detectObjects = "objects",
        openHome = "home",
        openSettings = "settings",
        openEmergency = "emergency",
        ocrQuick = "quick",
        ocrAccurate = "accurate",
        stop = "stop",
        nothingToRepeat = "nothing",
        help = "help",
        unknown = "unknown"
    )
    private val useCase = HandleVoiceCommandUseCase(
        voiceCommandRepository = repository,
        speechOutput = speechOutput,
        voiceCommandTextProvider = object : VoiceCommandTextProvider {
            override fun text(language: AppLanguage) = text
        }
    )

    @Test
    fun invoke_navigationCommands_speaksExpectedTextAndReturnsTarget() = runTest {
        val cases = listOf(
            VoiceCommand.ReadText to ("read" to VoiceNavigationTargetKind.Camera),
            VoiceCommand.DescribeScene to ("scene" to VoiceNavigationTargetKind.Camera),
            VoiceCommand.RecognizeCurrency to ("currency" to VoiceNavigationTargetKind.Camera),
            VoiceCommand.DetectObjects to ("objects" to VoiceNavigationTargetKind.Camera),
            VoiceCommand.OcrQuick to ("quick" to VoiceNavigationTargetKind.Camera),
            VoiceCommand.OcrAccurate to ("accurate" to VoiceNavigationTargetKind.Camera),
            VoiceCommand.Stop to ("stop" to VoiceNavigationTargetKind.Home),
            VoiceCommand.OpenHome to ("home" to VoiceNavigationTargetKind.Home),
            VoiceCommand.OpenSettings to ("settings" to VoiceNavigationTargetKind.Settings),
            VoiceCommand.OpenEmergency to ("emergency" to VoiceNavigationTargetKind.Emergency)
        )

        cases.forEach { (command, expected) ->
            val action = useCase(command, AppLanguage.EN)

            assertEquals(expected.first, speechOutput.spoken.last())
            assertEquals(expected.second, action.navigationTarget)
            assertEquals(false, action.shouldRestartListening)
            assertEquals(command, repository.lastCommand)
        }
    }

    @Test
    fun invoke_repeatWithoutPriorSpeech_speaksNothingToRepeatAndRestartsListening() = runTest {
        val action = useCase(VoiceCommand.Repeat, AppLanguage.EN)

        assertEquals("nothing", speechOutput.spoken.last())
        assertEquals(true, action.shouldRestartListening)
        assertEquals(null, action.navigationTarget)
    }

    @Test
    fun invoke_repeatAfterPriorSpeech_repeatsLastSpokenText() = runTest {
        useCase(VoiceCommand.OpenSettings, AppLanguage.EN)

        val action = useCase(VoiceCommand.Repeat, AppLanguage.EN)

        assertEquals("settings", speechOutput.spoken.last())
        assertEquals(true, action.shouldRestartListening)
    }

    @Test
    fun invoke_helpAndUnknown_restartListening() = runTest {
        val help = useCase(VoiceCommand.Help, AppLanguage.EN)
        val unknown = useCase(VoiceCommand.Unknown("wat"), AppLanguage.EN)

        assertEquals("unknown", speechOutput.spoken.last())
        assertEquals(true, help.shouldRestartListening)
        assertEquals(true, help.shouldExpandHelp)
        assertEquals(true, unknown.shouldRestartListening)
    }

    private class FakeVoiceCommandRepository : VoiceCommandRepository {
        private val state = MutableStateFlow<VoiceCommand?>(null)
        override val lastVoiceCommandFlow: Flow<VoiceCommand?> = state
        var lastCommand: VoiceCommand? = null

        override suspend fun setLastVoiceCommand(command: VoiceCommand) {
            lastCommand = command
            state.value = command
        }

        override suspend fun clearLastVoiceCommand() {
            lastCommand = null
            state.value = null
        }
    }

    private class FakeSpeechOutput : SpeechOutput {
        val spoken = mutableListOf<String>()

        override fun speak(text: String) {
            spoken += text
        }

        override suspend fun speakAndAwait(text: String, locale: Locale) {
            spoken += text
        }
    }
}
