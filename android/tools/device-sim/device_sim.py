#!/usr/bin/env python3
"""
Vaani device simulator — §3.6 of ARCHITECTURE.md.

Emulates the ESP32-S3's Wi-Fi bulk-transfer HTTP surface (§3.4) so the Android
app (and CI) can develop the full sync + ingest pipeline with NO hardware in the
loop. Serves synthetic "recording sessions" and can inject pathological cases
that are near-impossible to reproduce on real hardware: corrupt CRCs, mid-transfer
disconnects, large backlogs, clock skew, duplicate session IDs.

Wire contract (must match docs/PROTOCOL.md):
    GET    /v1/health                     -> liveness + free space
    GET    /v1/manifest                   -> JSON list of sealed sessions
    GET    /v1/session/{id}/meta          -> session metadata
    GET    /v1/session/{id}/audio         -> audio bytes; supports Range: bytes=N-
    POST   /v1/session/{id}/ack           -> body {"sha256": "..."} -> mark SYNCED
    DELETE /v1/session/{id}               -> only permitted after ACK

Everything is keyed by content hash so the protocol is idempotent (§3.4).

Usage:
    python3 device_sim.py --port 8080 --sessions 5
    python3 device_sim.py --port 8080 --chaos truncate   # drop connection mid-audio
    python3 device_sim.py --scenario backlog200          # 200-file backlog

No third-party deps — stdlib only, so CI needs nothing installed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import struct
import threading
import time
import zlib
from dataclasses import dataclass, field, asdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Optional

SCHEMA_VERSION = 1
FRAME_MAGIC = b"VFRM"  # §2.4 framing


# --------------------------------------------------------------------------- #
# Synthetic session model
# --------------------------------------------------------------------------- #
@dataclass
class Session:
    session_id: str
    start_ms: int
    duration_ms: int
    codec: str
    sample_rate: int
    audio: bytes
    sha256: str
    state: str = "SEALED"  # SEALED | SYNCED  (device never advertises OPEN)
    corrupt: bool = False  # chaos: CRC mismatch vs advertised hash

    def meta(self) -> dict:
        return {
            "session_id": self.session_id,
            "start_ms": self.start_ms,
            "duration_ms": self.duration_ms,
            "codec": self.codec,
            "sample_rate": self.sample_rate,
            "bytes": len(self.audio),
            "sha256": self.sha256,
            "state": self.state,
            "schema_version": SCHEMA_VERSION,
        }


def _framed_opus_blob(duration_ms: int, seed: int) -> bytes:
    """
    Build an append-only framed blob per §2.4:
        [u32 magic][u32 seq][u64 t_offset_ms][u16 payload_len][payload][u32 crc32]
    Payload is synthetic (not decodable Opus) — the app's sync layer treats it as
    opaque bytes; the transcode/ASR stages are exercised separately with real audio
    fixtures. 20 ms frames, ~60 bytes each (~24 kbps VBR-ish, §2.3).
    """
    rng = random.Random(seed)
    out = bytearray()
    seq = 0
    t = 0
    while t < duration_ms:
        payload_len = rng.randint(40, 80)
        payload = bytes(rng.getrandbits(8) for _ in range(payload_len))
        header = FRAME_MAGIC + struct.pack("<IQH", seq, t, payload_len)
        frame_wo_crc = header + payload
        crc = zlib.crc32(frame_wo_crc) & 0xFFFFFFFF
        out += frame_wo_crc + struct.pack("<I", crc)
        seq += 1
        t += 20
    return bytes(out)


def make_session(idx: int, *, base_start_ms: int, duration_ms: int,
                 corrupt: bool = False) -> Session:
    # ULID-ish sortable id
    sid = f"{base_start_ms + idx:013d}{idx:04d}"
    audio = _framed_opus_blob(duration_ms, seed=idx * 7919 + 1)
    real_sha = hashlib.sha256(audio).hexdigest()
    if corrupt:
        # advertise a hash that WON'T match the bytes -> phone must reject (§10.2)
        advertised = hashlib.sha256(audio + b"tampered").hexdigest()
        return Session(sid, base_start_ms + idx, duration_ms, "opus", 16000,
                       audio, advertised, corrupt=True)
    return Session(sid, base_start_ms + idx, duration_ms, "opus", 16000,
                   audio, real_sha)


# --------------------------------------------------------------------------- #
# Device state
# --------------------------------------------------------------------------- #
@dataclass
class DeviceState:
    sessions: dict[str, Session] = field(default_factory=dict)
    sd_total_bytes: int = 32 * 1024 * 1024 * 1024
    chaos: Optional[str] = None            # None | "truncate" | "slow" | "5xx"
    clock_skew_ms: int = 0                 # §10.2 clock-skew case
    lock: threading.Lock = field(default_factory=threading.Lock)

    def pending_bytes(self) -> int:
        return sum(len(s.audio) for s in self.sessions.values()
                   if s.state != "SYNCED")

    def sd_free_bytes(self) -> int:
        used = sum(len(s.audio) for s in self.sessions.values())
        return max(0, self.sd_total_bytes - used)


def build_state(args) -> DeviceState:
    st = DeviceState(chaos=args.chaos, clock_skew_ms=args.clock_skew_ms)
    now_ms = int(time.time() * 1000) + args.clock_skew_ms

    scenarios = {
        "default":    [(120_000, False)] * max(1, args.sessions),
        "backlog200": [(60_000, False) for _ in range(200)],
        "twohour":    [(2 * 3600_000, False)],        # §10.3 the 2-hour test
        "corrupt":    [(60_000, True), (60_000, False)],
        "duplicate":  [(60_000, False), (60_000, False)],  # same id injected below
        "mixed":      [(15_000, False), (90_000, False),
                       (2 * 3600_000, False), (45_000, True)],
    }
    plan = scenarios.get(args.scenario, scenarios["default"])
    for i, (dur, corrupt) in enumerate(plan):
        s = make_session(i, base_start_ms=now_ms, duration_ms=dur, corrupt=corrupt)
        st.sessions[s.session_id] = s

    if args.scenario == "duplicate":
        # force two sessions to share an id -> phone's sha256 UNIQUE no-ops it (§10.2)
        first = next(iter(st.sessions.values()))
        dup = make_session(999, base_start_ms=now_ms, duration_ms=60_000)
        dup.session_id = first.session_id
        st.sessions[first.session_id] = dup
    return st


# --------------------------------------------------------------------------- #
# HTTP handler
# --------------------------------------------------------------------------- #
class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "VaaniDeviceSim/1.0"
    state: DeviceState = None  # injected on the server instance

    # quieter logging
    def log_message(self, fmt, *a):
        print(f"[sim] {self.address_string()} {fmt % a}")

    # ---- helpers ----
    def _json(self, code: int, obj: dict):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _maybe_chaos_5xx(self) -> bool:
        if self.state.chaos == "5xx" and random.random() < 0.4:
            self._json(503, {"error": "chaos_5xx"})
            return True
        return False

    # ---- routing ----
    def do_GET(self):
        st = self.state
        path = self.path.split("?", 1)[0]
        if self._maybe_chaos_5xx():
            return

        if path == "/v1/health":
            return self._json(200, {
                "ok": True,
                "unix_time_ms": int(time.time() * 1000) + st.clock_skew_ms,
                "sd_free_bytes": st.sd_free_bytes(),
                "pending_files": sum(1 for s in st.sessions.values()
                                     if s.state != "SYNCED"),
                "pending_bytes": st.pending_bytes(),
                "fw_version": "sim-1.0.0",
            })

        if path == "/v1/manifest":
            with st.lock:
                return self._json(200, {
                    "sessions": [s.meta() for s in
                                 sorted(st.sessions.values(),
                                        key=lambda x: x.start_ms)]
                })

        if path.startswith("/v1/session/") and path.endswith("/meta"):
            sid = path.split("/")[3]
            s = st.sessions.get(sid)
            if not s:
                return self._json(404, {"error": "no_such_session"})
            return self._json(200, s.meta())

        if path.startswith("/v1/session/") and path.endswith("/audio"):
            sid = path.split("/")[3]
            return self._serve_audio(sid)

        return self._json(404, {"error": "not_found", "path": path})

    def _serve_audio(self, sid: str):
        st = self.state
        s = st.sessions.get(sid)
        if not s:
            return self._json(404, {"error": "no_such_session"})

        data = s.audio
        total = len(data)
        start, end = 0, total - 1

        rng = self.headers.get("Range")
        if rng and rng.startswith("bytes="):
            # §3.4 resume support: Range: bytes=N-
            spec = rng[len("bytes="):]
            lo, _, hi = spec.partition("-")
            start = int(lo) if lo else 0
            end = int(hi) if hi else total - 1
            code = 206
        else:
            code = 200

        chunk = data[start:end + 1]

        # chaos: truncate — send half the bytes then drop the connection (§10.2)
        if st.chaos == "truncate":
            half = len(chunk) // 2
            self.send_response(code)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(chunk)))
            if code == 206:
                self.send_header("Content-Range",
                                 f"bytes {start}-{end}/{total}")
            self.end_headers()
            self.wfile.write(chunk[:half])
            self.wfile.flush()
            # abruptly close — the phone must resume from its byte offset
            self.close_connection = True
            print(f"[sim] CHAOS truncate: sent {half}/{len(chunk)} bytes for {sid}")
            return

        self.send_response(code)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(len(chunk)))
        if code == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{total}")
        self.end_headers()

        if st.chaos == "slow":
            # dribble bytes to exercise timeouts / progress UI
            for i in range(0, len(chunk), 4096):
                self.wfile.write(chunk[i:i + 4096])
                self.wfile.flush()
                time.sleep(0.05)
        else:
            self.wfile.write(chunk)

    def do_POST(self):
        st = self.state
        path = self.path.split("?", 1)[0]
        if self._maybe_chaos_5xx():
            return

        if path.startswith("/v1/session/") and path.endswith("/ack"):
            sid = path.split("/")[3]
            s = st.sessions.get(sid)
            if not s:
                return self._json(404, {"error": "no_such_session"})
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length) or b"{}")
            claimed = body.get("sha256")
            # two-phase commit: only mark SYNCED if the phone proves it has the bytes
            actual = hashlib.sha256(s.audio).hexdigest()
            if claimed != actual:
                return self._json(409, {
                    "error": "hash_mismatch",
                    "expected": actual, "got": claimed,
                })
            with st.lock:
                s.state = "SYNCED"
            return self._json(200, {"session_id": sid, "state": "SYNCED"})

        return self._json(404, {"error": "not_found", "path": path})

    def do_DELETE(self):
        st = self.state
        path = self.path.split("?", 1)[0]
        if path.startswith("/v1/session/"):
            sid = path.split("/")[3]
            s = st.sessions.get(sid)
            if not s:
                return self._json(404, {"error": "no_such_session"})
            # §3.4: device only permits delete AFTER ack
            if s.state != "SYNCED":
                return self._json(403, {"error": "not_acked_yet"})
            with st.lock:
                del st.sessions[sid]
            return self._json(200, {"deleted": sid})
        return self._json(404, {"error": "not_found", "path": path})


# --------------------------------------------------------------------------- #
def main():
    ap = argparse.ArgumentParser(description="Vaani ESP32 device simulator (§3.6)")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--sessions", type=int, default=3,
                    help="number of default sessions (ignored if --scenario set)")
    ap.add_argument("--scenario",
                    choices=["default", "backlog200", "twohour", "corrupt",
                             "duplicate", "mixed"],
                    default="default")
    ap.add_argument("--chaos", choices=["truncate", "slow", "5xx"], default=None,
                    help="inject a pathological transport condition (§10.2)")
    ap.add_argument("--clock-skew-ms", type=int, default=0,
                    help="advertise a skewed device clock (§10.2)")
    args = ap.parse_args()

    state = build_state(args)
    Handler.state = state
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)

    total_bytes = sum(len(s.audio) for s in state.sessions.values())
    print(f"[sim] Vaani device simulator on http://{args.host}:{args.port}")
    print(f"[sim] scenario={args.scenario} sessions={len(state.sessions)} "
          f"pending={total_bytes/1e6:.1f} MB chaos={args.chaos} "
          f"clock_skew_ms={args.clock_skew_ms}")
    print("[sim] endpoints: /v1/health /v1/manifest "
          "/v1/session/{id}/meta|/audio|/ack DELETE /v1/session/{id}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[sim] shutting down")
        httpd.shutdown()


if __name__ == "__main__":
    main()
