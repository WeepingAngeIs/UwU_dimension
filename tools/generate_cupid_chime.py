"""Synthesize the Cupid ripple from sine partials; no samples or external recordings."""
from pathlib import Path
import numpy as np
import soundfile as sf


def synthesize():
    rate = 44100
    t = np.arange(round(.62 * rate)) / rate
    onset = 1 - np.exp(-t / .007)
    release = np.minimum(1, (.62 - t) / .06).clip(0, 1)
    # Quiet inharmonic partials give the fundamental a glassy edge.
    wave = sum(gain * np.sin(2*np.pi*freq*t) * np.exp(-t/decay)
               for freq, gain, decay in [(782,.67,.123), (1181,.18,.085), (2078,.06,.052)])
    # A small descending droplet follows the attack. Its phase is the integral of its frequency.
    drop = np.maximum(t - .045, 0)
    phase = 2*np.pi*(540*drop + 15*(1-np.exp(-drop/.082)))
    wave += .15*np.sin(phase)*(1-np.exp(-drop/.013))*np.exp(-drop/.103)
    return rate, .63 * onset * release * wave


if __name__ == "__main__":
    rate, waveform = synthesize()
    output = Path(__file__).resolve().parents[1] / "src/main/resources/assets/uwudimension/sounds/cupid/ripple.ogg"
    output.parent.mkdir(parents=True, exist_ok=True)
    sf.write(output, waveform, rate, format="OGG", subtype="VORBIS")
