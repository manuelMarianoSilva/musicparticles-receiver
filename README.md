Check the repo for the [Emitter App](https://github.com/manuelMarianoSilva/musicparticles-emitter) in order to get the full experience

# ✦ ParticleReceiver — The Stage

> *It listens. It glows. It plays.*

ParticleReceiver sits quietly on a screen, waiting. The moment a sender touches their phone somewhere across the room — or across the building — this app erupts. Particles cascade across the display mirroring every gesture in real time, and the speakers sing.

Each connected sender is assigned an instrument. Guitar, bass, drums, or a raw oscillator-sync synthesizer modeled after the classic sounds of early analog synthesis. Touch the sender screen to play a note. Drag to leave a trail of sound and light. Hold and release to let the tone swell and fade with a long exponential decay tail. The receiver doesn't know or care how many senders are connected — it handles them all simultaneously, each one painted in its own color, each one playing its own voice.

This is a networked audiovisual instrument. It has no interface to speak of, no buttons to press, no menus to navigate. It just receives, renders, and plays.

---

## Requirements

- Android 8.0 (API 26) or higher
- WiFi network shared with at least one sender device
- OpenGL ES 2.0 support
- Audio output (speaker or headphones)
- Audio sample files placed in `res/raw/` (see below)

---

## Getting Started

### 1. Clone and open
```bash
git clone https://github.com/yourname/particle-receiver.git
```
Open in Android Studio (Ladybug or later recommended).

### 2. Audio samples
At this moment these are the existing audio samples in the app:

**Guitar**
```
guitar_e2.ogg  guitar_a2.ogg  guitar_d3.ogg  guitar_g3.ogg
guitar_b3.ogg  guitar_e4.ogg  guitar_a4.ogg  guitar_d5.ogg
```
**Bass**
```
bass_e1.ogg  bass_a1.ogg  bass_d2.ogg  bass_g2.ogg
```
**Drums**
```
drum_kick.ogg      drum_snare.ogg     drum_hihat_closed.ogg  drum_hihat_open.ogg
drum_crash.ogg     drum_ride.ogg      drum_tom_hi.ogg        drum_tom_lo.ogg
```

Feel free to add more of your own — place your `.ogg` or `.wav` files in `app/src/main/res/raw/`. Free CC0 samples can be found at [freesound.org](https://freesound.org) or the [Versilian Community Sample Library](https://github.com/sgossner/VCSL).

When adding a new sample, update these four places:

**1. `SoundEngine.kt`** — add it to the preload list in the `init` block:
```kotlin
init {
    listOf(
        // existing samples...
        R.raw.your_new_sample   // ← add here
    ).forEach { resId -> soundIds[resId] = pool.load(context, resId, 1) }
}
```

**2. The instrument file** — add it to the note mapping for whichever instrument it belongs to:

- `GuitarInstrument.kt` → add to the `samples` list and update `resolveFromGrid()`
- `BassInstrument.kt` → add to the `samples` list and update `resolveFromGrid()`
- `DrumInstrument.kt` → add to the `zoneFor()` mapping

**3. `NoteGridView.kt` (receiver)** — update the grid label arrays to show the new note in the overlay:
- Guitar → `ONESHOT_GRID`
- Bass → `BASS_GRID`
- Drums → `drawDrumGrid()`

**4. `NoteGridView.kt` (sender)** — same change as above so the sender grid stays in sync with the receiver.

Please notice that this won't update the grid when turned on, that might need a little extra work.

### 3. Configure instruments
Open `app/src/main/assets/instruments.json` and assign each sender device to an instrument:

```json
{
  "assignments": [
    { "deviceId": "YOUR-SENDER-1-UUID", "instrument": "guitar", "continuous": false, "showGrid": true },
    { "deviceId": "YOUR-SENDER-2-UUID", "instrument": "bass",   "continuous": false, "showGrid": true },
    { "deviceId": "YOUR-SENDER-3-UUID", "instrument": "drums",  "continuous": false, "showGrid": true }
  ],
  "default": "guitar"
}
```

To find a sender's device ID, check its Logcat output filtered by `DeviceID` after launching the sender app.

### 4. Build and install
Connect your Android device via USB and hit **Run** in Android Studio.

---

## How It Works

### Instruments

| Instrument | Mode | Sound character |
|---|---|---|
| `guitar` | One-shot | Plucked notes, velocity-sensitive |
| `bass` | One-shot | Deep tones, y-position modulates muting |
| `drums` | One-shot | Screen divided into kit zones |
| `sync` | Continuous | Oscillator-sync synthesizer, y-axis pitch bend |
| `guitar` | Continuous | Sustained tone with pitch bend |
| `bass` | Continuous | Sustained low tone with pitch bend |

### Continuous mode
When `"continuous": true`, the instrument sustains a tone for as long as the sender's finger is held down. Moving the finger **up and down** bends the pitch in real time — up raises pitch, down lowers it. Releasing the finger triggers a long exponential decay tail rather than an abrupt cut.

### Note grid overlay
A semi-transparent grid is drawn over the particle surface showing which screen zone corresponds to which note or drum hit. The grid automatically switches layout based on the assigned instrument:

| Instrument | Grid layout |
|---|---|
| `guitar` (one-shot) | 4 rows × 2 cols — E2 to D5 |
| `bass` (one-shot) | 2 rows × 2 cols — E1 to G2 |
| `drums` | Irregular zones — full kit layout |
| Continuous | 3 rows × 12 cols — chromatic C2–B4 |

Toggle the grid per device in `instruments.json`:
```json
{ "deviceId": "...", "instrument": "guitar", "showGrid": true }
```

### Particle colors
Each instrument family renders in its own color range:

| Instrument | Color |
|---|---|
| Guitar | Reds |
| Bass | Blues |
| Drums | Yellows |
| Sync (continuous) | Greens |

Multiple senders of the same instrument type get slightly different shades within their color family.

---

## HUD

Tap anywhere on the screen to toggle the connection status overlay:

```
[a355-2b82]  ● LIVE
  ip: 192.168.1.42   pkts: 1842
```

| Status | Meaning |
|---|---|
| `SEARCHING FOR SENDERS…` | No sender has connected yet |
| `● LIVE` | Receiving packets within the last 2 seconds |
| `○ IDLE (Xs ago)` | Last packet received X seconds ago |

---

## Network

The receiver listens on two ports:

| Port | Protocol | Purpose |
|---|---|---|
| `9876` | UDP unicast | Touch event packets from paired senders |
| `9877` | UDP multicast (`239.255.0.1`) | Discovery handshake and heartbeats |

No manual IP configuration is required. The receiver joins the multicast group on startup and responds to any sender that broadcasts a `DISCOVER` packet on the same network.

### Multi-sender support
Any number of senders can connect simultaneously. Each sender is tracked independently by its device UUID. Instrument assignment, color, and grid highlighting are all per-sender. There is no practical limit on concurrent senders beyond the performance of the receiving device.

---

## Architecture

```
UDP :9877  ──► DiscoveryServer   — multicast handshake, ACK, heartbeat
UDP :9876  ──► UdpReceiver       — touch event stream
                    │
                    ▼
            RemoteParticleEmitter
                    │
                    ├──► InstrumentRouter ──► GuitarInstrument
                    │                    ──► BassInstrument
                    │                    ──► DrumInstrument
                    │                    ──► SynthEngine (continuous)
                    │
                    └──► ParticleEmitter ──► ParticleSystem
                                                    │
                                              ParticleRenderer
                                              (OpenGL ES 2.0)
```

### Synthesis engine
Continuous mode instruments use a real-time PCM synthesis engine built on `AudioTrack` in streaming mode — no sample files required. The synthesizer uses a detuned sawtooth oscillator with a sine sub for body, running at 44100Hz with a 2048-sample buffer. Pitch updates are instantaneous with no interpolation lag.

---

## Configuration Reference

### `instruments.json` fields

| Field | Type | Default | Description |
|---|---|---|---|
| `deviceId` | string | required | UUID from sender's Logcat |
| `instrument` | string | `"guitar"` | `"guitar"`, `"bass"`, `"drums"`, `"sync"` |
| `continuous` | boolean | `false` | Sustained tone mode |
| `showGrid` | boolean | `false` | Show note grid overlay |

### Particle tuning
Particle count and lifetime are controlled in `ParticleEmitter.kt`. The receiver runs up to **40,000 simultaneous particles**.

### Synthesis tuning
Release time and decay curve are in `SynthEngine.noteOff()` and `SynthVoice.startRelease()`. Default release is 800ms exponential decay.
