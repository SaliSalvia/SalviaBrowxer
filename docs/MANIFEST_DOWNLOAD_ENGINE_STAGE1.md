# Manifest download engine — Stage 1

This change extends the existing MediaDownloadManager and DownloadCoordinator; it does not replace the current direct-file Range downloader, Room queue, WorkManager, or Android 14+ user-initiated data transfer jobs.

## HLS

- Resolve a master playlist to the selected media playlist using the existing highest-bandwidth selection.
- Parse per-segment URL, media sequence, byte range, AES-128 key/IV, and EXT-X-MAP initialization data.
- Download up to four segments concurrently, write each segment to a temporary file, and atomically promote completed segments.
- Reuse completed segment files on explicit retry/resume.
- Validate HTTP 206 and Content-Range for byte-range requests.
- Decrypt AES-128 CBC with the sequence-derived IV or explicit IV. Failed decryption is an error; encrypted bytes are never mislabeled as playable output.
- Keep audio and video playlist staging separate.
- Require EXT-X-ENDLIST for offline completion. Live/event playlists are not silently saved as complete files.

Unsupported HLS: SAMPLE-AES/DRM and non-identity key formats.

## DASH

- The manifest parser supports static MPD, BaseURL, SegmentTemplate with fixed duration or SegmentTimeline, SegmentList, and SegmentBase with sidx.
- Video and audio representations are selected by stable representation IDs persisted in Room.
- Segment downloads are staged atomically and reused when a transfer is resumed.
- SIDX byte ranges are expanded before segment download. Nested SIDX references are rejected rather than partially assembled.
- The existing direct-file DASH path remains available for previously stored downloads that have no representation metadata.

Unsupported DASH: dynamic/live MPDs, DRM/ContentProtection, nested SIDX references, and unsupported/invalid segment templates.

## Persistence and compatibility

Room schema v5 adds two non-null text columns to downloads with empty-string defaults:
- manifestRepresentationId
- manifestAudioRepresentationId

Migration 4→5 is additive. Existing download rows and direct downloads retain their previous values and behavior. No destructive migration or database reset is introduced.

## Validation

Parser regression tests cover HLS AES key metadata and implicit byte ranges, DASH template/timeline expansion, SegmentList, SegmentBase/SIDX, and rejection of live/DRM manifests. The repository CI must pass before this branch is considered merge-ready.
