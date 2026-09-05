package com.salvia.salviabrowxer.media.processor

import java.io.File

interface MediaProcessor {
    suspend fun mergeAudioVideo(videoFile: File, audioFile: File, outputFile: File): Boolean
    suspend fun remux(inputFile: File, outputFile: File, targetFormat: String): Boolean
    suspend fun extractAudio(videoFile: File, outputFile: File): Boolean
}

class MediaProcessorImpl : MediaProcessor {
    override suspend fun mergeAudioVideo(videoFile: File, audioFile: File, outputFile: File): Boolean {
        // TODO: Implement with FFmpeg
        return false
    }

    override suspend fun remux(inputFile: File, outputFile: File, targetFormat: String): Boolean {
        // TODO: Implement with FFmpeg
        return false
    }

    override suspend fun extractAudio(videoFile: File, outputFile: File): Boolean {
        // TODO: Implement with FFmpeg
        return false
    }
}