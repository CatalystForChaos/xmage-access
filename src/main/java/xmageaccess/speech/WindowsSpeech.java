package xmageaccess.speech;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Windows speech output. Tries Tolk first for direct NVDA/JAWS
 * communication, falls back to Windows SAPI if Tolk is not available.
 *
 * The SAPI fallback keeps one persistent PowerShell SpeechSynthesizer
 * process alive and feeds it lines over stdin. Spawning a new PowerShell
 * per utterance (the old approach) added 1-2 seconds of latency to every
 * announcement and let queued announcements overlap each other.
 */
public class WindowsSpeech implements SpeechEngine {

    // Protocol: first char of each line is the command, rest is the text.
    // I = interrupt (cancel current speech, then speak)
    // S = speak queued
    // C = cancel all speech
    private static final String SAPI_WORKER_SCRIPT =
            "[Console]::InputEncoding = [System.Text.Encoding]::UTF8\n"
            + "Add-Type -AssemblyName System.Speech\n"
            + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer\n"
            + "while ($true) {\n"
            + "  $line = [Console]::In.ReadLine()\n"
            + "  if ($null -eq $line) { break }\n"
            + "  if ($line.Length -lt 1) { continue }\n"
            + "  $cmd = $line.Substring(0, 1)\n"
            + "  $txt = $line.Substring(1)\n"
            + "  if ($cmd -eq 'I') { $s.SpeakAsyncCancelAll(); if ($txt.Length -gt 0) { [void]$s.SpeakAsync($txt) } }\n"
            + "  elseif ($cmd -eq 'S') { if ($txt.Length -gt 0) { [void]$s.SpeakAsync($txt) } }\n"
            + "  elseif ($cmd -eq 'C') { $s.SpeakAsyncCancelAll() }\n"
            + "}\n";

    private TolkSpeech tolkSpeech;
    private boolean usingSapi;
    private Process sapiProcess;
    private Writer sapiWriter;

    public WindowsSpeech() {
        // Try to load Tolk for direct screen reader communication
        TolkLibrary tolkLib = TolkLibrary.load();
        if (tolkLib != null) {
            tolkSpeech = new TolkSpeech(tolkLib);
            if (tolkSpeech.isLoaded()) {
                System.out.println("[XMage Access] Speech: Using Tolk for screen reader output.");
                return;
            }
        }

        // Tolk not available, fall back to SAPI
        usingSapi = true;
        System.out.println("[XMage Access] Speech: Tolk not found, falling back to Windows SAPI.");
        // Start the worker eagerly so the first announcement isn't delayed
        // by PowerShell startup.
        ensureSapiWorker();
    }

    @Override
    public synchronized void speak(String text, boolean interrupt) {
        if (tolkSpeech != null && tolkSpeech.isLoaded()) {
            tolkSpeech.speak(text, interrupt);
        } else {
            sendSapiCommand((interrupt ? "I" : "S") + sanitize(text));
        }
    }

    @Override
    public synchronized void silence() {
        if (tolkSpeech != null && tolkSpeech.isLoaded()) {
            tolkSpeech.silence();
        }
        if (usingSapi) {
            sendSapiCommand("C");
        }
    }

    public synchronized void shutdown() {
        if (tolkSpeech != null) {
            tolkSpeech.shutdown();
        }
        if (sapiWriter != null) {
            try { sapiWriter.close(); } catch (Exception ignored) {}
            sapiWriter = null;
        }
        if (sapiProcess != null) {
            try { sapiProcess.destroy(); } catch (Exception ignored) {}
            sapiProcess = null;
        }
    }

    /** The worker reads one command per line; strip line breaks from text. */
    private static String sanitize(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
    }

    private void sendSapiCommand(String line) {
        try {
            ensureSapiWorker();
            if (sapiWriter == null) return;
            sapiWriter.write(line);
            sapiWriter.write("\n");
            sapiWriter.flush();
        } catch (Exception first) {
            // Worker likely died — restart once and retry.
            try {
                restartSapiWorker();
                if (sapiWriter != null) {
                    sapiWriter.write(line);
                    sapiWriter.write("\n");
                    sapiWriter.flush();
                }
            } catch (Exception second) {
                System.err.println("[XMage Access] SAPI speech error: " + second.getMessage());
            }
        }
    }

    private void ensureSapiWorker() {
        if (sapiProcess != null && sapiProcess.isAlive() && sapiWriter != null) {
            return;
        }
        restartSapiWorker();
    }

    private void restartSapiWorker() {
        shutdownSapiOnly();
        try {
            // -EncodedCommand avoids all quoting issues with the script text.
            String encoded = Base64.getEncoder().encodeToString(
                    SAPI_WORKER_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder("powershell",
                    "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
            pb.redirectErrorStream(true);
            sapiProcess = pb.start();
            sapiWriter = new OutputStreamWriter(sapiProcess.getOutputStream(), StandardCharsets.UTF_8);
            drainInBackground(sapiProcess);
        } catch (Exception e) {
            System.err.println("[XMage Access] Could not start SAPI worker: " + e.getMessage());
            sapiProcess = null;
            sapiWriter = null;
        }
    }

    private void shutdownSapiOnly() {
        if (sapiWriter != null) {
            try { sapiWriter.close(); } catch (Exception ignored) {}
            sapiWriter = null;
        }
        if (sapiProcess != null) {
            try { sapiProcess.destroy(); } catch (Exception ignored) {}
            sapiProcess = null;
        }
    }

    /** Consume worker output so the pipe buffer can never block it. */
    private static void drainInBackground(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {
                    // Discard
                }
            } catch (Exception ignored) {
                // Process ended
            }
        }, "xmage-access-sapi-drain");
        t.setDaemon(true);
        t.start();
    }
}
