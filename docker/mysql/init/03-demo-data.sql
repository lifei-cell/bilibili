USE bilibili;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- Bilibili Cloud demo data
--
-- Reserved ID ranges:
--   users       1001-1099
--   user auth  10001-10999
--   videos      2001-2099
--   comments    3001-3099
--   follows     4001-4099
--   likes       5001-5099
--   folders     6001-6099
--   collections 7001-7099
--   chunks      8001-8099
--   danmu        9001-9099
--
-- Every demo account uses the BCrypt password: Demo@123
-- This script is idempotent. Running it again restores the demo baseline.
-- ---------------------------------------------------------------------------

START TRANSACTION;

INSERT INTO sys_user
    (id, username, nickname, phone, avatar, gender, birthday, signature,
     role, status, create_time, update_time, deleted)
VALUES
    (1001, 'demo_alice', '爱丽丝的放映室', '13800001001',
     'https://api.dicebear.com/9.x/avataaars/svg?seed=alice', 2, '1998-03-18',
     '动画、摄影和生活记录创作者', 'user', 0,
     CURRENT_TIMESTAMP - INTERVAL 180 DAY, CURRENT_TIMESTAMP, 0),
    (1002, 'demo_bob', '代码玩家 Bob', '13800001002',
     'https://api.dicebear.com/9.x/avataaars/svg?seed=bob', 1, '1996-07-09',
     '分享 Java、云原生与工程实践', 'user', 0,
     CURRENT_TIMESTAMP - INTERVAL 150 DAY, CURRENT_TIMESTAMP, 0),
    (1003, 'demo_carol', 'Carol 音乐电台', '13800001003',
     'https://api.dicebear.com/9.x/avataaars/svg?seed=carol', 2, '2000-11-26',
     '今天也要认真听歌', 'user', 0,
     CURRENT_TIMESTAMP - INTERVAL 120 DAY, CURRENT_TIMESTAMP, 0),
    (1004, 'demo_dan', 'Dan 的运动日记', '13800001004',
     'https://api.dicebear.com/9.x/avataaars/svg?seed=dan', 1, '1997-05-12',
     '跑步、骑行、户外和健康生活', 'user', 0,
     CURRENT_TIMESTAMP - INTERVAL 90 DAY, CURRENT_TIMESTAMP, 0),
    (1005, 'demo_admin', '演示管理员', '13800001005',
     'https://api.dicebear.com/9.x/avataaars/svg?seed=admin', 0, '1995-01-01',
     '用于演示管理员权限', 'admin', 0,
     CURRENT_TIMESTAMP - INTERVAL 365 DAY, CURRENT_TIMESTAMP, 0),
    (1006, 'demo_eve', 'Eve 的美食实验室', '13800001006',
     'https://api.dicebear.com/9.x/avataaars/svg?seed=eve', 2, '1999-09-21',
     '简单食材也能做出好味道', 'user', 0,
     CURRENT_TIMESTAMP - INTERVAL 60 DAY, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    phone = VALUES(phone),
    avatar = VALUES(avatar),
    gender = VALUES(gender),
    birthday = VALUES(birthday),
    signature = VALUES(signature),
    role = VALUES(role),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO sys_user_auth
    (id, user_id, identity_type, identifier, credential, salt, verified,
     create_time, update_time)
VALUES
    (10001, 1001, 'phone', '13800001001', NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10002, 1001, 'password', 'demo_alice', '$2a$10$aYE7e1X1hFhTUUVYE3bS3uwXezqV0gg1Mudcu48OEaCtZpJBh3M22', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10003, 1002, 'phone', '13800001002', NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10004, 1002, 'password', 'demo_bob', '$2a$10$aYE7e1X1hFhTUUVYE3bS3uwXezqV0gg1Mudcu48OEaCtZpJBh3M22', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10005, 1003, 'phone', '13800001003', NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10006, 1003, 'password', 'demo_carol', '$2a$10$aYE7e1X1hFhTUUVYE3bS3uwXezqV0gg1Mudcu48OEaCtZpJBh3M22', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10007, 1004, 'phone', '13800001004', NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10008, 1004, 'password', 'demo_dan', '$2a$10$aYE7e1X1hFhTUUVYE3bS3uwXezqV0gg1Mudcu48OEaCtZpJBh3M22', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10009, 1005, 'phone', '13800001005', NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10010, 1005, 'password', 'demo_admin', '$2a$10$aYE7e1X1hFhTUUVYE3bS3uwXezqV0gg1Mudcu48OEaCtZpJBh3M22', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10011, 1006, 'phone', '13800001006', NULL, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10012, 1006, 'password', 'demo_eve', '$2a$10$aYE7e1X1hFhTUUVYE3bS3uwXezqV0gg1Mudcu48OEaCtZpJBh3M22', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    credential = VALUES(credential),
    verified = 1,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO video
    (id, user_id, title, description, cover_url, source_url, play_url,
     file_md5, file_size, duration, resolution, category_id, tags, status,
     audit_remark, create_time, update_time, deleted)
VALUES
    (2001, 1001, 'Spring Boot 微服务从零到一',
     '通过一个完整案例介绍服务拆分、配置管理、网关路由和服务发现。',
     'https://picsum.photos/seed/bili-2001/960/540',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     '11111111111111111111111111112001', 52428800, 312, '1080P', 7,
     'Spring Boot,Java,微服务', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 12 DAY, CURRENT_TIMESTAMP, 0),
    (2002, 1001, '城市夜景延时摄影教程',
     '从选址、参数设置到后期合成，完成一段城市夜景延时作品。',
     'https://picsum.photos/seed/bili-2002/960/540',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     '22222222222222222222222222222002', 73400320, 245, '4K', 8,
     '摄影,城市,教程', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 11 DAY, CURRENT_TIMESTAMP, 0),
    (2003, 1003, '适合学习时听的轻音乐',
     '30 分钟专注学习歌单，包含钢琴、木吉他与环境音。',
     'https://picsum.photos/seed/bili-2003/960/540',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     '33333333333333333333333333332003', 94371840, 1800, '1080P', 4,
     '音乐,学习,放松', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP, 0),
    (2004, 1002, 'Java 21 新特性实战',
     '用实际代码演示虚拟线程、记录模式和现代 Java 开发方式。',
     'https://picsum.photos/seed/bili-2004/960/540',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     '44444444444444444444444444442004', 68157440, 980, '1080P', 7,
     'Java,虚拟线程,编程', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP, 0),
    (2005, 1004, '零基础 5 公里跑步计划',
     '循序渐进的四周训练安排，适合第一次尝试长距离跑步的朋友。',
     'https://picsum.photos/seed/bili-2005/960/540',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     '55555555555555555555555555552005', 41943040, 420, '1080P', 9,
     '跑步,运动,健康', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 8 DAY, CURRENT_TIMESTAMP, 0),
    (2006, 1006, '十分钟学会番茄炒蛋',
     '家常番茄炒蛋的稳定做法，新手也能一次成功。',
     'https://picsum.photos/seed/bili-2006/960/540',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     '66666666666666666666666666662006', 26214400, 600, '1080P', 11,
     '美食,家常菜,教程', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 7 DAY, CURRENT_TIMESTAMP, 0),
    (2007, 1002, 'Docker Compose 本地开发最佳实践',
     '介绍多容器编排、健康检查、数据卷和常见网络问题。',
     'https://picsum.photos/seed/bili-2007/960/540',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     '77777777777777777777777777772007', 57671680, 760, '1080P', 8,
     'Docker,Compose,云原生', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 6 DAY, CURRENT_TIMESTAMP, 0),
    (2008, 1001, '周末露营 Vlog：湖边的一天',
     '记录搭帐篷、做饭、看日落和清晨湖面的周末露营。',
     'https://picsum.photos/seed/bili-2008/960/540',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     '88888888888888888888888888882008', 125829120, 1260, '4K', 10,
     '露营,Vlog,生活', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 5 DAY, CURRENT_TIMESTAMP, 0),
    (2009, 1003, '经典动画配乐赏析',
     '从旋律、配器和叙事角度，聊聊动画配乐为什么令人难忘。',
     'https://picsum.photos/seed/bili-2009/960/540',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     'https://media.w3.org/2010/05/sintel/trailer.mp4',
     '99999999999999999999999999992009', 83886080, 1020, '1080P', 1,
     '动画,音乐,影视配乐', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 4 DAY, CURRENT_TIMESTAMP, 0),
    (2010, 1004, '公路自行车入门装备清单',
     '从头盔、车灯到补胎工具，整理第一次骑行需要准备的装备。',
     'https://picsum.photos/seed/bili-2010/960/540',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     'https://media.w3.org/2010/05/bunny/trailer.mp4',
     'aaaaaaaaaaaaaaaaaaaaaaaaaaaa2010', 49283072, 530, '1080P', 9,
     '骑行,装备,入门', 1, NULL,
     CURRENT_TIMESTAMP - INTERVAL 3 DAY, CURRENT_TIMESTAMP, 0),
    (2011, 1001, '待审核：我的第一支短片',
     '用于演示用户投稿列表中的审核中状态。',
     'https://picsum.photos/seed/bili-2011/960/540',
     'https://media.w3.org/2010/05/sintel/trailer.mp4', NULL,
     'bbbbbbbbbbbbbbbbbbbbbbbbbbbb2011', 20971520, 180, '1080P', 12,
     '短片,投稿', 0, NULL,
     CURRENT_TIMESTAMP - INTERVAL 2 DAY, CURRENT_TIMESTAMP, 0),
    (2012, 1002, '审核未通过的演示视频',
     '用于演示审核拒绝状态，不会出现在公开列表。',
     'https://picsum.photos/seed/bili-2012/960/540',
     'https://media.w3.org/2010/05/bunny/trailer.mp4', NULL,
     'cccccccccccccccccccccccccccc2012', 18874368, 150, '720P', 10,
     '演示,审核', 2, '演示数据：封面信息需要调整',
     CURRENT_TIMESTAMP - INTERVAL 1 DAY, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    title = VALUES(title),
    description = VALUES(description),
    cover_url = VALUES(cover_url),
    source_url = VALUES(source_url),
    play_url = VALUES(play_url),
    file_md5 = VALUES(file_md5),
    file_size = VALUES(file_size),
    duration = VALUES(duration),
    resolution = VALUES(resolution),
    category_id = VALUES(category_id),
    tags = VALUES(tags),
    status = VALUES(status),
    audit_remark = VALUES(audit_remark),
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO video_stats
    (id, video_id, view_count, like_count, coin_count, collect_count,
     share_count, danmu_count, comment_count, update_time)
VALUES
    (2001, 2001, 128560, 8421, 680, 2301, 956, 8, 4, CURRENT_TIMESTAMP),
    (2002, 2002, 89630, 5210, 350, 1680, 720, 4, 2, CURRENT_TIMESTAMP),
    (2003, 2003, 236800, 15620, 920, 7340, 2100, 4, 2, CURRENT_TIMESTAMP),
    (2004, 2004, 175400, 12280, 1080, 5300, 1840, 5, 3, CURRENT_TIMESTAMP),
    (2005, 2005, 68300, 3650, 180, 980, 430, 2, 1, CURRENT_TIMESTAMP),
    (2006, 2006, 310200, 22800, 1320, 9900, 3400, 2, 2, CURRENT_TIMESTAMP),
    (2007, 2007, 142600, 9760, 760, 4120, 1250, 2, 2, CURRENT_TIMESTAMP),
    (2008, 2008, 97800, 6140, 410, 2860, 990, 2, 1, CURRENT_TIMESTAMP),
    (2009, 2009, 118900, 8560, 590, 3670, 1180, 1, 1, CURRENT_TIMESTAMP),
    (2010, 2010, 55200, 2890, 150, 730, 360, 1, 1, CURRENT_TIMESTAMP),
    (2011, 2011, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP),
    (2012, 2012, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    view_count = VALUES(view_count),
    like_count = VALUES(like_count),
    coin_count = VALUES(coin_count),
    collect_count = VALUES(collect_count),
    share_count = VALUES(share_count),
    danmu_count = VALUES(danmu_count),
    comment_count = VALUES(comment_count),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO file_chunk
    (id, upload_id, user_id, file_md5, chunk_md5, chunk_index, chunk_size,
     total_chunks, file_name, object_name, status, expire_time,
     create_time, update_time)
VALUES
    (8001, 'demo-upload-in-progress', 1001,
     'dddddddddddddddddddddddddddddddd', '11111111111111111111111111111111',
     0, 5242880, 3, 'demo-upload.mp4',
     'chunks/demo-upload-in-progress/0', 0,
     CURRENT_TIMESTAMP + INTERVAL 1 DAY, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8002, 'demo-upload-in-progress', 1001,
     'dddddddddddddddddddddddddddddddd', '22222222222222222222222222222222',
     1, 5242880, 3, 'demo-upload.mp4',
     'chunks/demo-upload-in-progress/1', 0,
     CURRENT_TIMESTAMP + INTERVAL 1 DAY, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8003, 'demo-upload-completed', 1002,
     'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', '33333333333333333333333333333333',
     0, 1048576, 1, 'completed-demo.mp4',
     'chunks/demo-upload-completed/0', 1,
     CURRENT_TIMESTAMP + INTERVAL 1 DAY, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    file_md5 = VALUES(file_md5),
    chunk_md5 = VALUES(chunk_md5),
    chunk_size = VALUES(chunk_size),
    total_chunks = VALUES(total_chunks),
    file_name = VALUES(file_name),
    object_name = VALUES(object_name),
    status = VALUES(status),
    expire_time = VALUES(expire_time),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO danmu
    (id, user_id, video_id, content, color, position, font_size, video_time,
     status, send_time)
VALUES
    (9001, 1003, 2001, '开场的架构图很清楚', '#FFFFFF', 0, 18, 12, 0, CURRENT_TIMESTAMP - INTERVAL 12 DAY),
    (9002, 1002, 2001, '网关这里建议记笔记', '#00FF88', 1, 20, 45, 0, CURRENT_TIMESTAMP - INTERVAL 12 DAY),
    (9003, 1004, 2001, '原来服务发现是这样工作的', '#66CCFF', 0, 18, 88, 0, CURRENT_TIMESTAMP - INTERVAL 12 DAY),
    (9004, 1006, 2001, '这个案例很适合入门', '#FFAA00', 2, 18, 132, 0, CURRENT_TIMESTAMP - INTERVAL 12 DAY),
    (9005, 1001, 2001, '后面的消息队列也很重要', '#FFFFFF', 0, 16, 190, 0, CURRENT_TIMESTAMP - INTERVAL 12 DAY),
    (9006, 1003, 2001, '讲得真详细', '#FF88CC', 0, 18, 245, 0, CURRENT_TIMESTAMP - INTERVAL 12 DAY),
    (9007, 1002, 2001, '收藏了，回头复习', '#66CCFF', 1, 20, 280, 0, CURRENT_TIMESTAMP - INTERVAL 12 DAY),
    (9008, 1006, 2001, '完结撒花', '#FFFFFF', 0, 18, 305, 0, CURRENT_TIMESTAMP - INTERVAL 12 DAY),
    (9009, 1004, 2002, '夜景参数很实用', '#FFFF00', 0, 18, 25, 0, CURRENT_TIMESTAMP - INTERVAL 11 DAY),
    (9010, 1003, 2002, '这个机位太漂亮了', '#FF88CC', 1, 20, 86, 0, CURRENT_TIMESTAMP - INTERVAL 11 DAY),
    (9011, 1002, 2002, '后期流程学到了', '#66CCFF', 0, 18, 150, 0, CURRENT_TIMESTAMP - INTERVAL 11 DAY),
    (9012, 1006, 2002, '下次也去试试', '#FFFFFF', 2, 18, 220, 0, CURRENT_TIMESTAMP - INTERVAL 11 DAY),
    (9013, 1001, 2003, '戴上耳机效果更好', '#FFFFFF', 0, 18, 32, 0, CURRENT_TIMESTAMP - INTERVAL 10 DAY),
    (9014, 1002, 2003, '开始专注学习', '#00FF88', 1, 20, 180, 0, CURRENT_TIMESTAMP - INTERVAL 10 DAY),
    (9015, 1004, 2003, '钢琴段落很好听', '#66CCFF', 0, 18, 620, 0, CURRENT_TIMESTAMP - INTERVAL 10 DAY),
    (9016, 1006, 2003, '已经循环播放了', '#FF88CC', 2, 18, 1250, 0, CURRENT_TIMESTAMP - INTERVAL 10 DAY),
    (9017, 1001, 2004, '虚拟线程终于讲明白了', '#FFFFFF', 0, 18, 40, 0, CURRENT_TIMESTAMP - INTERVAL 9 DAY),
    (9018, 1003, 2004, '代码示例很直观', '#FFFF00', 0, 18, 210, 0, CURRENT_TIMESTAMP - INTERVAL 9 DAY),
    (9019, 1004, 2004, '期待更多 Java 内容', '#66CCFF', 1, 20, 510, 0, CURRENT_TIMESTAMP - INTERVAL 9 DAY),
    (9020, 1006, 2004, '先收藏再学习', '#FF88CC', 0, 18, 760, 0, CURRENT_TIMESTAMP - INTERVAL 9 DAY),
    (9021, 1002, 2004, '最后的总结很完整', '#FFFFFF', 2, 18, 940, 0, CURRENT_TIMESTAMP - INTERVAL 9 DAY),
    (9022, 1001, 2005, '第一周计划打卡', '#00FF88', 0, 18, 65, 0, CURRENT_TIMESTAMP - INTERVAL 8 DAY),
    (9023, 1003, 2005, '热身动作很重要', '#FFFFFF', 1, 20, 180, 0, CURRENT_TIMESTAMP - INTERVAL 8 DAY),
    (9024, 1004, 2006, '今天晚饭就做这个', '#FFAA00', 0, 18, 120, 0, CURRENT_TIMESTAMP - INTERVAL 7 DAY),
    (9025, 1001, 2006, '看起来太下饭了', '#FFFFFF', 2, 18, 420, 0, CURRENT_TIMESTAMP - INTERVAL 7 DAY),
    (9026, 1003, 2007, '健康检查这段很有用', '#66CCFF', 0, 18, 210, 0, CURRENT_TIMESTAMP - INTERVAL 6 DAY),
    (9027, 1004, 2007, '数据卷的坑踩过了', '#FFFF00', 1, 20, 460, 0, CURRENT_TIMESTAMP - INTERVAL 6 DAY),
    (9028, 1006, 2008, '日落镜头太治愈了', '#FF88CC', 0, 18, 500, 0, CURRENT_TIMESTAMP - INTERVAL 5 DAY),
    (9029, 1002, 2008, '露营装备求清单', '#FFFFFF', 0, 18, 900, 0, CURRENT_TIMESTAMP - INTERVAL 5 DAY),
    (9030, 1001, 2009, '熟悉的旋律响起来了', '#66CCFF', 1, 20, 360, 0, CURRENT_TIMESTAMP - INTERVAL 4 DAY),
    (9031, 1003, 2010, '补胎工具确实必备', '#FFFF00', 0, 18, 260, 0, CURRENT_TIMESTAMP - INTERVAL 3 DAY)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    video_id = VALUES(video_id),
    content = VALUES(content),
    color = VALUES(color),
    position = VALUES(position),
    font_size = VALUES(font_size),
    video_time = VALUES(video_time),
    status = 0,
    send_time = VALUES(send_time);

INSERT INTO comment
    (id, user_id, video_id, parent_id, reply_to_id, content, like_count,
     status, create_time, update_time, deleted)
VALUES
    (3001, 1003, 2001, 0, 0, '结构清晰，跟着项目做一遍收获很大。', 128, 0, CURRENT_TIMESTAMP - INTERVAL 11 DAY, CURRENT_TIMESTAMP, 0),
    (3002, 1002, 2001, 3001, 1003, '同感，尤其是服务发现和网关这两部分。', 36, 0, CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP, 0),
    (3003, 1004, 2001, 3001, 1003, '我还把 RocketMQ 的部分单独做了笔记。', 21, 0, CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP, 0),
    (3004, 1006, 2001, 0, 0, '希望后续增加线上部署和监控章节。', 76, 0, CURRENT_TIMESTAMP - INTERVAL 8 DAY, CURRENT_TIMESTAMP, 0),
    (3005, 1001, 2002, 0, 0, '成片很有氛围，参数说明也足够详细。', 54, 0, CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP, 0),
    (3006, 1004, 2002, 3005, 1001, '准备周末带相机去试一下。', 12, 0, CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP, 0),
    (3007, 1002, 2003, 0, 0, '写代码时听刚刚好，不会分心。', 203, 0, CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP, 0),
    (3008, 1006, 2003, 3007, 1002, '已经加入我的工作歌单了。', 43, 0, CURRENT_TIMESTAMP - INTERVAL 8 DAY, CURRENT_TIMESTAMP, 0),
    (3009, 1001, 2004, 0, 0, 'Java 21 的虚拟线程示例非常实用。', 166, 0, CURRENT_TIMESTAMP - INTERVAL 8 DAY, CURRENT_TIMESTAMP, 0),
    (3010, 1003, 2004, 3009, 1001, '升级项目时可以参考这套写法。', 29, 0, CURRENT_TIMESTAMP - INTERVAL 7 DAY, CURRENT_TIMESTAMP, 0),
    (3011, 1004, 2004, 0, 0, '讲解节奏很好，示例也可以直接运行。', 98, 0, CURRENT_TIMESTAMP - INTERVAL 6 DAY, CURRENT_TIMESTAMP, 0),
    (3012, 1003, 2005, 0, 0, '已经按照计划完成第一次训练。', 62, 0, CURRENT_TIMESTAMP - INTERVAL 6 DAY, CURRENT_TIMESTAMP, 0),
    (3013, 1001, 2006, 0, 0, '照着做成功了，味道很不错。', 310, 0, CURRENT_TIMESTAMP - INTERVAL 5 DAY, CURRENT_TIMESTAMP, 0),
    (3014, 1002, 2006, 3013, 1001, '控制好火候确实是关键。', 47, 0, CURRENT_TIMESTAMP - INTERVAL 4 DAY, CURRENT_TIMESTAMP, 0),
    (3015, 1001, 2007, 0, 0, '建议把这期作为 Docker 入门必看。', 145, 0, CURRENT_TIMESTAMP - INTERVAL 4 DAY, CURRENT_TIMESTAMP, 0),
    (3016, 1004, 2007, 3015, 1001, '特别是端口冲突排查部分。', 25, 0, CURRENT_TIMESTAMP - INTERVAL 3 DAY, CURRENT_TIMESTAMP, 0),
    (3017, 1002, 2008, 0, 0, '看完想立刻安排一次露营。', 88, 0, CURRENT_TIMESTAMP - INTERVAL 3 DAY, CURRENT_TIMESTAMP, 0),
    (3018, 1006, 2009, 0, 0, '配乐和画面的关系分析得很好。', 73, 0, CURRENT_TIMESTAMP - INTERVAL 2 DAY, CURRENT_TIMESTAMP, 0),
    (3019, 1003, 2010, 0, 0, '装备清单很完整，对新人友好。', 41, 0, CURRENT_TIMESTAMP - INTERVAL 1 DAY, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    video_id = VALUES(video_id),
    parent_id = VALUES(parent_id),
    reply_to_id = VALUES(reply_to_id),
    content = VALUES(content),
    like_count = VALUES(like_count),
    status = 0,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO follow
    (id, follower_id, followed_id, status, create_time, update_time)
VALUES
    (4001, 1001, 1002, 1, CURRENT_TIMESTAMP - INTERVAL 80 DAY, CURRENT_TIMESTAMP),
    (4002, 1001, 1003, 1, CURRENT_TIMESTAMP - INTERVAL 70 DAY, CURRENT_TIMESTAMP),
    (4003, 1002, 1001, 1, CURRENT_TIMESTAMP - INTERVAL 60 DAY, CURRENT_TIMESTAMP),
    (4004, 1002, 1004, 1, CURRENT_TIMESTAMP - INTERVAL 50 DAY, CURRENT_TIMESTAMP),
    (4005, 1003, 1001, 1, CURRENT_TIMESTAMP - INTERVAL 40 DAY, CURRENT_TIMESTAMP),
    (4006, 1003, 1002, 1, CURRENT_TIMESTAMP - INTERVAL 35 DAY, CURRENT_TIMESTAMP),
    (4007, 1004, 1001, 1, CURRENT_TIMESTAMP - INTERVAL 30 DAY, CURRENT_TIMESTAMP),
    (4008, 1004, 1006, 1, CURRENT_TIMESTAMP - INTERVAL 25 DAY, CURRENT_TIMESTAMP),
    (4009, 1006, 1001, 1, CURRENT_TIMESTAMP - INTERVAL 20 DAY, CURRENT_TIMESTAMP),
    (4010, 1006, 1003, 1, CURRENT_TIMESTAMP - INTERVAL 15 DAY, CURRENT_TIMESTAMP),
    (4011, 1005, 1001, 1, CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP),
    (4012, 1001, 1006, 0, CURRENT_TIMESTAMP - INTERVAL 45 DAY, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    follower_id = VALUES(follower_id),
    followed_id = VALUES(followed_id),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO user_like
    (id, user_id, target_type, target_id, status, create_time, update_time)
VALUES
    (5001, 1001, 1, 2003, 1, CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP),
    (5002, 1001, 1, 2004, 1, CURRENT_TIMESTAMP - INTERVAL 8 DAY, CURRENT_TIMESTAMP),
    (5003, 1002, 1, 2001, 1, CURRENT_TIMESTAMP - INTERVAL 11 DAY, CURRENT_TIMESTAMP),
    (5004, 1002, 1, 2006, 1, CURRENT_TIMESTAMP - INTERVAL 6 DAY, CURRENT_TIMESTAMP),
    (5005, 1003, 1, 2001, 1, CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP),
    (5006, 1003, 1, 2007, 1, CURRENT_TIMESTAMP - INTERVAL 5 DAY, CURRENT_TIMESTAMP),
    (5007, 1004, 1, 2002, 1, CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP),
    (5008, 1004, 1, 2005, 1, CURRENT_TIMESTAMP - INTERVAL 7 DAY, CURRENT_TIMESTAMP),
    (5009, 1006, 1, 2003, 1, CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP),
    (5010, 1006, 1, 2008, 1, CURRENT_TIMESTAMP - INTERVAL 4 DAY, CURRENT_TIMESTAMP),
    (5011, 1001, 2, 3001, 1, CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP),
    (5012, 1002, 2, 3004, 1, CURRENT_TIMESTAMP - INTERVAL 7 DAY, CURRENT_TIMESTAMP),
    (5013, 1003, 2, 3009, 1, CURRENT_TIMESTAMP - INTERVAL 7 DAY, CURRENT_TIMESTAMP),
    (5014, 1004, 2, 3013, 1, CURRENT_TIMESTAMP - INTERVAL 4 DAY, CURRENT_TIMESTAMP),
    (5015, 1006, 2, 3015, 1, CURRENT_TIMESTAMP - INTERVAL 3 DAY, CURRENT_TIMESTAMP),
    (5016, 1001, 1, 2010, 0, CURRENT_TIMESTAMP - INTERVAL 2 DAY, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    target_type = VALUES(target_type),
    target_id = VALUES(target_id),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO collection_folder
    (id, user_id, name, is_public, description, cover_url, video_count,
     create_time, update_time, deleted)
VALUES
    (6001, 1001, '稍后学习', 0, '准备认真学习的技术视频',
     'https://picsum.photos/seed/folder-6001/640/360', 2,
     CURRENT_TIMESTAMP - INTERVAL 30 DAY, CURRENT_TIMESTAMP, 0),
    (6002, 1001, '摄影与生活', 1, '摄影教程和生活记录',
     'https://picsum.photos/seed/folder-6002/640/360', 2,
     CURRENT_TIMESTAMP - INTERVAL 25 DAY, CURRENT_TIMESTAMP, 0),
    (6003, 1003, '我的音乐收藏', 1, '适合循环播放的音乐内容',
     'https://picsum.photos/seed/folder-6003/640/360', 2,
     CURRENT_TIMESTAMP - INTERVAL 20 DAY, CURRENT_TIMESTAMP, 0),
    (6004, 1002, '工程实践', 0, 'Java、Docker 与微服务',
     'https://picsum.photos/seed/folder-6004/640/360', 2,
     CURRENT_TIMESTAMP - INTERVAL 15 DAY, CURRENT_TIMESTAMP, 0),
    (6005, 1004, '运动计划', 1, '跑步与骑行训练资料',
     'https://picsum.photos/seed/folder-6005/640/360', 2,
     CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP, 0),
    (6006, 1006, '美食灵感', 1, '想要复刻的家常菜',
     'https://picsum.photos/seed/folder-6006/640/360', 1,
     CURRENT_TIMESTAMP - INTERVAL 5 DAY, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    name = VALUES(name),
    is_public = VALUES(is_public),
    description = VALUES(description),
    cover_url = VALUES(cover_url),
    video_count = VALUES(video_count),
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO collection
    (id, user_id, video_id, folder_id, status, create_time, update_time)
VALUES
    (7001, 1001, 2003, 6001, 1, CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP),
    (7002, 1001, 2004, 6001, 1, CURRENT_TIMESTAMP - INTERVAL 8 DAY, CURRENT_TIMESTAMP),
    (7003, 1001, 2002, 6002, 1, CURRENT_TIMESTAMP - INTERVAL 10 DAY, CURRENT_TIMESTAMP),
    (7004, 1001, 2008, 6002, 1, CURRENT_TIMESTAMP - INTERVAL 4 DAY, CURRENT_TIMESTAMP),
    (7005, 1003, 2003, 6003, 1, CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP),
    (7006, 1003, 2009, 6003, 1, CURRENT_TIMESTAMP - INTERVAL 3 DAY, CURRENT_TIMESTAMP),
    (7007, 1002, 2001, 6004, 1, CURRENT_TIMESTAMP - INTERVAL 11 DAY, CURRENT_TIMESTAMP),
    (7008, 1002, 2007, 6004, 1, CURRENT_TIMESTAMP - INTERVAL 5 DAY, CURRENT_TIMESTAMP),
    (7009, 1004, 2005, 6005, 1, CURRENT_TIMESTAMP - INTERVAL 7 DAY, CURRENT_TIMESTAMP),
    (7010, 1004, 2010, 6005, 1, CURRENT_TIMESTAMP - INTERVAL 2 DAY, CURRENT_TIMESTAMP),
    (7011, 1006, 2006, 6006, 1, CURRENT_TIMESTAMP - INTERVAL 6 DAY, CURRENT_TIMESTAMP),
    (7012, 1001, 2005, 0, 1, CURRENT_TIMESTAMP - INTERVAL 7 DAY, CURRENT_TIMESTAMP),
    (7013, 1003, 2008, 0, 1, CURRENT_TIMESTAMP - INTERVAL 4 DAY, CURRENT_TIMESTAMP),
    (7014, 1006, 2002, 0, 0, CURRENT_TIMESTAMP - INTERVAL 3 DAY, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    video_id = VALUES(video_id),
    folder_id = VALUES(folder_id),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP;

-- Re-emit final video and statistics row events after all related demo rows.
-- When Canal is running, these updates make search documents and cached counts
-- converge to the baseline values even when this seed script is re-run.
UPDATE video
SET update_time = CURRENT_TIMESTAMP
WHERE id BETWEEN 2001 AND 2012;

UPDATE video_stats
SET update_time = CURRENT_TIMESTAMP
WHERE video_id BETWEEN 2001 AND 2012;

COMMIT;

SET FOREIGN_KEY_CHECKS = 1;
