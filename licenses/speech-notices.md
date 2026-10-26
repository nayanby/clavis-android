# Bundled offline speech recognition

- Vosk Android 0.3.75: https://github.com/alphacep/vosk-api (Apache 2.0).
- English model: vosk-model-small-en-us-0.15, https://alphacephei.com/vosk/models (Apache 2.0).
- JNA 5.18.1: https://github.com/java-native-access/jna (Apache 2.0 option; original notices remain in the AAR).

The English model is shipped in the APK and extracted into private no-backup storage on first use. No Android speech-model download, microphone recording, API key or network request is needed for transcription. Recognition is automatic and can contain errors.

Updated English model: vosk-model-en-us-0.22-lgraph, Apache 2.0. Official source: https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip
