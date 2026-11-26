package com.gary.bilibili.video.repository;

import com.gary.bilibili.video.model.CreatorDailyMetric;
import com.gary.bilibili.video.model.CreatorDirtyDay;
import com.gary.bilibili.video.model.CreatorTopVideo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class CreatorAnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreatorAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insertViewEvent(String requestId, long videoId, LocalDateTime occurredAt) {
        try {
            return jdbcTemplate.update("""
                    insert into video_view_event
                        (request_id, video_id, creator_id, occurred_at)
                    select ?, v.id, v.user_id, ?
                    from video v
                    where v.id = ? and v.deleted = 0
                    """, requestId, Timestamp.valueOf(occurredAt), videoId) == 1;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public void markDirtyForVideo(long videoId, LocalDate statDate) {
        jdbcTemplate.update("""
                insert into creator_analytics_dirty_day
                    (creator_id, stat_date, dirty_version, status)
                select v.user_id, ?, 1, 'PENDING'
                from video v where v.id = ?
                on duplicate key update
                    dirty_version = dirty_version + 1,
                    status = 'PENDING', claim_token = null, locked_until = null,
                    last_error = null
                """, Date.valueOf(statDate), videoId);
    }

    public void lockCreator(long creatorId) {
        jdbcTemplate.queryForObject(
                "select id from sys_user where id = ? for update", Long.class, creatorId);
    }

    public void replaceDay(long creatorId, LocalDate statDate) {
        LocalDateTime from = statDate.atStartOfDay();
        LocalDateTime to = statDate.plusDays(1).atStartOfDay();
        jdbcTemplate.update("delete from creator_video_daily_stats where creator_id = ? and stat_date = ?",
                creatorId, Date.valueOf(statDate));
        jdbcTemplate.update("""
                insert into creator_video_daily_stats
                    (creator_id, video_id, stat_date, view_count, like_count,
                     collect_count, comment_count, danmu_count)
                select metric.creator_id, metric.video_id, ?,
                       sum(metric.view_count), sum(metric.like_count),
                       sum(metric.collect_count), sum(metric.comment_count),
                       sum(metric.danmu_count)
                from (
                    select e.creator_id, e.video_id, count(*) as view_count,
                           0 as like_count, 0 as collect_count,
                           0 as comment_count, 0 as danmu_count
                    from video_view_event e
                    where e.creator_id = ? and e.occurred_at >= ? and e.occurred_at < ?
                    group by e.creator_id, e.video_id
                    union all
                    select v.user_id, likes.target_id, 0, count(*), 0, 0, 0
                    from user_like likes
                    join video v on v.id = likes.target_id
                    where v.user_id = ? and likes.target_type = 1 and likes.status = 1
                      and likes.create_time >= ? and likes.create_time < ?
                    group by v.user_id, likes.target_id
                    union all
                    select v.user_id, collected.video_id, 0, 0, count(*), 0, 0
                    from collection collected
                    join video v on v.id = collected.video_id
                    where v.user_id = ? and collected.status = 1
                      and collected.create_time >= ? and collected.create_time < ?
                    group by v.user_id, collected.video_id
                    union all
                    select v.user_id, comments.video_id, 0, 0, 0, count(*), 0
                    from comment comments
                    join video v on v.id = comments.video_id
                    where v.user_id = ? and comments.status = 0 and comments.deleted = 0
                      and comments.create_time >= ? and comments.create_time < ?
                    group by v.user_id, comments.video_id
                    union all
                    select v.user_id, danmus.video_id, 0, 0, 0, 0, count(*)
                    from danmu danmus
                    join video v on v.id = danmus.video_id
                    where v.user_id = ? and danmus.status = 0
                      and danmus.send_time >= ? and danmus.send_time < ?
                    group by v.user_id, danmus.video_id
                ) metric
                group by metric.creator_id, metric.video_id
                """,
                Date.valueOf(statDate),
                creatorId, Timestamp.valueOf(from), Timestamp.valueOf(to),
                creatorId, Timestamp.valueOf(from), Timestamp.valueOf(to),
                creatorId, Timestamp.valueOf(from), Timestamp.valueOf(to),
                creatorId, Timestamp.valueOf(from), Timestamp.valueOf(to),
                creatorId, Timestamp.valueOf(from), Timestamp.valueOf(to));
    }

    public List<CreatorDailyMetric> findTrend(long creatorId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query("""
                select stat_date, sum(view_count) as view_count,
                       sum(like_count) as like_count, sum(collect_count) as collect_count,
                       sum(comment_count) as comment_count, sum(danmu_count) as danmu_count
                from creator_video_daily_stats
                where creator_id = ? and stat_date between ? and ?
                group by stat_date order by stat_date
                """, (resultSet, rowNum) -> new CreatorDailyMetric(
                        resultSet.getDate("stat_date").toLocalDate(),
                        resultSet.getLong("view_count"),
                        resultSet.getLong("like_count"),
                        resultSet.getLong("collect_count"),
                        resultSet.getLong("comment_count"),
                        resultSet.getLong("danmu_count")),
                creatorId, Date.valueOf(startDate), Date.valueOf(endDate));
    }

    public List<CreatorTopVideo> findTopVideos(long creatorId, LocalDate startDate,
                                                LocalDate endDate, int limit) {
        return jdbcTemplate.query("""
                select daily.video_id, video.title, video.cover_url,
                       sum(daily.view_count) as view_count,
                       sum(daily.like_count) as like_count,
                       sum(daily.collect_count) as collect_count,
                       sum(daily.comment_count) as comment_count,
                       sum(daily.danmu_count) as danmu_count
                from creator_video_daily_stats daily
                join video on video.id = daily.video_id and video.user_id = daily.creator_id
                where daily.creator_id = ? and daily.stat_date between ? and ?
                group by daily.video_id, video.title, video.cover_url
                order by sum(daily.view_count) desc,
                         (sum(daily.like_count) + sum(daily.collect_count)
                          + sum(daily.comment_count) + sum(daily.danmu_count)) desc,
                         daily.video_id desc
                limit ?
                """, (resultSet, rowNum) -> new CreatorTopVideo(
                        resultSet.getLong("video_id"),
                        resultSet.getString("title"),
                        resultSet.getString("cover_url"),
                        resultSet.getLong("view_count"),
                        resultSet.getLong("like_count"),
                        resultSet.getLong("collect_count"),
                        resultSet.getLong("comment_count"),
                        resultSet.getLong("danmu_count")),
                creatorId, Date.valueOf(startDate), Date.valueOf(endDate), limit);
    }

    public LocalDateTime findLastAggregatedAt(long creatorId, LocalDate startDate, LocalDate endDate) {
        Timestamp timestamp = jdbcTemplate.queryForObject("""
                select max(update_time) from creator_video_daily_stats
                where creator_id = ? and stat_date between ? and ?
                """, Timestamp.class, creatorId, Date.valueOf(startDate), Date.valueOf(endDate));
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public List<CreatorDirtyDay> findDirtyDays(int limit) {
        return jdbcTemplate.query("""
                select creator_id, stat_date from creator_analytics_dirty_day
                where status = 'PENDING'
                   or (status = 'PROCESSING' and locked_until < now(3))
                order by update_time limit ?
                """, (resultSet, rowNum) -> new CreatorDirtyDay(
                        resultSet.getLong("creator_id"),
                        resultSet.getDate("stat_date").toLocalDate()), limit);
    }

    public Long claimDirtyDay(CreatorDirtyDay dirtyDay, String token, int leaseSeconds) {
        int updated = jdbcTemplate.update("""
                update creator_analytics_dirty_day
                set status = 'PROCESSING', claim_token = ?,
                    locked_until = timestampadd(SECOND, ?, now(3)),
                    attempt_count = attempt_count + 1, last_error = null
                where creator_id = ? and stat_date = ?
                  and (status = 'PENDING'
                       or (status = 'PROCESSING' and locked_until < now(3)))
                """, token, leaseSeconds, dirtyDay.creatorId(), Date.valueOf(dirtyDay.statDate()));
        if (updated == 0) {
            return null;
        }
        return jdbcTemplate.queryForObject("""
                select dirty_version from creator_analytics_dirty_day
                where creator_id = ? and stat_date = ? and claim_token = ?
                """, Long.class, dirtyDay.creatorId(), Date.valueOf(dirtyDay.statDate()), token);
    }

    public void completeDirtyDay(CreatorDirtyDay dirtyDay, String token, long version) {
        jdbcTemplate.update("""
                delete from creator_analytics_dirty_day
                where creator_id = ? and stat_date = ?
                  and claim_token = ? and dirty_version = ?
                """, dirtyDay.creatorId(), Date.valueOf(dirtyDay.statDate()), token, version);
    }

    public void failDirtyDay(CreatorDirtyDay dirtyDay, String token, long version, String error) {
        jdbcTemplate.update("""
                update creator_analytics_dirty_day
                set status = 'PENDING', claim_token = null, locked_until = null, last_error = ?
                where creator_id = ? and stat_date = ?
                  and claim_token = ? and dirty_version = ?
                """, truncate(error), dirtyDay.creatorId(), Date.valueOf(dirtyDay.statDate()), token, version);
    }

    private String truncate(String value) {
        if (value == null) {
            return "unknown aggregation failure";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
