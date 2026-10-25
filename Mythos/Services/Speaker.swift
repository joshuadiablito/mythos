import AVFoundation
import Combine
import Observation

/// Reads stories and words aloud with the device's own voices, and plays
/// native-speaker recordings from Wiktionary when there are some.
@Observable
final class Speaker: NSObject, AVSpeechSynthesizerDelegate {
    static let shared = Speaker()

    /// What is being spoken, so the button that started it can show a stop control.
    private(set) var speakingID: String?
    private(set) var isPaused = false
    /// The word being spoken, as a UTF-16 range of the text passed to `speak`,
    /// so a reader can highlight it as it's read.
    private(set) var spokenRange: NSRange?

    @ObservationIgnored private let synthesizer = AVSpeechSynthesizer()
    @ObservationIgnored private var player: AVPlayer?

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    private func activateSession() {
        // Playback, so stories are heard with the ring/silent switch on silent.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    func speak(_ text: String, language: Language, rate: Double = 0.42, id: String? = nil) {
        stop()
        activateSession()
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: language.voiceCode)
        utterance.rate = Float(rate)
        utterance.postUtteranceDelay = 0.1
        speakingID = id ?? text
        synthesizer.speak(utterance)
    }

    func pause() {
        guard synthesizer.isSpeaking, !isPaused else { return }
        synthesizer.pauseSpeaking(at: .word)
        isPaused = true
    }

    func resume() {
        guard isPaused else { return }
        synthesizer.continueSpeaking()
        isPaused = false
    }

    /// Plays a recording, falling back to the device's voice if it can't.
    func play(_ url: URL?, fallback text: String, language: Language) {
        guard let url else { return speak(text, language: language, rate: 0.4) }
        stop()
        activateSession()
        let item = AVPlayerItem(url: url)
        player = AVPlayer(playerItem: item)
        speakingID = text
        player?.play()
        Task {
            // A recording that fails to load is read by the device instead.
            for await status in item.publisher(for: \.status).values where status != .unknown {
                if status == .failed { speak(text, language: language, rate: 0.4) }
                break
            }
        }
    }

    func stop() {
        if synthesizer.isSpeaking { synthesizer.stopSpeaking(at: .immediate) }
        player?.pause()
        player = nil
        speakingID = nil
        isPaused = false
        spokenRange = nil
    }

    func isSpeaking(_ id: String) -> Bool { speakingID == id }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, willSpeakRangeOfSpeechString characterRange: NSRange, utterance: AVSpeechUtterance) {
        Task { @MainActor in self.spokenRange = characterRange }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            if !self.synthesizer.isSpeaking {
                self.speakingID = nil
                self.spokenRange = nil
                self.isPaused = false
            }
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {}
}
