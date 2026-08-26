package com.gary.bilibili.video.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoScaleGeometryTest {

    @Test
    void shouldProduceEvenWidthForRecordedFailingSource() {
        VideoScaleGeometry geometry = VideoScaleGeometry.fit(2556, 1600, 360);

        assertEquals(574, geometry.width());
        assertEquals(360, geometry.height());
        assertEquals("scale=574:360", geometry.ffmpegFilter());
    }

    @Test
    void shouldPreserveExactStandardAspectRatio() {
        VideoScaleGeometry geometry = VideoScaleGeometry.fit(1920, 1080, 720);

        assertEquals(new VideoScaleGeometry(1280, 720), geometry);
    }

    @Test
    void shouldRejectInvalidSourceDimensions() {
        assertThrows(IllegalArgumentException.class, () -> VideoScaleGeometry.fit(0, 1080, 720));
    }
}
