package com.gary.bilibili.video.model;

import java.util.List;

public record GovernancePage<T>(List<T> records, long total) { }
