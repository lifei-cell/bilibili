package com.gary.bilibili.video.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HlsTranscodeCommandBuilderTest {

    @Test
    void shouldDecodeOnceAndMapMultipleSoftwareVariants() {
        List<String> command = HlsTranscodeCommandBuilder.build(
                "ffmpeg", Path.of("source.mp4"), Path.of("hls"),
                List.of(
                        new HlsTranscodeCommandBuilder.Output(
                                "720P", new VideoScaleGeometry(1150, 720)),
                        new HlsTranscodeCommandBuilder.Output(
                                "1080P", new VideoScaleGeometry(1724, 1080))),
                true, VideoEncoder.SOFTWARE, "superfast", 23, 6);

        assertEquals(1, Collections.frequency(command, "-i"));
        assertEquals(2, Collections.frequency(command, "libx264"));
        assertTrue(optionValue(command, "-filter_complex").contains("split=2"));
        assertTrue(optionValue(command, "-filter_complex").contains("scale=1150:720"));
        assertEquals("v:0,a:0,name:720p v:1,a:1,name:1080p",
                optionValue(command, "-var_stream_map"));
        assertEquals("superfast", optionValue(command, "-preset:v:0"));
    }

    @Test
    void shouldBuildVaapiUploadFilterWithoutAudioMapping() {
        List<String> command = HlsTranscodeCommandBuilder.build(
                "ffmpeg", Path.of("source.mp4"), Path.of("hls"),
                List.of(new HlsTranscodeCommandBuilder.Output(
                        "360P", new VideoScaleGeometry(574, 360))),
                false, VideoEncoder.VAAPI, "veryfast", 22, 6);

        assertEquals("/dev/dri/renderD128", optionValue(command, "-vaapi_device"));
        assertTrue(optionValue(command, "-filter_complex").contains("format=nv12,hwupload"));
        assertEquals("h264_vaapi", optionValue(command, "-c:v:0"));
        assertEquals("v:0,name:360p", optionValue(command, "-var_stream_map"));
    }

    private String optionValue(List<String> command, String option) {
        return command.get(command.indexOf(option) + 1);
    }
}
