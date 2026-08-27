package com.gary.bilibili.video.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds one FFmpeg process that decodes once and produces multiple HLS variants. */
final class HlsTranscodeCommandBuilder {

    private HlsTranscodeCommandBuilder() {
    }

    static List<String> build(String ffmpegPath,
                              Path input,
                              Path outputDirectory,
                              List<Output> outputs,
                              boolean hasAudio,
                              VideoEncoder encoder,
                              String softwarePreset,
                              int crf,
                              int segmentSeconds) {
        if (outputs == null || outputs.isEmpty()) {
            throw new IllegalArgumentException("At least one HLS output is required");
        }

        List<String> command = new ArrayList<>();
        command.addAll(List.of(ffmpegPath, "-y"));
        if (encoder == VideoEncoder.VAAPI) {
            command.addAll(List.of("-vaapi_device", "/dev/dri/renderD128"));
        }
        command.addAll(List.of("-i", input.toString(), "-filter_complex", filterGraph(outputs, encoder)));

        for (int index = 0; index < outputs.size(); index++) {
            command.addAll(List.of("-map", "[v" + index + "]"));
            if (hasAudio) {
                command.addAll(List.of("-map", "0:a:0"));
            }
        }

        for (int index = 0; index < outputs.size(); index++) {
            appendVideoOptions(command, encoder, softwarePreset, crf, segmentSeconds, index);
            if (hasAudio) {
                command.addAll(List.of(
                        "-c:a:" + index, "aac",
                        "-b:a:" + index, "128k",
                        "-ar:a:" + index, "48000"));
            }
        }

        command.addAll(List.of(
                "-max_muxing_queue_size", "2048",
                "-f", "hls",
                "-hls_time", Integer.toString(segmentSeconds),
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-var_stream_map", variantMap(outputs, hasAudio),
                "-hls_segment_filename", outputDirectory.resolve("%v/seg_%05d.ts").toString(),
                outputDirectory.resolve("%v/index.m3u8").toString()));
        return List.copyOf(command);
    }

    private static String filterGraph(List<Output> outputs, VideoEncoder encoder) {
        StringBuilder graph = new StringBuilder();
        if (outputs.size() == 1) {
            graph.append("[0:v]");
        } else {
            graph.append("[0:v]split=").append(outputs.size());
            for (int index = 0; index < outputs.size(); index++) {
                graph.append("[source").append(index).append(']');
            }
            graph.append(';').append("[source0]");
        }

        for (int index = 0; index < outputs.size(); index++) {
            if (index > 0) {
                graph.append(';').append("[source").append(index).append(']');
            }
            graph.append(outputs.get(index).geometry().ffmpegFilter());
            if (encoder == VideoEncoder.VAAPI) {
                graph.append(",format=nv12,hwupload");
            } else {
                graph.append(",format=yuv420p");
            }
            graph.append("[v").append(index).append(']');
        }
        return graph.toString();
    }

    private static void appendVideoOptions(List<String> command,
                                           VideoEncoder encoder,
                                           String softwarePreset,
                                           int crf,
                                           int segmentSeconds,
                                           int index) {
        String stream = ":" + index;
        command.addAll(List.of("-c:v" + stream, encoder.ffmpegName()));
        switch (encoder) {
            case SOFTWARE -> command.addAll(List.of(
                    "-preset:v" + stream, softwarePreset,
                    "-profile:v" + stream, "main",
                    "-crf:v" + stream, Integer.toString(crf)));
            case NVIDIA -> command.addAll(List.of(
                    "-preset:v" + stream, "p4",
                    "-profile:v" + stream, "main",
                    "-cq:v" + stream, Integer.toString(crf),
                    "-b:v" + stream, "0"));
            case INTEL_QSV -> command.addAll(List.of(
                    "-preset:v" + stream, "veryfast",
                    "-profile:v" + stream, "main",
                    "-global_quality:v" + stream, Integer.toString(crf)));
            case VAAPI -> command.addAll(List.of(
                    "-profile:v" + stream, "main",
                    "-qp:v" + stream, Integer.toString(crf)));
        }
        command.addAll(List.of(
                "-g:v" + stream, Integer.toString(segmentSeconds * 30),
                "-keyint_min:v" + stream, Integer.toString(segmentSeconds * 30),
                "-sc_threshold:v" + stream, "0",
                "-force_key_frames:v" + stream, "expr:gte(t,n_forced*" + segmentSeconds + ")"));
    }

    private static String variantMap(List<Output> outputs, boolean hasAudio) {
        List<String> variants = new ArrayList<>();
        for (int index = 0; index < outputs.size(); index++) {
            StringBuilder value = new StringBuilder("v:").append(index);
            if (hasAudio) {
                value.append(",a:").append(index);
            }
            value.append(",name:").append(outputs.get(index).name().toLowerCase(Locale.ROOT));
            variants.add(value.toString());
        }
        return String.join(" ", variants);
    }

    record Output(String name, VideoScaleGeometry geometry) {
    }
}
