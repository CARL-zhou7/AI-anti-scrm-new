"""CosyVoice gateway for the simulation module (internal network only).

POST /v1/synthesize  {"voiceId": "...", "speed": 1.0, "segments": ["妈，是我。", "..."]}
  -> 200 {"format": "mp3", "audioBase64": "...", "segmentDurationsMs": [..], "totalDurationMs": n}
GET  /healthz        -> 200 {"model": "...", "voices": [...], "dryRun": bool}

Each segment is synthesized separately (zero-shot voice cloning from a reference clip per voice). Segment durations
are computed from the real number of PCM samples, the PCM is concatenated and transcoded once to mono MP3 with
ffmpeg (libmp3lame). `--dry-run` needs neither CosyVoice nor ffmpeg: it returns decodable silent MP3 frames sized
by text length, for wiring tests on machines without a GPU.

Run:  python app.py --host 127.0.0.1 --port 50080 --voices voices.json --model-dir pretrained_models/CosyVoice2-0.5B
"""
import argparse
import base64
import json
import logging
import math
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_SEGMENTS = 64
MAX_CHARS = 400


class Synthesizer:
    def __init__(self, voices_path, model_dir, dry_run):
        with open(voices_path, encoding='utf-8') as f:
            self.voices = {v['id']: v for v in json.load(f)['voices']}
        self.dry_run = dry_run
        self.model_name = 'dry-run'
        self.lock = threading.Lock()  # one inference at a time per model instance
        if not dry_run:
            from cosyvoice.cli.cosyvoice import CosyVoice2  # imported lazily: only the TTS host has CosyVoice
            self.model = CosyVoice2(model_dir, load_jit=False, load_trt=False, fp16=False)
            self.sample_rate = self.model.sample_rate
            self.prompts = {vid: (v['referenceText'], v['referenceAudio']) for vid, v in self.voices.items()}
            self.model_name = model_dir
            logging.getLogger().addFilter(lambda record: 'synthesis text' not in record.getMessage())

    def synthesize(self, voice_id, speed, segments):
        if voice_id not in self.voices:
            raise ValueError('unknown voice')
        speed = min(1.2, max(0.8, float(speed)))
        if self.dry_run:
            durations = [max(600, len(s) * 200) for s in segments]
            return silent_mp3(sum(durations)), durations
        import numpy as np
        text, prompt = self.prompts[voice_id]
        pcm_parts, durations = [], []
        with self.lock:
            for segment in segments:
                chunks = [out['tts_speech'].numpy().reshape(-1)
                          for out in self.model.inference_zero_shot(segment, text, prompt, stream=False, speed=speed)]
                pcm = np.concatenate(chunks)
                pcm_parts.append(pcm)
                durations.append(max(1, round(len(pcm) * 1000 / self.sample_rate)))
        pcm16 = (np.clip(np.concatenate(pcm_parts), -1, 1) * 32767).astype('<i2').tobytes()
        mp3 = subprocess.run(['ffmpeg', '-loglevel', 'error', '-f', 's16le', '-ar', str(self.sample_rate), '-ac', '1', '-i', '-',
                              '-codec:a', 'libmp3lame', '-b:a', '64k', '-f', 'mp3', '-'], input=pcm16, capture_output=True, check=True).stdout
        return mp3, durations


def silent_mp3(total_ms):
    """MPEG-1 Layer III, 32 kbps, 44.1 kHz, mono: 104-byte frames of 1152 samples (about 26.122 ms each)."""
    frames = math.ceil(total_ms / (1152 * 1000 / 44100))
    frame = bytes([0xFF, 0xFB, 0x10, 0xC0]) + bytes(100)
    return frame * frames


def make_handler(synth):
    class Handler(BaseHTTPRequestHandler):
        def _send(self, status, body):
            data = json.dumps(body, ensure_ascii=False).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            if self.path != '/healthz':
                return self._send(404, {'error': 'not found'})
            self._send(200, {'model': synth.model_name, 'voices': sorted(synth.voices), 'dryRun': synth.dry_run})

        def do_POST(self):
            if self.path != '/v1/synthesize':
                return self._send(404, {'error': 'not found'})
            try:
                body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))).decode('utf-8'))
                segments = body['segments']
                if not isinstance(segments, list) or not segments or len(segments) > MAX_SEGMENTS \
                        or not all(isinstance(s, str) and s.strip() for s in segments) or sum(map(len, segments)) > MAX_CHARS:
                    raise ValueError('invalid segments')
                mp3, durations = synth.synthesize(body['voiceId'], body.get('speed', 1.0), segments)
            except (KeyError, ValueError, json.JSONDecodeError) as e:
                return self._send(400, {'error': str(e) or 'bad request'})
            except Exception:  # noqa: BLE001 - never leak model internals to the caller
                return self._send(500, {'error': 'synthesis failed'})
            self._send(200, {'format': 'mp3', 'audioBase64': base64.b64encode(mp3).decode('ascii'),
                             'segmentDurationsMs': durations, 'totalDurationMs': sum(durations)})

        def log_message(self, fmt, *args):  # request lines only; never log text content
            pass
    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--host', default='127.0.0.1', help='bind address; keep it on the internal network')
    parser.add_argument('--port', type=int, default=50080)
    parser.add_argument('--voices', default='voices.json')
    parser.add_argument('--model-dir', default='pretrained_models/CosyVoice2-0.5B')
    parser.add_argument('--dry-run', action='store_true', help='silent MP3 sized by text length; no CosyVoice/ffmpeg needed')
    args = parser.parse_args()
    synth = Synthesizer(args.voices, args.model_dir, args.dry_run)
    print(f'tts-gateway listening on {args.host}:{args.port} (model={synth.model_name}, voices={len(synth.voices)})')
    ThreadingHTTPServer((args.host, args.port), make_handler(synth)).serve_forever()


if __name__ == '__main__':
    main()
